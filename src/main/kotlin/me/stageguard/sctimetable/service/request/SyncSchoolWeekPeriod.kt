package me.stageguard.sctimetable.service.request

import me.stageguard.sctimetable.database.Database
import me.stageguard.sctimetable.database.model.SchoolTimetable
import me.stageguard.sctimetable.database.model.SchoolTimetables
import me.stageguard.sctimetable.database.model.User
import me.stageguard.sctimetable.database.model.Users
import me.stageguard.sctimetable.service.*
import org.jetbrains.exposed.sql.and

/**
 * SyncSchoolWeekPeriod：同步这个学校当前学期的周数，
 *
 * 请注意：这个同步是指修改数据库中的[currentWeek]，并不是指[TimeProviderService.SchoolWeekPeriodUpdater]的同步，通常发生在用户发现当前weekPeriod不正确而提出修改请求。
 * @param qq 要修改这个用户所在的学校作息时间
 * @param currentWeek 当前周数
 */
class SyncSchoolWeekPeriod(val qq: Long, val currentWeek: Int) : IRequest {
    override suspend fun RequestHandlerService.handle() {
        Database.suspendQuery {
            val user = User.find { Users.qq eq qq }
            if(!user.empty()) {
                val schoolTimetable = SchoolTimetable.find {
                    (SchoolTimetables.schoolId eq user.first().schoolId) and
                            (SchoolTimetables.beginYear eq TimeProviderService.currentSemesterBeginYear) and
                            (SchoolTimetables.semester eq TimeProviderService.currentSemester)
                }
                if(!schoolTimetable.empty()) {
                    schoolTimetable.first().apply {
                        timeStampWhenAdd = TimeProviderService.currentTimeStamp.toString()
                        weekPeriodWhenAdd = currentWeek
                    }
                    TimeProviderService.immediateUpdateSchoolWeekPeriod() //阻塞以保证这个过程结束后再执行下一步
                    ScheduleListenerService.onChangeSchoolWeekPeriod(schoolTimetable.first().schoolId)
                    info("Sync school week period for user $qq's school successfully, currentWeek=$currentWeek.")
                    BotEventRouteService.sendMessageNonBlock(qq, "成功修改 ${TimeProviderService.currentSemesterBeginYear} 学年第 ${TimeProviderService.currentSemester} 学期的周数为第 $currentWeek 周。")
                    User.find { Users.schoolId eq user.first().schoolId }.forEach {
                        if(it.qq != qq) BotEventRouteService.sendMessageNonBlock(it.qq, "您所在的学校的当前周数已经被同校用户 $qq 修改，请输入\"查看时间表\"查看。\n若修改有误或恶意修改，请联系对方。", 60000L)
                    }
                } else {
                    error("Failed to sync school week period for user $qq's school, reason: School doesn't exist.")
                    BotEventRouteService.sendMessageNonBlock(qq, "无法修改 ${TimeProviderService.currentSemesterBeginYear} 学年第 ${TimeProviderService.currentSemester} 学期的周数，未找到该学校！\n如果你已经登录，请删除你的信息并重新登录。")
                }
            } else {
                error("Failed to sync school week period for user ${qq}'s school, reason: User doesn't exist.")
            }
        }
    }

    override fun toString() = "SyncSchoolWeekPeriodRequest(qq=$qq, currentWeek=$currentWeek)"
}