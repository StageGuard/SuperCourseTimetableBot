/*
 * Copyright 2020-2021 StageGuard.
 *
 *  此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 *  Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 *  https://github.com/KonnyakuCamp/SuperCourseTimetableBot/blob/main/LICENSE
 */
package me.stageguard.sctimetable.service.messageRoute

import kotlinx.coroutines.CoroutineScope
import me.stageguard.sctimetable.service.RequestHandlerService
import me.stageguard.sctimetable.service.request.DeleteCourse
import me.stageguard.sctimetable.utils.cast
import me.stageguard.sctimetable.utils.exception
import me.stageguard.sctimetable.utils.finish
import me.stageguard.sctimetable.utils.interactiveConversation
import net.mamoe.mirai.event.events.FriendMessageEvent
import kotlin.contracts.ExperimentalContracts

@OptIn(ExperimentalContracts::class)
suspend fun FriendMessageEvent.deleteAccount(coroutineScope: CoroutineScope) {
    interactiveConversation(coroutineScope, eachTimeLimit = 10000L) {
        send("确认要删除你的所有信息吗，包括用户信息，你的课程信息？\n删除后将不再为你发送课程提醒。\n发送\"确认\"以删除。")
        select {
            "确认" { collect("delete", true) }
            default { collect("delete", false) }
        }
    }.finish {
        if (it["delete"].cast()) {
            RequestHandlerService.sendRequest(DeleteCourse(subject.id))
        }
    }.exception { subject.sendMessage("长时间未确认，已取消删除。\n如的确要删除，请重新发送\"删除用户\"。") }
}