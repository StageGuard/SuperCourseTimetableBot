package stageguard.sctimetable.database.model

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Table

/**
 * Courses 存储一个用户的所有课程的列表。
 *
 * 会在目标数据库下建立一个新的[Table]来存储这个用户的课程表。
 *
 * 它是一个class而不是object，因为会有多个用户。
 * @param tableName table名称，一般表示为```用户的qq号```
 **/
class Courses(tableName: String) : Table(tableName) {
    /**
     * 课程ID
     **/
    var courseId: Column<Int> = integer("courseId")
    /**
     * 课程名称
     **/
    var courseName: Column<String> = varchar("courseName", 50)
    /**
     * 教师姓名
     **/
    var teacherName: Column<String> = varchar("teacherName", 50)
    /**
     * 地点
     **/
    var locale: Column<String> = varchar("locale", 50)
    /**
     * 这个课程在周几
     **/
    var whichDayOfWeek: Column<Int> = integer("whichDayOfWeek")
    /**
     * 第几节课开始
     **/
    var sectionStart: Column<Int> = integer("sectionStart")
    /**
     * 第几节课结束
     **/
    var sectionEnd: Column<Int> = integer("sectionEnd")
    /**
     * 时间计划(哪些周上这一节课)
     *
     * 它的格式是```week week week ...```
     *
     * 例如：```1 3 5 7 ...```表示这是单周课程
     **/
    var weekPeriod: Column<String> = varchar("weekPeriod", 100)
    /**
     * 这个时间表的开始年份
     **/
    val beginYear: Column<Int> = SchoolTimetables.integer("beginYear")
    /**
     * 这个时间表对应的学期
     **/
    val semester: Column<Int> = SchoolTimetables.integer("semester")
}