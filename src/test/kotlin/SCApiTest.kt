/*
 * Copyright 2020 KonnyakuCamp.
 *
 *  此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 *  Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 *  https://github.com/KonnyakuCamp/SuperCourseTimetableBot/blob/main/LICENSE
 */
package stageguard.sctimetable

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import stageguard.sctimetable.api.LoginCookieData
import stageguard.sctimetable.api.LoginInfoData
import stageguard.sctimetable.api.SuperCourseApiService
import stageguard.sctimetable.utils.AESUtils
import stageguard.sctimetable.utils.Either

class SCApiTest {
    @Test
    fun test() = runBlocking {
        /*val loginInfo = LoginInfoData("15065196743", "xzg20020410.")
        var cookieInfo = LoginCookieData()
        val loginReceipt = SuperCourseApiService.loginViaPassword(loginInfo) {
            cookieInfo = it
        }
        println("${loginReceipt.data!!.student.schoolName} -> ${loginReceipt.data!!.student.studentNum}")
        println("jSessionId = ${cookieInfo.jSessionId}, serverId = ${cookieInfo.serverId}")
        var courseReceipt = SuperCourseApiService.getCourses(cookieInfo)
        println(courseReceipt.data.lessonList.joinToString("\n") { it.name })*/
    }
    @Test
    fun loginWrong() = runBlocking {
        val x = SuperCourseApiService.loginViaPassword("15966355163", "033312ysz")
        println(x)
    }
}