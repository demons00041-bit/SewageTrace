package com.sewagetrace.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/**
 * 应用设置（全部本地持久化）。
 *
 * 语言切换用 per-app language（AppCompatDelegate.setApplicationLocales），
 * 由系统失效所有 Activity，stringResource 立即按新 locale 取值；
 * 数据层只保存资源 id 与格式化参数，因此历史工单日志也会同步换语言。
 */
object Settings {

    private const val PREF = "sewagetrace_settings_v1"

    const val AI_AUTO = "auto"
    const val AI_LOCAL = "local"
    const val AI_CLOUD = "cloud"

    const val MODEL = "deepseek-flash"
    const val BASE_URL = "https://api.deepseek.com"

    /** 网关令牌前缀：`st:` = SewageTrace token。用于和“直连原厂 key”区分开。 */
    const val PROXY_PREFIX = "st:"

    private val prefs: SharedPreferences
        get() = AppContextHolder.ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    // ---------------------------------------------------------------- 语言
    fun currentLang(): String =
        AppCompatDelegate.getApplicationLocales().toLanguageTags().takeIf { it.isNotBlank() }
            ?: prefs.getString("lang", "en")!!

    fun setLanguage(tag: String) {
        prefs.edit().putString("lang", tag).apply()
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
    }

    fun applySavedLanguage() {
        val saved = prefs.getString("lang", "en") ?: "en"
        if (AppCompatDelegate.getApplicationLocales().toLanguageTags() != saved) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(saved))
        }
    }

    // ---------------------------------------------------------------- 上报
    var autoReport: Boolean
        get() = prefs.getBoolean("auto_report", true)
        set(v) = prefs.edit().putBoolean("auto_report", v).apply()

    var anonymous: Boolean
        get() = prefs.getBoolean("anonymous", false)
        set(v) = prefs.edit().putBoolean("anonymous", v).apply()

    var locationPrecision: Int
        get() = prefs.getInt("precision", 50)
        set(v) = prefs.edit().putInt("precision", v).apply()

    // ---------------------------------------------------------------- 识别引擎
    var aiMode: String
        get() = prefs.getString("ai_mode", AI_AUTO)!!
        set(v) = prefs.edit().putString("ai_mode", v).apply()

    /**
     * 云端密钥。三种形态合法：
     *  - 空          → 未配置
     *  - `st:` 前缀  → 指向自建反代网关（推荐，APP 内不出现任何第三方密钥）
     *  - 其它        → 直连模式的原厂 key（仅本地调试，会在 UI 上警示）
     * 注意：密钥在写入时用 Keystore 派生密钥加密（[SecretStore]），绝不落明文。
     */
    var apiKey: String
        get() = SecretStore.read(prefs, "api_key")
        set(v) {
            val t = v.trim()
            if (t.isEmpty()) SecretStore.clear(prefs, "api_key") else SecretStore.write(prefs, "api_key", t)
        }

    fun hasApiKey(): Boolean = apiKey.isNotBlank()

    /** 反代网关地址，如 https://gw.example.com （必须以 http(s):// 开头）。 */
    var gatewayUrl: String
        get() = prefs.getString("gateway_url", "")!!.trim()
        set(v) = prefs.edit().putString("gateway_url", v.trim().trimEnd('/')).apply()

    /** 走网关时请求的模型名（由网关白名单决定，客户端不可任意指定）。 */
    var cloudModel: String
        get() = prefs.getString("cloud_model", MODEL)!!
        set(v) = prefs.edit().putString("cloud_model", v.trim().ifBlank { MODEL }).apply()

    fun isProxyMode(): Boolean = apiKey.startsWith(PROXY_PREFIX)

    /** 生成新的设备令牌（换设备/疑似泄露时用）。 */
    fun rotateDeviceToken(): String {
        val bytes = ByteArray(24)
        java.security.SecureRandom().nextBytes(bytes)
        val hex = bytes.joinToString("") { "%02x".format(it) }
        return PROXY_PREFIX + hex
    }

    // ---------------------------------------------------------------- 会话定位
    var hasSession: Boolean
        get() = prefs.getBoolean("has_session", false)
        set(v) = prefs.edit().putBoolean("has_session", v).apply()
}
