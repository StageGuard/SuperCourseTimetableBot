package stageguard.sctimetable

import kotlinx.coroutines.Job
import net.mamoe.mirai.console.plugin.jvm.JvmPluginDescription
import net.mamoe.mirai.console.plugin.jvm.KotlinPlugin
import net.mamoe.mirai.utils.info
import stageguard.sctimetable.api.SuperCourseApiService
import stageguard.sctimetable.database.Database
import stageguard.sctimetable.service.RequestHandlerService
import stageguard.sctimetable.service.TimeProviderService

object PluginMain : KotlinPlugin(
    JvmPluginDescription(
        id = "stageguard.sctimetable",
        version = "0.1.0",
        name = "SCTimetable"
    )
) {
    override fun onEnable() {
        PluginConfig.reload()
        Database.connect()
        TimeProviderService.start()
        RequestHandlerService.start()
        logger.info { "Plugin loaded" }
    }

    override fun onDisable() {
        SuperCourseApiService.closeHttpClient()
    }
}