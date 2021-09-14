package me.stageguard.sctimetable.service.request

import me.stageguard.sctimetable.database.Database
import me.stageguard.sctimetable.database.model.SchoolTimetable
import me.stageguard.sctimetable.database.model.SchoolTimetables
import me.stageguard.sctimetable.database.model.User
import me.stageguard.sctimetable.database.model.Users
import me.stageguard.sctimetable.service.*
import org.jetbrains.exposed.sql.and

/**
 * InheritTimetableFromLastSemester: 从上一个学期继承时间表
 *
 * @param qq 用户
 */
class InheritTimetableFromLastSemester(val qq: Long) : IRequest {
    override suspend fun RequestHandlerService.handle() {
        Database.suspendQuery {
            val user = User.find { Users.qq eq qq }
            if(!user.empty()) {
                val lastSemester = if(TimeProviderService.currentSemester == 2) 1 else 2
                val lastSemesterBeginYear = if(TimeProviderService.currentSemester == 2) TimeProviderService.currentSemesterBeginYear else TimeProviderService.currentSemesterBeginYear - 1
                val lastSemesterSchoolTimetable = Database.suspendQuery {
                    SchoolTimetable.find {
                        (SchoolTimetables.schoolId eq user.first().schoolId) and
                                (SchoolTimetables.beginYear eq lastSemesterBeginYear) and
                                (SchoolTimetables.semester eq lastSemester)
                    }.firstOrNull()
                }
                if(lastSemesterSchoolTimetable != null) {
                    SchoolTimetable.new {
                        schoolId = lastSemesterSchoolTimetable.schoolId
                        schoolName = lastSemesterSchoolTimetable.schoolName
                        beginYear = TimeProviderService.currentSemesterBeginYear
                        semester = TimeProviderService.currentSemester
                        scheduledTimeList = lastSemesterSchoolTimetable.scheduledTimeList
                        timeStampWhenAdd = TimeProviderService.currentTimeStamp.toString()
                        weekPeriodWhenAdd = 1
                    }
                    BotEventRouteService.sendMessageNonBlock(qq, "已将 ${TimeProviderService.currentSemesterBeginYear} 学年第 ${TimeProviderService.currentSemester} 学期的课表设置为沿用上个学期/学年，输入\"查看时间表\"查看。\n正在自动为你同步当前学期课程...")
                    TimeProviderService.immediateUpdateSchoolWeekPeriod()
                    ScheduleListenerService.onChangeSchoolTimetable(user.first().schoolId)
                    User.find { Users.schoolId eq user.first().schoolId }.forEach {
                        if(it.qq != qq) {
                            BotEventRouteService.sendMessageNonBlock(it.qq,
                                "您所在的学校 ${TimeProviderService.currentSemesterBeginYear} 学年第 ${TimeProviderService.currentSemester} 学期的时间表已经被同校用户 $qq 设置为与上学期/学年相同。\n请输入\"查看时间表\"查看。\n若当前学期和上学期的时间表有出入，请发送\"修改时间表\"手动修改。\n正在自动为你同步当前学期课程...",
                                60000L)
                        }
                        sendRequest(SyncCourse(it.qq))
                    }
                } else BotEventRouteService.sendMessageNonBlock(qq, "未找到上个学期/学年的时间表，请尝试从服务器同步。")
            } else {
                error("Failed to inherit school timetable $qq's school, reason: User doesn't exist.")
            }
        }
    }

    override fun toString() = "InheritTimetableFromLastSemester(qq=$qq)"
}