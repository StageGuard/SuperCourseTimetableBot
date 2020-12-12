package stageguard.sctimetable.service

import kotlinx.coroutines.Dispatchers
import stageguard.sctimetable.AbstractPluginManagedService

object CourseListenerService : AbstractPluginManagedService(Dispatchers.IO) {
    override suspend fun main() {

    }
}