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
import stageguard.sctimetable.PluginConfig
import java.util.regex.Pattern

/**
 * 用于封装必要的两个cookie字段
 **/
data class LoginCookieData(
    val jSessionId: String = "",
    val serverId: String = ""
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

    suspend fun loginViaPassword(loginInfo: LoginInfoData, cookieBlock: (LoginCookieData) -> Unit) : LoginReceiptDTO {
        return client.post<HttpStatement> {
            url("$BASE_URL/V2/StudentSkip/loginCheckV4.action")
            parameter("account", EncryptionUtils.encrypt(loginInfo.username))
            parameter("password", EncryptionUtils.encrypt(loginInfo.password))
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
            cookieBlock(LoginCookieData(cookieList[0].let {
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
            Json.decodeFromString(response.content.readUTF8Line() ?: "{}")
        }
    }

    suspend fun getCourses(cookie: LoginCookieData) : CourseReceiptDTO {
        return client.post<HttpStatement> {
            url("$BASE_URL/V2/Course/getCourseTableFromServer.action")
            header("Cookie", "JSESSIONID=${cookie.jSessionId};SERVERID=${cookie.serverId}")
            parameter("beginYear", PluginConfig.beginYear)
            parameter("term", PluginConfig.term)
            parameter("platform", 1)
            parameter("versionNumber", VERSION_NUMBER)
            parameter("phoneBrand", PHONE_BRAND)
            parameter("phoneVersion", PHONE_VERSION)
            parameter("phoneModel", PHONE_MODEL)
        }.execute { response ->
            Json.decodeFromString(response.content.readUTF8Line() ?: "{}")
        }
    }

    fun closeHttpClient() {
        runBlocking { client.close() }
    }
}