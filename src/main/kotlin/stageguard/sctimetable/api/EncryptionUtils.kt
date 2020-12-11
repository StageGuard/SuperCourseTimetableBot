package stageguard.sctimetable.api

import java.io.UnsupportedEncodingException
import java.net.URLEncoder
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

// this encryption utils is only for super course
object EncryptionUtils {
    fun encrypt(str: String?): String? {
        return try {
            aes(URLEncoder.encode(str, "utf-8"), md5("friday_syllabus"))
        } catch (e: UnsupportedEncodingException) {
            e.printStackTrace()
            null
        }
    }

    private fun aes(str: String, str2: String?): String {
        val str3 = "AES"
        val str4 = "utf-8"
        return try {
            val secretKeySpec =
                SecretKeySpec(MessageDigest.getInstance("MD5").digest(str2!!.toByteArray(charset(str4))), str3)
            val instance = Cipher.getInstance(str3)
            instance.init(1, secretKeySpec)
            byteToStr(instance.doFinal(str.toByteArray(charset(str4))))
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    private fun byteToStr(bArr: ByteArray): String {
        val sb = StringBuilder()
        for (b in bArr) {
            var hexString = Integer.toHexString(b.toInt() and 255)
            if (hexString.length == 1) {
                val sb2 = StringBuilder()
                sb2.append('0')
                sb2.append(hexString)
                hexString = sb2.toString()
            }
            sb.append(hexString.toUpperCase())
        }
        return sb.toString()
    }

    private fun md5(str: String): String? {
        val cArr = charArrayOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f')
        return try {
            val instance = MessageDigest.getInstance("MD5")
            instance.update(str.toByteArray())
            val digest = instance.digest()
            val cArr2 = CharArray(32)
            var i = 0
            for (i2 in 0..15) {
                val b = digest[i2]
                val i3 = i + 1
                cArr2[i] = cArr[b.toInt() ushr 4 and 15]
                i = i3 + 1
                cArr2[i3] = cArr[b.toInt() and 15]
            }
            String(cArr2).toUpperCase()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}