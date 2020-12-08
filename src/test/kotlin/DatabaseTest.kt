package stageguard.sctimetable

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.Test
import stageguard.sctimetable.database.model.Courses
import stageguard.sctimetable.database.model.Users
import stageguard.sctimetable.database.model.User

class DatabaseTest {
    @Test
    fun run() {
        Database.connect(HikariDataSource(HikariConfig().apply {
            jdbcUrl         = "jdbc:mysql://localhost/sctimetabledb"
            driverClassName = "com.mysql.cj.jdbc.Driver"
            username        = "root"
            password        = "123123123"
            maximumPoolSize = 10
        }))
        transaction {
            addLogger(StdOutSqlLogger)
            //创建用户table
            SchemaUtils.create(Users)
            val user = User.new {
                qq = 1355416608
                studentId = 1234567890
                name = "myName"
                schoolName = "mySchool"
                schoolId = 12345
            }
            //为这个用户创建他的课程表table
            val myCourses = Courses("courses_${user.qq}_${user.name}")
            SchemaUtils.create(myCourses)
            myCourses.insert {
                it[courseName] = "XX课"
                it[courseId] = 114514
                it[teacherName] = "XXX讲师"
                it[locale] = "教学楼1"
                it[sectionEnd] = 8
                it[sectionStart] = 7
                it[whichDayOfWeek] = 2
                it[weekPeriod] = "1 2 3 4 5 6"
            }

        }
    }
}