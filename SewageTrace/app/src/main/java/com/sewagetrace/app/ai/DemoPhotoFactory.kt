package com.sewagetrace.app.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Environment
import com.sewagetrace.app.R
import com.sewagetrace.app.model.SuspectedType
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

/**
 * 演示场景照片生成器。
 *
 * 模拟器没有真实相机可用，这里用 Canvas 合成"模拟拍摄画面"：不同场景对应的
 * 视觉特征（发黑、泡沫、藻华、油膜、模糊、屏幕翻拍……）会被识别引擎真实地"看"出来，
 * 从而让识别结果与所选场景在逻辑上自洽。
 *
 * 场景标题为资源 id，切换语言后跟着变。
 */
enum class DemoScene(
    val id: String,
    val titleRes: Int,
    val subtitleRes: Int,
    val prior: ScenePrior,
    val waterbody: String
) {
    HEAVY(
        "heavy", R.string.scene_heavy_title, R.string.scene_heavy_sub,
        ScenePrior(
            colorTurbidity = 0.92f, floatables = 0.55f, foamOil = 0.45f, state = 0.85f, eutrophication = 0.15f,
            suspectedType = SuspectedType.INDUSTRIAL, noteRes = "scene_heavy_note"
        ),
        "Klang River · industrial reach"
    ),
    MODERATE(
        "moderate", R.string.scene_moderate_title, R.string.scene_moderate_sub,
        ScenePrior(
            colorTurbidity = 0.62f, floatables = 0.30f, foamOil = 0.40f, state = 0.45f, eutrophication = 0.70f,
            suspectedType = SuspectedType.BLACK_ODOROUS, noteRes = "scene_moderate_note"
        ),
        "Sungai Kerayong · Cheras"
    ),
    MILD(
        "mild", R.string.scene_mild_title, R.string.scene_mild_sub,
        ScenePrior(
            colorTurbidity = 0.48f, floatables = 0.55f, foamOil = 0.15f, state = 0.30f, eutrophication = 0.20f,
            suspectedType = SuspectedType.DOMESTIC, noteRes = "scene_mild_note"
        ),
        "Sungai Bunus · Setapak"
    ),
    CLEAN(
        "clean", R.string.scene_clean_title, R.string.scene_clean_sub,
        ScenePrior(
            colorTurbidity = 0.08f, floatables = 0.05f, foamOil = 0.03f, state = 0.05f, eutrophication = 0.12f,
            suspectedType = SuspectedType.UNKNOWN, noteRes = "scene_clean_note"
        ),
        "Klang River · Masjid Jamek reach"
    ),
    BLURRED(
        "blurred", R.string.scene_blurred_title, R.string.scene_blurred_sub,
        ScenePrior.blurred(), "(water body not identified)"
    ),
    SCREENSHOT(
        "screenshot", R.string.scene_screenshot_title, R.string.scene_screenshot_sub,
        ScenePrior.reverseShot(), "(water body not identified)"
    ),
    NIGHT(
        "night", R.string.scene_night_title, R.string.scene_night_sub,
        ScenePrior.darkScene().copy(colorTurbidity = 0.55f, state = 0.4f, suspectedType = SuspectedType.DOMESTIC),
        "Sungai Batu · Jinjang"
    );

    companion object {
        fun byId(id: String?): DemoScene = entries.firstOrNull { it.id == id } ?: MILD
    }
}

object DemoPhotoFactory {

    fun photoDir(ctx: Context): File {
        val base = ctx.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: ctx.filesDir
        val d = File(base, "photos")
        if (!d.exists()) d.mkdirs()
        return d
    }

    fun ensure(ctx: Context, scene: DemoScene): String {
        val f = File(photoDir(ctx), "demo_${scene.id}.jpg")
        if (f.exists() && f.length() > 1024) return f.absolutePath
        render(scene, f)
        return f.absolutePath
    }

    /** 最近一次 [generateRandom] 选中的场景，供 ViewModel 取用水体名与场景先验 */
    var lastScene: DemoScene = DemoScene.MILD
        private set

    fun generateRandom(ctx: Context): String {
        val scene = DemoScene.entries.filter { it.prior.qualityIssue < 0.5f }.random()
        lastScene = scene
        val f = File(photoDir(ctx), "demo_${scene.id}.jpg")
        if (!f.exists() || f.length() <= 1024) render(scene, f)
        return f.absolutePath
    }

    private fun render(scene: DemoScene, out: File) {
        val w = 1080
        val h = 1440
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val rnd = Random(scene.id.hashCode().toLong())

        val skyPaint = Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, h * 0.32f,
                intArrayOf(Color.parseColor("#8CA6B0"), Color.parseColor("#B8C6C8")),
                null, Shader.TileMode.CLAMP
            )
        }
        c.drawRect(0f, 0f, w.toFloat(), h * 0.34f, skyPaint)

        val baseColors = when (scene) {
            DemoScene.HEAVY -> intArrayOf(Color.parseColor("#161A18"), Color.parseColor("#2A2F2B"))
            DemoScene.MODERATE -> intArrayOf(Color.parseColor("#3F5A2A"), Color.parseColor("#5C7A3C"))
            DemoScene.MILD -> intArrayOf(Color.parseColor("#6E6A4C"), Color.parseColor("#8A8258"))
            DemoScene.CLEAN -> intArrayOf(Color.parseColor("#3E6E7C"), Color.parseColor("#5F93A0"))
            DemoScene.BLURRED -> intArrayOf(Color.parseColor("#6B7075"), Color.parseColor("#8C9296"))
            DemoScene.SCREENSHOT -> intArrayOf(Color.parseColor("#2B3B44"), Color.parseColor("#43606E"))
            DemoScene.NIGHT -> intArrayOf(Color.parseColor("#0E1418"), Color.parseColor("#1C2429"))
        }
        val waterPaint = Paint().apply {
            shader = LinearGradient(0f, h * 0.30f, 0f, h.toFloat(), baseColors, null, Shader.TileMode.CLAMP)
        }
        c.drawRect(0f, h * 0.30f, w.toFloat(), h.toFloat(), waterPaint)

        val ripple = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f
            color = if (scene == DemoScene.HEAVY) Color.parseColor("#3A403A") else Color.parseColor("#FFFFFF")
            alpha = 40
        }
        for (i in 0 until 26) {
            val y = h * 0.34f + i * (h * 0.024f)
            val path = Path()
            path.moveTo(0f, y)
            var x = 0f
            while (x < w) {
                path.quadTo(x + 60f, y + sin((x + i * 40) / 90.0).toFloat() * 8f, x + 120f, y)
                x += 120f
            }
            c.drawPath(path, ripple)
        }

        when (scene) {
            DemoScene.HEAVY -> {
                val pipe = Paint().apply { color = Color.parseColor("#4A4740") }
                c.drawRect(w * 0.62f, h * 0.33f, w * 0.80f, h * 0.42f, pipe)
                val flow = Paint().apply {
                    shader = RadialGradient(
                        w * 0.71f, h * 0.72f, w * 0.55f,
                        intArrayOf(Color.parseColor("#0B0D0C"), Color.TRANSPARENT), null, Shader.TileMode.CLAMP
                    )
                }
                c.drawCircle(w * 0.71f, h * 0.72f, w * 0.55f, flow)
                val foam = Paint().apply { color = Color.parseColor("#D8DCD6"); alpha = 150 }
                for (i in 0 until 60) {
                    val x = rnd.nextFloat() * w
                    val y = h * 0.40f + rnd.nextFloat() * h * 0.55f
                    c.drawCircle(x, y, 6f + rnd.nextFloat() * 18f, foam)
                }
            }
            DemoScene.MODERATE -> {
                val algae = Paint().apply { color = Color.parseColor("#7FA83B"); alpha = 200 }
                for (i in 0 until 120) {
                    val x = rnd.nextFloat() * w
                    val y = h * 0.36f + rnd.nextFloat() * h * 0.6f
                    c.drawOval(x, y, x + 40f + rnd.nextFloat() * 80f, y + 18f + rnd.nextFloat() * 30f, algae)
                }
                val foam = Paint().apply { color = Color.WHITE; alpha = 190 }
                for (i in 0 until 40) {
                    val x = rnd.nextFloat() * w
                    val y = h * 0.45f + rnd.nextFloat() * h * 0.45f
                    c.drawCircle(x, y, 10f + rnd.nextFloat() * 26f, foam)
                }
            }
            DemoScene.MILD -> {
                val colors = listOf("#D9D2C0", "#B0B7B0", "#8C7B63", "#E8E4D8")
                for (i in 0 until 45) {
                    val p = Paint().apply { color = Color.parseColor(colors[i % colors.size]); alpha = 220 }
                    val x = rnd.nextFloat() * w
                    val y = h * 0.48f + rnd.nextFloat() * h * 0.4f
                    c.save()
                    c.rotate(rnd.nextFloat() * 90f, x, y)
                    c.drawRect(x, y, x + 20f + rnd.nextFloat() * 60f, y + 10f + rnd.nextFloat() * 22f, p)
                    c.restore()
                }
            }
            DemoScene.CLEAN -> {
                val glow = Paint().apply {
                    shader = LinearGradient(
                        0f, h * 0.32f, 0f, h * 0.75f,
                        intArrayOf(Color.argb(120, 255, 255, 255), Color.TRANSPARENT), null, Shader.TileMode.CLAMP
                    )
                }
                c.drawRect(w * 0.3f, h * 0.32f, w * 0.6f, h * 0.75f, glow)
            }
            DemoScene.NIGHT -> {
                val lamp = Paint().apply {
                    shader = RadialGradient(
                        w * 0.3f, h * 0.25f, w * 0.5f,
                        intArrayOf(Color.argb(150, 255, 214, 140), Color.TRANSPARENT), null, Shader.TileMode.CLAMP
                    )
                }
                c.drawCircle(w * 0.3f, h * 0.25f, w * 0.5f, lamp)
            }
            else -> Unit
        }

        val bank = Paint().apply { color = Color.parseColor("#4A4A3E") }
        c.drawRect(0f, h * 0.28f, w.toFloat(), h * 0.325f, bank)

        // 时间/位置水印（吉隆坡）
        val stamp = Paint().apply {
            color = Color.WHITE
            textSize = 34f
            alpha = 210
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
        c.drawRect(40f, h - 150f, 640f, h - 40f, Paint().apply { color = Color.argb(120, 0, 0, 0) })
        c.drawText("SewageTrace · demo capture", 60f, h - 100f, stamp)
        c.drawText("3.1578N 101.7118E", 60f, h - 56f, stamp)

        when (scene) {
            DemoScene.BLURRED -> {
                val src = bmp.copy(Bitmap.Config.ARGB_8888, true)
                val blurPaint = Paint().apply { alpha = 90 }
                for (i in 1..8) c.drawBitmap(src, i * 12f, i * 9f, blurPaint)
                val veil = Paint().apply { color = Color.argb(70, 200, 200, 200) }
                c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), veil)
                src.recycle()
            }
            DemoScene.SCREENSHOT -> {
                val frame = Paint().apply {
                    style = Paint.Style.STROKE; strokeWidth = 26f; color = Color.parseColor("#101418")
                }
                c.drawRect(30f, 30f, w - 30f, h - 30f, frame)
                val scan = Paint().apply { color = Color.argb(60, 120, 200, 255); strokeWidth = 2f }
                for (i in 0 until 60) c.drawLine(0f, i * 24f, w.toFloat(), i * 24f, scan)
                val moire = Paint().apply { color = Color.argb(50, 255, 255, 255); strokeWidth = 1f }
                for (i in 0 until 120) {
                    val y = ((i * 12f + rnd.nextFloat() * 4f))
                    c.drawLine(0f, y, w.toFloat(), y, moire)
                }
            }
            else -> Unit
        }

        FileOutputStream(out).use { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        bmp.recycle()
    }

    /** 用于"送检报告 vs AI 结论"对照演示的实测值 */
    fun measuredSample(index: Int): List<Pair<String, String>> {
        val rnd = Random(index.toLong() * 7919)
        fun v(base: Double, spread: Double, digits: Int = 1) =
            String.format(Locale.US, "%.${digits}f", base + (rnd.nextDouble() - 0.5) * spread)
        return when {
            index >= 70 -> listOf(
                "pH" to v(6.4, 1.2, 2), "COD" to v(180.0, 90.0, 0) + " mg/L",
                "NH3-N" to v(28.0, 12.0) + " mg/L", "TP" to v(3.4, 1.6) + " mg/L",
                "DO" to v(1.6, 1.0) + " mg/L"
            )
            index >= 50 -> listOf(
                "pH" to v(6.9, 1.0, 2), "COD" to v(96.0, 40.0, 0) + " mg/L",
                "NH3-N" to v(12.0, 6.0) + " mg/L", "TP" to v(1.8, 0.8) + " mg/L",
                "DO" to v(3.2, 1.2) + " mg/L"
            )
            index >= 30 -> listOf(
                "pH" to v(7.2, 0.8, 2), "COD" to v(48.0, 20.0, 0) + " mg/L",
                "NH3-N" to v(5.0, 3.0) + " mg/L", "TP" to v(0.9, 0.5) + " mg/L",
                "DO" to v(4.8, 1.4) + " mg/L"
            )
            else -> listOf(
                "pH" to v(7.4, 0.4, 2), "COD" to v(14.0, 8.0, 0) + " mg/L",
                "NH3-N" to v(0.6, 0.4) + " mg/L", "TP" to v(0.12, 0.08, 2) + " mg/L",
                "DO" to v(7.6, 1.2) + " mg/L"
            )
        }
    }

    fun diff(a: Int, b: Int) = abs(a - b)
}
