package stageguard.sctimetable.database.model

import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.Column

/**
 * Users存储所有用户的信息
 *
 * 它是一个object而不是class，因为每一个用户是一个表格内的项目。
 **/
object Users : IntIdTable() {
    val qq : Column<Long> = long("qq").uniqueIndex()
    val studentId : Column<Long> = long("studentId")
    val name : Column<String> = varchar("name", 50)
    val schoolName: Column<String> = varchar("schoolName", 50)
    val schoolId: Column<Int> = integer("schoolId")
    val jSessionId: Column<String> = varchar("jSessionId", 100)
}

/**
 * User DAO 是表示[Users]中每一个项目的抽象类
 **/
class User(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<User>(Users)
    var qq by Users.qq
    var studentId by Users.studentId
    var name by Users.name
    var schoolName by Users.schoolName
    var schoolId by Users.schoolId
    var jSessionId by Users.jSessionId
}

