@file:Suppress("unused")

package stageguard.sctimetable.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import net.mamoe.mirai.utils.error
import net.mamoe.mirai.utils.warning
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import stageguard.sctimetable.PluginConfig
import stageguard.sctimetable.PluginMain
import stageguard.sctimetable.service.DatabaseSynchronizationService
import kotlin.coroutines.CoroutineContext

object Database {

    sealed class ConnectionStatus {
        object CONNECTED : ConnectionStatus()
        object DISCONNECTED : ConnectionStatus()
    }

    private lateinit var db : Database
    private var connectionStatus: ConnectionStatus = ConnectionStatus.DISCONNECTED

    fun dbQuery(block: (Transaction) -> Unit) {
        if(connectionStatus == ConnectionStatus.DISCONNECTED) {
            PluginMain.logger.error { "Database is disconnected and the query operation will not be completed." }
        } else {
            transaction(db) {
                block(this)
            }
        }
    }

    suspend fun DatabaseSynchronizationService.suspendDbQuery(block: suspend (Transaction) -> Unit) {
        if(connectionStatus == ConnectionStatus.DISCONNECTED) {
            PluginMain.logger.error { "Database is disconnected and the query operation will not be completed." }
        } else {
            newSuspendedTransaction(context = Dispatchers.IO, db = db) { block(this) }
        }
    }

    fun connect() {
        try {
            Database.connect(hikariDataSourceProvider()).let {
                db = it
            }
            connectionStatus = ConnectionStatus.CONNECTED
        } catch (ex: Exception) {
            when(ex) {
                //当配置文件的配置不符合要求时throw
                is InvalidDatabaseConfigException -> { TODO() }
            }
        }
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
    })

}

