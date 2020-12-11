package stageguard.sctimetable.service

import kotlinx.coroutines.*
import net.mamoe.mirai.utils.info
import org.jetbrains.exposed.sql.selectAll
import org.quartz.*
import org.quartz.Job
import org.quartz.impl.StdSchedulerFactory
import stageguard.sctimetable.AbstractPluginManagedService
import stageguard.sctimetable.PluginMain
import stageguard.sctimetable.database.Database
import stageguard.sctimetable.database.model.SchoolTimetable
import stageguard.sctimetable.database.model.SchoolTimetables
import java.time.LocalDate
import java.time.ZoneId
import java.util.*

/**
 * 考虑到bot可能会长期运行(指超过半年或者一个学期)，将时间相关的所有时间放在TimeProviderService中更新
 *
 * 包含以下定时更新属性：
 * - [currentYear] 当前年份，由 [YearUpdater] 在每年1月1日00:00更新
 * - [currentSemester] 当前学期，由 [SemesterUpdater] 在每年2月1日00:00和8月1日00:00长假时更新
 * - [currentWeek] 由 [SchoolWeekPeriodUpdater] 在每周一的00:00更新，不同学校同一时间的周数不同
 *
 * 以上时间均为 ```UTF+8``` 时间 ```ZoneId.of("Asia/Shanghai")```
 **/
object TimeProviderService : AbstractPluginManagedService(Dispatchers.IO) {
    /**
     * 当前年份
     **/
    var currentYear: Int = 0
    /**
     * 当前学期，```1```表示秋季学期，```2```表示夏季学期
     **/
    var currentSemester: Int = 0
    /**
     * 当前周数，```Map```中的```key```为学校id，```value```为当前周数。
     **/
    var currentWeek: MutableList<Pair<Int, Int>> = mutableListOf()

    val currentSemesterBeginYear: Int
        get() = if(currentSemester == 2) currentYear - 1 else currentYear

    private val scheduledQuartzJob: MutableList<Pair<JobDetail, Trigger>> = mutableListOf(
        JobBuilder.newJob(YearUpdater::class.java).apply {
            withIdentity(JobKey.jobKey("YearUpdaterJob"))
        }.build() to TriggerBuilder.newTrigger().apply {
            withIdentity(TriggerKey.triggerKey("YearUpdaterTrigger"))
            withSchedule(CronScheduleBuilder.cronSchedule("0 0 0 1 1 ? *"))
            startNow()
        }.build(),
        JobBuilder.newJob(SemesterUpdater::class.java).apply {
            withIdentity(JobKey.jobKey("SemesterUpdaterJob"))
        }.build() to TriggerBuilder.newTrigger().apply {
            withIdentity(TriggerKey.triggerKey("SemesterUpdaterTrigger"))
            withSchedule(CronScheduleBuilder.cronSchedule("0 0 0 15 2,8 ? *"))
            startNow()
        }.build(),
        JobBuilder.newJob(SchoolWeekPeriodUpdater::class.java).apply {
            withIdentity(JobKey.jobKey("SchoolWeekPeriodUpdaterJob"))
        }.build() to TriggerBuilder.newTrigger().apply {
            withIdentity(TriggerKey.triggerKey("SchoolWeekPeriodUpdaterTrigger"))
            withSchedule(CronScheduleBuilder.cronSchedule("0 0 0 ? * 2 *"))
            startNow()
        }.build()
    )
    override suspend fun main() {
        //Scheduled Job
        StdSchedulerFactory.getDefaultScheduler().apply {

        }.start()
        //Immediate start once
        StdSchedulerFactory.getDefaultScheduler().apply {
            scheduledQuartzJob.forEach { scheduleJob(it.first, TriggerBuilder.newTrigger().startNow().build()) }
        }.start()
        PluginMain.logger.info { "TimeProviderServices(${scheduledQuartzJob.joinToString(", ") { it.first.key.name }}) have started." }
        //unlimited job, kotlin still has no scheduler framework like quartz
        while (true) if(this@TimeProviderService.isActive) delay(100)
    }

    fun immediateUpdate(jobKey: String) {
        StdSchedulerFactory.getDefaultScheduler().apply {
            scheduleJob(
                JobBuilder.newJob(StdSchedulerFactory.getDefaultScheduler().getJobDetail(JobKey.jobKey(jobKey)).jobClass).build(),
                TriggerBuilder.newTrigger().startNow().build()
            )
        }.start()
    }
    class YearUpdater : Job {
        override fun execute(context: JobExecutionContext?) {
            currentYear = LocalDate.now(ZoneId.of("Asia/Shanghai")).year
            PluginMain.logger.info { "Job YearUpdater is executed. (currentYear -> $currentYear)" }
        }
    }
    class SemesterUpdater: Job {
        override fun execute(context: JobExecutionContext?) {
            currentSemester = if(LocalDate.now(ZoneId.of("Asia/Shanghai")).monthValue in 3..7) 2 else 1
            PluginMain.logger.info { "Job SemesterUpdater is executed. (currentSemester -> $currentSemester)" }
        }
    }
    class SchoolWeekPeriodUpdater: Job {
        override fun execute(context: JobExecutionContext?) {
            Database.query { SchoolTimetable.all() } ?.apply {
                PluginMain.logger.info { toString() }
            }
            PluginMain.logger.info { "Job SchoolWeekPeriodUpdater is executed." }
        }
    }
}