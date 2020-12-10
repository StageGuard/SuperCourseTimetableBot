package stageguard.sctimetable.database.model

import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.Column
import stageguard.sctimetable.api.SuperCourseApiService.loginViaPassword

/**
 * Users存储所有用户的信息
 *
 * 它是一个object而不是class，因为每一个用户是一个表格内的项目。
 **/
object Users : IntIdTable() {
    /**
     * 用户QQ号
     **/
    val qq : Column<Long> = long("qq").uniqueIndex()
    /**
     * 学生ID
     **/
    val studentId : Column<Long> = long("studentId")
    /**
     * 学生在SC的昵称
     **/
    val name : Column<String> = varchar("name", 50)
    /**
     * 学生所在学校的ID
     **/
    val schoolId: Column<Int> = integer("schoolId")
    /**
     * 用户JSESSIONID，从[loginViaPassword]获取，方便下次同步课程
     **/
    val jSessionId: Column<String> = varchar("jSessionId", 100)
    /**
     * 用户用户SERVERID，从[loginViaPassword]获取，方便下次同步课程
     **/
    val serverId: Column<String> = varchar("serverId", 100)
}

/**
 * User DAO 是表示[Users]中每一个项目的抽象类
 **/
class User(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<User>(Users)
    var qq by Users.qq
    var studentId by Users.studentId
    var name by Users.name
    var schoolId by Users.schoolId
    var jSessionId by Users.jSessionId
    var serverId by Users.serverId
}

