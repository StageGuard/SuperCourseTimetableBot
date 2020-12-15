package stageguard.sctimetable.service

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import net.mamoe.mirai.utils.error
import net.mamoe.mirai.utils.info
import net.mamoe.mirai.utils.warning
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import stageguard.sctimetable.AbstractPluginManagedService
import stageguard.sctimetable.PluginConfig
import stageguard.sctimetable.PluginData
import stageguard.sctimetable.PluginMain
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

    private val handlerChannel = Channel<Request>(100) {
        PluginMain.logger.warning { "Request is not handled. Request = $it" }
    }

    override suspend fun main() { for(request in handlerChannel) { if(this@RequestHandlerService.isActive) {
        PluginMain.logger.info { "Handle Request: $request" }
        when (request) {
            is Request.LoginRequest -> {
                Database.suspendQuery {
                    val user = User.find { Users.qq eq request.qq }
                    val cookieData = LoginCookieData()
                    val loginReceipt = SuperCourseApiService.loginViaPassword(request.loginInfoData) {
                        cookieData.apply {
                            this.jSessionId = it.jSessionId
                            this.serverId = it.serverId
                        }
                    }
                    when(loginReceipt) {
                        is Either.Left -> {
                            if(user.empty()) {
                                Database.query { User.new {
                                    qq = request.qq
                                    studentId = loginReceipt.value.data.student.studentNum.toLong()
                                    name = loginReceipt.value.data.student.nickName
                                    schoolId = loginReceipt.value.data.student.schoolId
                                    account = request.loginInfoData.username
                                    password = AESUtils.encrypt(request.loginInfoData.password, SuperCourseApiService.pkey)
                                } }
                                PluginData.advancedTipOffset[request.qq] = PluginConfig.advancedTipTime
                            }
                            PluginMain.logger.info { "User ${request.qq} login successful." }
                            sendRequest(Request.InternalSyncCourseRequestViaCookieDataRequest(request.qq, cookieData))
                            sendRequest(Request.SyncSchoolTimetableRequest(request.qq))
                        }
                        //用户登录请求失败，密码错误或其他错误(网络问题等)
                        is Either.Right -> PluginMain.logger.error { "Failed to login user ${request.qq}'s SuperCourse error, reason: ${loginReceipt.value.data.errorStr}" }
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
                            is Either.Right -> PluginMain.logger.error { "Failed to sync user ${request.qq}'s courses, reason: ${it.value.data.errorStr}" }
                        } }
                    } else PluginMain.logger.error { "Failed to sync user ${request.qq}'s courses, reason: User doesn't exist." }
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
                                PluginMain.logger.info { "Sync user ${request.qq}'s courses successfully." }
                            }
                            is Either.Right -> PluginMain.logger.error { "Failed to sync user ${request.qq}'s courses, reason: ${coursesDTO.value.message}." }
                        } }
                    } else PluginMain.logger.error { "Failed to sync user ${request.qq}'s courses, reason: User doesn't exist." }
                }
            }
            is Request.DeleteCourseRequest -> {
                Database.suspendQuery {
                    val user = User.find { Users.qq eq request.qq }
                    if(!user.empty()) {
                        User.find { Users.schoolId eq user.first().schoolId }.also { remainUsers -> if(remainUsers.count() == 1L) {
                            SchoolTimetables.deleteWhere { SchoolTimetables.schoolId eq user.first().schoolId }
                            TimeProviderService.currentWeekPeriod.remove(user.first().schoolId)
                            TODO("还有一个移除ScheduleListenerJob里cachedSchoolTimetables的学校")
                        } }
                        PluginData.advancedTipOffset.remove(request.qq)
                        TODO("还有个移除ScheduleListenerJob里的user，要先停止Job")
                        SchemaUtils.drop(Courses(request.qq))
                        Users.deleteWhere { Users.qq eq request.qq }
                    }
                }
            }
            is Request.SyncSchoolTimetableRequest -> {
                Database.suspendQuery {
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
                                        }.courseTimeList.courseTimeBO.joinToString("|") { time ->
                                            "${time.beginTimeStr.substring(0..1)}:${time.beginTimeStr.substring(2..3)}-${time.endTimeStr.substring(0..1)}:${time.endTimeStr.substring(2..3)}"
                                        }
                                        timeStampWhenAdd = TimeProviderService.currentTimeStamp.toString()
                                        weekPeriodWhenAdd = 1
                                    }
                                    TimeProviderService.immediateUpdateSchoolWeekPeriod()
                                }
                                //用户记录在User的密码有误或者其他问题(网络问题等)
                                is Either.Right -> PluginMain.logger.error { "Failed to sync user ${request.qq}'s school timetable, reason: ${user.value.data.errorStr}" }
                            }
                        }
                    }
                    if(!user.empty()) {
                        val schoolTimeTable = SchoolTimetable.find { SchoolTimetables.schoolId eq user.first().schoolId }
                        if(schoolTimeTable.empty()) {
                            //首次从服务器同步时间表
                            syncFromServer()
                            PluginMain.logger.info { "Sync timetable from server for ${request.qq}'s school successfully." }
                        } else if(request.forceUpdate) { //强制更新，要告知用户可能会产生不好影响
                            schoolTimeTable.first().delete()
                            if(request.newTimetable == null) {
                                //从服务器更新时间表
                                syncFromServer()
                                PluginMain.logger.info { "Sync timetable from server for ${request.qq}'s school successfully, forceUpdate=${request.forceUpdate}." }
                            } else PluginMain.logger.info { "Sync timetable from user customed list for ${request.qq}'s school successfully, forceUpdate=${request.forceUpdate}." }
                        } else PluginMain.logger.warning { "Deny to sync school timetable for ${request.qq}'s school, forceUpdate=${request.forceUpdate}." }
                    } else PluginMain.logger.error { "Failed to sync school timetable ${request.qq}'s school, reason: User doesn't exist." }
                }
            }
            is Request.SyncSchoolWeekPeriodRequest -> {
                Database.suspendQuery {
                    val user = User.find { Users.qq eq request.qq }
                    if(!user.empty()) {
                        val schoolTimetable = SchoolTimetable.find { SchoolTimetables.schoolId eq user.first().schoolId }
                        if(!schoolTimetable.empty()) {
                            if(request.forceUpdate) {
                                schoolTimetable.first().apply {
                                    timeStampWhenAdd = TimeProviderService.currentTimeStamp.toString()
                                    weekPeriodWhenAdd = request.currentWeek
                                }
                                TimeProviderService.immediateUpdateSchoolWeekPeriod()
                                PluginMain.logger.info { "Sync school week period for user ${request.qq}'s school successfully, currentWeek=${request.currentWeek}." }
                            } else PluginMain.logger.warning { "Deny to sync school week period for user ${request.qq}'s school, reason: forceUpdate=${request.forceUpdate}" }
                        } else PluginMain.logger.error { "Failed to sync school week period for user ${request.qq}'s school, reason: School doesn't exist." }
                    } else PluginMain.logger.error { "Failed to sync school week period for user ${request.qq}'s school, reason: User doesn't exist." }
                }
            }
        }
    } } }
    suspend fun sendRequest(request: Request) {
        handlerChannel.send(request)
    }
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
    class SyncSchoolTimetableRequest(val qq: Long, val newTimetable: List<Pair<String, String>>? = null, val forceUpdate: Boolean = false) : Request() {
        override fun toString() = "SyncSchoolTimetableRequest(qq=$qq,newTimetable=${newTimetable?.joinToString(",") { "[${it.first}->${it.second}]" } ?: "<syncFromServer>"},forceUpdate=$forceUpdate)"
    }
    /**
     * SyncSchoolWeekPeriod：同步这个学校当前学期的周数，
     *
     * 请注意：这个同步是指修改数据库中的[currentWeek]，并不是指[TimeProviderService.SchoolWeekPeriodUpdater]的同步，通常发生在用户发现当前weekPeriod不正确而提出修改请求。
     * @param qq 要修改这个用户所在的学校作息时间
     * @param currentWeek 当前周数
     */
    class SyncSchoolWeekPeriodRequest(val qq: Long, val currentWeek: Int, val forceUpdate: Boolean = false) : Request() {
        override fun toString() = "SyncSchoolWeekPeriodRequest(qq=$qq, currentWeek=$currentWeek)"
    }
}