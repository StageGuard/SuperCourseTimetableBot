package stageguard.sctimetable.database.model

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Table

/**
 * Courses 存储一个用户的所有课程的列表。
 *
 * 会在目标数据库下建立一个新的[Table]来存储这个用户的课程表。
 *
 * 它是一个class而不是object，因为会有多个用户。
 * @param studentUniqueIdentity 这个学生的唯一标识(也就是表格名称)。
 **/
class Courses(studentUniqueIdentity: String) : Table(studentUniqueIdentity) {
    var courseId: Column<Int> = integer("courseId")
    var courseName: Column<String> = varchar("courseName", 50)
    var teacherName: Column<String> = varchar("teacherName", 50)
    var locale: Column<String> = varchar("locale", 50)
    var whichDayOfWeek: Column<Int> = integer("whichDayOfWeek")
    var sectionStart: Column<Int> = integer("sectionStart")
    var sectionEnd: Column<Int> = integer("sectionEnd")
    var weekPeriod: Column<String> = varchar("weekPeriod", 50)
}