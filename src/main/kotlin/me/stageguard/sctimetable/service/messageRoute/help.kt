/*
 * Copyright 2020-2021 StageGuard.
 *
 *  此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 *  Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 *  https://github.com/KonnyakuCamp/SuperCourseTimetableBot/blob/main/LICENSE
 */
package me.stageguard.sctimetable.service.messageRoute

import net.mamoe.mirai.event.events.FriendMessageEvent

suspend fun FriendMessageEvent.help() {
    subject.sendMessage(
        """
                    欢迎使用 超级课表课程提醒QQ机器人。
                    它可以在你下一节课上课前提醒你这节课的信息，避免你错过课程。
                    指令：
                      "登录超级课表" - 使用密码登录你的超课表账户
                      "查看时间表" - 查看本校的作息时间表
                      "修改时间表" - 修改本校的作息时间表
                      "同步课程" - 手动从超级课表同步你的课程
                      "今(明)日课程" - 查看你今(明)天的所有课程信息
                      "周X课程" - 查看你周X的所有课程信息
                      "怎么用/帮助" - 显示这条信息
                      "修改密码" - 修改记录在机器人数据库中的密码
                      "删除用户" - 删除你的记录在机器人数据库中的信息，并停止课程提醒服务。
                      "修改提前提醒时间" - 修改上课提前多长时间提醒
                      "状态" - 查看超级课表课程提醒QQ机器人的运行情况
                   
                    注意：当前处于初代测试阶段。
                """.trimIndent()
    )
}