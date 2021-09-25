/*
 * Copyright 2020-2021 StageGuard.
 *
 *  此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 *  Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 *  https://github.com/KonnyakuCamp/SuperCourseTimetableBot/blob/main/LICENSE
 */
package me.stageguard.sctimetable

import net.mamoe.mirai.console.data.AutoSavePluginConfig
import net.mamoe.mirai.console.data.value
import kotlinx.serialization.Serializable
import net.mamoe.mirai.console.data.ValueDescription

object PluginConfig : AutoSavePluginConfig("SG.SCTimeTableBotConfig") {
    @ValueDescription("""
        用于工作的BOT的QQ号
    """)
    val qq by value(123456789L)
    @ValueDescription("""
        默认提前多长时间提醒(单位：分钟)。
        此值会在用户第一次被添加进数据库时设置给这个用户。
        注意：如果你修改了这个值，在修改之前已经被设置的用户和自己设定值的用户不会受到影响。
    """)
    val advancedTipTime by value(15)
    val database by value<DatabaseConfig>()
}

@Serializable
data class DatabaseConfig(
    @ValueDescription("""
        数据库地址，支持MariaDB和MySQL数据库.
        默认值：localhost
    """)
    val address: String = "localhost",
    @ValueDescription("""
        数据库登入用户.
        默认值：root
    """)
    val user: String = "root",
    @ValueDescription("数据库登入密码")
    val password: String = "",
    @ValueDescription("""
        数据库表单，表示要将用户数据存储在这个表单里
        默认值：sctimetabledb
    """)
    var table: String = "sctimetabledb",
    @ValueDescription("最大连接数，我也不知道它能决定什么")
    var maximumPoolSize: Int? = 10
)