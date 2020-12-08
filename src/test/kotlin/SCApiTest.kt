package stageguard.sctimetable

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import stageguard.sctimetable.api.LoginCookieData
import stageguard.sctimetable.api.LoginInfoData
import stageguard.sctimetable.api.SuperCourseApiService

class SCApiTest {
    @Test
    fun test() = runBlocking {
        val loginInfo = LoginInfoData("123123123", "123123123")
        var cookieInfo = LoginCookieData()
        val loginReceipt = SuperCourseApiService.loginViaPassword(loginInfo) {
            cookieInfo = it
        }
        println("${loginReceipt.data.student.schoolName} -> ${loginReceipt.data.student.studentNum}")
        println("jSessionId = ${cookieInfo.jSessionId}, serverId = ${cookieInfo.serverId}")
        var courseReceipt = SuperCourseApiService.getCourses(cookieInfo)
        println(courseReceipt.data.lessonList.joinToString("\n") { it.name })
    }
}