package com.sewagetrace.app.ai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.sewagetrace.app.data.Settings
import com.sewagetrace.app.model.Confidence
import com.sewagetrace.app.model.Evidence
import com.sewagetrace.app.model.EvidenceDim
import com.sewagetrace.app.model.Report
import com.sewagetrace.app.model.SeverityLevel
import com.sewagetrace.app.model.SuspectedType
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/** 云端识别失败时抛出，[reason] 为可直接用于日志/提示的短代码 */
class CloudException(val reason: String, message: String) : Exception(message)

/**
 * 云端视觉识别：DeepSeek `deepseek-flash`，OpenAI 兼容 /chat/completions。
 *
 * 关键约束（来自官方文档）：
 *  - 图片只能出现在 user 消息里，system/assistant 放图会 400；
 *  - 传图用 base64 data URL，单图 ≤ 32 MiB，请求体 ≤ 48 MiB；
 *  - 每张图片会被缩放到约 1300×1300，最多计 1024 tokens，所以本地先压到长边 1280 就能省流量；
 *  - `response_format: json_object` 保证返回可直接解析的 JSON。
 *
 * 这个类只做“一次网络调用 + 一次解析”，不做重试和回退——回退由 [AutoClassifier] 负责。
 */
class CloudClassifier(
    private val apiKey: String,
    private val model: String = Settings.MODEL,
    baseUrl: String = Settings.BASE_URL,
    private val timeoutSeconds: Int = 60,
    /**
     * 反代网关模式：为 true 时 [baseUrl] 指向自建网关，[apiKey] 是本应用自签的设备令牌
     * （`st:` 前缀），请求头用 `X-ST-Token`，**不带** `Authorization: Bearer`。
     * 服务器密钥永远留在网关侧，APK 里不存在任何第三方密钥。
     */
    private val proxyMode: Boolean = apiKey.startsWith(Settings.PROXY_PREFIX)
) : SeverityClassifier {

    /** 网关地址本身可能带子路径（如 /ai），统一去掉尾部斜杠 */
    private val baseUrl: String = baseUrl.trimEnd('/')

    override val name = if (proxyMode) "gateway" else "cloud"
    override val modelVersion get() = model

    /** 统一的鉴权头：网关用设备令牌，直连才用 Bearer */
    private fun authHeaders(c: HttpURLConnection) {
        if (proxyMode) {
            c.setRequestProperty("X-ST-Token", apiKey)
            c.setRequestProperty("X-ST-Client", "android")
        } else {
            c.setRequestProperty("Authorization", "Bearer $apiKey")
        }
    }

    private fun completionsUrl(): String =
        if (proxyMode) "$baseUrl/v1/ai/analyze" else "$baseUrl/chat/completions"

    private fun modelsUrl(): String =
        if (proxyMode) "$baseUrl/v1/ai/health" else "$baseUrl/models"

    override fun analyze(photoPath: String): AnalysisResult {
        if (apiKey.isBlank()) throw CloudException("no_key", "API key not configured")
        val t0 = System.currentTimeMillis()
        val dataUrl = encodeImage(photoPath)

        val body = JSONObject().apply {
            // 网关模式也照常带 model，网关会用白名单校验，不认就直接改写
            put("model", model)
            put("temperature", 0.1)
            put("max_tokens", 2000)
            put("stream", false)
            put("response_format", JSONObject().put("type", "json_object"))
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", JSONArray().apply {
                        put(JSONObject().apply {
                            put("type", "text")
                            put("text", PROMPT + "\nObservation language requirement: " + languageName() + ".")
                        })
                        put(JSONObject().apply {
                            put("type", "image_url")
                            put("image_url", JSONObject().apply {
                                put("url", dataUrl)
                                put("detail", "high")
                            })
                        })
                    })
                })
            })
        }

        val raw = post(body.toString().toByteArray(Charsets.UTF_8))
        val content = extractContent(raw)
        return parse(content, System.currentTimeMillis() - t0, languageName())
    }

    // ---------------------------------------------------------------- HTTP

    /**
     * 轻量连通性测试：GET /models。
     * 不产生图像 token，只用来确认 key 是否有效、网络是否可达。
     * 返回一段可直接展示给用户的说明（含可用模型数量与是否含目标模型）。
     */
    fun ping(): String {
        val conn = (URL(modelsUrl()).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = TimeUnit.SECONDS.toMillis(15).toInt()
            readTimeout = TimeUnit.SECONDS.toMillis(15).toInt()
            setRequestProperty("Accept", "application/json")
            authHeaders(this)
        }
        try {
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) {
                val short = runCatching {
                    JSONObject(text).getJSONObject("error").optString("message")
                }.getOrNull().orEmpty().ifBlank { text.take(200) }
                throw CloudException("http_$code", "HTTP $code: $short")
            }
            if (proxyMode) {
                // 网关健康检查：{ "ok": true, "models": ["deepseek-flash"], "quota_left": 37 }
                val root = runCatching { JSONObject(text) }.getOrNull()
                val arr = root?.optJSONArray("models")
                val ids = buildList {
                    if (arr != null) for (i in 0 until arr.length()) {
                        arr.optString(i).takeIf { it.isNotBlank() }?.let(::add)
                    }
                }
                val quota = root?.optInt("quota_left", -1) ?: -1
                return buildString {
                    append("Gateway OK · ")
                    if (ids.isEmpty()) append("no model advertised") else append(ids.joinToString())
                    if (quota >= 0) append(" · quota ").append(quota)
                }
            }
            val arr = runCatching { JSONObject(text).optJSONArray("data") }.getOrNull()
            val ids = buildList {
                if (arr != null) for (i in 0 until arr.length()) {
                    arr.optJSONObject(i)?.optString("id")?.takeIf { it.isNotBlank() }?.let(::add)
                }
            }
            val hasModel = ids.any { it == model }
            return buildString {
                append("HTTP 200 · ").append(ids.size).append(" models")
                if (hasModel) append(" · ").append(model).append(" available")
                else if (ids.isNotEmpty()) append(" · ").append(model).append(" not in list")
            }
        } catch (e: CloudException) {
            throw e
        } catch (e: java.net.SocketTimeoutException) {
            throw CloudException("timeout", "request timed out")
        } catch (e: java.net.UnknownHostException) {
            throw CloudException("no_network", "network unavailable")
        } catch (e: java.io.IOException) {
            throw CloudException("io", "network error: ${e.message}")
        } finally {
            conn.disconnect()
        }
    }

    private fun post(payload: ByteArray): String {
        val conn = (URL(completionsUrl()).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = TimeUnit.SECONDS.toMillis(20).toInt()
            readTimeout = TimeUnit.SECONDS.toMillis(timeoutSeconds.toLong()).toInt()
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
            authHeaders(this)
        }
        try {
            conn.outputStream.use { it.write(payload) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) {
                val short = runCatching {
                    JSONObject(text).getJSONObject("error").optString("message")
                }.getOrNull().orEmpty().ifBlank { text.take(200) }
                throw CloudException("http_$code", "HTTP $code: $short")
            }
            return text
        } catch (e: CloudException) {
            throw e
        } catch (e: java.net.SocketTimeoutException) {
            throw CloudException("timeout", "request timed out", )
        } catch (e: java.net.UnknownHostException) {
            throw CloudException("no_network", "network unavailable")
        } catch (e: java.io.IOException) {
            throw CloudException("io", "network error: ${e.message}")
        } finally {
            conn.disconnect()
        }
    }

    private fun extractContent(json: String): String {
        val root = try {
            JSONObject(json)
        } catch (e: Exception) {
            throw CloudException("bad_json", "response is not JSON")
        }
        val choices = root.optJSONArray("choices") ?: throw CloudException("no_choices", "response has no choices")
        if (choices.length() == 0) throw CloudException("no_choices", "empty choices")
        val choice = choices.getJSONObject(0)
        val msg = choice.optJSONObject("message")
            ?: throw CloudException("no_message", "choice has no message")
        val content = msg.optString("content", "")
        if (content.isBlank()) {
            // 便于定位：把 finish_reason / 推理内容长度 / usage 一并带出来
            val finish = choice.optString("finish_reason", "?")
            val reasoning = msg.optString("reasoning_content", "")
            val usage = root.optJSONObject("usage")?.toString().orEmpty()
            throw CloudException(
                "empty_content",
                "finish=$finish reasoningLen=${reasoning.length} usage=$usage raw=${json.take(300)}"
            )
        }
        return content
    }

    // ---------------------------------------------------------------- 解析

    /**
     * 解析模型返回。注意模型偶尔会在 JSON 外层包 ``` 或解说文字，[unwrap] 会剥离。
     * [language] 仅用于日志，不参与逻辑。
     */
    private fun parse(content: String, elapsed: Long, language: String = "English"): AnalysisResult {
        val json = unwrap(content)
        val o = try {
            JSONObject(json)
        } catch (e: Exception) {
            throw CloudException("bad_model_json", "content=${content.take(400)}")
        }

        val idx = o.optDouble("index", -1.0)
        if (idx.isNaN() || idx < 0) throw CloudException("bad_index", "index missing")
        val index = idx.toInt().coerceIn(0, 100)

        val level = runCatching { SeverityLevel.valueOf(o.optString("level", "").uppercase()) }
            .getOrElse { SeverityLevel.of(index) }

        val confidence = when (o.optString("confidence", "medium").lowercase()) {
            "high" -> Confidence.HIGH
            "low" -> Confidence.LOW
            else -> Confidence.MEDIUM
        }

        val suspected = SuspectedType.byId(o.optString("suspected_type", "unknown"))

        val evidence = mutableListOf<Evidence>()
        val arr = o.optJSONArray("evidence")
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val e = arr.optJSONObject(i) ?: continue
                val text = e.optString("observation", "").trim()
                if (text.isBlank()) continue
                evidence += Evidence(
                    dim = dimOf(e.optString("dimension", "")),
                    weight = e.optDouble("score", 0.0).toInt().coerceIn(0, 100),
                    noteRes = text,
                    noteResId = 0
                )
            }
        }
        if (evidence.isEmpty()) {
            evidence += Evidence(
                EvidenceDim.WATER_STATE, index,
                noteRes = o.optString("summary", "").ifBlank { "n/a" }, noteResId = 0
            )
        }

        // 非水体：保留结果但标记 isNonWater，由 ReportFlow 阻止上报
        return AnalysisResult(
            index = index,
            level = if (o.has("is_water") && !o.optBoolean("is_water", true)) SeverityLevel.R0 else level,
            confidence = confidence,
            suspectedType = suspected,
            evidence = evidence.sortedByDescending { it.weight }.take(5),
            modelVersion = model,
            isNonWater = o.has("is_water") && !o.optBoolean("is_water", true),
            elapsedMs = elapsed,
            engine = Report.ENGINE_CLOUD
        )
    }

    /** 模型偶尔会把 JSON 包在 ```json ... ``` 里，或者前后带解说文字 */
    private fun unwrap(s: String): String {
        var t = s.trim()
        if (t.startsWith("```")) {
            t = t.removePrefix("```json").removePrefix("```JSON").removePrefix("```")
            val end = t.lastIndexOf("```")
            if (end > 0) t = t.substring(0, end)
            t = t.trim()
        }
        if (t.startsWith("{") && t.endsWith("}")) return t
        val a = t.indexOf('{')
        val b = t.lastIndexOf('}')
        if (a >= 0 && b > a) return t.substring(a, b + 1)
        return t
    }

    private fun dimOf(s: String): EvidenceDim = when (s.lowercase()) {
        "color", "colour", "color_turbidity", "turbidity" -> EvidenceDim.COLOR_TURBIDITY
        "floatables", "debris", "trash" -> EvidenceDim.FLOATABLES
        "foam", "oil", "foam_oil" -> EvidenceDim.FOAM_OIL
        "state", "bank", "water_state" -> EvidenceDim.WATER_STATE
        "eutrophication", "algae", "bloom" -> EvidenceDim.EUTROPHICATION
        "quality" -> EvidenceDim.QUALITY
        else -> EvidenceDim.WATER_STATE
    }

    // ---------------------------------------------------------------- 图像

    /** 压缩到长边 1280 的 JPEG（还能再让每像素更准确），base64 成 data URL */
    private fun encodeImage(path: String): String {
        val file = File(path)
        if (!file.exists() || file.length() < 64) throw CloudException("no_image", "photo file missing")

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        var sample = 1
        val maxSide = maxOf(bounds.outWidth, bounds.outHeight)
        while (maxSide / sample > 1280) sample *= 2

        val bmp = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: throw CloudException("decode_failed", "cannot decode photo")

        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 82, out)
        bmp.recycle()
        val bytes = out.toByteArray()
        val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
        return "data:image/jpeg;base64,$b64"
    }

    private fun languageName(): String =
        if (Settings.currentLang().startsWith("zh")) "Simplified Chinese (简体中文)" else "English"

    companion object {
        /**
         * 提示词：要求“只输出 JSON”，并把六维权重、R0–R4 阈值写进去，
         * 保证云端与本机引擎的等级口径一致（等级规则不因引擎而变）。
         */
        val PROMPT = """
You are a river water observation assistant. Look at the photo and describe only what is
visible about the water body. Never claim a lab test result or a legal compliance verdict.

Score these six dimensions from 0 to 100:
  color        colour and turbidity (black, grey, green, brown, milky, opaque)
  floatables   floating debris, rubbish, scum
  foam_oil     persistent foam, oil film, iridescent sheen
  state        water surface condition, bank wash marks, discharge pipes, stagnant look
  eutrophication  algal film, bloom, dense green mat
  quality      how much the photo limits your certainty (0 = crisp, 100 = useless)

Then compute index = round(0.25*color + 0.20*floatables + 0.15*foam_oil
                              + 0.20*state + 0.10*eutrophication + 0.10*(100-quality))
Grade: R4 >= 70, R3 50-69, R2 30-49, R1 15-29, R0 < 15.
confidence: "high" only when the surface is crisp with a bank or object for scale and good
light; "low" when blurry, night, extreme close-up, glare, or the water is not identifiable.

Reply with ONE json object and nothing else:
{
 "is_water": true|false,
 "index": int 0-100,
 "level": "R0"|"R1"|"R2"|"R3"|"R4",
 "confidence": "high"|"medium"|"low",
 "suspected_type": "domestic"|"industrial"|"livestock"|"blackodorous"|"dumping"|"construction"|"oil"|"unknown",
 "summary": "one sentence, <= 120 chars",
 "evidence": [
   {"dimension":"color","observation":"what you actually see","score":int},
   {"dimension":"floatables","observation":"...","score":int}
 ]
}
Write observations in the language named by the caller (see the text line above); default to English.
Do not mention that you are an AI model.
        """.trimIndent()
    }
}
