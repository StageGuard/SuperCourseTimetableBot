package me.stageguard.sctimetable.service.request

import me.stageguard.sctimetable.api.edu_system.`super`.SuperCourseApiService
import me.stageguard.sctimetable.database.Database
import me.stageguard.sctimetable.database.model.User
import me.stageguard.sctimetable.database.model.Users
import me.stageguard.sctimetable.service.BotEventRouteService
import me.stageguard.sctimetable.service.IRequest
import me.stageguard.sctimetable.service.RequestHandlerService
import me.stageguard.sctimetable.utils.AESUtils

/**
 * ChangeUserPasswordRequest: 修改用户密码
 *
 * @param qq 用户
 * @param password 新密码
 */
class ChangeUserPassword(val qq: Long, val password: String) : IRequest {
    override suspend fun RequestHandlerService.handle() {
        Database.suspendQuery {
            val user = User.find { Users.qq eq qq }
            if(!user.empty()) {
                user.first().password = AESUtils.encrypt(password, SuperCourseApiService.pkey)
                info("User $qq successfully changed his password.")
                BotEventRouteService.sendMessageNonBlock(qq, "修改密码成功！")
            } else {
                error("Cannot change password for $qq because user doesn't exist.")
            }
        }
    }

    override fun toString() = "ChangeUserPasswordRequest(qq=$qq)"
}