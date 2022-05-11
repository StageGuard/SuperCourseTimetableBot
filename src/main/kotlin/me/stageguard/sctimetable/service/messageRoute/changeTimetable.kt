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
import me.stageguard.sctimetable.database.model.SchoolTimetable
import me.stageguard.sctimetable.database.model.SchoolTimetables
import me.stageguard.sctimetable.database.model.User
import me.stageguard.sctimetable.database.model.Users
import me.stageguard.sctimetable.service.RequestHandlerService
import me.stageguard.sctimetable.service.request.SyncSchoolTimetable
import me.stageguard.sctimetable.service.request.SyncSchoolWeekPeriod
import me.stageguard.sctimetable.utils.*
import net.mamoe.mirai.event.events.FriendMessageEvent
import java.util.regex.Pattern
import kotlin.contracts.ExperimentalContracts

private val TIME_PERIOD_EXPRESSION = Pattern.compile("(\\d){1,2}[:：](\\d){1,2}-(\\d){1,2}[:：](\\d){1,2}")

@OptIn(ExperimentalContracts::class)
suspend fun FriendMessageEvent.changeTimetable(coroutineScope: CoroutineScope) {
    Database.suspendQuery {
        val thisUser = User.find { Users.qq eq subject.id }
        if (thisUser.empty()) {
            subject.sendMessage("你还没有登录超级课表，无法修改时间表，请先登录超级课表。")
            return@suspendQuery
        }

        val schoolTimetable =
            SchoolTimetable.find { SchoolTimetables.schoolId eq thisUser.first().schoolId }.first()
        interactiveConversation(coroutineScope, eachTimeLimit = 30000L, eachTryLimit = 5) {
            send(
                """
                        确认要修改 ${schoolTimetable.schoolName} 的时间表吗
                        注意：修改时间表将会影响到本校所有用户，在修改前请确保时间表确实有误！
                        若你不知道当前时间表数据，发送"查看时间表"查看。
                        发送"确认"继续修改，否则取消修改。
                    """.trimIndent()
            )
            select {
                "确认" {
                    send(
                        """
                                要修改当前周数还是时间表？
                                发送 "周数" 或 "时间表" 决定
                            """.trimIndent()
                    )
                    select {
                        "周数" {
                            send("正在修改当前周数\n请输入一个数字表示当前周数")
                            receivePlain(key = "weekPeriod") { it.toInt() > 0 }
                            collect("selection", 1)
                        }
                        "时间表" {
                            send("正在修改当前时间表\n请输入一个数字表示当前学校一天有几节课\n默认为 8 节课")
                            val timetable = mutableListOf<Pair<String, String>>()
                            repeat(receivePlain { it.toInt() > 2 }.toInt()) {
                                send("请输入第 ${it + 1} 节课的时间${if (it == 0) "\n例如：8:10-8:55\n注意：不要在中间加空格，小时是24小时制！" else ""}")
                                timetable.add(receivePlain { res ->
                                    TIME_PERIOD_EXPRESSION.matcher(res).find()
                                }.replace("：", ":").split("-").let { ls -> ls[0] to ls[1] })
                            }
                            collect("selection", 2)
                            collect("timetable", timetable)
                        }
                    }
                }
                default { finish() }
            }
        }.finish {
            when (it["selection"].cast<Int>()) {
                1 -> RequestHandlerService.sendRequest(
                    SyncSchoolWeekPeriod(
                        subject.id,
                        it["weekPeriod"].cast<String>().toInt()
                    )
                )
                2 -> RequestHandlerService.sendRequest(
                    SyncSchoolTimetable(
                        subject.id,
                        it["timetable"].cast(),
                        forceUpdate = true
                    )
                )
            }
        }.exception {
            when (it) {
                is QuitConversationExceptions.IllegalInputException -> subject.sendMessage("输入格式有误次数，过多，请重新发送\"修改时间表\"")
                is QuitConversationExceptions.TimeoutException -> subject.sendMessage("长时间未输入，请重新发送\"修改时间表\"")
                is QuitConversationExceptions.AdvancedQuitException -> subject.sendMessage("取消修改时间表。")
            }
        }
    }
}
