package stageguard.sctimetable

import net.mamoe.mirai.console.data.AutoSavePluginConfig
import net.mamoe.mirai.console.data.ValueDescription
import net.mamoe.mirai.console.data.value
import java.util.Calendar
import kotlinx.serialization.Serializable
import net.mamoe.yamlkt.Comment

object PluginConfig : AutoSavePluginConfig("SG.SCTimeTableBotConfig") {
    @ValueDescription("""
        当前年份，代表插件将要解析这个年份的课表.
        默认值：插件第一次运行时的年份.
    """)
    val beginYear by value(Calendar.getInstance().get(Calendar.YEAR))

    @ValueDescription("""
        当前学期，代表插件将要解析这个学期的课表.
        "1" 代表秋季学期，从当年9月到来年1月.
        "2" 代表夏季学期，从当年3月到当年7月.
        默认值：插件第一次运行时月份代表的学期.
    """)
    val term by value(when(Calendar.getInstance().get(Calendar.MONTH)) {
        1, 2, 3, 4, 5, 6 -> 2
        else -> 1
    })

    val database by value<DatabaseConfig>()
}

@Serializable
data class DatabaseConfig(
    @Comment("""
        数据库地址，支持MariaDB和MySQL数据库.
        默认值：localhost
    """)
    val address: String = "localhost",
    @Comment("""
        数据库登入用户.
        默认值：root
    """)
    val user: String = "root",
    @Comment("数据库登入密码")
    val password: String = "",
    @Comment("""
        数据库表单，表示要将用户数据存储在这个表单里
        默认值：sctimetabledb
    """)
    var table: String = "sctimetabledb",
    @Comment("最大连接数，我也不知道它能决定什么")
    var maximumPoolSize: Int? = 10
)