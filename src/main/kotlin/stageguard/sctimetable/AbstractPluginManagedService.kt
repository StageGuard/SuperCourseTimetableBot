package stageguard.sctimetable

import kotlinx.coroutines.*
import net.mamoe.mirai.utils.*
import net.mamoe.mirai.utils.debug
import kotlin.coroutines.CoroutineContext

/**
 * AbstractPluginManagedService 是受到[PluginMain]管理的协程服务
 **/
abstract class AbstractPluginManagedService(ctx: CoroutineContext? = null) : CoroutineScope {

    abstract val TAG: String
    final override val coroutineContext: CoroutineContext
        get() = SupervisorJob(PluginMain.coroutineContext.job)

    init {
        if(ctx != null) {
            coroutineContext.plus(ctx)
        }
    }

    protected abstract suspend fun main()

    @Suppress("NOTHING_TO_INLINE")
    inline fun warning(text: String) {
        PluginMain.logger.warning { "$TAG: $text" }
    }
    @Suppress("NOTHING_TO_INLINE")
    inline fun error(text: String) {
        PluginMain.logger.error { "$TAG: $text" }
    }
    @Suppress("NOTHING_TO_INLINE")
    inline fun info(text: String) {
        PluginMain.logger.info { "$TAG: $text" }
    }
    @Suppress("NOTHING_TO_INLINE")
    inline fun verbose(text: String) {
        PluginMain.logger.verbose { "$TAG: $text" }
    }

    fun start() : Job = this.launch(context = this.coroutineContext) { main() }
}