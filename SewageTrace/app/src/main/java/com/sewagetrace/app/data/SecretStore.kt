package com.sewagetrace.app.data

import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 云端凭据的加密存储。
 *
 * 设计取舍（为什么不用 EncryptedSharedPreferences）：
 *  - 那个依赖会引入 security-crypto 和 Tink，体积大、在部分 ROM 上有兼容问题；
 *  - 这里只有一两个短字符串要存，直接用 Android Keystore 生成一把**不可导出**的 AES 密钥，
 *    自己拼 `iv + ciphertext`，够用且零外部依赖。
 *
 * 安全边界（必须清楚，别把它当银弹）：
 *  - 密钥材料在 TEE/StrongBox 里，root 也拿不到明文**密钥**；
 *  - 但在已 root 的设备上，攻击者仍可用同一把系统密钥解密本应用的 prefs 文件；
 *  - 防的是「prefs 被拷走 / 备份被导出 / 用 adb backup 读明文」这类离线泄露，
 *    **不能**防「运行时内存 dump」。真正要防服务器密钥泄露，唯一正解是把密钥留在服务端
 *    （也就是 PROJECT.md 里的反代网关方案），客户端只拿网关签发的设备令牌。
 */
object SecretStore {

    private const val KEYSTORE = "AndroidKeyStore"
    private const val ALIAS = "sewagetrace_secret_v1"
    private const val TRANSFORM = "AES/GCM/NoPadding"
    private const val IV_LEN = 12
    private const val TAG_BITS = 128

    private fun secretKey(): SecretKey? = try {
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
            ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).apply {
                init(
                    KeyGenParameterSpec.Builder(
                        ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                    )
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(256)
                        .build()
                )
            }.generateKey()
    } catch (e: Exception) {
        null
    }

    fun write(prefs: SharedPreferences, name: String, plain: String) {
        val key = secretKey()
        if (key == null) {
            // Keystore 不可用（极少数模拟器/定制 ROM）：退化为明文，但至少标记出来
            prefs.edit().putString(name, plain).putBoolean("${name}_enc", false).apply()
            return
        }
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val ct = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        val blob = ByteArray(iv.size + ct.size)
        System.arraycopy(iv, 0, blob, 0, iv.size)
        System.arraycopy(ct, 0, blob, iv.size, ct.size)
        prefs.edit()
            .putString(name, Base64.encodeToString(blob, Base64.NO_WRAP))
            .putBoolean("${name}_enc", true)
            .apply()
    }

    fun read(prefs: SharedPreferences, name: String): String {
        val raw = prefs.getString(name, "") ?: return ""
        if (raw.isBlank()) return ""
        val encrypted = prefs.getBoolean("${name}_enc", false)
        if (!encrypted) return raw
        return try {
            val key = secretKey() ?: return ""
            val blob = Base64.decode(raw, Base64.NO_WRAP)
            if (blob.size <= IV_LEN) return ""
            val cipher = Cipher.getInstance(TRANSFORM)
            cipher.init(
                Cipher.DECRYPT_MODE,
                key,
                GCMParameterSpec(TAG_BITS, blob, 0, IV_LEN)
            )
            String(cipher.doFinal(blob, IV_LEN, blob.size - IV_LEN), Charsets.UTF_8)
        } catch (e: Exception) {
            // 换机 / 恢复备份 / Keystore 被重置 → 解不开就当没配过
            ""
        }
    }

    fun clear(prefs: SharedPreferences, name: String) {
        prefs.edit().remove(name).remove("${name}_enc").apply()
    }
}
