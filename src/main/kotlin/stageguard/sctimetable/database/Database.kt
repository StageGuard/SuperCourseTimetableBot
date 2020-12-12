package stageguard.sctimetable.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import net.mamoe.mirai.utils.error
import net.mamoe.mirai.utils.info
import net.mamoe.mirai.utils.verbose
import net.mamoe.mirai.utils.warning
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
        try {
            db = Database.connect(hikariDataSourceProvider())
            connectionStatus = ConnectionStatus.CONNECTED
            PluginMain.logger.info { "Database ${PluginConfig.database.table} is connected." }
            initDatabase()
        } catch (ex: Exception) {
            when(ex) {
                //当配置文件的配置不符合要求时throw
                is InvalidDatabaseConfigException -> {
                    throw ex
                }
            }
        }
    }

    private fun initDatabase() { query {
        it.addLogger(object : SqlLogger {
            override fun log(context: StatementContext, transaction: Transaction) {
                PluginMain.logger.verbose { "SQL: ${context.expandArgs(transaction)}" }
            }
        })
        SchemaUtils.create(Users, SchoolTimetables)

    } }

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

