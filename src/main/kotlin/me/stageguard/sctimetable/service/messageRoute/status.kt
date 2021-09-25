/*
 * Copyright 2020-2021 StageGuard.
 *
 *  此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 *  Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 *  https://github.com/KonnyakuCamp/SuperCourseTimetableBot/blob/main/LICENSE
 */
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