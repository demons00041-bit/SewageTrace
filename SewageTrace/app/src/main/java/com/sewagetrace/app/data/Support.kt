package com.sewagetrace.app.data

import androidx.annotation.StringRes
import com.sewagetrace.app.R
import com.sewagetrace.app.ai.AnalysisResult
import com.sewagetrace.app.ai.DemoScene
import com.sewagetrace.app.model.Report
import com.sewagetrace.app.model.Ticket

/** 上报尝试的结果分级，UI 据此展示不同文案 */
sealed class SubmitOutcome {
    /** 被拦截（低置信度 / 重复 / 限流），[reasonRes] 是驳回原因资源 */
    data class Blocked(@StringRes val reasonRes: Int) : SubmitOutcome()

    /** 未达自动上报阈值，只存草稿 */
    data class DraftOnly(@StringRes val reasonRes: Int) : SubmitOutcome()

    /** 已上报（可能同时建了工单） */
    data class Reported(
        val report: Report,
        val ticket: Ticket?,
        val autoReported: Boolean,
        val escalated: Boolean
    ) : SubmitOutcome()

    /** 失败（IO 错误等），[detail] 是原始异常信息，仅用于排障 */
    data class Failed(val detail: String) : SubmitOutcome()
}

/** 识别引擎模式 */
enum class AnalysisMode(val key: String, @StringRes val labelRes: Int) {
    AUTO(Settings.AI_AUTO, R.string.mode_auto),
    CLOUD(Settings.AI_CLOUD, R.string.mode_cloud),
    LOCAL(Settings.AI_LOCAL, R.string.mode_local);

    companion object {
        fun of(key: String?): AnalysisMode = entries.firstOrNull { it.key == key } ?: AUTO
    }
}

/** 按城市显示当前热门抓拍场景（吉隆坡优先重度工业段） */
fun defaultSceneForCity(city: String): DemoScene = when {
    city.contains("Petaling", ignoreCase = true) -> DemoScene.MODERATE
    city.contains("Shah Alam", ignoreCase = true) -> DemoScene.MILD
    else -> DemoScene.HEAVY
}

/** 把引擎结果整理成一行"引擎说明"给 UI 用 */
fun AnalysisResult.engineLabel(): String = when (engine) {
    Report.ENGINE_CLOUD -> "deepseek-flash"
    else -> modelVersion
}
