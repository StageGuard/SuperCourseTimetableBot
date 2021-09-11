/*
 * Copyright 2020-2021 KonnyakuCamp.
 *
 *  此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 *  Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 *  https://github.com/KonnyakuCamp/SuperCourseTimetableBot/blob/main/LICENSE
 */
package stageguard.sctimetable.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.quartz.*
import stageguard.sctimetable.AbstractPluginManagedService
import stageguard.sctimetable.PluginConfig
import stageguard.sctimetable.PluginData
import stageguard.sctimetable.PluginMain
import stageguard.sctimetable.database.Database
import stageguard.sctimetable.database.model.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.floor

/**
 * 为每个用户设定定时任务
 */
object ScheduleListenerService : AbstractPluginManagedService(Dispatchers.IO) {

    override val TAG: String = "ScheduleListenerService"

    private const val JOB_GROUP = "ScheduleListenerServiceGroup"

    /**
     * 课程提醒Job，```Key```为QQ号，```Value```为[JobDetail]
     */
    private val userNotificationJobs: MutableMap<Long, JobDetail> = mutableMapOf()

    /**
     * 用户今天的课程，按照时间顺序存储到[SingleCourse]中，通过[getUserTodayCourses]获取
     */
    private val userCourses: MutableMap<Long, MutableList<SingleCourse>> = mutableMapOf()

    /**
     * 学校时间表，第一次使用时从数据库通过[getSchoolTimetable]函数解析到这里。
     *
     * 时间是按照分钟算的，比如 ```00:30``` 转换为 ```30```，```08:30``` 转换为 ```510 (8x60+30)```。
     */
    private val cachedSchoolTimetables: MutableList<PeriodSchoolTimetable> = mutableListOf()

    /**
     * 现在时间
     */
    private val nowTime
        get() = LocalDateTime.now(ZoneId.of("Asia/Shanghai"))

    fun getSchoolTimetable(schoolId: Int): List<Pair<Int, Int>> = cachedSchoolTimetables.filter {
        it.schoolId == schoolId && it.semester == TimeProviderService.currentSemester && it.beginYear == TimeProviderService.currentSemesterBeginYear
    }.run {
        if (!isEmpty()) single().scheduledTimetable else Database.query {
            val table = SchoolTimetable.find {
                (SchoolTimetables.schoolId eq schoolId) and
                        (SchoolTimetables.semester eq TimeProviderService.currentSemester) and
                        (SchoolTimetables.beginYear eq TimeProviderService.currentSemesterBeginYear)
            }.firstOrNull()
            if (table != null) {
                table.scheduledTimeList.split("|").map { period ->
                    period.split("-").map { stamp ->
                        stamp.split(":").let { it[0].toInt() * 60 + it[1].toInt() }
                    }.let { it[0] to it[1] }
                }.also {
                    cachedSchoolTimetables.add(
                        PeriodSchoolTimetable(
                            schoolId,
                            TimeProviderService.currentSemester,
                            TimeProviderService.currentSemesterBeginYear,
                            it
                        )
                    )
                }
            } else {
                warning("Timetable in ${TimeProviderService.currentSemesterBeginYear}:${TimeProviderService.currentSemester} of school $schoolId is not found!")
                listOf()
            }
        } ?: listOf()
    }.also {
        verbose("getSchoolTimetable(schoolId=$schoolId)")
    }

    fun removeSchoolTimetable(schoolId: Int) {
        cachedSchoolTimetables.removeIf { it.schoolId == schoolId }
        verbose("removeSchoolTimetable(schoolId=$schoolId)")
    }

    fun getUserTodayCourses(qq: Long, belongingSchool: Int, inputDayOfWeek: Int): List<SingleCourse> {
        fun getCourseFromDatabase() = Database.query {
            val courses = Courses(qq)
            val coursesList = mutableListOf<SingleCourse>()
            val isNextWeek = if (inputDayOfWeek > 7) 1 else 0
            courses.select {
                (courses.beginYear eq TimeProviderService.currentSemesterBeginYear) and
                        (courses.semester eq TimeProviderService.currentSemester) and
                        (courses.whichDayOfWeek eq if (isNextWeek == 1) 1 else inputDayOfWeek)
            }.forEach {
                it[courses.weekPeriod].split(" ").forEach { week ->
                    if (week.toInt() == TimeProviderService.currentWeekPeriod[belongingSchool]!! + isNextWeek) {
                        coursesList.add(
                            SingleCourse(
                                it[courses.sectionStart],
                                it[courses.sectionEnd],
                                it[courses.courseName],
                                it[courses.teacherName],
                                it[courses.locale],
                                TimeProviderService.currentSemester,
                                TimeProviderService.currentSemesterBeginYear
                            )
                        )
                    }
                }
            }
            coursesList.sortedBy { it.startSection }
        } ?: listOf()
        return userCourses.run {
            if (this.containsKey(qq)) this[qq]!!.run {
                filter {
                    it.semester == TimeProviderService.currentSemester && it.beginYear == TimeProviderService.currentSemesterBeginYear
                }.run {
                    if (isEmpty()) getCourseFromDatabase().also { forEach { userCourses[qq]!!.add(it) } } else this
                }
            } else getCourseFromDatabase().also { this[qq] = it.toMutableList() }
        }
    }

    fun removeUserTodayCourses(qq: Long) {
        if (userCourses.containsKey(qq)) {
            userCourses.remove(qq)
            verbose("removeUserTodayCourses(qq=$qq)")
        }
    }

    /**
     * 开始一个课程提醒 Job
     *
     * 当[whichSection] 为 ```null``` 时，表示从 Plugin 启动时间开始的下一节课
     *
     * 如果用户的 下一节课 - 提前通知时间 小于当前时间，那就推到下下节课
     */
    fun startUserNotificationJob(qq: Long, belongingSchool: Int, whichSection: Int? = null) = userNotificationJobs.run {
        if (!this.containsKey(qq)) {
            val schoolTimetable = getSchoolTimetable(belongingSchool)
            val todayCourses =
                getUserTodayCourses(qq, belongingSchool, TimeProviderService.currentTimeStamp.dayOfWeek.value)
            //空则表示今天没课或者获取错误
            if (todayCourses.isNotEmpty()) {
                val tipOffset = PluginData.advancedTipOffset[qq] ?: PluginConfig.advancedTipTime
                val nowTimeAsMinute = nowTime.hour * 60 + nowTime.minute
                val explicitSection = whichSection ?: todayCourses.let { courses ->
                    when {
                        nowTimeAsMinute >= schoolTimetable[courses[courses.lastIndex].startSection - 1].first -> return@run
                        else -> courses.first { nowTimeAsMinute <= schoolTimetable[it.startSection - 1].first }.startSection
                    }
                }
                //判断一下第section节课是不是超过了学校时间表里最后一个课程的时间ss
                if (explicitSection <= schoolTimetable.count()) {
                    val theComingCourseList = todayCourses.filter { it.startSection == explicitSection }
                    //判断一下今天第section节课有没有课
                    if (theComingCourseList.isNotEmpty()) {
                        val theComingCourse = theComingCourseList.first()
                        this[qq] = JobBuilder.newJob(UserNotificationJob::class.java).apply {
                            withIdentity(JobKey.jobKey("ClassNotificationJob_${qq}_$explicitSection", JOB_GROUP))
                            usingJobData("qq", qq)
                            usingJobData("belongingSchool", belongingSchool)
                            //如果为 -1 则表示今天课程已结束，今天没有下一节课了
                            usingJobData(
                                "theNextClassSectionStart",
                                if (todayCourses.indexOf(theComingCourse) == todayCourses.lastIndex) -1 else todayCourses[todayCourses.indexOf(
                                    theComingCourse
                                ) + 1].startSection
                            )
                            usingJobData("theComingCourseName", theComingCourse.courseName)
                            usingJobData("theComingCourseTeacherName", theComingCourse.teacherName)
                            usingJobData("theComingCourseLocale", theComingCourse.locale)
                            usingJobData(
                                "theComingCourseStartTime",
                                schoolTimetable[theComingCourse.startSection - 1].first
                            )
                            usingJobData(
                                "theComingCourseEndTime",
                                schoolTimetable[theComingCourse.endSection - 1].second
                            )
                        }.build()
                        PluginMain.quartzScheduler.scheduleJob(this[qq],
                            TriggerBuilder.newTrigger()
                                .withSchedule(if (schoolTimetable[theComingCourse.startSection - 1].first - tipOffset < nowTimeAsMinute) {
                                    verbose("schedule notification job for $qq: immediate.")
                                    SimpleScheduleBuilder.simpleSchedule()
                                } else {
                                    CronScheduleBuilder.cronSchedule("${floor(Math.random() * 60).toInt()} ${
                                        (schoolTimetable[theComingCourse.startSection - 1].first - tipOffset).let { "${it % 60} ${(it - (it % 60)) / 60}" }
                                    } ${TimeProviderService.currentTimeStamp.let { "${it.dayOfMonth} ${it.month.value} ? ${it.year}" }}".also {
                                        verbose("schedule notification job for $qq: cron $it.")
                                    })
                                }
                                ).build()
                        )
                    }
                } else warning("Cannot start a class notification job for user $qq because school $belongingSchool is not exist in database or your lesson time is overpassed the last schedule.")
            }
        } else warning("A notification job has started for user $qq and it is not allowed to start another one.")
    }

    fun stopAndRemoveUserNotificationJob(qq: Long) = userNotificationJobs.run {
        if (this.containsKey(qq)) {
            PluginMain.quartzScheduler.interrupt(this[qq]?.key)
            PluginMain.quartzScheduler.deleteJob(this[qq]?.key)
            this.remove(qq)
            verbose("Stopped notification job for user $qq")
        }
    }

    /**
     * 在学校当前周数更新时调用
     *
     * @see Request.SyncSchoolWeekPeriodRequest
     */
    fun onChangeSchoolWeekPeriod(schoolId: Int) = launch(PluginMain.coroutineContext) {
        info("onChangeSchoolWeekPeriod(schoolId=$schoolId)")
        Database.suspendQuery {
            User.find { Users.schoolId eq schoolId }.forEach {
                stopAndRemoveUserNotificationJob(it.qq)
                removeUserTodayCourses(it.qq)
                startUserNotificationJob(it.qq, schoolId, whichSection = null)
            }
        }
    }

    /**
     * 在学校更新时间表时调用
     *
     * @see Request.SyncSchoolTimetableRequest
     */

    fun onChangeSchoolTimetable(schoolId: Int) = launch(PluginMain.coroutineContext) {
        info("onChangeSchoolTimetable(schoolId=$schoolId)")
        removeSchoolTimetable(schoolId)
        Database.suspendQuery {
            User.find { Users.schoolId eq schoolId }.forEach {
                stopAndRemoveUserNotificationJob(it.qq)
                removeUserTodayCourses(it.qq)
                startUserNotificationJob(it.qq, schoolId, whichSection = null)
            }
        }
    }

    /**
     * 在用户修改了提前提醒时间时调用.
     */
    fun restartUserNotification(qq: Long) = Database.query {
        info("ScheduleListenerService.restartUserNotification(qq=$qq)")
        val user = User.find { Users.qq eq qq }
        if (!user.empty()) {
            stopAndRemoveUserNotificationJob(user.first().qq)
            startUserNotificationJob(user.first().qq, user.first().schoolId, whichSection = null)
        } else error("User $qq doesn't exist, cannot restart notification job.")
    }

    override suspend fun main() {
        //scheduled job
        PluginMain.quartzScheduler.scheduleJob(
            JobBuilder.newJob(UserNotificationDistributionJob::class.java).apply {
                withIdentity(JobKey.jobKey("UserNotificationDistributionJob", JOB_GROUP))
            }.build(),
            TriggerBuilder.newTrigger().apply {
                withIdentity(TriggerKey.triggerKey("UserNotificationDistributionTrigger", JOB_GROUP))
                withSchedule(CronScheduleBuilder.cronSchedule("30 0 0 * * ? *"))
            }.build()
        )
        //immediate start once
        PluginMain.quartzScheduler.scheduleJob(
            JobBuilder.newJob(UserNotificationDistributionJob::class.java).build(),
            TriggerBuilder.newTrigger().startNow().build()
        )
        info("ScheduleListenerService is started.")
    }

    /**
     * 用户课程提醒分发Job，在每天的 00:00:30 执行
     *
     * 若前一天的提醒Job还未执行完成，则打断并删除Job
     */
    class UserNotificationDistributionJob : Job {
        override fun execute(context: JobExecutionContext?) {
            Database.query {
                val users = User.all()
                for (user in users) {
                    stopAndRemoveUserNotificationJob(user.qq)
                    removeUserTodayCourses(user.qq)
                    startUserNotificationJob(user.qq, user.schoolId, whichSection = null)
                }
            }
            info("Notification distribution job has executed.")
        }
    }

    class UserNotificationJob : InterruptableJob {
        override fun execute(context: JobExecutionContext?) {
            context?.jobDetail?.jobDataMap?.run {
                context.jobDetail.jobDataMap.also {
                    val qq = it.getLong("qq")
                    val courseName = it.getString("theComingCourseName")
                    val teacherName = it.getString("theComingCourseTeacherName")
                    val locale = it.getString("theComingCourseLocale")
                    val nextSection = it.getInt("theNextClassSectionStart")
                    BotEventRouteService.sendMessageNonBlock(qq, """
                        下节课是${if (nextSection == -1) "今天的最后一节课" else ""} $courseName
                        讲师：$teacherName
                        时间：${
                        it.getInt("theComingCourseStartTime").let { stamp ->
                            "${(stamp - (stamp % 60)) / 60} : ${(stamp % 60).let { min -> if (min < 10) ("0$min") else min }}"
                        }
                    } -> ${
                        it.getInt("theComingCourseEndTime").let { stamp ->
                            "${(stamp - (stamp % 60)) / 60} : ${(stamp % 60).let { min -> if (min < 10) ("0$min") else min }}"
                        }
                    }
                        地点：$locale
                        还有 ${it.getInt("theComingCourseStartTime") - (nowTime.hour * 60 + nowTime.minute)} 分钟上课。
                    """.trimIndent())
                    stopAndRemoveUserNotificationJob(qq)
                    if (nextSection != -1) startUserNotificationJob(qq, it.getInt("belongingSchool"), nextSection)
                    info("Notification job executed for user $qq")
                }
            }
        }

        override fun interrupt() {}
    }
}

data class SingleCourse(
    val startSection: Int,
    val endSection: Int,
    val courseName: String,
    val teacherName: String,
    val locale: String,
    val semester: Int,
    val beginYear: Int
)

data class PeriodSchoolTimetable(
    val schoolId: Int,
    val semester: Int,
    val beginYear: Int,
    val scheduledTimetable: List<Pair<Int, Int>>
)