# SuperCourseTimetableBot 代码结构解析

这篇文档将简单为你分析一下 SuperCourseTimetableBot 的**结构**和**工作机制**。

SuperCourseTimetableBot 能够实现课前提醒的核心是定时任务 [Quartz](https://github.com/quartz-scheduler/quartz)，它的 [`CronTrigger`](https://github.com/quartz-scheduler/quartz/blob/master/docs/tutorials/crontrigger.md) 可以通过一个 cron 字符串实现灵活地触发定时任务。

SuperCourseTimetableBot 将不同工作放在不同的 [service](https://github.com/KonnyakuCamp/SuperCourseTimetableBot/tree/main/src/main/kotlin/stageguard/sctimetable/service) 里进行：

- [RequestHandlerService](https://github.com/KonnyakuCamp/SuperCourseTimetableBot/blob/main/src/main/kotlin/stageguard/sctimetable/service/RequestHandlerService.kt)： 处理用户的各种请求，如**登录**，**同步课程表**和**同步时间表**等。
- [ScheduleListenerService](https://github.com/KonnyakuCamp/SuperCourseTimetableBot/blob/main/src/main/kotlin/stageguard/sctimetable/service/ScheduleListenerService.kt)：为每个用户分发课程提醒定时任务，是插件的核心工作。
- [TimeProviderService](https://github.com/KonnyakuCamp/SuperCourseTimetableBot/blob/main/src/main/kotlin/stageguard/sctimetable/service/TimeProviderService.kt)：为每个学校计算**当前周数**，**当前学期**和**当前学期开始的年份**。
- [BotEventRouteService](https://github.com/KonnyakuCamp/SuperCourseTimetableBot/blob/main/src/main/kotlin/stageguard/sctimetable/service/BotEventRouteService.kt)：**捕获用户发送的消息**和**新的好友请求**。

看看插件的接入点[`PluginMain.kt`](https://github.com/KonnyakuCamp/SuperCourseTimetableBot/blob/main/src/main/kotlin/stageguard/sctimetable/PluginMain.kt)：

```kotlin
object PluginMain : KotlinPlugin(/*...*/) {
    /*...*/
    override fun onEnable() {
        PluginConfig.reload()
        PluginData.reload()
        logger.info { "Plugin loaded" }
        Database.connect()
        logger.info { "Waiting target Bot ${PluginConfig.qq} goes online/*...*/" }

        subscribe<BotOnlineEvent> {
            if(this.bot.id == PluginConfig.qq) {
                /*...*/
                TimeProviderService.start()
                ScheduleListenerService.start()
                RequestHandlerService.start()
                BotEventRouteService.start()
                ListeningStatus.STOPPED
            } else ListeningStatus.LISTENING
        }
    }
    /*...*/
}
```

它首先会连接在配置文件设置好的数据库，然后监听 `BotOnlineEvent` 即**机器人上线事件**，在收到指定的机器人上线后则立即开启上面的 4 个 services。

**所以插件的工作完全是围绕着这 4 个 service 进行的。**

## Services

- ### BotEventRouteService

```kotlin
override suspend fun main() {
    subscribe<NewFriendRequestEvent> { if(this.bot.id == PluginConfig.qq) {
        this.accept()
        this@BotEventRouteService.launch(coroutineContext) {
            delay(5000L)
            PluginMain.targetBotInstance.friends[this@subscribe.fromId]?.sendMessage("欢迎/*...*/")
        }
    }ListeningStatus.LISTENING }
    subscribeAlways<FriendMessageEvent> { if(this.bot.id == PluginConfig.qq) {
        val plainText = message.firstIsInstanceOrNull<PlainText>()?.content ?: ""
        when {
            plainText.matches(Regex("^登录超级(课程表|课表)")) -> {/*...*/}
            plainText.matches(Regex("^修改时间表")) -> launch(this@BotEventRouteService.coroutineContext) {/*...*/}
            plainText.matches(Regex("^查看时间表")) -> launch(this@BotEventRouteService.coroutineContext) {/*...*/}
            plainText.matches(Regex("^今[日天]课[表程]")) -> launch(this@BotEventRouteService.coroutineContext) {/*...*/}
            plainText.startsWith("删除用户") -> {/*...*/}
            plainText.startsWith("修改密码") -> launch(this@BotEventRouteService.coroutineContext) {/*...*/}
            plainText.startsWith("修改提前提醒时间") -> {/*...*/}
            (plainText.startsWith("怎么用") || plainText.startsWith("帮助")) -> {/*...*/}
            plainText.startsWith("状态") -> launch(this@BotEventRouteService.coroutineContext) {/*...*/}
        }
    } ListeningStatus.LISTENING }
    verbose("start listening FriendMessageEvent and NewFriendRequestEvent")
}
```

 订阅 `NewFriendRequestEvent` 时收到请求后同意好友并在 5 秒后发送帮助提示，订阅 `FriendMessageEvent` 通过判断消息执行不同的操作。

这里用到了**交互式对话创建器**，它可以方便地为用户创建交互式对话：

```kotlin
interactiveConversation {
    /*...*/
}.finish {
    /*...*/
}
```

它的实现就在 `main`的下面。

> 更多实现细节还请浏览[完整代码](https://github.com/KonnyakuCamp/SuperCourseTimetableBot/blob/main/src/main/kotlin/stageguard/sctimetable/service/BotEventRouteService.kt)。

- ### RequestHandlerService

```kotlin
object RequestHandlerService : AbstractPluginManagedService(Dispatchers.IO) {
    private val handlerChannel = Channel<Request>(100) { warning("Request is not handled. Request = $it") }

    override suspend fun main() { for(request in handlerChannel) { if(this@RequestHandlerService.isActive) {
        info("Handle Request: $request")
        when (request) {
            is Request.LoginRequest -> {/*...*/}
            is Request.SyncCourseRequest -> {/*...*/}
            is Request.InternalSyncCourseRequestViaCookieDataRequest -> {/*...*/}
            is Request.DeleteCourseRequest -> {/*...*/}
            is Request.SyncSchoolTimetableRequest -> {/*...*/}
            is Request.SyncSchoolWeekPeriodRequest -> {/*...*/}
            is Request.ChangeUserPasswordRequest -> {/*...*/}
        }
    } } }
    
    fun sendRequest(request: Request) { launch(coroutineContext) { handlerChannel.send(request) } }
}
sealed class Request {
    class LoginRequest(val qq: Long, val loginInfoData: LoginInfoData) : Request() {/*...*/}
    class SyncCourseRequest(val qq: Long) : Request() {/*...*/}
    class InternalSyncCourseRequestViaCookieDataRequest(val qq: Long, val cookieData: LoginCookieData) : Request() {/*...*/}
    class DeleteCourseRequest(val qq: Long) : Request() {/*...*/}
    class SyncSchoolTimetableRequest(val qq: Long, val newTimetable: List<Pair<String, String>>? = null, val forceUpdate: Boolean = false) : Request() {/*...*/}
    class SyncSchoolWeekPeriodRequest(val qq: Long, val currentWeek: Int) : Request() {/*...*/}
    class ChangeUserPasswordRequest(val qq: Long, val password: String) : Request() {/*...*/}
}
```

`RequestHandlerService` 的实现也很简单，通过 `launch` 一个新的协程来捕获 `handlerChannel` 的数据，而

```kotlin
for(request in handlerChannel) {
    
}
```

是永远也不会结束的。

`RequestHandlerService` 定义了一个 `sendRequest(request: Request)` 方法来发送一个事件非阻塞地发送到 `handlerChannel`。

```kotlin
RequestHandlerService.sendRequest(Request.XXX(arguments/*...*/))
```

所有事件都被定义在了 `sealed class Request` 下，每一个事件都有详细的解释。

> 更多细节请浏览[完整代码](https://github.com/KonnyakuCamp/SuperCourseTimetableBot/blob/main/src/main/kotlin/stageguard/sctimetable/service/RequestHandlerService.kt)。

- ### ScheduleListenerService

```kotlin
object ScheduleListenerService : AbstractPluginManagedService(Dispatchers.IO) {
      /*...*/
      private val userNotificationJobs: MutableMap<Long, JobDetail> = mutableMapOf()
      private val userCourses: MutableMap<Long, List<SingleCourse>> = mutableMapOf()
      private val cachedSchoolTimetables: MutableMap<Int, List<Pair<Int, Int>>> = mutableMapOf()
      private lateinit var dateOfToday: LocalDate
      private val nowTime
          get() = LocalDateTime.now(ZoneId.of("Asia/Shanghai"))
  
      fun getSchoolTimetable(schoolId: Int): List<Pair<Int, Int>> = cachedSchoolTimetables[schoolId] ?: run {/*...*/}
  
      fun removeSchoolTimetable(schoolId: Int) {/*...*/}
  
      fun getUserTodayCourses(qq: Long, belongingSchool: Int): List<SingleCourse> = userCourses.run {/*...*/}
  
      fun removeUserTodayCourses(qq: Long) {/*...*/}
  
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
                  val nowTimeAsMinute = nowTime.hour * 60 + nowTime.minute
                  val explicitSection = whichSection ?: todayCourses.let { courses ->
                      when {
                          nowTimeAsMinute >= schoolTimetable[courses[courses.lastIndex].startSection - 1].first -> return@run
                          else -> courses.first { nowTimeAsMinute <= schoolTimetable[it.startSection - 1].first }.startSection
                      }
                  }
                  //判断一下第section节课是不是超过了学校时间表里最后一个课程的时间ss
                  if(explicitSection <= schoolTimetable.count()) {
                      val theComingCourseList = todayCourses.filter { it.startSection == explicitSection }
                      //判断一下今天第section节课有没有课
                      if(theComingCourseList.isNotEmpty()) {
                          val theComingCourse = theComingCourseList.first()
                          //this: userNotificationJobs
                          this[qq] = JobBuilder.newJob(UserNotificationJob::class.java).apply {
                              //usingJobData: qq, belongingSchool, theComingCourseName/TeacherName/Locale/StartTime/EndTime
                              //如果为 -1 则表示今天课程已结束，今天没有下一节课了
                              usingJobData("theNextClassSectionStart", if(todayCourses.indexOf(theComingCourse) == todayCourses.lastIndex) -1 else todayCourses[todayCourses.indexOf(theComingCourse) + 1].startSection)
                          }.build()
                          //这里判断一下Plugin启动时间是不是在用户上课前提醒的时间内，如果是的话就设定立刻提醒，如果不是则设定定时提醒
                          PluginMain.quartzScheduler.scheduleJob(this[qq], TriggerBuilder.newTrigger().withSchedule(if(schoolTimetable[theComingCourse.startSection - 1].first - tipOffset < nowTimeAsMinute) {
                              verbose("schedule notification job for $qq: immediate.")
                              SimpleScheduleBuilder.simpleSchedule()
                          } else {
                              //cron 第一个秒数在0-60随机，避免瞬间发送所有提醒，防止被判断为业务操作而封号。
                              CronScheduleBuilder.cronSchedule("${floor(Math.random() * 60).toInt()} ${
                                  (schoolTimetable[theComingCourse.startSection - 1].first - tipOffset).let { "${it % 60} ${(it - (it % 60)) / 60}" }
                              } ${ dateOfToday.let { "${it.dayOfMonth} ${it.month.value} ? ${it.year}" } }".also {
                                  verbose("schedule notification job for $qq: cron $it.")
                              })
                          }).build())
                      }
                  } else warning("/*...*/")
              }
          } else warning("/*...*/" )
      }
  
      fun stopAndRemoveUserNotificationJob(qq: Long) = userNotificationJobs.run {
          if(this.containsKey(qq)) {
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
          Database.suspendQuery { User.find { Users.schoolId eq schoolId }.forEach {
              stopAndRemoveUserNotificationJob(it.qq)
              removeUserTodayCourses(it.qq)
              startUserNotificationJob(it.qq, schoolId, whichSection = null)
          } }
      }
  
      /**
       * 在学校更新时间表时调用
       *
       * @see Request.SyncSchoolTimetableRequest
       */
  
      fun onChangeSchoolTimetable(schoolId: Int) = launch(PluginMain.coroutineContext) {
          info("onChangeSchoolTimetable(schoolId=$schoolId)")
          removeSchoolTimetable(schoolId)
          Database.suspendQuery { User.find { Users.schoolId eq schoolId }.forEach {
              stopAndRemoveUserNotificationJob(it.qq)
              removeUserTodayCourses(it.qq)
              startUserNotificationJob(it.qq, schoolId, whichSection = null)
          } }
      }
  
      /**
       * 在用户修改了提前提醒时间时调用.
       */
      fun restartUserNotification(qq: Long) = Database.query<Unit> {
          info("ScheduleListenerService.restartUserNotification(qq=$qq)")
          val user = User.find { Users.qq eq qq }
          if(!user.empty()) {
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
              //更新今天日期
              dateOfToday = TimeProviderService.currentTimeStamp
              Database.query {
                  val users = User.all()
                  for(user in users) {
                      stopAndRemoveUserNotificationJob(user.qq)
                      removeUserTodayCourses(user.qq)
                      startUserNotificationJob(user.qq, user.schoolId, whichSection = null)
                  }
              }
              info("Notification distribution job has executed.")
          }
      }
  
      /**
       * 用户课程提醒Job，在上课前的某个设定时间执行。
       *
       * 若前一天的提醒Job还未执行完成，则打断并删除Job
       */
      class UserNotificationJob: InterruptableJob {
          override fun execute(context: JobExecutionContext?) {
              BotEventRouteService.sendMessageNonBlock(/*...*/)
              stopAndRemoveUserNotificationJob(qq)
              if(nextSection != -1) startUserNotificationJob(qq, it.getInt("belongingSchool"), nextSection)
              info("Notification job executed for user $qq")
          }
          override fun interrupt() {  }
      }
  }
  
  data class SingleCourse(/*...*/)
```

`ScheduleListenerService`比较复杂，我们从 `main` 开始看：

```kotlin
//scheduled job
PluginMain.quartzScheduler.scheduleJob(/*...*/)
//immediate start once
PluginMain.quartzScheduler.scheduleJob(/*...*/)
```

在 `main` 中，分配了一个立刻执行和每天凌晨 00:00:30 定时执行的任务，它们都指向了 `UserNotificationDistributionJob` 任务：

```kotlin
class UserNotificationDistributionJob : Job {
    override fun execute(context: JobExecutionContext?) {
        /*...*/
        Database.query {
            val users = User.all()
            for(user in users) {
                stopAndRemoveUserNotificationJob(user.qq)
                removeUserTodayCourses(user.qq)
                startUserNotificationJob(user.qq, user.schoolId, whichSection = null)
            }
        }
        info("Notification distribution job has executed.")
    }
}
```

这个任务通过 `removeUserTodayCourses` 更新用户的今日课程，通过 `stopAndRemoveUserNotificationJob` 清除了前一天没有完成的 `UserNotificationJob` (一般情况下不会有未完成的`UserNotificationJob`)。

同时 **通过`startUserNotificationJob`为每个用户设定今天第一节课`UserNotificationJob`触发时间** (这个触发时间就是用户设定的在上课前多长时间触发的那个时间) 。

> 为什么是第一节课的触发时间？
>
> `startUserNotificationJob`的第三个参数 `whichSection` 用于指定为那一节课设定`UserNotificationJob`，而当这个参数为 `null` 时，则自动根据当前时间为最近的下一节课设置 `UserNotificationJob`，所以凌晨触发的 `UserNotificationDistributionJob` ，最近的下一节课是今天的第一节课。
>
> 这同时也能帮你理解为什么 `main` 中要设置一个立刻执行的 `UserNotificationDistributionJob`。

你可能会有疑问：我只设定了第一节课的触发时间，那后面的课程呢？

现在我们看看 `UserNotificationJob`：

```kotlin
class UserNotificationJob: InterruptableJob {
    override fun execute(context: JobExecutionContext?) {
        val nextSection = it.getInt("theNextClassSectionStart")
        BotEventRouteService.sendMessageNonBlock(/*...*/)
        stopAndRemoveUserNotificationJob(qq)
        if(nextSection != -1) startUserNotificationJob(qq, it.getInt("belongingSchool"), nextSection)
        info("Notification job executed for user $qq")
    }
    override fun interrupt() {  }
}
```

在发送完提醒后，删除了存储在`userNotificationJobs`的 `UserNotificationJob`，

> 注意：**只是删除了存储在`userNotificationJobs`的 `JobDetail`对象和解绑了和 `Scheduler` 的关系**，并没有停止这个 Job 的运行，因为我们就是在这个 Job 中删除的。

 并**判断 `nextSection` 是不是 `-1`，如果不是，那么就设定下一节课的课程提醒 `UserNotificationJob`**。

 `nextSection` 是什么？

现在看看`startUserNotificationJob`中设定`UserNotificationJob`的片段：

```kotlin
val theComingCourse = todayCourses.filter { it.startSection == explicitSection }.first()
this[qq] = JobBuilder.newJob(UserNotificationJob::class.java).apply {
    //usingJobData: qq, belongingSchool, theComingCourseName/TeacherName/Locale/StartTime/EndTime
    //如果为 -1 则表示今天课程已结束，今天没有下一节课了
    usingJobData("theNextClassSectionStart", if(todayCourses.indexOf(theComingCourse) == todayCourses.lastIndex) -1 else todayCourses[todayCourses.indexOf(theComingCourse) + 1].startSection)
}.build()
```

这段中 `theComingCourse` 表示即将到来的一节课，也就是要设置提醒的那一节课，在为 `Job`传递数据时，我们传递了`theNextClassSectionStart`，代表**即将到来的这一节课的下一节课**，并做了判断：**如果即将到来的这一节课是今天课程的 `List`最后一个元素，那么就传递进去 `-1`，否则就传递下一节课的`sectionStart`**。

这下你应该明白了`ScheduleListenerService`的工作机制，**它实际上是一个链式循环**：

[![](https://mermaid.ink/img/eyJjb2RlIjoiZ3JhcGggTFJcbiAgICB1bmRqb2JbXCJVc2VyTm90aWZpY2F0aW9uRGlzdHJpYnV0aW9uSm9iXCJdIC0tXCJzZXQgYSBpbml0aWFsICd4J1wiLS0-IHN0YXJ0dW5qXG4gICAgc3RhcnR1bmooXCJzdGFydFVzZXJOb3RpZmljYXRpb25Kb2I8YnI-Y29tbWluZyA9IHg8YnI-bmV4dCA9IHggKyAxID4gbiA_IC0xIDogeCArIG5cIikgLS0-IHVuam9iXG4gICAgdW5qb2JbXCJVc2VyTm90aWZpY2F0aW9uSm9iPGJyPnNlbmRNc2cgLT4gY29tbWluZ1wiXSAtLT4ganVkZ2VcbiAgICBqdWRnZXt7XCJuZXh0ID09IC0xID9cIn19IC0tXCJObzogeCA9IG5leHRcIi0tPiBzdGFydHVualxuICAgIGp1ZGdlIC0tXCJZZXNcIi0tPiB5ZXMoXCJzdG9wXCIpIiwibWVybWFpZCI6e30sInVwZGF0ZUVkaXRvciI6ZmFsc2V9)](https://mermaid-js.github.io/mermaid-live-editor/#/edit/eyJjb2RlIjoiZ3JhcGggTFJcbiAgICB1bmRqb2JbXCJVc2VyTm90aWZpY2F0aW9uRGlzdHJpYnV0aW9uSm9iXCJdIC0tXCJzZXQgYSBpbml0aWFsICd4J1wiLS0-IHN0YXJ0dW5qXG4gICAgc3RhcnR1bmooXCJzdGFydFVzZXJOb3RpZmljYXRpb25Kb2I8YnI-Y29tbWluZyA9IHg8YnI-bmV4dCA9IHggKyAxID4gbiA_IC0xIDogeCArIG5cIikgLS0-IHVuam9iXG4gICAgdW5qb2JbXCJVc2VyTm90aWZpY2F0aW9uSm9iPGJyPnNlbmRNc2cgLT4gY29tbWluZ1wiXSAtLT4ganVkZ2VcbiAgICBqdWRnZXt7XCJuZXh0ID09IC0xID9cIn19IC0tXCJObzogeCA9IG5leHRcIi0tPiBzdGFydHVualxuICAgIGp1ZGdlIC0tXCJZZXNcIi0tPiB5ZXMoXCJzdG9wXCIpIiwibWVybWFpZCI6e30sInVwZGF0ZUVkaXRvciI6ZmFsc2V9)

更多的实现细节还请浏览[完整代码](https://github.com/KonnyakuCamp/SuperCourseTimetableBot/blob/main/src/main/kotlin/stageguard/sctimetable/service/ScheduleListenerService.kt)。

- ### [TimeProviderService](https://github.com/KonnyakuCamp/SuperCourseTimetableBot/blob/main/src/main/kotlin/stageguard/sctimetable/service/TimeProviderService.kt)

```kotlin
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
    var currentWeekPeriod: MutableMap<Int, Int> = mutableMapOf()

    val currentSemesterBeginYear: Int
        get() = if (currentSemester == 2) currentYear - 1 else currentYear

    val currentTimeStamp: LocalDate
        get() = LocalDate.now(ZoneId.of("Asia/Shanghai"))

    private val scheduledQuartzJob: MutableList<Pair<JobDetail, Trigger>> = mutableListOf(
        //Scheduled
        YearUpdater
        SemesterUpdater
        SchoolWeekPeriodUpdater
        //immediate start once
        YearUpdater
        SemesterUpdater
        SchoolWeekPeriodUpdater
    )


    override suspend fun main() {
        PluginMain.quartzScheduler.apply {
            scheduledQuartzJob.forEach { /*...*/ }
        }.start()
        info("TimeProviderServices(${scheduledQuartzJob.joinToString(", ") { it.first.key.name }}) have started.")
    }

    class YearUpdater : Job {
        override fun execute(context: JobExecutionContext?) {
            currentYear = LocalDate.now(ZoneId.of("Asia/Shanghai")).year
            info("Job YearUpdater is executed. (currentYear -> $currentYear)")
        }
    }
    class SemesterUpdater : Job {
        override fun execute(context: JobExecutionContext?) {
            currentSemester = if (LocalDate.now(ZoneId.of("Asia/Shanghai")).monthValue in 3..7) 2 else 1
            info("Job SemesterUpdater is executed. (currentSemester -> $currentSemester)")
        }
    }
    class SchoolWeekPeriodUpdater : Job {
        override fun execute(context: JobExecutionContext?) {
            Database.query {
                val timetables = SchoolTimetable.all()
                for (ttb in timetables) {
                    val addTime = LocalDate.parse(ttb.timeStampWhenAdd)
                    val dayAddedBasedWeek = addTime.dayOfWeek.value + (currentTimeStamp.toEpochDay() - addTime.toEpochDay())
                    val result = if (dayAddedBasedWeek <= 7) { 0 } else { ceil((dayAddedBasedWeek / 7).toFloat()).toInt() }
                    if (currentWeekPeriod.containsKey(ttb.schoolId)) currentWeekPeriod.remove(ttb.schoolId)
                    currentWeekPeriod[ttb.schoolId] = ttb.weekPeriodWhenAdd + result
                }
            }
            info("Job SchoolWeekPeriodUpdater is executed.")
        }
    }
}
```

`TimeProviderService`的实现也非常简单，在 `main` 中开启了 3 个定时任务和 3 个立即启动的任务，他们都用于更新开头的那些变量 `currentXXX`，并直接提供给外部访问。

另外请额外注意一下 `SchoolWeekPeriodUpdater`，它通过 `timeStampWhenAdd`(学校时间表存储进数据库中时的时间戳) 和 `weekPeriodWhenAdd`(学校时间表存储进数据库中时的周数) 来计算任意一个事件所处的周数。

> 用户修改学校当前周数就是将**用户修改周数时的时间**和**用户指定的周数**存储进 `timeStampWhenAdd` 和 `weekPeriodWhenAdd`，来实现修改当前周数的功能。

## 其他代码

- ### [PluginData](https://github.com/KonnyakuCamp/SuperCourseTimetableBot/blob/main/src/main/kotlin/stageguard/sctimetable/PluginData.kt)

现在`PluginData.kt`中仅记录了用户指定的提前多长时间提醒数据，不将这个数据存储到数据库中是因为更方便修改。

- ### Package  stageguard.sctimetable.api

主要负责与超级课表的服务器交互，核心文件是`SuperCourseApiService.kt`

- ### Package stageguard.sctimetable.database

主要负责与数据库对接，`model`中的 `dsl`/`dao`为抽象化的数据库条目。