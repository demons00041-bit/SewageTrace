package com.sewagetrace.app.ai

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File

/**
 * 真实相机取图。
 *
 * 流程：先在应用私有目录建一个空文件 → 用 FileProvider 换成 content:// URI →
 * 交给系统相机写入 → 回到 App 后把这个文件的路径交给识别流程。
 *
 * 为什么不用 `ActivityResultContracts.TakePicture()`：
 * 部分相机 App 拿不到 FileProvider 的写权限，会静默返回 RESULT_CANCELED 或写入 0 字节。
 * 这里显式用 ClipData 授予读+写权限，兼容性更好。
 */
object CameraCapture {

    /** 新建一个待写入的 jpg，返回文件 */
    fun newFile(ctx: Context): File {
        val dir = DemoPhotoFactory.photoDir(ctx)
        return File(dir, "capture_${System.currentTimeMillis()}.jpg")
    }

    fun uriFor(ctx: Context, file: File): Uri =
        FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)

    /** 构造带授权的拍照 Intent */
    fun intent(ctx: Context, uri: Uri): Intent {
        val i = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        i.putExtra(MediaStore.EXTRA_OUTPUT, uri)
        i.clipData = ClipData.newUri(ctx.contentResolver, "capture", uri)
        i.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return i
    }

    /**
     * 把相册选中的图片复制进应用目录，返回本地路径。
     * 复制而不是直接用 content:// URI，是因为上报记录要长期引用这张图，
     * 临时授权一过 URI 就失效了。
     */
    fun copyFromUri(ctx: Context, uri: Uri): String? = runCatching {
        val out = newFile(ctx)
        ctx.contentResolver.openInputStream(uri)?.use { input ->
            out.outputStream().use { output -> input.copyTo(output) }
        } ?: return null
        if (out.length() <= 0L) {
            out.delete()
            null
        } else {
            out.absolutePath
        }
    }.getOrNull()

    /** 相机返回后确认文件真的写进去了 */
    fun isValidCapture(file: File): Boolean = file.exists() && file.length() > 1024L
}
