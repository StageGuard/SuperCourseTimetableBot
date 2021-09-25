/*
 * Copyright 2020-2021 StageGuard.
 *
 *  此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 *  Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 *  https://github.com/KonnyakuCamp/SuperCourseTimetableBot/blob/main/LICENSE
 */
package me.stageguard.sctimetable.service.messageRoute

import kotlinx.coroutines.CoroutineScope
import me.stageguard.sctimetable.database.Database
import me.stageguard.sctimetable.database.model.Courses
import me.stageguard.sctimetable.database.model.User
import me.stageguard.sctimetable.database.model.Users
import me.stageguard.sctimetable.service.BotEventRouteService
import me.stageguard.sctimetable.service.RequestHandlerService
import me.stageguard.sctimetable.service.TimeProviderService
import me.stageguard.sctimetable.service.request.SyncCourse
import me.stageguard.sctimetable.utils.*
import net.mamoe.mirai.event.events.FriendMessageEvent
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import kotlin.contracts.ExperimentalContracts

@OptIn(ExperimentalContracts::class)
suspend fun FriendMessageEvent.syncCourse(coroutineScope: CoroutineScope) {
    Database.suspendQuery {
        val user = User.find { Users.qq eq subject.id }
        if (!user.empty()) {
            val courses = Courses(subject.id)
            SchemaUtils.create(courses)
            val currentSemesterCourses = courses.select {
                (courses.beginYear eq TimeProviderService.currentSemesterBeginYear) and
                        (courses.semester eq TimeProviderService.currentSemester)
            }
            if (currentSemesterCourses.empty()) {
                interactiveConversation(coroutineScope, eachTimeLimit = 10000L) {
                    send(
                        """
                                未找到 ${TimeProviderService.currentSemesterBeginYear} 学年第 ${TimeProviderService.currentSemester} 学期的课表信息！
                                是否要从超级课表同步？
                                发送 "确认" 进行同步。
                                请注意：请务必先打开超级课表App导入当前学期的课程，机器人才能从超级课表同步！
                            """.trimIndent()
                    )
                    select {
                        "确认" { collect("sync", true) }
                        default { collect("sync", false) }
                    }
                }.finish {
                    if (it["sync"].cast()) RequestHandlerService.sendRequest(
                        SyncCourse(
                            subject.id
                        )
                    )
                }.exception {
                    if (it is QuitConversationExceptions.TimeoutException) {
                        BotEventRouteService.sendMessageNonBlock(subject.id, "取消操作。")
                    }
                }
            } else {
                interactiveConversation(coroutineScope, eachTimeLimit = 10000L) {
                    send(
                        """
                                当前记录的课程有：
                                ${
                            Database.query {
                                currentSemesterCourses.joinToString("\n") {
                                    "                                ${it[courses.courseName]}"
                                }
                            }
                        }
                                是否要继续从超级课表同步？
                                发送 "确认" 进行同步。
                                请注意：继续同步会覆盖现在的所有课程信息。
                            """.trimIndent()
                    )
                    select {
                        "确认" { collect("sync", true) }
                        default { collect("sync", false) }
                    }
                }.finish {
                    if (it["sync"].cast()) RequestHandlerService.sendRequest(
                        SyncCourse(
                            subject.id
                        )
                    )
                }.exception {
                    if (it is QuitConversationExceptions.TimeoutException) {
                        BotEventRouteService.sendMessageNonBlock(subject.id, "取消操作。")
                    }
                }
            }
        } else subject.sendMessage("你还没有登录超级课表，无法同步课程")
    }
}