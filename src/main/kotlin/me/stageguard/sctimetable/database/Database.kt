/*
 * Copyright 2020-2021 StageGuard.
 *
 *  此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 *  Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 *  https://github.com/KonnyakuCamp/SuperCourseTimetableBot/blob/main/LICENSE
 */
package me.stageguard.sctimetable.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import me.stageguard.sctimetable.*
import net.mamoe.mirai.utils.error
import net.mamoe.mirai.utils.info
import net.mamoe.mirai.utils.verbose
import net.mamoe.mirai.utils.warning
import okhttp3.internal.closeQuietly
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.statements.StatementContext
import org.jetbrains.exposed.sql.statements.expandArgs
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import me.stageguard.sctimetable.database.model.SchoolTimetables
import me.stageguard.sctimetable.database.model.Users
import net.mamoe.mirai.console.util.ConsoleExperimentalApi
import org.jetbrains.exposed.sql.transactions.TransactionManager
import java.sql.Connection

object Database {

    sealed class ConnectionStatus {
        object CONNECTED : ConnectionStatus()
        object DISCONNECTED : ConnectionStatus()
    }

    private lateinit var db : Database
    private var hikariSource: HikariDataSource? = null
    private var connectionStatus: ConnectionStatus = ConnectionStatus.DISCONNECTED

    fun <T> query(block: (Transaction) -> T) : T? = if(connectionStatus == ConnectionStatus.DISCONNECTED) {
        PluginMain.logger.error { "Database is disconnected and the query operation will not be completed." }
        null
    } else transaction(db) { block(this) }

    suspend fun <T> suspendQuery(block: suspend (Transaction) -> T) : T? = if(connectionStatus == ConnectionStatus.DISCONNECTED) {
        PluginMain.logger.error { "Database is disconnected and the query operation will not be completed." }
        null
    } else newSuspendedTransaction(context = Dispatchers.IO, db = db) { block(this) }

    fun connect() {
        db = when (PluginConfig.database) {
            "mysql" -> Database.connect(hikariDataSourceProvider(PluginConfig.mysqlConfig))
            "sqlite" -> {
                TransactionManager.manager.defaultIsolationLevel = Connection.TRANSACTION_SERIALIZABLE
                Database.connect("jdbc:sqlite:${PluginMain.dataFolder}/${PluginConfig.sqliteConfig.file}", "org.sqlite.JDBC")
            }
            else -> throw InvalidDatabaseConfigException("未知的数据库类型或数据库配置未找到：${PluginConfig.database}")
        }
        connectionStatus = ConnectionStatus.CONNECTED
        PluginMain.logger.info { "Database is connected." }
        initDatabase()
    }

    fun isConnected() = connectionStatus == ConnectionStatus.CONNECTED

    private fun initDatabase() { query {
        it.addLogger(object : SqlLogger {
            override fun log(context: StatementContext, transaction: Transaction) {
                PluginMain.logger.verbose { "SQL: ${context.expandArgs(transaction)}" }
            }
        })
        SchemaUtils.create(Users, SchoolTimetables)

    } }

    fun close() {
        connectionStatus = ConnectionStatus.DISCONNECTED
        hikariSource?.closeQuietly()
    }

    @OptIn(ConsoleExperimentalApi::class)
    private fun hikariDataSourceProvider(config: MySQLorMariaDB) : HikariDataSource = HikariDataSource(HikariConfig().apply {
        when {
            config.address == "" -> throw InvalidDatabaseConfigException("Database address is not set in config file ${PluginConfig.saveName}.")
            config.table == "" -> {
                PluginMain.logger.warning { "Database table is not set in config file ${PluginConfig.saveName} and now it will be default value 'sctimetabledb'." }
                config.table = "sctimetabledb"
            }
            config.user == "" -> throw InvalidDatabaseConfigException("Database user is not set in config file ${PluginConfig.saveName}.")
            config.password == "" -> throw InvalidDatabaseConfigException("Database password is not set in config file ${PluginConfig.saveName}.")
            config.maximumPoolSize == null -> {
                PluginMain.logger.warning { "Database maximumPoolSize is not set in config file ${PluginConfig.saveName} and now it will be default value 10." }
                config.maximumPoolSize = 10
            }
        }
        jdbcUrl         = "jdbc:mysql://${config.address}/${config.table}"
        driverClassName = "com.mysql.cj.jdbc.Driver"
        username        = config.user
        password        = config.password
        maximumPoolSize = config.maximumPoolSize!!
        poolName        = "SCTimetableDB Pool"
    })

}

