package com.sewagetrace.app.ai

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.sewagetrace.app.data.AppContextHolder
import com.sewagetrace.app.data.Settings

/** 一次识别的最终产出：结果 + 实际使用的引擎 + 回退原因（用于 UI 提示） */
data class EngineOutcome(val result: AnalysisResult, val usedCloud: Boolean, val fallback: String?)

object NetworkState {
    fun online(): Boolean = try {
        val cm = AppContextHolder.ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    } catch (e: Exception) {
        false
    }
}

/**
 * 引擎路由：
 *  AUTO  —— 联网 + 有 key → 云端，失败自动回退本机；
 *  LOCAL —— 只用本机；
 *  CLOUD —— 只用云端，失败回退本机（宁可给个结论也不让流程死掉，但会明确标注回退）。
 */
class AutoClassifier(private val scenePrior: ScenePrior = ScenePrior.none()) {

    private val local = OnDeviceClassifier(scenePrior)

    fun run(photoPath: String): EngineOutcome {
        val mode = Settings.aiMode
        val key = Settings.apiKey
        val gateway = Settings.gatewayUrl
        val proxy = key.startsWith(Settings.PROXY_PREFIX)

        // 网关模式必须同时有令牌和网关地址，缺一个就当没配
        val configured = key.isNotBlank() && (!proxy || gateway.isNotBlank())
        val wantCloud = mode == Settings.AI_CLOUD || (mode == Settings.AI_AUTO && configured)

        if (!wantCloud || !configured) {
            val reason = when {
                mode == Settings.AI_CLOUD && key.isBlank() -> "no_key"
                proxy && gateway.isBlank() -> "no_gateway"
                mode == Settings.AI_LOCAL -> "forced_local"
                else -> "no_key"
            }
            return EngineOutcome(local.analyze(photoPath), usedCloud = false, fallback = reason)
        }
        if (!NetworkState.online()) {
            return EngineOutcome(local.analyze(photoPath), usedCloud = false, fallback = "no_network")
        }

        return try {
            val r = if (proxy) {
                CloudClassifier(
                    apiKey = key,
                    model = Settings.cloudModel,
                    baseUrl = gateway,
                    proxyMode = true
                ).analyze(photoPath)
            } else {
                CloudClassifier(key).analyze(photoPath)
            }
            EngineOutcome(r, usedCloud = true, fallback = null)
        } catch (e: CloudException) {
            EngineOutcome(local.analyze(photoPath), usedCloud = false, fallback = e.reason)
        } catch (e: Exception) {
            EngineOutcome(local.analyze(photoPath), usedCloud = false, fallback = "io")
        }
    }
}
