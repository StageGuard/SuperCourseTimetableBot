/*
 * Copyright 2020-2021 StageGuard.
 *
 *  此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 *  Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 *  https://github.com/KonnyakuCamp/SuperCourseTimetableBot/blob/main/LICENSE
 */
@file:Suppress("unused")

package me.stageguard.sctimetable.service

import kotlinx.coroutines.*
import net.mamoe.mirai.event.*
import me.stageguard.sctimetable.AbstractPluginManagedService
import me.stageguard.sctimetable.PluginConfig
import me.stageguard.sctimetable.PluginData
import me.stageguard.sctimetable.PluginMain
import me.stageguard.sctimetable.api.edu_system.`super`.LoginInfoData
import me.stageguard.sctimetable.database.Database
import java.lang.management.ManagementFactory
import com.sun.management.OperatingSystemMXBean
import net.mamoe.mirai.event.events.*
import net.mamoe.mirai.utils.MiraiExperimentalApi
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import me.stageguard.sctimetable.database.model.*
import me.stageguard.sctimetable.service.messageRoute.*
import me.stageguard.sctimetable.utils.*
import java.util.regex.Pattern
import kotlin.contracts.ExperimentalContracts

object BotEventRouteService : AbstractPluginManagedService() {

    override val TAG: String = "BotEventRouteService"

    @ExperimentalContracts
    @MiraiExperimentalApi
    override suspend fun main() {
        PluginMain.targetBotInstance.eventChannel.subscribeAlways<NewFriendRequestEvent> {
            if (this.bot.id == PluginConfig.qq) {
                this.accept()
                this@BotEventRouteService.launch(coroutineContext) {
                    delay(5000L)
                    PluginMain.targetBotInstance.friends[this@subscribeAlways.fromId]?.sendMessage(
                        """
                        欢迎使用 超级课表课程提醒机器人。
                        发送 "怎么用"/"帮助" 获取使用方法。
                    """.trimIndent()
                    )
                }
            }
        }
        PluginMain.targetBotInstance.eventChannel.subscribeFriendMessages {
            finding(Regex("^登[录陆]超级(课程表|课表)")) {
                login(this@BotEventRouteService)
            }
            startsWith("修改时间表") {
                changeTimetable(this@BotEventRouteService)
            }
            startsWith("同步课程") {
                syncCourse(this@BotEventRouteService)
            }
            startsWith("查看时间表") {
                queryTimetable(this@BotEventRouteService)
            }
            finding(Regex("^((?:星期|周)(.*)|([今明])[天日])课[表程]")) {
                queryCourse(it)
            }
            finding(Regex("^删除(用户|账户|账号)")) {
                deleteAccount(this@BotEventRouteService)
            }
            startsWith("修改密码") {
                changePassword(this@BotEventRouteService)
            }
            startsWith("修改提前提醒时间") {
                changeNotificationTime(this@BotEventRouteService)
            }
            (case("怎么用") or case("帮助")) {
                help()
            }
            case("状态") {
                status()
            }
        }
        verbose("start listening FriendMessageEvent and NewFriendRequestEvent")
    }

    fun sendMessageNonBlock(friendId: Long, msg: String, randomDelayMax: Long = 0L) = launch(coroutineContext) {
        if (PluginMain.targetBotInstance.isOnline) {
            delay((Math.random() * randomDelayMax).toLong())
            PluginMain.targetBotInstance.friends[friendId]?.sendMessage(msg)
        }
    }
}