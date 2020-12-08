@file:Suppress("unused")

package stageguard.sctimetable

import net.mamoe.mirai.console.command.CommandManager.INSTANCE.register
import net.mamoe.mirai.console.command.CommandSender
import net.mamoe.mirai.console.command.CompositeCommand
import net.mamoe.mirai.console.plugin.jvm.JvmPluginDescription
import net.mamoe.mirai.console.plugin.jvm.KotlinPlugin
import net.mamoe.mirai.console.util.cast
import net.mamoe.mirai.event.events.GroupEvent
import net.mamoe.mirai.event.subscribeAlways
import net.mamoe.mirai.message.MessageEvent
import net.mamoe.mirai.message.data.Image
import net.mamoe.mirai.message.data.content
import net.mamoe.mirai.utils.info
import stageguard.sctimetable.api.SuperCourseApiService

object PluginMain : KotlinPlugin(
    JvmPluginDescription(
        id = "stageguard.sctimetable",
        version = "0.1.0",
        name = "SCTimetable"
    )
) {
    override fun onEnable() {
        PluginConfig.reload()
        SCCompositeCommand.register()
        logger.info { "Plugin loaded" }

    }

    override fun onDisable() {
        SuperCourseApiService.closeHttpClient()
    }
}

object SCCompositeCommand : CompositeCommand(
    PluginMain,
    "bind",
    //description = "Bind your account."
) {
    @SubCommand
    suspend fun CommandSender.bind(scAccount: Long, scPassword: String) {

    }
}