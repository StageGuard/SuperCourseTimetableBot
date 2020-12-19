package stageguard.sctimetable.service

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import stageguard.sctimetable.AbstractPluginManagedService
import stageguard.sctimetable.PluginConfig
import stageguard.sctimetable.PluginData
import stageguard.sctimetable.api.LoginCookieData
import stageguard.sctimetable.api.LoginInfoData
import stageguard.sctimetable.api.SuperCourseApiService
import stageguard.sctimetable.service.RequestHandlerService.handlerChannel
import stageguard.sctimetable.database.Database
import stageguard.sctimetable.database.model.*
import stageguard.sctimetable.utils.AESUtils
import stageguard.sctimetable.utils.Either

/**
 * RequestHandlerService 负责处理各种请求，
 *
 * 对于[handlerChannel]，它接收一个[Request]并通过它的具体类型来处理不同类型的请求
 **/
object RequestHandlerService : AbstractPluginManagedService(Dispatchers.IO) {

    override val TAG: String = "RequestHandlerService"

    private val handlerChannel = Channel<Request>(100) {
        warning("Request is not handled. Request = $it")
    }

    override suspend fun main() { for(request in handlerChannel) { if(this@RequestHandlerService.isActive) {
        info("Handle Request: $request")
        when (request) {
            is Request.LoginRequest -> {
                Database.suspendQuery {
                    val user = User.find { Users.qq eq request.qq }
                    if(user.empty()) {
                        val cookieData = LoginCookieData()
                        val loginReceipt = SuperCourseApiService.loginViaPassword(request.loginInfoData) {
                            cookieData.apply {
                                this.jSessionId = it.jSessionId
                                this.serverId = it.serverId
                            }
                        }
                        when(loginReceipt) {
                            is Either.Left -> {
                                Database.query { User.new {
                                    qq = request.qq
                                    studentId = loginReceipt.value.data.student.studentNum.toLong()
                                    name = loginReceipt.value.data.student.nickName
                                    schoolId = loginReceipt.value.data.student.schoolId
                                    account = request.loginInfoData.username
                                    password = AESUtils.encrypt(request.loginInfoData.password, SuperCourseApiService.pkey)
                                } }
                                PluginData.advancedTipOffset[request.qq] = PluginConfig.advancedTipTime
                                info("User ${request.qq} login successful.")
                                BotEventRouteService.sendMessageNonBlock(request.qq, "登录成功，正在同步你的课程。。。")
                                sendRequest(Request.InternalSyncCourseRequestViaCookieDataRequest(request.qq, cookieData))
                                sendRequest(Request.SyncSchoolTimetableRequest(request.qq))
                            }
                            //用户登录请求失败，密码错误或其他错误(网络问题等)
                            is Either.Right -> {
                                error("Failed to login user ${request.qq}'s SuperCourse error, reason: ${loginReceipt.value.data.errorStr}")
                                BotEventRouteService.sendMessageNonBlock(request.qq, "无法登录超级课表，原因：${loginReceipt.value.data.errorStr}")
                            }
                        }
                    } else {
                        info("User ${request.qq} has already login and cannot login again.")
                        BotEventRouteService.sendMessageNonBlock(request.qq, "你已经登陆过了，不可以重复登录。\n如果你想修改密码，请发送\"帮助\"查看如何修改密码。")
                    }
                }
            }
            is Request.SyncCourseRequest -> {
                Database.suspendQuery {
                    val user = User.find { Users.qq eq request.qq }
                    if(!user.empty()) {
                        val cookieData = LoginCookieData()
                        SuperCourseApiService.loginViaPassword(user.first().account, AESUtils.decrypt(user.first().password, SuperCourseApiService.pkey)) {
                            cookieData.jSessionId = it.jSessionId
                            cookieData.serverId = it.serverId
                        }.also { when(it) {
                            is Either.Left -> sendRequest(Request.InternalSyncCourseRequestViaCookieDataRequest(request.qq, cookieData))
                            //用户记录在User的密码有误或者其他问题(网络问题等)
                            is Either.Right -> {
                                error("Failed to sync user ${request.qq}'s courses, reason: ${it.value.data.errorStr}")
                                BotEventRouteService.sendMessageNonBlock(request.qq, "无法同步课程。原因：${it.value.data.errorStr}")
                            }
                        } }
                    } else {
                        error("Failed to sync user ${request.qq}'s courses, reason: User doesn't exist.")
                    }
                }
            }
            is Request.InternalSyncCourseRequestViaCookieDataRequest -> {
                val courseTable = Courses(request.qq)
                Database.suspendQuery {
                    SchemaUtils.create(courseTable)
                    courseTable.deleteWhere { (courseTable.beginYear eq TimeProviderService.currentSemesterBeginYear) and (courseTable.semester eq TimeProviderService.currentSemester) }
                    val user = User.find { Users.qq eq request.qq }
                    if(!user.empty()) {
                        SuperCourseApiService.getCourses(request.cookieData).also { coursesDTO -> when(coursesDTO) {
                            is Either.Left -> {
                                Database.query { coursesDTO.value.data.lessonList.forEach { e -> courseTable.insert { crs ->
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
                                info("Sync user ${request.qq}'s courses successfully.")
                                BotEventRouteService.sendMessageNonBlock(request.qq,"""
                                    课程同步成功！
                                    你将在每节课上课前 ${PluginData.advancedTipOffset[request.qq] ?: PluginConfig.advancedTipTime} 分钟收到课程提醒。
                                    可以发送"今日课程"查看今天的课程，发送"查看时间表"查看本校的时间表，
                                    或者发送"修改提前提醒时间"来修改课程提醒时间。
                                """.trimIndent())
                            }
                            is Either.Right -> {
                                error("Failed to sync user ${request.qq}'s courses, reason: ${coursesDTO.value.message}.")
                                BotEventRouteService.sendMessageNonBlock(request.qq, "无法同步课程。原因：${coursesDTO.value.message}")
                            }
                        } }
                    } else {
                        error("Failed to sync user ${request.qq}'s courses, reason: User doesn't exist.")
                    }
                }
            }
            is Request.DeleteCourseRequest -> {
                Database.suspendQuery {
                    val user = User.find { Users.qq eq request.qq }
                    if(!user.empty()) {
                        User.find { Users.schoolId eq user.first().schoolId }.also { remainUsers -> if(remainUsers.count() == 1L) {
                            SchoolTimetables.deleteWhere { SchoolTimetables.schoolId eq user.first().schoolId }
                            TimeProviderService.currentWeekPeriod.remove(user.first().schoolId)
                            ScheduleListenerService.removeSchoolTimetable(user.first().schoolId)
                        } }
                        PluginData.advancedTipOffset.remove(request.qq)
                        ScheduleListenerService.stopAndRemoveUserNotificationJob(request.qq)
                        SchemaUtils.drop(Courses(request.qq))
                        Users.deleteWhere { Users.qq eq request.qq }
                        BotEventRouteService.sendMessageNonBlock(request.qq, "已经成功删除你的所有信息，将不会再收到课程提醒。\n若还想继续使用课程提醒，请重新登录。")
                    }
                }
            }
            is Request.SyncSchoolTimetableRequest -> {
                Database.suspendQuery<Unit> {
                    val user = User.find { Users.qq eq request.qq }
                    suspend fun syncFromServer() {
                        SuperCourseApiService.loginViaPassword(user.first().account, AESUtils.decrypt(user.first().password, SuperCourseApiService.pkey)).also { user ->
                            when(user) {
                                is Either.Left -> {
                                    SchoolTimetable.new {
                                        schoolId = user.value.data.student.schoolId
                                        schoolName = user.value.data.student.schoolName
                                        beginYear = TimeProviderService.currentSemesterBeginYear
                                        semester = TimeProviderService.currentSemester
                                        scheduledTimeList = user.value.data.student.attachmentBO.myTermList.first { termList ->
                                            termList.beginYear == TimeProviderService.currentSemesterBeginYear && termList.term == TimeProviderService.currentSemester
                                            //这超级课表是什么傻逼，妈的还带空字符串
                                        }.courseTimeList.courseTimeBO.filter { it.beginTimeStr.isNotEmpty() }.joinToString("|") { time ->
                                            "${time.beginTimeStr.substring(0..1)}:${time.beginTimeStr.substring(2..3)}-${time.endTimeStr.substring(0..1)}:${time.endTimeStr.substring(2..3)}"
                                        }
                                        timeStampWhenAdd = TimeProviderService.currentTimeStamp.toString()
                                        weekPeriodWhenAdd = 1
                                    }
                                    TimeProviderService.immediateUpdateSchoolWeekPeriod()
                                }
                                //用户记录在User的密码有误或者其他问题(网络问题等)
                                is Either.Right -> {
                                    error("Failed to sync user ${request.qq}'s school timetable, reason: ${user.value.data.errorStr}")
                                    BotEventRouteService.sendMessageNonBlock(request.qq,"无法从服务器同步学校时间表信息，可能是因为你已经修改了超级课表的密码。\n具体原因：${user.value.data.errorStr}")
                                }
                            }
                        }
                    }
                    if(!user.empty()) {
                        val schoolTimeTable = SchoolTimetable.find { SchoolTimetables.schoolId eq user.first().schoolId }
                        if(schoolTimeTable.empty()) {
                            //首次从服务器同步时间表
                            syncFromServer()
                            info("Sync timetable from server for ${request.qq}'s school successfully.")
                            BotEventRouteService.sendMessageNonBlock(request.qq, "成功从服务器同步学校的时间表信息。\n注意：这是首次同步您的学校时间表，若当前周数有误，请发送\"修改时间表\"修改。")
                            sendRequest(Request.SyncSchoolWeekPeriodRequest(request.qq, 1))
                        } else if(request.forceUpdate) {
                            schoolTimeTable.first().delete()
                            if(request.newTimetable == null) {
                                //从服务器更新时间表
                                syncFromServer()
                                ScheduleListenerService.onChangeSchoolTimetable(user.first().schoolId)
                                info("Sync timetable from server for ${request.qq}'s school successfully, forceUpdate=${request.forceUpdate}.")
                                BotEventRouteService.sendMessageNonBlock(request.qq, "成功强制从服务器同步时间表，将影响学校其他用户。")
                                //提醒着各学校的其他人时间表已被强制从服务器同步
                            } else {
                                //手动修正时间表
                                schoolTimeTable.first().scheduledTimeList = request.newTimetable.joinToString("|") { "${it.first}-${it.second}" }
                                ScheduleListenerService.onChangeSchoolTimetable(user.first().schoolId)
                                info("Sync timetable from user custom list for ${request.qq}'s school successfully, forceUpdate=${request.forceUpdate}.")
                                BotEventRouteService.sendMessageNonBlock(request.qq, "已修正学校时间表，将影响学校的其他用户。")
                                //提醒这个学校的其他人时间表已被手动修改
                            }
                        } else {
                            warning("Deny to sync school timetable for ${request.qq}'s school, forceUpdate=${request.forceUpdate}.")
                            BotEventRouteService.sendMessageNonBlock(request.qq, "您所在的学校的时间表信息已在之前被其他同校用户同步并修正，输入\"查看时间表\"查看。")
                        }
                    } else {
                        error("Failed to sync school timetable ${request.qq}'s school, reason: User doesn't exist.")
                    }
                }
            }
            is Request.SyncSchoolWeekPeriodRequest -> {
                Database.suspendQuery {
                    val user = User.find { Users.qq eq request.qq }
                    if(!user.empty()) {
                        val schoolTimetable = SchoolTimetable.find { SchoolTimetables.schoolId eq user.first().schoolId }
                        if(!schoolTimetable.empty()) {
                            schoolTimetable.first().apply {
                                timeStampWhenAdd = TimeProviderService.currentTimeStamp.toString()
                                weekPeriodWhenAdd = request.currentWeek
                            }
                            TimeProviderService.immediateUpdateSchoolWeekPeriod() //阻塞以保证这个过程结束后再执行下一步
                            ScheduleListenerService.onChangeSchoolWeekPeriod(schoolTimetable.first().schoolId)
                            info("Sync school week period for user ${request.qq}'s school successfully, currentWeek=${request.currentWeek}.")
                            BotEventRouteService.sendMessageNonBlock(request.qq, "成功修改当前周数为第 ${request.currentWeek} 周。")
                            //通知其他用户已被修改
                        } else {
                            error("Failed to sync school week period for user ${request.qq}'s school, reason: School doesn't exist.")
                            BotEventRouteService.sendMessageNonBlock(request.qq, "无法修改当前周数，该学校不存在！\n如果你已经登录，请删除你的信息并重新登录。")
                        }
                    } else {
                        error("Failed to sync school week period for user ${request.qq}'s school, reason: User doesn't exist.")
                    }
                }
            }
            is Request.ChangeUserPasswordRequest -> {
                Database.suspendQuery {
                    val user = User.find { Users.qq eq request.qq }
                    if(!user.empty()) {
                        user.first().password = AESUtils.encrypt(request.password, SuperCourseApiService.pkey)
                        info("User ${request.qq} successfully changed his password.")
                        BotEventRouteService.sendMessageNonBlock(request.qq, "修改密码成功！")
                    } else {
                        error("Cannot change password for ${request.qq} because user doesn't exist.")
                    }
                }
            }
        }
    } } }
    fun sendRequest(request: Request) { launch(coroutineContext) { handlerChannel.send(request) } }
}

/**
 * Request是事件对象，并传输到[handlerChannel]
 *
 * 可以根据根据不同的类型来解析不同的需求，避免创建多个[Channel]
 *
 * 不过这也意味着[handlerChannel]将承载更多的工作，所以它的容量应该设置得大一点
 **/
sealed class Request {
    /**
     * LoginAndStoreRequest：新用户请求登录并存储课程信息到数据库
     *
     * 之后将发送[InternalSyncCourseRequestViaCookieDataRequest]请求
     * @param loginInfoData 用户登录信息，包括用户名和密码
     **/
    class LoginRequest(val qq: Long, val loginInfoData: LoginInfoData) : Request() {
        override fun toString() = "LoginRequest(qq=$qq,account=${loginInfoData.username},password=${"*".repeat(loginInfoData.password.length)})"
    }
    /**
     * SyncCourseRequest：当数据库中已存在这个用户信息和课表信息，请求更新用户的课表信息
     *
     * 这是通过重新登录SuperCourse来同步
     *
     * 获取到cookie后将委托给[InternalSyncCourseRequestViaCookieDataRequest]更新
     * @param qq 要更新课表信息的用户的QQ号
     **/
    class SyncCourseRequest(val qq: Long) : Request() {
        override fun toString() = "SyncCourseRequest(qq=$qq)"
    }
    /**
     * InternalSyncCourseRequestViaCookieData：当数据库中已存在这个用户信息和课表信息，请求更新用户的课表信息
     *
     * 这是通过cookie更新
     * @param qq 要更新课表信息的用户的QQ号
     **/
    class InternalSyncCourseRequestViaCookieDataRequest(val qq: Long, val cookieData: LoginCookieData) : Request() {
        override fun toString() = "InternalSyncCourseRequestViaCookieData(qq=$qq,cookie=$cookieData)"
    }
    /**
     * DeleteCourseRequest：用户请求删除数据库中该用户的所有信息
     * @param qq 要删除信息的用户的QQ号
     */
    class DeleteCourseRequest(val qq: Long) : Request() {
        override fun toString() = "DeleteCourseRequest(qq=$qq)"
    }
    /**
     * SyncSchoolTimetableRequest：同步这个学校当前学期的作息表.
     *
     * @param qq 要修改这个用户所在的学校作息时间
     * @param newTimetable 时间表列表
     * @param forceUpdate 当[newTimetable]为null时决定是否强制从服务器同步
     */
    class SyncSchoolTimetableRequest(
        val qq: Long,
        val newTimetable: List<Pair<String, String>>? = null,
        val forceUpdate: Boolean = false
    ) : Request() {
        override fun toString() = "SyncSchoolTimetableRequest(qq=$qq,newTimetable=${newTimetable?.joinToString(",") { "[${it.first}->${it.second}]" } ?: "<syncFromServer>"},forceUpdate=$forceUpdate)"
    }
    /**
     * SyncSchoolWeekPeriod：同步这个学校当前学期的周数，
     *
     * 请注意：这个同步是指修改数据库中的[currentWeek]，并不是指[TimeProviderService.SchoolWeekPeriodUpdater]的同步，通常发生在用户发现当前weekPeriod不正确而提出修改请求。
     * @param qq 要修改这个用户所在的学校作息时间
     * @param currentWeek 当前周数
     */
    class SyncSchoolWeekPeriodRequest(val qq: Long, val currentWeek: Int) : Request() {
        override fun toString() = "SyncSchoolWeekPeriodRequest(qq=$qq, currentWeek=$currentWeek)"
    }
    /**
     * ChangeUserPasswordRequest: 修改用户密码
     *
     * @param qq 用户
     * @param password 新密码
     */
    class ChangeUserPasswordRequest(val qq: Long, val password: String) : Request() {
        override fun toString() = "ChangeUserPasswordRequest(qq=$qq)"
    }
}