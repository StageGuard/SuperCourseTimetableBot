/*
 * Copyright 2020-2021 KonnyakuCamp.
 *
 *  此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 *  Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 *  https://github.com/KonnyakuCamp/SuperCourseTimetableBot/blob/main/LICENSE
 */
@file:Suppress("unused")

package stageguard.sctimetable.service

import kotlinx.coroutines.*
import net.mamoe.mirai.event.*
import stageguard.sctimetable.AbstractPluginManagedService
import stageguard.sctimetable.PluginConfig
import stageguard.sctimetable.PluginData
import stageguard.sctimetable.PluginMain
import stageguard.sctimetable.api.edu_system.`super`.LoginInfoData
import stageguard.sctimetable.database.Database
import java.lang.management.ManagementFactory
import com.sun.management.OperatingSystemMXBean
import net.mamoe.mirai.event.events.*
import net.mamoe.mirai.message.data.*
import net.mamoe.mirai.utils.MiraiExperimentalApi
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import stageguard.sctimetable.database.model.*
import stageguard.sctimetable.utils.*
import java.util.regex.Pattern
import kotlin.contracts.ExperimentalContracts

object BotEventRouteService : AbstractPluginManagedService() {

    override val TAG: String = "BotEventRouteService"

    private val TIME_PERIOD_EXPRESSION = Pattern.compile("(\\d){1,2}[:：](\\d){1,2}-(\\d){1,2}[:：](\\d){1,2}")

    @ExperimentalContracts
    @MiraiExperimentalApi
    override suspend fun main() {
        PluginMain.targetBotInstance.eventChannel.subscribeAlways<NewFriendRequestEvent> {
            if(this.bot.id == PluginConfig.qq) {
                this.accept()
                this@BotEventRouteService.launch(coroutineContext) {
                    delay(5000L)
                    PluginMain.targetBotInstance.friends[this@subscribeAlways.fromId]?.sendMessage("""
                        欢迎使用 超级课表课程提醒机器人。
                        发送 "怎么用"/"帮助" 获取使用方法。
                    """.trimIndent())
                }
            }
        }
        PluginMain.targetBotInstance.eventChannel.subscribeFriendMessages {
            finding(Regex("^登录超级(课程表|课表)")) {
                interactiveConversation(this@BotEventRouteService, eachTimeLimit = 30000L) {
                    send("请输入超级课表账号")
                    receivePlain(key = "account")
                    send("请输入超级课表密码\n注意：若用手机验证码登录的超级课表，请先在超级课表app设置账号密码")
                    receivePlain(key = "password")
                }.finish {
                    subject.sendMessage("正在登录。。。")
                    RequestHandlerService.sendRequest(Request.LoginRequest(subject.id, LoginInfoData(it["account"].cast(), it["password"].cast())))
                }.exception { if(it is QuitConversationExceptions.TimeoutException) {
                    subject.sendMessage("太长时间未输入，请重新登录")
                } }
            }
            startsWith("修改时间表") { Database.suspendQuery {
                val thisUser = User.find { Users.qq eq subject.id }
                val schoolTimetable = SchoolTimetable.find { SchoolTimetables.schoolId eq thisUser.first().schoolId }.first()
                interactiveConversation(this@BotEventRouteService, eachTimeLimit = 30000L, eachTryLimit = 5) {
                    send("""
                        确认要修改 ${schoolTimetable.schoolName} 的时间表吗
                        注意：修改时间表将会影响到本校所有用户，在修改前请确保时间表确实有误！
                        若你不知道当前时间表数据，发送"查看时间表"查看。
                        发送"确认"继续修改，否则取消修改。
                    """.trimIndent())
                    select {
                        "确认" {
                            send("""
                                要修改当前周数还是时间表？
                                发送 "周数" 或 "时间表" 决定
                            """.trimIndent())
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
                }.finish { when(it["selection"].cast<Int>()) {
                    1 -> RequestHandlerService.sendRequest(Request.SyncSchoolWeekPeriodRequest(subject.id, it["weekPeriod"].cast<String>().toInt()))
                    2 -> RequestHandlerService.sendRequest(Request.SyncSchoolTimetableRequest(subject.id, it["timetable"].cast(), forceUpdate = true))
                } }.exception { when(it) {
                    is QuitConversationExceptions.IllegalInputException -> subject.sendMessage("输入格式有误次数，过多，请重新发送\"修改时间表\"")
                    is QuitConversationExceptions.TimeoutException -> subject.sendMessage("长时间未输入，请重新发送\"修改时间表\"")
                    is QuitConversationExceptions.AdvancedQuitException -> subject.sendMessage("取消修改时间表。")
                } }
            } }
            startsWith("同步课程") { Database.suspendQuery {
                val user = User.find { Users.qq eq subject.id }
                if(!user.empty()) {
                    val courses = Courses(subject.id)
                    SchemaUtils.create(courses)
                    val currentSemesterCourses = courses.select {
                        (courses.beginYear eq TimeProviderService.currentSemesterBeginYear) and
                        (courses.semester eq TimeProviderService.currentSemester)
                    }
                    if(currentSemesterCourses.empty()) {
                        interactiveConversation(this@BotEventRouteService, eachTimeLimit = 10000L) {
                            send("""
                                未找到 ${TimeProviderService.currentSemesterBeginYear} 学年第 ${TimeProviderService.currentSemester} 学期的课表信息！
                                是否要从超级课表同步？
                                发送 "确认" 进行同步。
                                请注意：请务必先打开超级课表App导入当前学期的课程，机器人才能从超级课表同步！
                            """.trimIndent())
                            select {
                                "确认" { collect("sync", true) }
                                default { collect("sync", false) }
                            }
                        }.finish {
                            if(it["sync"].cast()) RequestHandlerService.sendRequest(Request.SyncCourseRequest(subject.id))
                        }.exception {
                            if(it is QuitConversationExceptions.TimeoutException) {
                                sendMessageNonBlock(subject.id, "取消操作。")
                            }
                        }
                    } else {
                        interactiveConversation(this@BotEventRouteService, eachTimeLimit = 10000L) {
                            send("""
                                当前记录的课程有：
                                ${Database.query { currentSemesterCourses.joinToString("\n") { 
                                    "                                ${it[courses.courseName]}"
                                } }}
                                是否要继续从超级课表同步？
                                发送 "确认" 进行同步。
                                请注意：继续同步会覆盖现在的所有课程信息。
                            """.trimIndent())
                            select {
                                "确认" { collect("sync", true) }
                                default { collect("sync", false) }
                            }
                        }.finish {
                            if(it["sync"].cast()) RequestHandlerService.sendRequest(Request.SyncCourseRequest(subject.id))
                        }.exception {
                            if(it is QuitConversationExceptions.TimeoutException) {
                                sendMessageNonBlock(subject.id, "取消操作。")
                            }
                        }
                    }
                } else subject.sendMessage("你还没有登录超级课表，无法同步课程")
            } }
            startsWith("查看时间表") { Database.suspendQuery {
                val user = User.find { Users.qq eq subject.id }
                if(!user.empty()) {
                    val schoolTimetable = SchoolTimetable.find {
                        (SchoolTimetables.schoolId eq user.first().schoolId) and
                        (SchoolTimetables.beginYear eq TimeProviderService.currentSemesterBeginYear) and
                        (SchoolTimetables.semester eq TimeProviderService.currentSemester)
                    }.firstOrNull()
                    if(schoolTimetable != null) {
                        var index = 1
                        subject.sendMessage("${schoolTimetable.schoolName}\n" +
                                "${TimeProviderService.currentSemesterBeginYear} 学年第 ${TimeProviderService.currentSemester} 学期。\n" +
                                "当前是第 ${TimeProviderService.currentWeekPeriod[schoolTimetable.schoolId]} 周。\n" +
                                "时间表：\n" +
                                "${schoolTimetable.scheduledTimeList.split("|").joinToString("\n") {
                                    "${index ++}. ${it.replace("-", " 到 ")}"
                                }}\n" +
                                "如果以上数据有任何问题，请发送\"修改时间表\"修改。"
                        )
                    } else interactiveConversation(scope = this@BotEventRouteService) {
                        send("""
                            未找到 ${TimeProviderService.currentSemesterBeginYear} 年第 ${TimeProviderService.currentSemester} 学期的时间表。
                            要添加时间表吗？
                            发送"沿用"将上个学期/学年的时间表沿用至当前学期。
                            发送"同步"将从服务器重新同步时间表信息。
                            否则取消操作。
                        """.trimIndent())
                        select(timeoutLimit = 10000L) {
                            "沿用" { collect("syncType", 1) }
                            "同步" { collect("syncType", 2) }
                        }
                    }.finish {
                        when(it["syncType"].cast<Int>()) {
                            1 -> RequestHandlerService.sendRequest(Request.InheritTimetableFromLastSemester(sender.id))
                            2 -> RequestHandlerService.sendRequest(Request.SyncSchoolTimetableRequest(sender.id, forceUpdate = true, alsoSyncCourses = true))
                        }
                    }

                } else subject.sendMessage("你还没有登录超级课表，无法同步时间表")
            } }
            finding(Regex("^今[日天]课[表程]")) { Database.suspendQuery {
                val user = User.find { Users.qq eq subject.id }
                if(!user.empty()) {
                    val schoolTimetable = ScheduleListenerService.getSchoolTimetable(user.first().schoolId)
                    val courses = ScheduleListenerService.getUserTodayCourses(subject.id, user.first().schoolId)
                    var index = 1
                    subject.sendMessage(if(courses.isEmpty()) "今日没有课程。" else courses.joinToString("\n") {
                        "${index ++}. " + it.courseName + "(${schoolTimetable[it.startSection - 1].first.let { stamp ->
                            "${(stamp - (stamp % 60)) / 60}:${(stamp % 60).let { min -> if(min < 10) ("0$min") else min }}"
                        }}到${schoolTimetable[it.endSection - 1].second.let { stamp ->
                            "${(stamp - (stamp % 60)) / 60}:${(stamp % 60).let { min -> if(min < 10) ("0$min") else min }}"
                        }})在${it.locale}"
                    })
                } else subject.sendMessage("你还没有登录超级课表，无法查看今天课程")
            } }
            finding(Regex("^删除(用户|账户|账号)")) {
                interactiveConversation(this@BotEventRouteService, eachTimeLimit = 10000L) {
                    send("确认要删除你的所有信息吗，包括用户信息，你的课程信息？\n删除后将不再为你发送课程提醒。\n发送\"确认\"以删除。")
                    select {
                        "确认" { collect("delete", true) }
                        default { collect("delete", false) }
                    }
                }.finish { if(it["delete"].cast()) {
                    RequestHandlerService.sendRequest(Request.DeleteCourseRequest(subject.id))
                } }.exception { subject.sendMessage("长时间未确认，已取消删除。\n如的确要删除，请重新发送\"删除用户\"。") }
            }
            startsWith("修改密码") { Database.suspendQuery {
                if(!User.find { Users.qq eq subject.id }.empty()) {
                    interactiveConversation(this@BotEventRouteService, eachTimeLimit = 30000L) {
                        send("请输入新的密码\n注意：只会存储在数据库中，不会验证新密码的正确性。")
                        receivePlain(key = "password")
                    }.finish {
                        RequestHandlerService.sendRequest(Request.ChangeUserPasswordRequest(subject.id, it["password"].cast()))
                    }.exception {
                        subject.sendMessage("长时间未输入新的密码，请重新发送\"修改密码\"。")
                    }
                } else subject.sendMessage("你还没有登录超级课表，无法修改密码")
            } }
            startsWith("修改提醒时间") {
                interactiveConversation(this@BotEventRouteService, eachTimeLimit = 10000L) {
                    send("""
                        你想在上课前多长时间收到课程提醒(单位：分钟)？
                        请输入一个数字代表你想要修改的时间。
                        当前为提前 ${PluginData.advancedTipOffset[subject.id] ?: PluginConfig.advancedTipTime} 分钟提醒。
                    """.trimIndent())
                    receivePlain(key = "advTipTime", tryLimit = 3) { it.toInt() > 0 }
                }.finish {
                    PluginData.advancedTipOffset[subject.id] = it["advTipTime"].cast<String>().toInt()
                    ScheduleListenerService.restartUserNotification(subject.id)
                    subject.sendMessage("成功修改课程提醒时间为 ${PluginData.advancedTipOffset[subject.id]} 分钟前提醒。")
                }.exception { when(it) {
                    is QuitConversationExceptions.TimeoutException -> subject.sendMessage("长时间未输入，请重新输入\"修改提醒时间\"。")
                    is QuitConversationExceptions.IllegalInputException -> subject.sendMessage("格式输入有误次数过多，请重新输入\"修改提醒时间\"。")
                    else -> {}
                } }
            }
            (case("怎么用") or case("帮助")) {
                subject.sendMessage("""
                    欢迎使用 超级课表课程提醒QQ机器人。
                    它可以在你下一节课上课前提醒你这节课的信息，避免你错过课程。
                    指令：
                      "登录超级课表" - 使用密码登录你的超课表账户
                      "查看时间表" - 查看本校的作息时间表
                      "修改时间表" - 修改本校的作息时间表
                      "今日课程" - 查看你今天的所有课程信息
                      "怎么用/帮助" - 显示这条信息
                      "修改密码" - 修改记录在机器人数据库中的密码
                      "删除用户" - 删除你的记录在机器人数据库中的信息，并停止课程提醒服务。
                      "修改提前提醒时间" - 修改上课提前多长时间提醒
                      "状态" - 查看超级课表课程提醒QQ机器人的运行情况
                   
                    注意：当前处于初代测试阶段。
                """.trimIndent())
            }
            case("状态") { Database.suspendQuery {
                val osMxBean: OperatingSystemMXBean = ManagementFactory.getOperatingSystemMXBean() as OperatingSystemMXBean
                subject.sendMessage("""
                    SuperCourseTimetable Plugin
                    Running on powerful Mirai Console
                    
                    Status: 
                    - Serving ${User.all().count()} users.
                    - System info: ${osMxBean.name} (${osMxBean.arch})
                    - Process(java) / System CPU load: ${String.format("%.2f", osMxBean.processCpuLoad * 100)}% / ${String.format("%.2f", osMxBean.cpuLoad * 100)}%
                    - Memory usage / total: ${(osMxBean.totalMemorySize - osMxBean.freeMemorySize) / 1024 / 1024}MB / ${osMxBean.totalMemorySize / 1024 / 1024}MB
                    
                    This project is a open source project.
                    You can visit https://github.com/KonnyakuCamp/SuperCourseTimetableBot for more details.
                """.trimIndent())
            } }
        }
        verbose("start listening FriendMessageEvent and NewFriendRequestEvent")
    }

    fun sendMessageNonBlock(friendId: Long, msg: String, randomDelayMax: Long = 0L) = launch(coroutineContext) {
        if(PluginMain.targetBotInstance.isOnline) {
            delay((Math.random() * randomDelayMax).toLong())
            PluginMain.targetBotInstance.friends[friendId]?.sendMessage(msg)
        }
    }
}