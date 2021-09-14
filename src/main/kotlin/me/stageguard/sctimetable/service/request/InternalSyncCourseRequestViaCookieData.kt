package me.stageguard.sctimetable.service.request

import me.stageguard.sctimetable.PluginConfig
import me.stageguard.sctimetable.PluginData
import me.stageguard.sctimetable.api.edu_system.`super`.LoginCookieData
import me.stageguard.sctimetable.api.edu_system.`super`.SuperCourseApiService
import me.stageguard.sctimetable.database.Database
import me.stageguard.sctimetable.database.model.Courses
import me.stageguard.sctimetable.database.model.User
import me.stageguard.sctimetable.database.model.Users
import me.stageguard.sctimetable.service.*
import me.stageguard.sctimetable.utils.Either.Companion.onLeft
import me.stageguard.sctimetable.utils.Either.Companion.onRight
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert

/**
 * InternalSyncCourseRequestViaCookieData：当数据库中已存在这个用户信息和课表信息，请求更新用户的课表信息
 *
 * 这是通过cookie更新
 * @param qq 要更新课表信息的用户的QQ号
 **/
class InternalSyncCourseRequestViaCookieData(val qq: Long, val cookieData: LoginCookieData) : IRequest {
    override suspend fun RequestHandlerService.handle() {
        val courseTable = Courses(qq)
        Database.suspendQuery {
            SchemaUtils.create(courseTable)
            courseTable.deleteWhere { (courseTable.beginYear eq TimeProviderService.currentSemesterBeginYear) and (courseTable.semester eq TimeProviderService.currentSemester) }
            val user = User.find { Users.qq eq qq }
            if(!user.empty()) {
                SuperCourseApiService.getCourses(cookieData).also { coursesDTO ->
                    coursesDTO.onRight { r ->
                        r.data.lessonList.run {
                            if(isNotEmpty()) {
                                Database.query { forEach { e -> courseTable.insert { crs ->
                                    crs[courseId] = e.courseId
                                    crs[courseName] = e.name
                                    crs[teacherName] = e.teacher
                                    crs[locale] = e.locale
                                    crs[whichDayOfWeek] = e.day
                                    crs[sectionStart] = e.sectionstart
                                    crs[sectionEnd] = e.sectionend
                                    crs[weekPeriod] = e.smartPeriod
                                    crs[beginYear] = TimeProviderService.currentSemesterBeginYear
                                    crs[semester] = TimeProviderService.currentSemester
                                } } }
                                info("Sync user $qq's courses successfully.")
                                ScheduleListenerService.restartUserNotification(qq)
                                BotEventRouteService.sendMessageNonBlock(qq,"""
                                            课程同步成功！
                                            你将在每节课上课前 ${PluginData.advancedTipOffset[qq] ?: PluginConfig.advancedTipTime} 分钟收到课程提醒。
                                            可以发送"今日课程"查看今天的课程，发送"查看时间表"查看本校的时间表，
                                            或者发送"修改提前提醒时间"来修改课程提醒时间。
                                        """.trimIndent())
                            } else {
                                warning("Failed to sync user ${qq}'s courses, reason: get empty list from server.")
                                BotEventRouteService.sendMessageNonBlock(qq,"""
                                            无法同步课程，从服务器中获取了空的课程列表。
                                            请确保 ${TimeProviderService.currentSemesterBeginYear} 学年第 ${TimeProviderService.currentSemester} 学期的课表已添加进超级课表。
                                            查看超级课表 app，若无课表，请先在超级课表 app 中同步教务系统的课表。。
                                        """.trimIndent())
                            }
                        }
                    }.onLeft { l ->
                        error("Failed to sync user $qq's courses, reason: ${l.message}.")
                        BotEventRouteService.sendMessageNonBlock(qq, "无法同步课程。原因：${l.message}")
                    }
                }
            } else {
                error("Failed to sync user $qq's courses, reason: User doesn't exist.")
            }
        }
    }

    override fun toString() = "InternalSyncCourseRequestViaCookieData(qq=$qq,cookie=$cookieData)"
}