/*
 * Copyright 2020-2021 StageGuard.
 *
 *  此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 *  Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 *  https://github.com/KonnyakuCamp/SuperCourseTimetableBot/blob/main/LICENSE
 */
package me.stageguard.sctimetable.api.edu_system.`super`

import io.ktor.client.HttpClient
import io.ktor.client.call.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.request.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import me.stageguard.sctimetable.service.TimeProviderService
import me.stageguard.sctimetable.utils.Either
import java.util.regex.Pattern

/**
 * 用于封装必要的两个cookie字段
 **/
data class LoginCookieData(
    var jSessionId: String = "",
    var serverId: String = ""
)

/**
 * 用于封装用户名和密码
 **/
data class LoginInfoData(
    val username: String,
    val password: String
)

object SuperCourseApiService {

    private const val BASE_URL: String = "http://120.55.151.61"
    private const val PLATFORM: Int = 1
    private const val VERSION_NUMBER: String = "9.4.1"
    private const val PHONE_BRAND = "xiaomi"
    private const val PHONE_VERSION = "30" // Android R
    private const val PHONE_MODEL = "vince" // XiaoMi Redmi 5 Plus

    private val client = HttpClient(OkHttp)
    private val json = Json { ignoreUnknownKeys = true }

    val pkey
        get() = "ia7sgeb8woqbq2r9"

    /**
     * 通过密码登录超级课表，并返回一个[Either].
     *
     * 成功则返回[LoginReceiptDTO]，失败则返回[ErrorLoginReceiptDTO]
     */
    suspend fun loginViaPassword(
        loginInfo: LoginInfoData,
        cookieBlock: ((LoginCookieData) -> Unit)?
    ) : Either<ErrorLoginReceiptDTO, LoginReceiptDTO> = loginViaPassword(loginInfo.username, loginInfo.password, cookieBlock)

    /**
     * 通过密码登录超级课表，并返回一个[Either].
     *
     * 成功则返回[LoginReceiptDTO]，失败则返回[ErrorLoginReceiptDTO]
     */
    suspend fun loginViaPassword(
        username: String,
        password: String,
        cookieBlock: ((LoginCookieData) -> Unit)? = null
    ) : Either<ErrorLoginReceiptDTO, LoginReceiptDTO> = try {
        val response = client.post {
            url("$BASE_URL/V2/StudentSkip/loginCheckV4.action")
            parameter("account", EncryptionUtils.encrypt(username))
            parameter("password", EncryptionUtils.encrypt(password))
            parameter("platform", PLATFORM)
            parameter("versionNumber", VERSION_NUMBER)
            parameter("phoneBrand", PHONE_BRAND)
            parameter("phoneVersion", PHONE_VERSION)
            parameter("phoneModel", PHONE_MODEL)
            parameter("updateInfo", false)
            parameter("channel", "ppMarket")
        }

        val cookies: MutableMap<String, String> = mutableMapOf()
        response.headers.forEach { s: String, list: List<String> ->
            if (s.contains("set-cookie")) {
                list.forEach { v ->
                    val sp = v.split('=')
                    cookies[sp[0]] = sp[1].split(';')[0]
                }
                return@forEach
            }
        }
        cookieBlock?.invoke(LoginCookieData(cookies["JSESSIONID"] ?: "", cookies["SERVERID"] ?: ""))
        val result = (response.body<ByteReadChannel>().readUTF8Line() ?: "{\"data\":{\"errorStr\":\"Empty response content.\"},\"status\":1}")
        if(Pattern.compile("errorStr").matcher(result).find()) {
            Either(try {
                json.decodeFromString(result)
            } catch (ignored: Exception) {
                ErrorLoginReceiptDTO(__InternalErrorLoginMsg(result), 1)
            } )
        } else {
            Either.invoke<ErrorLoginReceiptDTO, LoginReceiptDTO>(json.decodeFromString<LoginReceiptDTO>(result))
        }
    } catch (ex: Exception) {
        Either(ErrorLoginReceiptDTO(__InternalErrorLoginMsg(ex.toString(),0, 0), 1))
    }

    /**
     * 获取课程信息，并返回一个[Either].
     *
     * 成功则返回[CourseReceiptDTO]，失败则返回[ErrorCourseReceiptDTO]
     */
    suspend fun getCourses(cookie: LoginCookieData) : Either<ErrorCourseReceiptDTO, CourseReceiptDTO> =
        getCourses(cookie.jSessionId, cookie.serverId)

    /**
     * 获取课程信息，并返回一个[Either].
     *
     * 成功则返回[CourseReceiptDTO]，失败则返回[ErrorCourseReceiptDTO]
     */
    suspend fun getCourses(jSessionId: String, serverId: String) : Either<ErrorCourseReceiptDTO, CourseReceiptDTO> = try {
        val response = client.post {
            url("$BASE_URL/V2/Course/getCourseTableFromServer.action")
            header("Cookie", "JSESSIONID=$jSessionId;SERVERID=$serverId")
            parameter("beginYear", TimeProviderService.currentSemesterBeginYear)
            parameter("term", TimeProviderService.currentSemester)
            parameter("platform", PLATFORM)
            parameter("versionNumber", VERSION_NUMBER)
            parameter("phoneBrand", PHONE_BRAND)
            parameter("phoneVersion", PHONE_VERSION)
            parameter("phoneModel", PHONE_MODEL)
        }

        val result = response.body<ByteReadChannel>().readUTF8Line() ?: "{\"message\":\"Empty response content.\",\"title\":\"\"}"
        try {
            Either.invoke<ErrorCourseReceiptDTO, CourseReceiptDTO>(json.decodeFromString<CourseReceiptDTO>(result))
        } catch (error: Exception) {
            try {
                Either.invoke(json.decodeFromString(result))
            } catch (ex: Exception) {
                Either.invoke(ErrorCourseReceiptDTO("decode error", result))
            }
        }
    } catch (ex: Exception) {
        Either(ErrorCourseReceiptDTO("internal error", ex.toString()))
    }

    fun closeHttpClient() {
        runBlocking { client.close() }
    }
}