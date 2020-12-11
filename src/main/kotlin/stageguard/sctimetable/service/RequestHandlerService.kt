@file:Suppress("unused")

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
import stageguard.sctimetable.PluginMain
import stageguard.sctimetable.api.LoginCookieData
import stageguard.sctimetable.api.LoginInfoData
import stageguard.sctimetable.api.SuperCourseApiService
import stageguard.sctimetable.api.pkey
import stageguard.sctimetable.service.RequestHandlerService.handlerChannel
import stageguard.sctimetable.database.Database
import stageguard.sctimetable.database.model.Courses
import stageguard.sctimetable.database.model.SchoolTimetables
import stageguard.sctimetable.database.model.User
import stageguard.sctimetable.database.model.Users
import stageguard.sctimetable.utils.AESUtils
import stageguard.sctimetable.utils.Either

/**
 * RequestHandlerService 负责处理各种请求，
 *
 * 对于[handlerChannel]，它接收一个[RequestType]并通过它的具体类型来处理不同类型的请求
 **/
object RequestHandlerService : AbstractPluginManagedService(Dispatchers.IO) {

    private val handlerChannel = Channel<RequestType>(100) {
        PluginMain.logger.warning { "Request is not handled. Request = $it" }
    }

    override suspend fun main() { for(request in handlerChannel) { if(this@RequestHandlerService.isActive) {
        PluginMain.logger.info { "Handle Request: $request" }
        when (request) {
            is RequestType.LoginRequest -> {
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
                            if(user.empty()) { Database.query {
                                User.new {
                                    qq = request.qq
                                    studentId = loginReceipt.value.data.student.studentNum.toLong()
                                    name = loginReceipt.value.data.student.nickName
                                    schoolId = loginReceipt.value.data.student.schoolId
                                    account = request.loginInfoData.username
                                    password = AESUtils.encrypt(request.loginInfoData.password, SuperCourseApiService.pkey)
                                }
                            } }
                            PluginMain.logger.info { "User ${request.qq} login successful." }
                            sendRequest(RequestType.InternalSyncCourseRequestViaCookieData(request.qq, cookieData))
                        }
                        //用户登录请求失败，密码错误或其他错误(网络问题等)
                        is Either.Right -> PluginMain.logger.error { "Failed to login user ${request.qq}'s SuperCourse error, reason: ${loginReceipt.value.data.errorStr}" }
                    }
                }
            }
            is RequestType.SyncCourseRequest -> {
                Database.suspendQuery {
                    val user = User.find { Users.qq eq request.qq }
                    if(!user.empty()) {
                        val cookieData = LoginCookieData()
                        SuperCourseApiService.loginViaPassword(user.first().account, AESUtils.decrypt(user.first().password, SuperCourseApiService.pkey)) {
                            cookieData.jSessionId = it.jSessionId
                            cookieData.serverId = it.serverId
                        }.also { when(it) {
                            is Either.Left -> sendRequest(RequestType.InternalSyncCourseRequestViaCookieData(request.qq, cookieData))
                            //用户记录在User的密码有误或者其他问题(网络问题等)
                            is Either.Right -> PluginMain.logger.error { "Failed to sync user ${request.qq}'s courses, reason: ${it.value.data.errorStr}" }
                        } }
                    } else {
                        //用户在User表里没有记录，无法同步
                        PluginMain.logger.error { "Failed to sync user ${request.qq}'s courses, reason: User doesn't exist." }
                    }
                }
            }
            is RequestType.InternalSyncCourseRequestViaCookieData -> {
                val courseTable = Courses(request.qq)
                Database.suspendQuery {
                    SchemaUtils.create(courseTable)
                    courseTable.deleteWhere { (courseTable.beginYear eq TimeProviderService.currentSemesterBeginYear) and (courseTable.semester eq TimeProviderService.currentSemester) }
                    val user = User.find { Users.qq eq request.qq }
                    if(user.empty()) {
                        PluginMain.logger.warning { "User ${request.qq} requests to sync courses but he/she doesn't exist in user table." }
                    } else {
                        SuperCourseApiService.getCourses(request.cookieData).also { coursesDTO -> when(coursesDTO) {
                            is Either.Left -> {
                                Database.query {
                                    //delete exist course
                                    coursesDTO.value.data.lessonList.forEach { e -> courseTable.insert { crs ->
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
                                    } }
                                }
                                PluginMain.logger.info { "Sync user ${request.qq}'s courses successfully." }
                            }
                            is Either.Right -> PluginMain.logger.error { "Failed to Sync user ${request.qq}'s." }
                        } }
                    }
                }
            }
            is RequestType.DeleteCourseRequest -> {
                Database.suspendQuery {
                    val users = User.find { Users.qq eq request.qq }
                    if(users.count() == 1L) {
                        SchoolTimetables.deleteWhere { SchoolTimetables.schoolId eq users.first().schoolId }
                        TimeProviderService.currentWeek.removeIf { it.first == users.first().schoolId }
                    }
                    SchemaUtils.drop(Courses(request.qq))
                    Users.deleteWhere { Users.qq eq request.qq }
                }
            }
            is RequestType.SyncSchoolTimetableRequest -> {

            }
            is RequestType.SyncSchoolWeekPeriodRequest -> {

            }
        }
    }
    } }

    suspend fun sendRequest(request: RequestType) {
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
sealed class RequestType {
    /**
     * LoginAndStoreRequest：新用户请求登录并存储课程信息到数据库
     *
     * 之后将发送[InternalSyncCourseRequestViaCookieData]请求
     * @param loginInfoData 用户登录信息，包括用户名和密码
     **/
    class LoginRequest(val qq: Long, val loginInfoData: LoginInfoData) : RequestType() {
        override fun toString() = "LoginRequest(qq=$qq,account=${loginInfoData.username},password=${"*".repeat(loginInfoData.password.length)})"
    }
    /**
     * SyncCourseRequest：当数据库中已存在这个用户信息和课表信息，请求更新用户的课表信息
     *
     * 这是通过重新登录SuperCourse来同步
     *
     * 获取到cookie后将委托给[InternalSyncCourseRequestViaCookieData]更新
     * @param qq 要更新课表信息的用户的QQ号
     **/
    class SyncCourseRequest(val qq: Long) : RequestType() {
        override fun toString() = "SyncCourseRequest(qq=$qq)"
    }
    /**
     * InternalSyncCourseRequestViaCookieData：当数据库中已存在这个用户信息和课表信息，请求更新用户的课表信息
     *
     * 这是通过cookie更新
     * @param qq 要更新课表信息的用户的QQ号
     **/
    class InternalSyncCourseRequestViaCookieData(val qq: Long, val cookieData: LoginCookieData) : RequestType() {
        override fun toString() = "InternalSyncCourseRequestViaCookieData(qq=$qq,cookie=$cookieData)"
    }
    /**
     * DeleteCourseRequest：用户请求删除数据库中该用户的所有信息
     * @param qq 要删除信息的用户的QQ号
     */
    class DeleteCourseRequest(val qq: Long) : RequestType() {
        override fun toString() = "DeleteCourseRequest(qq=$qq)"
    }
    /**
     * SyncSchoolTimetableRequest：同步这个学校当前学期的作息表.
     * @param qq 要修改这个用户所在的学校作息时间
     * @param newTimetable 时间表列表
     */
    class SyncSchoolTimetableRequest(val qq: Long, val newTimetable: List<Pair<String, String>>) : RequestType() {
        override fun toString() = "SyncSchoolTimetableRequest(qq=$qq, newTimetable=${newTimetable.joinToString(",") { "[${it.first}->${it.second}]" }})"
    }
    /**
     * SyncSchoolWeekPeriod：同步这个学校当前学期的周数，
     *
     * 请注意：这个同步是指修改数据库中的[currentWeek]，并不是指[TimeProviderService.SchoolWeekPeriodUpdater]的同步，通常发生在用户发现当前weekPeriod不正确而提出修改请求。
     * @param qq 要修改这个用户所在的学校作息时间
     * @param currentWeek 当前周数
     */
    class SyncSchoolWeekPeriodRequest(val qq: Long, val currentWeek: Int) : RequestType() {
        override fun toString() = "SyncSchoolWeekPeriodRequest(qq=$qq, currentWeek=$currentWeek)"
    }

}