package me.stageguard.sctimetable.service.messageRoute

import kotlinx.coroutines.CoroutineScope
import me.stageguard.sctimetable.database.Database
import me.stageguard.sctimetable.database.model.SchoolTimetable
import me.stageguard.sctimetable.database.model.SchoolTimetables
import me.stageguard.sctimetable.database.model.User
import me.stageguard.sctimetable.database.model.Users
import me.stageguard.sctimetable.service.RequestHandlerService
import me.stageguard.sctimetable.service.TimeProviderService
import me.stageguard.sctimetable.service.request.InheritTimetableFromLastSemester
import me.stageguard.sctimetable.service.request.SyncSchoolTimetable
import me.stageguard.sctimetable.utils.cast
import me.stageguard.sctimetable.utils.finish
import me.stageguard.sctimetable.utils.interactiveConversation
import net.mamoe.mirai.event.events.FriendMessageEvent
import org.jetbrains.exposed.sql.and
import kotlin.contracts.ExperimentalContracts

@OptIn(ExperimentalContracts::class)
suspend fun FriendMessageEvent.queryTimetable(coroutineScope: CoroutineScope) {
    Database.suspendQuery {
        val user = User.find { Users.qq eq subject.id }
        if (!user.empty()) {
            val schoolTimetable = SchoolTimetable.find {
                (SchoolTimetables.schoolId eq user.first().schoolId) and
                        (SchoolTimetables.beginYear eq TimeProviderService.currentSemesterBeginYear) and
                        (SchoolTimetables.semester eq TimeProviderService.currentSemester)
            }.firstOrNull()
            if (schoolTimetable != null) {
                var index = 1
                subject.sendMessage(
                    "${schoolTimetable.schoolName}\n" +
                            "${TimeProviderService.currentSemesterBeginYear} 学年第 ${TimeProviderService.currentSemester} 学期。\n" +
                            "当前是第 ${TimeProviderService.currentWeekPeriod[schoolTimetable.schoolId]} 周。\n" +
                            "时间表：\n" +
                            "${
                                schoolTimetable.scheduledTimeList.split("|").joinToString("\n") {
                                    "${index++}. ${it.replace("-", " 到 ")}"
                                }
                            }\n" +
                            "如果以上数据有任何问题，请发送\"修改时间表\"修改。"
                )
            } else interactiveConversation(coroutineScope) {
                send(
                    """
                            未找到 ${TimeProviderService.currentSemesterBeginYear} 年第 ${TimeProviderService.currentSemester} 学期的时间表。
                            要添加时间表吗？
                            发送"沿用"将上个学期/学年的时间表沿用至当前学期。
                            发送"同步"将从服务器重新同步时间表信息。
                            否则取消操作。
                        """.trimIndent()
                )
                select(timeoutLimit = 10000L) {
                    "沿用" { collect("syncType", 1) }
                    "同步" { collect("syncType", 2) }
                }
            }.finish {
                when (it["syncType"].cast<Int>()) {
                    1 -> RequestHandlerService.sendRequest(InheritTimetableFromLastSemester(sender.id))
                    2 -> RequestHandlerService.sendRequest(
                        SyncSchoolTimetable(
                            sender.id,
                            forceUpdate = true,
                            alsoSyncCourses = true
                        )
                    )
                }
            }

        } else subject.sendMessage("你还没有登录超级课表，无法同步时间表")
    }
}