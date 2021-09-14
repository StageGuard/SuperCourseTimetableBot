package me.stageguard.sctimetable.service.request

import me.stageguard.sctimetable.api.edu_system.`super`.LoginCookieData
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
 * SyncCourseRequest：当数据库中已存在这个用户信息和课表信息，请求更新用户的课表信息
 *
 * 这是通过重新登录SuperCourse来同步
 *
 * 获取到cookie后将委托给[InternalSyncCourseRequestViaCookieData]更新
 * @param qq 要更新课表信息的用户的QQ号
 **/
class SyncCourse(val qq: Long) : IRequest {
    override suspend fun RequestHandlerService.handle() {
        Database.suspendQuery {
            val user = User.find { Users.qq eq qq }
            if(!user.empty()) {
                val cookieData = LoginCookieData()
                SuperCourseApiService.loginViaPassword(user.first().account, AESUtils.decrypt(user.first().password, SuperCourseApiService.pkey)) {
                    cookieData.jSessionId = it.jSessionId
                    cookieData.serverId = it.serverId
                }.also {
                    it.onRight {
                        sendRequest(InternalSyncCourseRequestViaCookieData(qq, cookieData))
                    }.onLeft { l ->
                        //用户记录在User的密码有误或者其他问题(网络问题等)
                        error("Failed to sync user $qq's courses, reason: ${l.data.errorStr}")
                        BotEventRouteService.sendMessageNonBlock(qq, "无法同步课程。原因：${l.data.errorStr}")
                    }
                }
            } else {
                error("Failed to sync user $qq's courses, reason: User doesn't exist.")
            }
        }
    }

    override fun toString() = "SyncCourseRequest(qq=$qq)"
}