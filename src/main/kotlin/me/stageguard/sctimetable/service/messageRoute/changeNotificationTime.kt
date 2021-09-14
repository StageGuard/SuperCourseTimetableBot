package me.stageguard.sctimetable.service.messageRoute

import kotlinx.coroutines.CoroutineScope
import me.stageguard.sctimetable.PluginConfig
import me.stageguard.sctimetable.PluginData
import me.stageguard.sctimetable.service.ScheduleListenerService
import me.stageguard.sctimetable.utils.*
import net.mamoe.mirai.event.events.FriendMessageEvent
import kotlin.contracts.ExperimentalContracts

@OptIn(ExperimentalContracts::class)
suspend fun FriendMessageEvent.changeNotificationTime(coroutineScope: CoroutineScope) {
    interactiveConversation(coroutineScope, eachTimeLimit = 10000L) {
        send(
            """
                        你想在上课前多长时间收到课程提醒(单位：分钟)？
                        请输入一个数字代表你想要修改的时间。
                        当前为提前 ${PluginData.advancedTipOffset[subject.id] ?: PluginConfig.advancedTipTime} 分钟提醒。
                    """.trimIndent()
        )
        receivePlain(key = "advTipTime", tryLimit = 3) { it.toInt() > 0 }
    }.finish {
        PluginData.advancedTipOffset[subject.id] = it["advTipTime"].cast<String>().toInt()
        ScheduleListenerService.restartUserNotification(subject.id)
        subject.sendMessage("成功修改课程提醒时间为 ${PluginData.advancedTipOffset[subject.id]} 分钟前提醒。")
    }.exception {
        when (it) {
            is QuitConversationExceptions.TimeoutException -> subject.sendMessage("长时间未输入，请重新输入\"修改提醒时间\"。")
            is QuitConversationExceptions.IllegalInputException -> subject.sendMessage("格式输入有误次数过多，请重新输入\"修改提醒时间\"。")
            else -> {
            }
        }
    }
}