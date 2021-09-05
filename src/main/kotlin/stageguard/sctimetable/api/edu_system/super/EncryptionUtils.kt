/*
 * Copyright 2020-2021 KonnyakuCamp.
 *
 *  此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 *  Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 *  https://github.com/KonnyakuCamp/SuperCourseTimetableBot/blob/main/LICENSE
 */
package stageguard.sctimetable.api.edu_system.`super`

import java.io.UnsupportedEncodingException
import java.net.URLEncoder
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

object EncryptionUtils {
    fun encrypt(str: String?): String? = try {
        aes(URLEncoder.encode(str, "utf-8"), md5("friday_syllabus"))
    } catch (e: UnsupportedEncodingException) {
        e.printStackTrace()
        null
    }

    private fun aes(str: String, str2: String?) = try {
        val secretKeySpec =
            SecretKeySpec(MessageDigest.getInstance("MD5").digest(str2!!.toByteArray(charset("utf-8"))), "AES")
        val instance = Cipher.getInstance("AES")
        instance.init(1, secretKeySpec)
        byteToStr(instance.doFinal(str.toByteArray(charset("utf-8"))))
    } catch (e: Exception) {
        e.printStackTrace()
        ""
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