/*
 * Copyright 2020-2021 StageGuard.
 *
 *  此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 *  Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 *  https://github.com/KonnyakuCamp/SuperCourseTimetableBot/blob/main/LICENSE
 */
package me.stageguard.sctimetable.service.messageRoute

import me.stageguard.sctimetable.database.Database
import me.stageguard.sctimetable.database.model.Courses
import me.stageguard.sctimetable.database.model.User
import me.stageguard.sctimetable.database.model.Users
import me.stageguard.sctimetable.service.ScheduleListenerService
import me.stageguard.sctimetable.service.SingleCourse
import me.stageguard.sctimetable.service.TimeProviderService
import net.mamoe.mirai.event.events.FriendMessageEvent
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select

fun queryFromDatabase(
    qq: Long, belongingSchool: Int, inputDayOfWeek: Int
) = Courses(qq).run {
    val isNextWeek = if(inputDayOfWeek > 7) 1 else 0
    select {
        (beginYear eq TimeProviderService.currentSemesterBeginYear) and
                (semester eq TimeProviderService.currentSemester) and
                (whichDayOfWeek eq if(inputDayOfWeek > 7) inputDayOfWeek % 7 else inputDayOfWeek)
    }.filter {
        val weeks = it[weekPeriod].split(" ").map { w -> w.toInt() }
        weeks.contains(TimeProviderService.currentWeekPeriod.getOrDefault(belongingSchool, -1) + isNextWeek)
    }.sortedBy { it[sectionStart] }.map {
        SingleCourse(
            it[sectionStart],
            it[sectionEnd],
            it[courseName],
            it[teacherName],
            it[locale],
            TimeProviderService.currentSemester,
            TimeProviderService.currentSemesterBeginYear
        )
    }
}

suspend fun FriendMessageEvent.queryCourse(matchResult: MatchResult) {
    val whichDay = when {
        matchResult.groupValues[2] == "一" -> 1
        matchResult.groupValues[2] == "二" -> 2
        matchResult.groupValues[2] == "三" -> 3
        matchResult.groupValues[2] == "四" -> 4
        matchResult.groupValues[2] == "五" -> 5
        matchResult.groupValues[2] == "六" -> 6
        matchResult.groupValues[2] == "天" -> 7
        matchResult.groupValues[2] == "日" -> 7
        matchResult.groupValues[3] == "今" -> TimeProviderService.currentTimeStamp.dayOfWeek.value
        matchResult.groupValues[3] == "明" -> TimeProviderService.currentTimeStamp.dayOfWeek.value + 1
        else -> {
            subject.sendMessage("你还没有输入想查询的日期或格式有误！\n 例子：今日课程 或 明日课程 或 周几课程")
            return
        }
    }

    Database.suspendQuery {
        val user = User.find { Users.qq eq subject.id }
        if (user.empty()) {
            subject.sendMessage("你还没有登录超级课表，无法查看${matchResult.groupValues[1]}课程，请先登录超级课表。")
            return@suspendQuery
        }

        val schoolTimetable = ScheduleListenerService.getSchoolTimetable(user.first().schoolId)

        val courses = queryFromDatabase(subject.id, user.first().schoolId, whichDay)
        var index = 1
        subject.sendMessage(if (courses.isEmpty()) "${matchResult.groupValues[1]}没有课程。" else courses.joinToString("\n") {
            "${index++}. " + it.courseName + "(${
                schoolTimetable[it.startSection - 1].first.let { stamp ->
                    "${(stamp - (stamp % 60)) / 60}:${(stamp % 60).let { min -> if (min < 10) ("0$min") else min }}"
                }
            }到${
                schoolTimetable[it.endSection - 1].second.let { stamp ->
                    "${(stamp - (stamp % 60)) / 60}:${(stamp % 60).let { min -> if (min < 10) ("0$min") else min }}"
                }
            })在${it.locale}"
        })
    }
}
