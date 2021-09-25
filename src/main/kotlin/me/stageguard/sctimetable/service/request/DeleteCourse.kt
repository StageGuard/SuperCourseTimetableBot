/*
 * Copyright 2020-2021 StageGuard.
 *
 *  此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 *  Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 *  https://github.com/KonnyakuCamp/SuperCourseTimetableBot/blob/main/LICENSE
 */
package me.stageguard.sctimetable.service.request

import me.stageguard.sctimetable.PluginData
import me.stageguard.sctimetable.database.Database
import me.stageguard.sctimetable.database.model.Courses
import me.stageguard.sctimetable.database.model.SchoolTimetables
import me.stageguard.sctimetable.database.model.User
import me.stageguard.sctimetable.database.model.Users
import me.stageguard.sctimetable.service.*
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.deleteWhere

/**
 * DeleteCourseRequest：用户请求删除数据库中该用户的所有信息
 * @param qq 要删除信息的用户的QQ号
 */
class DeleteCourse(val qq: Long) : IRequest {
    override suspend fun RequestHandlerService.handle() {
        Database.suspendQuery {
            val user = User.find { Users.qq eq qq }
            if(!user.empty()) {
                User.find { Users.schoolId eq user.first().schoolId }.also { remainUsers -> if(remainUsers.count() == 1L) {
                    SchoolTimetables.deleteWhere { SchoolTimetables.schoolId eq user.first().schoolId }
                    TimeProviderService.currentWeekPeriod.remove(user.first().schoolId)
                    ScheduleListenerService.removeSchoolTimetable(user.first().schoolId)
                } }
                PluginData.advancedTipOffset.remove(qq)
                ScheduleListenerService.stopAndRemoveUserNotificationJob(qq)
                SchemaUtils.drop(Courses(qq))
                Users.deleteWhere { Users.qq eq qq }
                BotEventRouteService.sendMessageNonBlock(qq, "已经成功删除你的所有信息，将不会再收到课程提醒。\n若还想继续使用课程提醒，请重新登录。")
            }
        }
    }

    override fun toString() = "DeleteCourseRequest(qq=$qq)"
}