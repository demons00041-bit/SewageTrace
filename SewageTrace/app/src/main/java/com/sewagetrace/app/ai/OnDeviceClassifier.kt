package com.sewagetrace.app.ai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import com.sewagetrace.app.R
import com.sewagetrace.app.model.Confidence
import com.sewagetrace.app.model.Evidence
import com.sewagetrace.app.model.EvidenceDim
import com.sewagetrace.app.model.SeverityLevel
import com.sewagetrace.app.model.SuspectedType
import kotlin.math.max
import kotlin.math.min

/**
 * 识别结果（与具体模型实现无关的统一 schema）。
 * 接入真实视觉模型时，只需要另一个 SeverityClassifier 实现产出同样的对象。
 *
 * [evidence] 中的文案一律是资源 id 或样例 key，不存任何具体语言字符串，
 * 因此历史记录在切换语言后仍然可翻译。
 */
data class AnalysisResult(
    val index: Int,
    val level: SeverityLevel,
    val confidence: Confidence,
    val suspectedType: SuspectedType,
    val evidence: List<Evidence>,
    val modelVersion: String,
    val isNonWater: Boolean = false,
    val elapsedMs: Long = 0,
    val engine: String = "local",
    val fallbackReason: String? = null
)

/** AI 能力抽象层：本机规则引擎 / 云端多模态模型都实现这个接口 */
interface SeverityClassifier {
    val name: String
    val modelVersion: String
    fun analyze(photoPath: String): AnalysisResult
}

/**
 * 本机规则引擎（离线可用）。
 *
 * 真正的视觉模型在这一层之上；这里用 7 维图像特征 + 规则后处理模拟同样的输出契约：
 *  1. 下采样到 48x48 计算基础统计（亮度、饱和度、局部细节密度、边缘连通性、色相分布）
 *  2. 叠加"拍摄场景先验"（演示版为模拟相机识别到的对焦对象）——让不同演示场景产出可区分的合理结论
 *  3. 按 assets/severity-rubric.json 的六维权重累加 → 0-100 指数 → R0-R4 → 置信度 + 疑似类型
 *
 * 全程不联网、不上传任何像素。
 */
class OnDeviceClassifier(
    private val scenePrior: ScenePrior = ScenePrior.none()
) : SeverityClassifier {

    override val name = "local"
    override val modelVersion = "rule-engine-1.1.0"

    override fun analyze(photoPath: String): AnalysisResult {
        val t0 = System.currentTimeMillis()
        val features = extractFeatures(photoPath)
        return classify(features, t0)
    }

    private fun extractFeatures(path: String): ImageFeatures {
        val bmp: Bitmap = try {
            if (path.isBlank()) throw IllegalArgumentException("empty path")
            val opt = BitmapFactory.Options().apply { inSampleSize = 4 }
            BitmapFactory.decodeFile(path, opt) ?: throw IllegalArgumentException("decode failed")
        } catch (e: Exception) {
            return ImageFeatures.EMPTY
        }

        val w = min(bmp.width, 48)
        val h = min(bmp.height, 48)
        val small = if (bmp.width > w || bmp.height > h) Bitmap.createScaledBitmap(bmp, w, h, true) else bmp
        val pixels = IntArray(w * h)
        small.getPixels(pixels, 0, w, 0, 0, w, h)

        var lumSum = 0.0
        var satSum = 0.0
        var darkish = 0
        var greenish = 0
        var whitish = 0
        var colorful = 0

        val hsv = FloatArray(3)
        for (p in pixels) {
            Color.colorToHSV(p, hsv)
            val hDeg = hsv[0]
            val s = hsv[1]
            val v = hsv[2]
            lumSum += (0.299 * Color.red(p) + 0.587 * Color.green(p) + 0.114 * Color.blue(p)) / 255.0
            satSum += s
            if (v < 0.35f) darkish++
            if (s > 0.18f && hDeg in 60f..180f) greenish++
            if (s < 0.08f && v > 0.80f) whitish++
            if (s > 0.55f) colorful++
        }
        val n = pixels.size.toDouble()

        var diffSum = 0.0
        var edges = 0
        var pairs = 0
        for (y in 0 until h) {
            for (x in 0 until w - 1) {
                val p = pixels[y * w + x]
                val q = pixels[y * w + x + 1]
                val lp = 0.299 * Color.red(p) + 0.587 * Color.green(p) + 0.114 * Color.blue(p)
                val lq = 0.299 * Color.red(q) + 0.587 * Color.green(q) + 0.114 * Color.blue(q)
                val d = kotlin.math.abs(lp - lq)
                diffSum += d
                if (d > 28) edges++
                pairs++
            }
        }
        if (small !== bmp) small.recycle()
        bmp.recycle()

        return ImageFeatures(
            luminance = lumSum / n,
            saturation = satSum / n,
            darkRatio = darkish / n,
            greenRatio = greenish / n,
            whiteRatio = whitish / n,
            vividRatio = colorful / n,
            detailDensity = if (pairs > 0) edges.toDouble() / pairs else 0.0,
            contrast = if (pairs > 0) diffSum / pairs else 0.0,
            isValid = true
        )
    }

    private fun classify(f: ImageFeatures, t0: Long): AnalysisResult {
        val darkSignal = clamp((f.darkRatio * 1.6 + (0.42 - f.luminance).coerceAtLeast(0.0) * 1.4).toFloat(), 0f, 1f)
        val murk = clamp(((0.55 - f.saturation) * 1.6 + f.whiteRatio * 0.5).toFloat(), 0f, 1f)
        val garbage = clamp(((f.detailDensity - 0.05) * 5.0).toFloat(), 0f, 1f)
        val green = clamp((f.greenRatio * 1.5).toFloat(), 0f, 1f)
        val oily = clamp(((f.vividRatio - 0.05) * 2.5).toFloat(), 0f, 1f)

        val p = scenePrior
        fun base(v: Float, delta: Float) = clamp(v + delta, 0f, 1f)

        val colorTurbidity = base(max(darkSignal, murk), p.colorTurbidity)
        val floatables = base(garbage, p.floatables)
        val foamOil = base(oily, p.foamOil)
        val state = base(max(murk * 0.8f, darkSignal * 0.6f), p.state)
        val eutro = base(green, p.eutrophication)

        val weighted = 25f * colorTurbidity + 20f * floatables + 15f * foamOil + 20f * state + 10f * eutro
        val qualityGood = clamp((0.35f - p.qualityIssue * 0.9f) + (f.contrast / 60.0).toFloat() * 0.2f, 0f, 1f)
        val index = (weighted + 10f * qualityGood).coerceIn(0f, 100f).toInt()

        // 置信度
        val flagRes = mutableListOf<Int>()
        var flagCount = 0
        if (p.qualityIssue >= 0.5f) { flagRes += p.qualityIssueReasonRes; flagCount++ }
        if (!f.isValid) { flagRes += R.string.ev_blurred; flagCount++ }
        if (f.contrast < 4.0 && f.isValid) { flagRes += R.string.ev_screenshot; flagCount++ }
        val confidence = when {
            !f.isValid || p.qualityIssue >= 0.6f || flagCount >= 2 -> Confidence.LOW
            p.qualityIssue >= 0.3f || flagCount > 0 -> Confidence.MEDIUM
            else -> Confidence.HIGH
        }

        // 证据条目
        val evidence = mutableListOf<Evidence>()
        if (colorTurbidity > 0.35f) evidence += Evidence(
            EvidenceDim.COLOR_TURBIDITY, (colorTurbidity * 100).toInt(),
            noteRes = p.noteRes ?: when {
                f.darkRatio > 0.4 -> "color_dark"
                f.greenRatio > 0.3 -> "color_green"
                else -> "color_murky"
            }
        )
        if (floatables > 0.3f) evidence += Evidence(
            EvidenceDim.FLOATABLES, (floatables * 100).toInt(),
            noteRes = if (p.floatables > 0.3f) "float_many" else "float_some"
        )
        if (foamOil > 0.3f) evidence += Evidence(
            EvidenceDim.FOAM_OIL, (foamOil * 100).toInt(), noteRes = "foam"
        )
        if (state > 0.3f) evidence += Evidence(
            EvidenceDim.WATER_STATE, (state * 100).toInt(), noteRes = "state"
        )
        if (eutro > 0.3f) evidence += Evidence(
            EvidenceDim.EUTROPHICATION, (eutro * 100).toInt(), noteRes = "eutro"
        )
        if (confidence != Confidence.HIGH) evidence += Evidence(
            EvidenceDim.QUALITY, (100 - qualityGood * 100).toInt().coerceIn(0, 100),
            noteRes = "quality", noteResId = flagRes.firstOrNull()
        )
        if (evidence.isEmpty()) evidence += Evidence(EvidenceDim.WATER_STATE, 0, noteRes = "clean")

        val level = SeverityLevel.of(index)
        val suspected = p.suspectedType ?: guessType(darkSignal, green, oily, floatables, level)

        return AnalysisResult(
            index = index,
            level = level,
            confidence = confidence,
            suspectedType = suspected,
            evidence = evidence.sortedByDescending { it.weight }.take(4),
            modelVersion = modelVersion,
            isNonWater = p.isNonWater,
            elapsedMs = System.currentTimeMillis() - t0,
            engine = "local"
        )
    }

    private fun guessType(dark: Float, green: Float, oily: Float, garbage: Float, level: SeverityLevel): SuspectedType =
        when {
            level == SeverityLevel.R0 -> SuspectedType.UNKNOWN
            oily > 0.5f -> SuspectedType.OIL
            garbage > 0.65f -> SuspectedType.DUMPING
            green > 0.45f -> SuspectedType.BLACK_ODOROUS
            dark > 0.55f -> SuspectedType.DOMESTIC
            else -> SuspectedType.UNKNOWN
        }

    private fun clamp(v: Float, lo: Float, hi: Float): Float = min(hi, max(lo, v))
}

/** 模拟相机/场景识别给出的先验（真实版本由视觉模型直接给出各维度概率） */
data class ScenePrior(
    val colorTurbidity: Float = 0f,
    val floatables: Float = 0f,
    val foamOil: Float = 0f,
    val state: Float = 0f,
    val eutrophication: Float = 0f,
    val qualityIssue: Float = 0f,
    val qualityIssueReasonRes: Int = 0,
    val suspectedType: SuspectedType? = null,
    val isNonWater: Boolean = false,
    val noteRes: String? = null
) {
    companion object {
        fun none() = ScenePrior()
        fun blurred() = ScenePrior(qualityIssue = 0.7f, qualityIssueReasonRes = R.string.ev_blurred)
        fun reverseShot() = ScenePrior(qualityIssue = 0.75f, qualityIssueReasonRes = R.string.ev_screenshot)
        fun darkScene() = ScenePrior(qualityIssue = 0.35f, qualityIssueReasonRes = R.string.ev_night)
    }
}

data class ImageFeatures(
    val luminance: Double,
    val saturation: Double,
    val darkRatio: Double,
    val greenRatio: Double,
    val whiteRatio: Double,
    val vividRatio: Double,
    val detailDensity: Double,
    val contrast: Double,
    val isValid: Boolean
) {
    companion object {
        val EMPTY = ImageFeatures(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, false)
    }
}
