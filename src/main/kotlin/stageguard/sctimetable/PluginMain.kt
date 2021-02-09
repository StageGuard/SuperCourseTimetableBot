/*
 * Copyright 2020-2021 KonnyakuCamp.
 *
 *  此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 *  Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 *  https://github.com/KonnyakuCamp/SuperCourseTimetableBot/blob/main/LICENSE
 */
package stageguard.sctimetable

import net.mamoe.mirai.Bot
import net.mamoe.mirai.console.plugin.jvm.JvmPluginDescription
import net.mamoe.mirai.console.plugin.jvm.KotlinPlugin
import net.mamoe.mirai.event.GlobalEventChannel
import net.mamoe.mirai.event.events.BotOnlineEvent
import net.mamoe.mirai.utils.MiraiExperimentalApi
import net.mamoe.mirai.utils.error
import net.mamoe.mirai.utils.info
import org.quartz.Scheduler
import org.quartz.impl.StdSchedulerFactory
import stageguard.sctimetable.api.edu_system.`super`.SuperCourseApiService
import stageguard.sctimetable.database.Database
import stageguard.sctimetable.service.*

object PluginMain : KotlinPlugin(
    JvmPluginDescription(
        id = "stageguard.sctimetable",
        version = "0.3.5",
        name = "SuperCourseTimetable"
    )
) {
    val quartzScheduler: Scheduler = StdSchedulerFactory.getDefaultScheduler().also { it.start() }
    lateinit var targetBotInstance: Bot

    @MiraiExperimentalApi
    override fun onEnable() {
        PluginConfig.reload()
        PluginData.reload()

        logger.info { "Plugin loaded" }

        Database.connect()

        if (Database.isConnected()) {
            logger.info { "Waiting target Bot ${PluginConfig.qq} goes online..." }

            GlobalEventChannel.filter {
                it is BotOnlineEvent && it.bot.id == PluginConfig.qq
            }.subscribeOnce<BotOnlineEvent> {
                targetBotInstance = this.bot
                TimeProviderService.start()
                ScheduleListenerService.start()
                RequestHandlerService.start()
                BotEventRouteService.start()
            }
        } else PluginMain.logger.error("Database is not connected, services will not start.")
    }

    override fun onDisable() {
        SuperCourseApiService.closeHttpClient()
    }
}