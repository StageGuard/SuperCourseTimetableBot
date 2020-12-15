package stageguard.sctimetable

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import net.mamoe.mirai.Bot
import net.mamoe.mirai.console.plugin.jvm.JvmPluginDescription
import net.mamoe.mirai.console.plugin.jvm.KotlinPlugin
import net.mamoe.mirai.event.broadcast
import net.mamoe.mirai.event.events.BotOnlineEvent
import net.mamoe.mirai.event.subscribeAlways
import net.mamoe.mirai.utils.info
import org.quartz.Scheduler
import org.quartz.impl.StdSchedulerFactory
import stageguard.sctimetable.api.LoginInfoData
import stageguard.sctimetable.api.SuperCourseApiService
import stageguard.sctimetable.database.Database
import stageguard.sctimetable.service.Request
import stageguard.sctimetable.service.RequestHandlerService
import stageguard.sctimetable.service.ScheduleListenerService
import stageguard.sctimetable.service.TimeProviderService

object PluginMain : KotlinPlugin(
    JvmPluginDescription(
        id = "stageguard.sctimetable",
        version = "0.1.0",
        name = "SuperCourseTimetable"
    )
) {
    val quartzScheduler: Scheduler = StdSchedulerFactory.getDefaultScheduler().also { it.start() }
    var botInstance: Bot? = null

    override fun onEnable() {
        //Load plugin settings
        PluginConfig.reload()
        PluginData.reload()
        //Connect to database
        Database.connect()

        logger.info { "Waiting target Bot ${PluginConfig.qq} goes online..." }

        subscribeAlways<BotOnlineEvent> {
            if(this.bot.id == PluginConfig.qq) {
                botInstance = this.bot
                //Start services
                TimeProviderService.start()
                ScheduleListenerService.start()
                RequestHandlerService.start()
            }
        }

        logger.info { "Plugin loaded" }

        //PluginMain.logger.info { ScheduleListenerService.getSchoolTimetable(16008).toString() }
        /*runBlocking {
            delay(3000L)
            RequestHandlerService.sendRequest(Request.LoginRequest(1683070754L, LoginInfoData("18265090197", "xwh5201314")))
            //RequestHandlerService.sendRequest(RequestType.SyncCourseRequest(1355416608L)).
            delay(3000L)
            RequestHandlerService.sendRequest(Request.SyncCourseRequest(1683070754L))
            delay(3000L)
            RequestHandlerService.sendRequest(Request.SyncSchoolTimetableRequest(1683070754L))
            delay(3000L)
        }*/

    }

    override fun onDisable() {
        SuperCourseApiService.closeHttpClient()
    }
}