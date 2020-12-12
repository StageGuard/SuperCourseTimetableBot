package stageguard.sctimetable.api

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.util.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import stageguard.sctimetable.service.TimeProviderService
import stageguard.sctimetable.utils.Either
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

    private val client = HttpClient(CIO)

    private val jSessionIdRegExp = Pattern.compile("JSESSIONID=([0-9A-F]+-[a-z1-9]+);")
    private val serverIdRegexp = Pattern.compile("SERVERID=([0-9a-f|]+);")

    val pkey
        get() = "ia7sgeb8woqbq2r9"

    suspend fun loginViaPassword(
        loginInfo: LoginInfoData,
        cookieBlock: ((LoginCookieData) -> Unit)?
    ) : Either<LoginReceiptDTO, ErrorLoginReceiptDTO> = loginViaPassword(loginInfo.username, loginInfo.password, cookieBlock)
    suspend fun loginViaPassword(
        username: String,
        password: String,
        cookieBlock: ((LoginCookieData) -> Unit)? = null
    ) : Either<LoginReceiptDTO, ErrorLoginReceiptDTO> = try {
        client.post<HttpStatement> {
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
        }.execute { response ->
            var cookieList: List<String> = arrayListOf("", "")
            response.headers.forEach { s: String, list: List<String> ->
                if (s.contains("Cookie")) {
                    cookieList = list
                    return@forEach
                }
            }
            cookieBlock ?.invoke(LoginCookieData(cookieList[0].let {
                val jSessionMatcher = jSessionIdRegExp.matcher(it)
                if(jSessionMatcher.find()) {
                    jSessionMatcher.group(1)
                } else ""
            }, cookieList[1].let {
                val serverIdMatcher = serverIdRegexp.matcher(it)
                if(serverIdMatcher.find()) {
                    serverIdMatcher.group(1)
                } else ""
            }))
            //超级课表的api可真是狗屎，逼我自定义一个Either
            val result = response.content.readUTF8Line() ?: "{\"data\":{\"errorStr\":\"Empty response content.\"},\"status\":1}"
            if(Pattern.compile("errorStr").matcher(result).find()) {
                Either.Right(Json.decodeFromString(result))
            } else {
                Either.Left(Json.decodeFromString(result))
            }

        }
    } catch (ex: Exception) {
        Either.Right(ErrorLoginReceiptDTO(__InternalErrorLoginMsg(ex.toString(),0, 0), 1))
    }

    suspend fun getCourses(cookie: LoginCookieData) : Either<CourseReceiptDTO, ErrorCourseReceiptDTO> = getCourses(cookie.jSessionId, cookie.serverId)
    suspend fun getCourses(jSessionId: String, serverId: String) : Either<CourseReceiptDTO, ErrorCourseReceiptDTO> = try {
        client.post<HttpStatement> {
            url("$BASE_URL/V2/Course/getCourseTableFromServer.action")
            header("Cookie", "JSESSIONID=$jSessionId;SERVERID=$serverId")
            parameter("beginYear", TimeProviderService.currentSemesterBeginYear)
            parameter("term", TimeProviderService.currentSemester)
            parameter("platform", PLATFORM)
            parameter("versionNumber", VERSION_NUMBER)
            parameter("phoneBrand", PHONE_BRAND)
            parameter("phoneVersion", PHONE_VERSION)
            parameter("phoneModel", PHONE_MODEL)
        }.execute {
            val result = it.content.readUTF8Line() ?: "{\"message\":\"Empty response content.\",\"title\":\"\"}"
            try {
                Either.Left(Json.decodeFromString(result))
            } catch (error: Exception) {
                Either.Right(Json.decodeFromString(result))
            }
        }
    } catch (ex: Exception) {
        Either.Right(ErrorCourseReceiptDTO("", ex.toString()))
    }

    fun closeHttpClient() {
        runBlocking { client.close() }
    }
}