package stageguard.sctimetable

import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext

/**
 * AbstractPluginManagedService 是受到[PluginMain]管理的协程服务
 **/
abstract class AbstractPluginManagedService(ctx: CoroutineContext? = null) : CoroutineScope {
    final override val coroutineContext: CoroutineContext
        get() = SupervisorJob(PluginMain.coroutineContext.job)

    init {
        if(ctx != null) {
            coroutineContext.plus(ctx)
        }
    }

    protected abstract suspend fun main()
    fun start() : Job = this.launch(context = this.coroutineContext) { main() }
}