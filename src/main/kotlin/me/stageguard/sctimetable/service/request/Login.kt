/*
 * Copyright 2020-2021 StageGuard.
 *
 *  此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 *  Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 *  https://github.com/KonnyakuCamp/SuperCourseTimetableBot/blob/main/LICENSE
 */
package me.stageguard.sctimetable.service.request

import me.stageguard.sctimetable.PluginConfig
import me.stageguard.sctimetable.PluginData
import me.stageguard.sctimetable.api.edu_system.`super`.LoginCookieData
import me.stageguard.sctimetable.api.edu_system.`super`.LoginInfoData
import me.stageguard.sctimetable.api.edu_system.`super`.SuperCourseApiService
import me.stageguard.sctimetable.database.Database
import me.stageguard.sctimetable.database.model.User
import me.stageguard.sctimetable.database.model.Users
import me.stageguard.sctimetable.service.BotEventRouteService
import me.stageguard.sctimetable.service.IRequest
import me.stageguard.sctimetable.service.RequestHandlerService
import me.stageguard.sctimetable.utils.AESUtils
import me.stageguard.sctimetable.utils.Either.Companion.onLeft
import me.stageguard.sctimetable.utils.Either.Companion.onRight

/**
 * LoginAndStoreRequest：新用户请求登录并存储课程信息到数据库
 *
 * 之后将发送[InternalSyncCourseRequestViaCookieData]请求
 * @param loginInfoData 用户登录信息，包括用户名和密码
 **/
class Login(val qq: Long, val loginInfoData: LoginInfoData) : IRequest {
    override suspend fun RequestHandlerService.handle() {
        Database.suspendQuery {
            val user = User.find { Users.qq eq qq }
            if(user.empty()) {
                val cookieData = LoginCookieData()
                val loginReceipt = SuperCourseApiService.loginViaPassword(loginInfoData) {
                    cookieData.apply {
                        this.jSessionId = it.jSessionId
                        this.serverId = it.serverId
                    }
                }
                loginReceipt.onRight { r ->
                    Database.query { User.new {
                        qq = this@Login.qq
                        studentId = r.data.student.studentId.toLong()
                        name = r.data.student.nickName
                        schoolId = r.data.student.schoolId
                        account = loginInfoData.username
                        password = AESUtils.encrypt(loginInfoData.password, SuperCourseApiService.pkey)
                    } }
                    PluginData.advancedTipOffset[qq] = PluginConfig.advancedTipTime
                    info("User $qq login successful.")
                    BotEventRouteService.sendMessageNonBlock(qq, "登录成功，正在同步你的课程。。。")
                    sendRequest(InternalSyncCourseRequestViaCookieData(qq, cookieData))
                    sendRequest(SyncSchoolTimetable(qq))
                }.onLeft { l ->
                    //用户登录请求失败，密码错误或其他错误(网络问题等)
                    error("Failed to login user ${qq}'s SuperCourse error, reason: ${l.data.errorStr}")
                    BotEventRouteService.sendMessageNonBlock(qq, "无法登录超级课表，原因：${l.data.errorStr}")
                }
            } else {
                info("User $qq has already login and cannot login again.")
                BotEventRouteService.sendMessageNonBlock(qq, "你已经登陆过了，不可以重复登录。\n如果你想修改密码，请发送\"帮助\"查看如何修改密码。")
            }
        }
    }

    override fun toString() = "LoginRequest(qq=$qq,account=${loginInfoData.username},password=${"*".repeat(loginInfoData.password.length)})"
}