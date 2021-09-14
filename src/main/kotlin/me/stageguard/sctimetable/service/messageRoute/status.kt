package me.stageguard.sctimetable.service.messageRoute

import com.sun.management.OperatingSystemMXBean
import me.stageguard.sctimetable.database.Database
import me.stageguard.sctimetable.database.model.User
import net.mamoe.mirai.event.events.FriendMessageEvent
import java.lang.management.ManagementFactory

suspend fun FriendMessageEvent.status() {
    Database.suspendQuery {
        val osMxBean: OperatingSystemMXBean =
            ManagementFactory.getOperatingSystemMXBean() as OperatingSystemMXBean
        subject.sendMessage(
            """
                    SuperCourseTimetable Plugin
                    Running on powerful Mirai Console

                    Status:
                    - Serving ${User.all().count()} users.
                    - System info: ${osMxBean.name} (${osMxBean.arch})
                    - Process(java) / System CPU load: ${
                String.format(
                    "%.2f",
                    osMxBean.processCpuLoad * 100
                )
            }% / ${String.format("%.2f", osMxBean.systemCpuLoad * 100)}%
                    - Memory usage / total: ${(osMxBean.totalPhysicalMemorySize - osMxBean.freePhysicalMemorySize) / 1024 / 1024}MB / ${osMxBean.totalPhysicalMemorySize / 1024 / 1024}MB

                    This project is a open source project.
                    You can visit https://github.com/KonnyakuCamp/SuperCourseTimetableBot for more details.
                """.trimIndent()
        )
    }
}