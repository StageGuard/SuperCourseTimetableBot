package stageguard.sctimetable

import net.mamoe.mirai.Bot
import net.mamoe.mirai.console.plugin.jvm.JvmPluginDescription
import net.mamoe.mirai.console.plugin.jvm.KotlinPlugin
import net.mamoe.mirai.event.ListeningStatus
import net.mamoe.mirai.event.events.BotOnlineEvent
import net.mamoe.mirai.event.subscribe
import net.mamoe.mirai.utils.info
import org.quartz.Scheduler
import org.quartz.impl.StdSchedulerFactory
import stageguard.sctimetable.api.SuperCourseApiService
import stageguard.sctimetable.database.Database
import stageguard.sctimetable.service.*

object PluginMain : KotlinPlugin(
    JvmPluginDescription(
        id = "stageguard.sctimetable",
        version = "0.1.0",
        name = "SuperCourseTimetable"
    )
) {
    val quartzScheduler: Scheduler = StdSchedulerFactory.getDefaultScheduler().also { it.start() }
    lateinit var targetBotInstance: Bot

    override fun onEnable() {
        PluginConfig.reload()
        PluginData.reload()

        logger.info { "Plugin loaded" }

        Database.connect()

        logger.info { "Waiting target Bot ${PluginConfig.qq} goes online..." }

        subscribe<BotOnlineEvent> {
            if(this.bot.id == PluginConfig.qq) {
                targetBotInstance = this.bot
                TimeProviderService.start()
                ScheduleListenerService.start()
                RequestHandlerService.start()
                BotEventRouteService.start()
                ListeningStatus.STOPPED
            } else ListeningStatus.LISTENING
        }
    }

    override fun onDisable() {
        SuperCourseApiService.closeHttpClient()
    }
}