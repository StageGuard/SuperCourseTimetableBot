package me.stageguard.sctimetable.service.messageRoute

import kotlinx.coroutines.CoroutineScope
import me.stageguard.sctimetable.database.Database
import me.stageguard.sctimetable.database.model.User
import me.stageguard.sctimetable.database.model.Users
import me.stageguard.sctimetable.service.ScheduleListenerService
import me.stageguard.sctimetable.service.TimeProviderService
import net.mamoe.mirai.event.events.FriendMessageEvent

suspend fun FriendMessageEvent.queryCourse(matchResult: MatchResult) {
    val whichDayOfWeek = when {
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
        if (!user.empty()) {
            val schoolTimetable = ScheduleListenerService.getSchoolTimetable(user.first().schoolId)

            val courses =
                ScheduleListenerService.getUserTodayCourses(
                    subject.id,
                    user.first().schoolId,
                    whichDayOfWeek
                )
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
        } else subject.sendMessage("你还没有登录超级课表，无法查看${matchResult.groupValues[1]}课程")
    }
}