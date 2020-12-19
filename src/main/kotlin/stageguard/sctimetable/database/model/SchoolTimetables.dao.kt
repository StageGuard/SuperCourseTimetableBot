/*
 * Copyright 2020 KonnyakuCamp。
 *
 *  此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 *  Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 *  https://github.com/KonnyakuCamp/SuperCourseTimetableBot/blob/main/LICENSE
 */
package stageguard.sctimetable.database.model

import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.Column
import stageguard.sctimetable.service.TimeProviderService

/**
 * SchoolTimetables存储每个学校的某个学期的时间作息表
 *
 * 它是一个object而不是class，因为每一个用户是一个表格内的项目。
 **/
object SchoolTimetables : IntIdTable("schooltimetables") {
    /**
     * 学校ID
     **/
    val schoolId: Column<Int> = integer("schoolId").uniqueIndex()
    /**
     * 学校名称
     **/
    val schoolName: Column<String> = varchar("schoolName", 50)
    /**
     * 这个时间表的开始年份
     **/
    val beginYear: Column<Int> = integer("beginYear")
    /**
     * 这个时间表对应的学期
     **/
    val semester: Column<Int> = integer("semester")
    /**
     * SchoolTimetables存储每个学校的某个学期的时间作息表
     *
     * 它的格式是```section.startHour:startMinute-endHour:endMinute|...```
     *
     * 例如：```1.08:10-08:55|2.09:00-09:45|...```
     **/
    val scheduledTimeList: Column<String> = varchar("timeList", 200)
    /**
     * 以下两个属性用于推断当前是这个学期的第几个周。
     *
     * 通过这个学校添加到数据库的时间和第一个用户指定的当前是第几周来推算任意时间的周数
     *
     * 计算工作由[TimeProviderService]执行
     **/
    val timeStampWhenAdd: Column<String> = varchar("timeStampWhenAdd", 10)
    val weekPeriodWhenAdd: Column<Int> = integer("weekPeriodWhenAdd")
}

/**
 * SchoolTimetable DAO 是表示[SchoolTimetables]中每一个项目的抽象类
 **/
class SchoolTimetable(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<SchoolTimetable>(SchoolTimetables)
    var schoolId by SchoolTimetables.schoolId
    var schoolName by SchoolTimetables.schoolName
    var beginYear by SchoolTimetables.beginYear
    var semester by SchoolTimetables.semester
    var scheduledTimeList by SchoolTimetables.scheduledTimeList
    var timeStampWhenAdd by SchoolTimetables.timeStampWhenAdd
    var weekPeriodWhenAdd by SchoolTimetables.weekPeriodWhenAdd
}