@file:Suppress("unused")

package stageguard.sctimetable.service

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import net.mamoe.mirai.utils.info
import net.mamoe.mirai.utils.warning
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.deleteWhere
import stageguard.sctimetable.AbstractPluginManagedService
import stageguard.sctimetable.PluginMain
import stageguard.sctimetable.api.LoginCookieData
import stageguard.sctimetable.api.LoginInfoData
import stageguard.sctimetable.api.SuperCourseApiService
import stageguard.sctimetable.service.RequestHandlerService.handlerChannel
import stageguard.sctimetable.database.Database
import stageguard.sctimetable.database.model.Courses
import stageguard.sctimetable.database.model.SchoolTimetables
import stageguard.sctimetable.database.model.User
import stageguard.sctimetable.database.model.Users

/**
 * RequestHandlerService 负责处理各种请求，
 *
 * 对于[handlerChannel]，它接收一个[Request]并通过它的具体类型来处理不同类型的请求
 **/
object RequestHandlerService : AbstractPluginManagedService(Dispatchers.IO) {

    private val handlerChannel = Channel<Request> {
        PluginMain.logger.warning { "Request is not handled. Request = $it" }
    }

    override suspend fun main() { for(request in handlerChannel) { if(this@RequestHandlerService.isActive) {
        PluginMain.logger.info { "Handle Request: $request" }
        when (request) {
            is Request.LoginAndStoreRequest -> {
                val queryIfExist = Database.suspendQuery { User.find { Users.qq eq request.qq } }
                if(queryIfExist != null && queryIfExist.empty()) {
                    val cookieData = LoginCookieData()
                    val loginReceipt = SuperCourseApiService.loginViaPassword(request.loginInfoData) {
                        cookieData.apply {
                            this.jSessionId = it.jSessionId
                            this.serverId = it.serverId
                        }
                    }
                    Database.suspendQuery { User.new {
                        qq = request.qq
                        studentId = loginReceipt.data.student.studentNum.toLong()
                        name = loginReceipt.data.student.nickName
                        schoolId = loginReceipt.data.student.schoolId
                        jSessionId = cookieData.jSessionId
                        serverId = cookieData.serverId
                    } }
                }
                sendRequest(Request.SyncCourseRequest(request.qq))
            }
            is Request.SyncCourseRequest -> {
                request.qq
            }
            is Request.DeleteCourseRequest -> {
                Database.suspendQuery { User.find { Users.qq eq request.qq }.also {
                    if(!it.empty()) {
                        if(it.count() == 1L) SchoolTimetables.deleteWhere { SchoolTimetables.schoolId eq it.first().schoolId }
                        SchemaUtils.drop(Courses("courses_${request.qq}"))
                        Users.deleteWhere { Users.qq eq request.qq }
                    }
                } }
            }
            is Request.SyncSchoolTimetableRequest -> {
                Database.suspendQuery {
                    val user = User.find { Users.qq eq request.qq }.also {
                        if(it.empty()) {
                            PluginMain.logger.warning { "This user(${request.qq}) doesn't exist in database, cannot sync school timetable" }
                            //提示用户不在User表里。
                        } else {

                        }
                    }
                }
            }
        }
    } } }

    suspend fun sendRequest(Request: Request) {
        handlerChannel.send(Request)
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
     * @param loginInfoData 用户登录信息，包括用户名和密码
     **/
    class LoginAndStoreRequest(val qq: Long, val loginInfoData: LoginInfoData) : Request()
    /**
     * SyncCourseRequest：当数据库中已存在这个用户信息和课表信息，请求更新用户的课表信息
     * @param qq 要更新课表信息的用户的QQ号
     **/
    class SyncCourseRequest(val qq: Long) : Request()
    /**
     * SyncCourseRequest：用户请求删除数据库中该用户的所有信息
     * @param qq 要删除信息的用户的QQ号
     */
    class DeleteCourseRequest(val qq: Long) : Request()
    /**
     * SyncSchoolTimetableRequest：同步这个学校当前学期的作息表.
     * @param qq 要修改这个用户所在的学校作息时间
     * @param newTimetable 时间表列表
     */
    class SyncSchoolTimetableRequest(val qq: Long, val newTimetable: List<Pair<String, String>>) : Request()
    /**
     * SyncSchoolWeekPeriod：同步这个学校当前学期的周数
     * @param qq 要修改这个用户所在的学校作息时间
     * @param currentWeek 当前周数
     */
    class SyncSchoolWeekPeriod(val qq: Long, val currentWeek: Int)

}