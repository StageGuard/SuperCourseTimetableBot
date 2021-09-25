/*
 * Copyright 2020-2021 StageGuard.
 *
 *  此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 *  Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 *  https://github.com/KonnyakuCamp/SuperCourseTimetableBot/blob/main/LICENSE
 */
package me.stageguard.sctimetable.service.request

import me.stageguard.sctimetable.api.edu_system.`super`.SuperCourseApiService
import me.stageguard.sctimetable.database.Database
import me.stageguard.sctimetable.database.model.SchoolTimetable
import me.stageguard.sctimetable.database.model.SchoolTimetables
import me.stageguard.sctimetable.database.model.User
import me.stageguard.sctimetable.database.model.Users
import me.stageguard.sctimetable.service.*
import me.stageguard.sctimetable.utils.AESUtils
import me.stageguard.sctimetable.utils.Either.Companion.onLeft
import me.stageguard.sctimetable.utils.Either.Companion.onRight
import org.jetbrains.exposed.sql.SizedIterable
import org.jetbrains.exposed.sql.and

/**
 * SyncSchoolTimetableRequest：同步这个学校当前学期的作息表.
 *
 * @param qq 要修改这个用户所在的学校作息时间
 * @param newTimetable 时间表列表
 * @param forceUpdate 当[newTimetable]为null时决定是否强制从服务器同步
 */
class SyncSchoolTimetable(
    val qq: Long,
    val newTimetable: List<Pair<String, String>>? = null,
    val forceUpdate: Boolean = false,
    val alsoSyncCourses: Boolean = false
) : IRequest {
    override suspend fun RequestHandlerService.handle() {
        Database.suspendQuery<Unit> {
            val user = User.find { Users.qq eq qq }
            if(!user.empty()) {
                val schoolTimeTable = SchoolTimetable.find {
                    (SchoolTimetables.schoolId eq user.first().schoolId) and
                            (SchoolTimetables.beginYear eq TimeProviderService.currentSemesterBeginYear) and
                            (SchoolTimetables.semester eq TimeProviderService.currentSemester)
                }
                if(forceUpdate) {
                    if(newTimetable == null) {
                        //从服务器更新时间表
                        syncFromServer(user, schoolTimeTable.empty())
                        ScheduleListenerService.onChangeSchoolTimetable(user.first().schoolId)
                        info("Sync timetable from server for $qq's school successfully, forceUpdate=$forceUpdate.")
                        BotEventRouteService.sendMessageNonBlock(qq, "成功强制从服务器同步时间表，将影响学校其他用户。")
                    } else {
                        //手动修正时间表
                        schoolTimeTable.first().scheduledTimeList = newTimetable.joinToString("|") { "${it.first}-${it.second}" }
                        ScheduleListenerService.onChangeSchoolTimetable(user.first().schoolId)
                        info("Sync timetable from user custom list for $qq's school successfully, forceUpdate=$forceUpdate.")
                        BotEventRouteService.sendMessageNonBlock(qq, "已修正学校时间表，将影响学校的其他用户。")
                    }
                    User.find { Users.schoolId eq user.first().schoolId }.forEach {
                        if(alsoSyncCourses) {
                            if(it.qq != qq) BotEventRouteService.sendMessageNonBlock(it.qq,
                                "您所在的学校 ${TimeProviderService.currentSemesterBeginYear} 学年第 ${TimeProviderService.currentSemester} 学期的时间表已经被同校用户 $qq 从服务器同步。\n请输入\"查看时间表\"查看。\n正在自动为你同步当前学期课程...",
                                60000L)
                            sendRequest(SyncCourse(it.qq))
                        } else {
                            if(it.qq != qq) BotEventRouteService.sendMessageNonBlock(it.qq, "您所在的学校时间表已经被同校用户 $qq 同步/修正，请输入\"查看时间表\"查看。\n若修改有误或恶意修改，请联系对方。", 60000L)
                        }

                    }
                } else if(schoolTimeTable.empty()) {
                    //首次从服务器同步时间表
                    syncFromServer(user, true)
                    info("Sync timetable from server for $qq's school successfully.")
                    BotEventRouteService.sendMessageNonBlock(qq, "成功从服务器同步学校的时间表信息。\n注意：这是首次同步您的学校时间表，若当前周数有误，请发送\"修改时间表\"修改。")
                    sendRequest(SyncSchoolWeekPeriod(qq, 1))
                } else {
                    warning("Deny to sync school timetable for $qq's school, forceUpdate=$forceUpdate.")
                    BotEventRouteService.sendMessageNonBlock(qq, "您所在的学校的时间表信息已在之前被同校用户 $qq 同步并修正，输入\"查看时间表\"查看。")
                }
            } else {
                error("Failed to sync school timetable $qq's school, reason: User doesn't exist.")
            }
        }
    }

    suspend fun syncFromServer(user: SizedIterable<User>, createNew : Boolean) {
        SuperCourseApiService.loginViaPassword(
            user.first().account, AESUtils.decrypt(user.first().password, SuperCourseApiService.pkey)
        ).onRight { r ->
            val scheduledTimetable = r.data.student.attachmentBO.myTermList.first { termList ->
                termList.beginYear == TimeProviderService.currentSemesterBeginYear && termList.term == TimeProviderService.currentSemester
                //这超级课表是什么傻逼，妈的还带空字符串
            }.courseTimeList.courseTimeBO.run {
                if(isNotEmpty()) {
                    filter { it.beginTimeStr.isNotEmpty() }.joinToString("|") { time ->
                        "${time.beginTimeStr.substring(0..1)}:${time.beginTimeStr.substring(2..3)}-${time.endTimeStr.substring(0..1)}:${time.endTimeStr.substring(2..3)}"
                    }
                } else {
                    //empty default
                    "08:10-08:55|09:05-09:50|10:10-10:55|11:05-11:50|13:40-14:25|14:35-15:20|15:30-16:15|16:25-17:10|18:05-18:50|19:00-19:45|19:55-20:40|20:50-21:35"
                }
            }
            if(createNew) {
                SchoolTimetable.new {
                    schoolId = r.data.student.schoolId
                    schoolName = r.data.student.schoolName
                    beginYear = TimeProviderService.currentSemesterBeginYear
                    semester = TimeProviderService.currentSemester
                    scheduledTimeList = scheduledTimetable
                    timeStampWhenAdd = TimeProviderService.currentTimeStamp.toString()
                    weekPeriodWhenAdd = 1
                }
            } else {
                SchoolTimetable.find {
                    (SchoolTimetables.schoolId eq user.first().schoolId) and
                            (SchoolTimetables.beginYear eq TimeProviderService.currentSemesterBeginYear) and
                            (SchoolTimetables.semester eq TimeProviderService.currentSemester)
                }.first().scheduledTimeList = scheduledTimetable
            }
            TimeProviderService.immediateUpdateSchoolWeekPeriod()
        }.onLeft { l ->
            //用户记录在User的密码有误或者其他问题(网络问题等)
            RequestHandlerService.error("Failed to sync user $qq's school timetable, reason: ${l.data.errorStr}")
            BotEventRouteService.sendMessageNonBlock(qq,"无法从服务器同步学校时间表信息，可能是因为你已经修改了超级课表的密码。\n具体原因：${l.data.errorStr}")
        }
    }

    override fun toString() = "SyncSchoolTimetableRequest(qq=$qq,newTimetable=${newTimetable?.joinToString(",") { "[${it.first}->${it.second}]" } ?: "<syncFromServer>"},forceUpdate=$forceUpdate,alsoSyncCourses=$alsoSyncCourses)"
}