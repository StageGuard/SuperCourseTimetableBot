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
import me.stageguard.sctimetable.database.Database
import me.stageguard.sctimetable.database.model.User
import me.stageguard.sctimetable.database.model.Users
import me.stageguard.sctimetable.service.RequestHandlerService
import me.stageguard.sctimetable.service.request.ChangeUserPassword
import me.stageguard.sctimetable.utils.cast
import me.stageguard.sctimetable.utils.exception
import me.stageguard.sctimetable.utils.finish
import me.stageguard.sctimetable.utils.interactiveConversation
import net.mamoe.mirai.event.events.FriendMessageEvent
import kotlin.contracts.ExperimentalContracts

@OptIn(ExperimentalContracts::class)
suspend fun FriendMessageEvent.changePassword(coroutineScope: CoroutineScope) {
    Database.suspendQuery {
        if (!User.find { Users.qq eq subject.id }.empty()) {
            interactiveConversation(coroutineScope, eachTimeLimit = 30000L) {
                send("请输入新的密码\n注意：只会存储在数据库中，不会验证新密码的正确性。")
                receivePlain(key = "password")
            }.finish {
                RequestHandlerService.sendRequest(
                    ChangeUserPassword(
                        subject.id,
                        it["password"].cast()
                    )
                )
            }.exception {
                subject.sendMessage("长时间未输入新的密码，请重新发送\"修改密码\"。")
            }
        } else subject.sendMessage("你还没有登录超级课表，无法修改密码。")
    }
}
