@file:Suppress("unused")

package stageguard.sctimetable.service

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import net.mamoe.mirai.utils.warning
import stageguard.sctimetable.PluginMain
import stageguard.sctimetable.api.CourseReceiptDTO
import kotlin.coroutines.CoroutineContext

object DatabaseSynchronizationService : CoroutineScope {

    override val coroutineContext: CoroutineContext
        get() = SupervisorJob(PluginMain.coroutineContext.job) + Dispatchers.IO

    private var isRunning: Boolean = false

    private val syncChannel = Channel<CourseReceiptDTO>(capacity = 50) {
        PluginMain.logger.warning { "Sync request is not delivered: ${it.data}" }
    }

    private fun main() {

    }

    fun startService() = this.launch(context = this.coroutineContext) { main() }
}