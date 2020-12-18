@file:Suppress("unused")

package stageguard.sctimetable.service

import kotlinx.coroutines.*
import net.mamoe.mirai.Bot
import net.mamoe.mirai.contact.Friend
import net.mamoe.mirai.event.*
import net.mamoe.mirai.event.events.NewFriendRequestEvent
import net.mamoe.mirai.message.FriendMessageEvent
import net.mamoe.mirai.message.data.Message
import net.mamoe.mirai.message.data.PlainText
import net.mamoe.mirai.message.nextMessage
import net.mamoe.mirai.utils.info
import stageguard.sctimetable.AbstractPluginManagedService
import stageguard.sctimetable.PluginConfig
import stageguard.sctimetable.PluginData
import stageguard.sctimetable.PluginMain
import stageguard.sctimetable.api.LoginInfoData
import stageguard.sctimetable.database.Database
import stageguard.sctimetable.database.model.SchoolTimetable
import stageguard.sctimetable.database.model.SchoolTimetables
import stageguard.sctimetable.database.model.User
import stageguard.sctimetable.database.model.Users
import stageguard.sctimetable.utils.Either
import java.lang.Exception

object BotEventRouteService : AbstractPluginManagedService() {

    override val TAG: String = "BotEventRouteService"

    override suspend fun main() {
        subscribe<NewFriendRequestEvent> {
            if(this.bot.id == PluginConfig.qq) {
                this.accept()
                this@BotEventRouteService.launch(coroutineContext) {
                    delay(5000L)
                    PluginMain.targetBotInstance.friends[this@subscribe.fromId].sendMessage("""
                        欢迎使用 超级课表课程提醒机器人。
                        发送 "怎么用"/"帮助" 获取使用方法。
                    """.trimIndent())
                }
            }
            ListeningStatus.LISTENING
        }
        subscribeAlways<FriendMessageEvent> { if(this.bot.id == PluginConfig.qq) {
            val plainText = message[PlainText]?.content ?: ""
            when {
                plainText.matches(Regex("^登录超级(课程表|课表)")) -> {
                    verbose("capture 登录超级(课程表|课表)")
                    interactiveConversation {
                        send("请输入超级课表账号")
                        receive(timeoutLimit = 30000L)
                        send("请输入超级课表密码\n注意：若用手机验证码登录的超级课表，请先在超级课表app设置账号密码")
                        receive(timeoutLimit = 30000L)
                    }.finish(failed = {
                        when(it) {
                            is InteractiveConversationBuilder.QuitConversationExceptions.TimeoutException -> sender.sendMessage("太长时间未输入，请重新登录")
                            else -> {}
                        }
                    }) {
                        sendMessageNonBlock(sender.id, "正在登录。。。")
                        RequestHandlerService.sendRequest(Request.LoginRequest(sender.id, LoginInfoData(it[0], it[1])))
                    }
                }
                plainText.matches(Regex("^修改时间表")) -> Database.suspendQuery {
                    verbose("capture 修改时间表")
                    val thisUser = User.find { Users.qq eq sender.id }
                    val schoolTimetable = SchoolTimetable.find { SchoolTimetables.schoolId eq thisUser.first().schoolId }.first()
                    if(!thisUser.empty()) {
                        interactiveConversation {
                            send("""
                                确认要修改 ${schoolTimetable.schoolName} 的时间表吗
                                注意：修改时间表将会影响到本校所有用户，在修改前请确保时间表确实有误！
                                若你不知道当前时间表数据，发送"查看时间表"查看。
                                发送"确认"继续修改，否则取消修改。
                            """.trimIndent())
                            when(judge(timeoutLimit = 30000L)) {
                                "确认" -> {
                                    send("""
                                        要修改当前周数还是时间表？
                                        发送 "周数" 或 "时间表" 决定
                                    """.trimIndent())
                                    when(judge(tryLimit = 3, timeoutLimit = 30000L) { it == "周数" || it == "时间表" }) {
                                        "周数" -> {
                                            send("正在修改当前周数\n请输入一个数字表示当前周数")
                                            RequestHandlerService.sendRequest(Request.SyncSchoolWeekPeriodRequest(sender.id, receive(3) { it.toInt() > 0 }.toInt()))
                                        }
                                        "时间表" -> {
                                            send("正在修改当前时间表\n请输入一个数字表示当前周数")
                                        }
                                    }
                                }
                                else -> finish()
                            }
                        }.finish(failed = {
                            when(it) {
                                is InteractiveConversationBuilder.QuitConversationExceptions.IllegalInputException -> sender.sendMessage("输入格式有误次数，过多，请重新发送\"修改时间表\"")
                                is InteractiveConversationBuilder.QuitConversationExceptions.TimeoutException -> sender.sendMessage("长时间未输入，请重新发送\"修改时间表\"")
                                is InteractiveConversationBuilder.QuitConversationExceptions.AdvancedQuitException -> sender.sendMessage("取消修改时间表。")
                            }
                        })
                    } else {
                        sender.sendMessage("你还没有登录超级课表，无法修改时间表")
                    }
                }
                plainText.matches(Regex("^查看时间表")) -> launch(this@BotEventRouteService.coroutineContext) {
                    verbose("capture 查看时间表")
                    Database.suspendQuery {
                        val user = User.find { Users.qq eq sender.id }
                        if(!user.empty()) {
                            val schoolTimetable = SchoolTimetable.find { SchoolTimetables.schoolId eq user.first().schoolId }.first()
                            var index = 1
                            sender.sendMessage("${schoolTimetable.schoolName}\n" +
                                "当前是第 ${TimeProviderService.currentWeekPeriod[schoolTimetable.schoolId]} 周。\n" +
                                "时间表：\n" +
                                "${schoolTimetable.scheduledTimeList.split("|").joinToString("\n") { 
                                    "${index ++}. ${it.replace("-", " 到 ")}" 
                                }}\n" +
                            "如果以上数据有任何问题，请发送\"修改时间表\"修改。")
                        } else {
                            sender.sendMessage("你还没有登录超级课表，无法同步时间表")
                        }
                    }
                }
                plainText.matches(Regex("^今[日天]课[表程]")) -> launch(this@BotEventRouteService.coroutineContext) {
                    verbose("capture 今[日天]课[表程]")
                    Database.suspendQuery {
                        val user = User.find { Users.qq eq sender.id }
                        if(!user.empty()) {
                            val courses = ScheduleListenerService.getUserTodayCourses(sender.id, user.first().schoolId)
                            val schoolTimetable = ScheduleListenerService.getSchoolTimetable(user.first().schoolId)
                            var index = 1
                            sender.sendMessage(if(courses.isEmpty()) "今日没有课程。" else courses.joinToString("\n") {
                                "${index ++}. " + it.courseName + "(${schoolTimetable[it.startSection - 1].first.let { stamp ->
                                    "${(stamp - (stamp % 60)) / 60}:${(stamp % 60).let { min -> if(min < 10) ("0$min") else min }}"
                                }}到${schoolTimetable[it.endSection - 1].second.let { stamp ->
                                    "${(stamp - (stamp % 60)) / 60}:${(stamp % 60).let { min -> if(min < 10) ("0$min") else min }}"
                                }})在${it.locale}"
                            })
                        } else {
                            sender.sendMessage("你还没有登录超级课表，无法查看今天课程")
                        }
                    }
                }
                plainText.startsWith("删除用户") -> {
                    verbose("capture 删除用户")
                    interactiveConversation {
                        send("确认要删除你的所有信息吗，包括用户信息，你的课程信息？\n删除后将不再为你发送课程提醒。\n发送\"确认\"以删除。")
                        when(judge(timeoutLimit = 30000L)) {
                            "确认" -> RequestHandlerService.sendRequest(Request.DeleteCourseRequest(sender.id))
                            else -> send("取消删除用户。")
                        }
                    }.finish(failed = {
                        when(it) {
                            is InteractiveConversationBuilder.QuitConversationExceptions.TimeoutException -> sender.sendMessage("长时间未确认，请重新发送\"删除用户\"。")
                        }
                    })
                }
                plainText.startsWith("修改密码") -> launch(PluginMain.coroutineContext) {
                    verbose("capture 修改密码")
                    Database.suspendQuery {
                        val user = User.find { Users.qq eq sender.id }
                        if(!user.empty()) {
                            interactiveConversation {
                                send("请输入新的密码\n注意：只会存储在数据库中，不会验证新密码的正确性。")
                                receive(timeoutLimit = 30000L)
                            }.finish {
                                RequestHandlerService.sendRequest(Request.ChangeUserPasswordRequest(sender.id, it[0]))
                            }
                        } else sender.sendMessage("你还没有登录超级课表，无法修改密码")

                    }
                }
                plainText.startsWith("修改提前提醒时间") -> {
                    verbose("capture 修改提前提醒时间")
                    interactiveConversation {
                        send("你想在上课前多长时间收到课程提醒(单位：分钟)？\n当前为提前 ${PluginData.advancedTipOffset[sender.id] ?: PluginConfig.advancedTipTime} 分钟提醒。")
                        receive(tryLimit = 3, timeoutLimit = 30000L) { it.toInt() > 0 }
                    }.finish(failed = {
                        when(it) {
                            is InteractiveConversationBuilder.QuitConversationExceptions.TimeoutException -> sender.sendMessage("时间输入有误次数过多，请重新发送\"修改提前提醒时间\"。")
                        }
                    }) {
                        PluginData.advancedTipOffset[sender.id] = it[0].toInt()
                        ScheduleListenerService.restartUserNotification(sender.id)
                        sender.sendMessage("成功修改课程提醒时间为 ${PluginData.advancedTipOffset[sender.id]} 分钟前提醒。")
                    }
                }
                (plainText.startsWith("怎么用") || plainText.startsWith("帮助")) -> {
                    verbose("capture 帮助")
                    sender.sendMessage("""
                        欢迎使用 超级课表课程提醒QQ机器人。
                        它可以在你下一节课上课前提醒你这节课的信息，避免你错过课程。
                        指令：
                          "登录超级课表" - 使用密码登录你的超课表账户
                          "查看时间表" - 查看本校的作息时间表
                          "今日课程" - 查看你今天的所有课程信息
                          "怎么用/帮助" - 显示这条信息
                          "修改密码" - 修改记录在机器人数据库中的密码
                          "删除用户" - 删除你的记录在机器人数据库中的信息，并停止课程提醒服务。
                          "修改提前提醒时间" - 修改上课提前多长时间提醒
                       
                        注意：当前处于初代测试阶段，如使用过程中有任何问题，请联系机器人主人QQ: 1355416608
                    """.trimIndent())
                }
                plainText.startsWith("test interactive conversation") -> {
                    interactiveConversation {
                        send("input1")
                        receive(3) { it.toInt() in 1..10 }
                        send("input2")
                        receive()
                    }.finish(failed = {
                        sendMessageNonBlock(sender.id, "请重试！")
                    }, success = {
                        PluginMain.logger.info { it.toString() }
                    })
                }
            }
        }
            ListeningStatus.LISTENING
        }
        verbose("start listening FriendMessageEvent and NewFriendRequestEvent")
    }

    /**
     * 简易的交互式对话创建器。
     *
     * 在 [interactiveConversation] 中 launch 第一个协程用于阻塞式监听对话(通过 [nextMessage])
     *
     * 在 [finish] 中 launch 第二个协程用于阻塞式监听 [InteractiveConversationBuilder.receive] 的结果并返回List
     *
     * 直到 [interactiveConversation] 的 block 运行完成时获取结果。
     */
    private class InteractiveConversationBuilder(
        private val eventContext: FriendMessageEvent,
        private val timeoutLimitation: Long = -1L,
        private val tryCountLimitation: Int = -1
    ) {
        private val targetFriend: Friend = eventContext.friend
        private val bot: Bot = eventContext.bot
        val capturedList: MutableList<String> = mutableListOf()

        /**
         * 发送一条提示消息
         */
        suspend fun send(msg: String) { if (bot.isOnline) targetFriend.sendMessage(msg) }
        /**
         * 发送一条提示消息
         */
        suspend fun send(msg: Message) { if (bot.isOnline) targetFriend.sendMessage(msg) }
        /**
         * 阻塞式监听下一条消息，并存储到 [capturedList] 中，通过 [finish] 来获取。
         *
         * 如果 [checkBlock] 不为 ```null```，则会对消息进行判断，判断为 ```false``` 时会反复询问，直到符合要求。
         *
         * @param tryLimit 设置尝试最大次数，超过则会在 [finish] 中的 [failed] 返回 [IllegalInputException]
         * @param timeoutLimit 设置每次监听的最大限制，超时则会在 [finish] 中的 [failed] 返回 [TimeoutException]
         *
         * @return 返回监听到的消息。
         *
         * @see [judge]
         */
        suspend fun receive(
            tryLimit: Int = tryCountLimitation,
            timeoutLimit: Long = timeoutLimitation,
            checkBlock: ((String) -> Boolean)? = null
        ): String = judge(tryLimit, timeoutLimit, isReceive = true, checkBlock)
        suspend fun judge(
            tryLimit: Int = tryCountLimitation,
            timeoutLimit: Long = timeoutLimitation,
            isReceive: Boolean = false,
            checkBlock: ((String) -> Boolean)? = null
        ) : String {
            if(checkBlock == null) {
                (eventContext.nextMessage(timeoutLimit)[PlainText]?.content ?: "").also {
                    if(isReceive) capturedList.add(it)
                    return it
                }
            } else {
                if(tryLimit == -1) {
                    while (true) {
                        val plainText = (eventContext.nextMessage(timeoutLimit)[PlainText]?.content ?: "")
                        kotlin.runCatching {
                            if(checkBlock(plainText)) {
                                if(isReceive) capturedList.add(plainText)
                                return plainText
                            }
                        }
                        send("输入不符合要求，请重新输入！")
                    }
                } else {
                    repeat(tryLimit) {
                        val plainText = (eventContext.nextMessage(timeoutLimit)[PlainText]?.content ?: "")
                        kotlin.runCatching {
                            if(checkBlock(plainText)) {
                                if(isReceive) capturedList.add(plainText)
                                return plainText
                            }
                        }
                        if(it != tryLimit - 1) send("输入不符合要求，请重新输入！")
                    }
                    //会话直接结束
                    throw QuitConversationExceptions.IllegalInputException()
                }
            }
        }
        fun finish() { throw QuitConversationExceptions.AdvancedQuitException() }

        sealed class QuitConversationExceptions : Exception() {
            class AdvancedQuitException : QuitConversationExceptions()
            class IllegalInputException : QuitConversationExceptions()
            class TimeoutException : QuitConversationExceptions()
        }
    }

    /**
     * 交互式对话的创建器
     */
    private inline fun FriendMessageEvent.interactiveConversation(
        crossinline block: suspend InteractiveConversationBuilder.() -> Unit
    ): Deferred<Either<InteractiveConversationBuilder, InteractiveConversationBuilder.QuitConversationExceptions>> = async (coroutineContext) {
        try {
            InteractiveConversationBuilder(this@interactiveConversation).also {
                block(it)
            }.let { Either.Left(it) }
        } catch (ex: Exception) {
            when(ex) {
                is TimeoutCancellationException -> Either.Right(InteractiveConversationBuilder.QuitConversationExceptions.TimeoutException())
                else -> Either.Right(ex as InteractiveConversationBuilder.QuitConversationExceptions)
            }
        }
    }

    private fun Deferred<Either<InteractiveConversationBuilder, InteractiveConversationBuilder.QuitConversationExceptions>>.finish(
        failed: suspend (InteractiveConversationBuilder.QuitConversationExceptions) -> Unit = {  },
        success: suspend (List<String>) -> Unit = { }
    ) = launch(coroutineContext) {
        when(val icBuilder = this@finish.await()) {
            is Either.Left -> success(icBuilder.value.capturedList.toList())
            is Either.Right -> failed(icBuilder.value)
        }
    }

    fun sendMessageNonBlock(friendId: Long, msg: String) = launch(coroutineContext) {
        if(PluginMain.targetBotInstance.isOnline) PluginMain.targetBotInstance.friends[friendId].sendMessage(msg)
    }
}