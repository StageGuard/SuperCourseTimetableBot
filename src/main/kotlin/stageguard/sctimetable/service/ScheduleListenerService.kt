package stageguard.sctimetable.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import net.mamoe.mirai.utils.info
import net.mamoe.mirai.utils.warning
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.quartz.*
import stageguard.sctimetable.AbstractPluginManagedService
import stageguard.sctimetable.PluginConfig
import stageguard.sctimetable.PluginData
import stageguard.sctimetable.PluginMain
import stageguard.sctimetable.database.Database
import stageguard.sctimetable.database.model.Courses
import stageguard.sctimetable.database.model.SchoolTimetable
import stageguard.sctimetable.database.model.SchoolTimetables
import stageguard.sctimetable.database.model.User
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 为每个用户设定定时任务
 */
object ScheduleListenerService : AbstractPluginManagedService(Dispatchers.IO) {

    private const val JOB_GROUP = "ScheduleListenerServiceGroup"

    /**
     * 课程提醒Job，```Key```为QQ号，```Value```为[JobDetail]
     */
    private val userNotificationJobs: MutableMap<Long, JobDetail> = mutableMapOf()
    /**
     * 用户今天的课程，按照时间顺序存储到[SingleCourse]中，通过[getUserTodayCourses]获取
     */
    private val userCourses: MutableMap<Long, List<SingleCourse>> = mutableMapOf()
    /**
     * 学校时间表，第一次使用时从数据库通过[getSchoolTimetable]函数解析到这里。
     *
     * ```Map```中的```Key```是学校ID，```Pair``` 中的 ```first``` 是这节课的开始时间，```second``` 是结束时间。
     *
     * 时间是按照分钟算的，比如 ```00:30``` 转换为 ```30```，```08:30``` 转换为 ```510 (8x60+30)```。
     */
    private val cachedSchoolTimetables: MutableMap<Int, List<Pair<Int, Int>>> = mutableMapOf()
    /**
     * 今天日期
     */
    private lateinit var dateOfToday: LocalDate
    /**
     * 现在时间
     */
    private val nowTime
        get() = LocalDateTime.now(ZoneId.of("Asia/Shanghai"))

    fun getSchoolTimetable(schoolId: Int): List<Pair<Int, Int>> = cachedSchoolTimetables[schoolId] ?: run {
        (Database.query {
            val table = SchoolTimetable.find { SchoolTimetables.schoolId eq schoolId }
            if (!table.empty()) {
                table.first().scheduledTimeList.split("|").map { period ->
                    period.split("-").map { stamp ->
                        stamp.split(":").let { it[0].toInt() * 60 + it[1].toInt() }
                    }.let { it[0] to it[1] }
                }.also { cachedSchoolTimetables[schoolId] = it }
            } else listOf()
        } ?: listOf())
    }

    fun updateSchoolTimetable(schoolId: Int) = cachedSchoolTimetables.run {
        if(this.containsKey(schoolId)) {
            this.remove(schoolId)
            this[schoolId] = getSchoolTimetable(schoolId)
        }
    }

    fun stopAndRemoveUserNotificationJob(qq: Long) = userNotificationJobs.run {
        if(this.containsKey(qq)) {
            PluginMain.quartzScheduler.interrupt(this[qq]?.key)
            userNotificationJobs.remove(qq)
        }
    }

    fun getUserTodayCourses(qq: Long, belongingSchool: Int): List<SingleCourse> = userCourses.run {
        if(this.containsKey(qq)) this[qq]!! else {
            if(TimeProviderService.currentWeekPeriod[belongingSchool] != null) {
                (Database.query {
                    val courses = Courses(qq)
                    val coursesList = mutableListOf<SingleCourse>()
                    courses.select {
                        (courses.beginYear eq TimeProviderService.currentSemesterBeginYear) and (courses.semester eq TimeProviderService.currentSemester) and (courses.whichDayOfWeek eq dateOfToday.dayOfWeek.value)
                    }.forEach {
                        it[courses.weekPeriod].split(" ").forEach { week ->
                            if(week.toInt() == TimeProviderService.currentWeekPeriod[belongingSchool]) {
                                coursesList.add(SingleCourse(
                                    it[courses.sectionStart],
                                    it[courses.sectionEnd],
                                    it[courses.courseName],
                                    it[courses.locale],
                                    it[courses.teacherName]
                                ))
                            }
                        }
                    }
                    coursesList.toList()
                } ?: listOf()).also { this[qq] = it }
            } else {
                PluginMain.logger.warning { "Cannot get user $qq's today courses because school doesn't exist in TimeProviderService" }
                listOf<SingleCourse>().also { this[qq] = it }
            }
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
        if(!this.containsKey(qq)) {
            val schoolTimetable = getSchoolTimetable(belongingSchool)
            val todayCourses = getUserTodayCourses(qq, belongingSchool)
            //空则表示今天没课或者获取错误
            if(todayCourses.isNotEmpty()) {
                val tipOffset = PluginData.advancedTipOffset[qq] ?: PluginConfig.advancedTipTime
                val explicitSection = whichSection ?: todayCourses.let { courses ->
                    val nowTimeAsMinute = nowTime.hour * 60 + nowTime.minute
                    when {
                        //现在时间在今天第一节课开始之前
                        nowTimeAsMinute <= schoolTimetable[courses.first().startSection - 1].first - tipOffset -> courses.first().startSection
                        //现在时间在今天最后一节课开时候
                        nowTimeAsMinute >= schoolTimetable[courses[courses.lastIndex].startSection - 1].first -> return@run
                        else -> courses.asReversed().first { nowTimeAsMinute <= schoolTimetable[it.startSection - 1].first - tipOffset }.startSection
                    }
                }
                //判断一下第section节课是不是超过了学校时间表里最后一个课程的时间
                if(explicitSection <= schoolTimetable.count()) {
                    val theComingCourseList = todayCourses.filter { it.startSection == explicitSection }
                    //判断一下今天第section节课有没有课
                    if(theComingCourseList.isNotEmpty()) {
                        val theComingCourse = theComingCourseList.first()
                        this[qq] = JobBuilder.newJob(UserNotificationJob::class.java).apply {
                            withIdentity(JobKey.jobKey("ClassNotificationJob_${qq}_$explicitSection", JOB_GROUP))
                            usingJobData("qq", qq)
                            usingJobData("belongingSchool", belongingSchool)
                            //如果为 -1 则表示今天课程已结束，今天没有下一节课了
                            usingJobData("theNextClassSectionStart", if(todayCourses.indexOf(theComingCourse) == todayCourses.size - 1) -1 else todayCourses[todayCourses.indexOf(theComingCourse) + 1].startSection)
                            usingJobData("theComingCourseName", theComingCourse.courseName)
                            usingJobData("theComingCourseTeacherName", theComingCourse.teacherName)
                            usingJobData("theComingCourseLocale", theComingCourse.locale)
                            usingJobData("theComingCourseStartTime", schoolTimetable[theComingCourse.startSection - 1].first)
                            usingJobData("theComingCourseEndTime", schoolTimetable[theComingCourse.endSection - 1].second)
                        }.build()
                        val cronString = "0 ${
                            (schoolTimetable[theComingCourse.startSection - 1].first - tipOffset).let { "${it % 60} ${(it - (it % 60)) / 60}" }
                        } ${ dateOfToday.let { "${it.dayOfMonth} ${it.month.value} ? ${it.year}" } }"
                        PluginMain.logger.info { "Cron for user $qq is $cronString" }
                        PluginMain.quartzScheduler.scheduleJob(this[qq], TriggerBuilder.newTrigger().withSchedule(CronScheduleBuilder.cronSchedule(cronString)).build())
                    }
                } else PluginMain.logger.warning { "Cannot start a class notification job for user $qq because school $belongingSchool is not exist in database or your lesson time is overpassed the last schedule." }
            }
        } else PluginMain.logger.warning { "A notification job has started for user $qq and it is not allowed to start another one." }
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
        PluginMain.logger.info { "ScheduleListenerService is started." }
    }

    /**
     * 用户课程提醒分发Job，在每天的 00:00:30 执行
     *
     * 若前一天的提醒Job还未执行完成，则打断并删除Job
     */
    class UserNotificationDistributionJob : Job {
        override fun execute(context: JobExecutionContext?) {
            //更新今天日期
            dateOfToday = TimeProviderService.currentTimeStamp
            Database.query {
                val users = User.all()
                for(user in users) {
                    stopAndRemoveUserNotificationJob(user.qq)
                    startUserNotificationJob(user.qq, user.schoolId)
                }
            }
        }
    }

    class UserNotificationJob: Job {
        override fun execute(context: JobExecutionContext?) {
            context?.jobDetail?.jobDataMap?.run {
                context.jobDetail.jobDataMap.also {
                    val qq = it.getLong("qq")
                    val courseName = it.getString("theComingCourseName")
                    val teacherName = it.getString("theComingCourseTeacherName")
                    val locale = it.getString("theComingCourseLocale")
                    val nextSection = it.getInt("theNextClassSectionStart")

                    //稳定性测试
                    launch {
                        PluginMain.botInstance?.friends?.filter { friend -> friend.id == qq }?.first()?.sendMessage("""
                            下节课是${if(nextSection == -1) "今天的最后一节课" else ""} $courseName
                            讲师：$teacherName
                            时间：${it.getInt("theComingCourseStartTime").let { stamp -> "${(stamp - (stamp % 60)) / 60}:${stamp % 60}" }} -> ${it.getInt("theComingCourseEndTime").let { stamp -> "${(stamp - (stamp % 60)) / 60}:${stamp % 60}" }}
                            地点：$locale
                        """.trimIndent())
                    }

                    if(nextSection != -1) startUserNotificationJob(qq, it.getInt("belongingSchool"), nextSection)
                }
            }
        }
    }
}

data class SingleCourse(
    val startSection: Int,
    val endSection: Int,
    val courseName: String,
    val teacherName: String,
    val locale: String
)