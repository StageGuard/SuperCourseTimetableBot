package stageguard.sctimetable.api

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.util.*
import io.ktor.utils.io.*
import kotlinx.coroutines.runBlocking
import stageguard.sctimetable.PluginMain
import java.util.regex.Pattern

/* *
 * 这个是用在登录返回到获取课表之间的用于存储cookie的结构体
 * */
data class LoginCookieInfo(
    val jSessionId: String = "",
    val serverId: String = ""
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


    suspend fun login(account: Long, password: String,  block: suspend (String) -> Unit) : LoginCookieInfo {
        return client.post<HttpStatement> {
            url("$BASE_URL/V2/StudentSkip/loginCheckV4.action")
            parameter("account", EncryptionUtils.encrypt(account.toString()))
            parameter("password", EncryptionUtils.encrypt(password))
            parameter("platform", 1)
            parameter("versionNumber", VERSION_NUMBER)
            parameter("phoneBrand", PHONE_BRAND)
            parameter("phoneVersion", PHONE_VERSION)
            parameter("phoneModel", PHONE_MODEL)
            parameter("updateInfo", false)
            parameter("channel", "ppMarket")
        }.execute { response ->
            val content: String = response.content.readUTF8Line() ?: ""
            val cookieList = ({
                var result: List<String> = arrayListOf("", "")
                response.headers.forEach { s: String, list: List<String> ->
                    if(s.contains("Cookie")) {
                        result = list
                        return@forEach
                    }
                }
                result
            }())
            block(content)
            LoginCookieInfo(cookieList[0].let {
                val jSessionMatcher = jSessionIdRegExp.matcher(it)
                if(jSessionMatcher.find()) {
                    jSessionMatcher.group(1)
                } else {
                    ""
                }
            }, cookieList[1].let {
                val serverIdMatcher = serverIdRegexp.matcher(it)
                if(serverIdMatcher.find()) {
                    serverIdMatcher.group(1)
                } else {
                    ""
                }
            })
        }
    }

    suspend fun getCourse(loginCookieInfo: LoginCookieInfo, beginYear: Int, term: Int, block: suspend (String) -> Unit) {
            client.post<HttpStatement> {
                url("$BASE_URL/V2/Course/getCourseTableFromServer.action")
                header("Cookie", "JSESSIONID=${loginCookieInfo.jSessionId};SERVERID=${loginCookieInfo.serverId}")
                parameter("beginYear", beginYear)
                parameter("term", term)
                parameter("platform", 1)
                parameter("versionNumber", VERSION_NUMBER)
                parameter("phoneBrand", PHONE_BRAND)
                parameter("phoneVersion", PHONE_VERSION)
                parameter("phoneModel", PHONE_MODEL)
            }.execute { response ->
                val content = response.content.readUTF8Line()
                content?.let { block(it) }
            }
    }
    fun closeHttpClient() {
        runBlocking { client.close() }
    }
}