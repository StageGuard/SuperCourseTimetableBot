/*
 * Copyright 2020-2021 KonnyakuCamp.
 *
 *  此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 *  Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 *  https://github.com/KonnyakuCamp/SuperCourseTimetableBot/blob/main/LICENSE
 */
package stageguard.sctimetable.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
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
import stageguard.sctimetable.PluginConfig
import stageguard.sctimetable.PluginMain
import stageguard.sctimetable.database.model.SchoolTimetables
import stageguard.sctimetable.database.model.Users

object Database {

    sealed class ConnectionStatus {
        object CONNECTED : ConnectionStatus()
        object DISCONNECTED : ConnectionStatus()
    }

    private lateinit var db : Database
    private lateinit var hikariSource: HikariDataSource
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
        db = Database.connect(hikariDataSourceProvider().also {
            hikariSource = it
        })
        connectionStatus = ConnectionStatus.CONNECTED
        PluginMain.logger.info { "Database ${PluginConfig.database.table} is connected." }
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
        hikariSource.closeQuietly()
    }

    private fun hikariDataSourceProvider() : HikariDataSource = HikariDataSource(HikariConfig().apply {
        when {
            PluginConfig.database.address == "" -> throw InvalidDatabaseConfigException("Database address is not set in config file ${PluginConfig.saveName}.")
            PluginConfig.database.table == "" -> {
                PluginMain.logger.warning { "Database table is not set in config file ${PluginConfig.saveName} and now it will be default value 'sctimetabledb'." }
                PluginConfig.database.table = "sctimetabledb"
            }
            PluginConfig.database.user == "" -> throw InvalidDatabaseConfigException("Database user is not set in config file ${PluginConfig.saveName}.")
            PluginConfig.database.password == "" -> throw InvalidDatabaseConfigException("Database password is not set in config file ${PluginConfig.saveName}.")
            PluginConfig.database.maximumPoolSize == null -> {
                PluginMain.logger.warning { "Database maximumPoolSize is not set in config file ${PluginConfig.saveName} and now it will be default value 10." }
                PluginConfig.database.maximumPoolSize = 10
            }
        }
        jdbcUrl         = "jdbc:mysql://${PluginConfig.database.address}/${PluginConfig.database.table}"
        driverClassName = "com.mysql.cj.jdbc.Driver"
        username        = PluginConfig.database.user
        password        = PluginConfig.database.password
        maximumPoolSize = PluginConfig.database.maximumPoolSize!!
        poolName        = "SCTimetableDB Pool"
    })

}

