/*
 * Copyright 2020-2021 KonnyakuCamp.
 *
 *  此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 *  Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 *  https://github.com/KonnyakuCamp/SuperCourseTimetableBot/blob/main/LICENSE
 */
@file:Suppress("unused", "unchecked")
package stageguard.sctimetable.utils

import kotlinx.coroutines.*
import net.mamoe.mirai.Bot
import net.mamoe.mirai.console.util.cast
import net.mamoe.mirai.contact.Contact
import net.mamoe.mirai.event.events.MessageEvent
import net.mamoe.mirai.message.data.Message
import net.mamoe.mirai.message.data.PlainText
import net.mamoe.mirai.message.data.firstIsInstanceOrNull
import net.mamoe.mirai.message.nextMessage
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

/**
 * 交互式对话对象。
 *
 * 使用 [interactiveConversation] 创建一个交互式对话
 *
 * 限定 [judge], [receive], [select] 和 [collect] 在这个对象中使用。
 *
 * @see interactiveConversation
 * @see judge
 * @see receive
 * @see select
 * @see collect
 */
class InteractiveConversationBuilder(
    private val eventContext: MessageEvent,
    private val coroutineScope: CoroutineScope,
    private val timeoutLimitation: Long,
    private val tryCountLimitation: Int
) {
    private val target: Contact = eventContext.subject
    private val bot: Bot = eventContext.bot
    val capturedList: MutableMap<String, Any> = mutableMapOf()

    /**
     * 发送一条文本消息
     */
    suspend fun send(msg: String) { if (bot.isOnline) target.sendMessage(msg) }
    /**
     * 发送一条消息
     */
    suspend fun send(msg: Message) { if (bot.isOnline) target.sendMessage(msg) }

    /**
     * 将任何值存入捕获列表。
     *
     * 使用：
     * ```
     * interactiveConversation {
     *    val foo: Foo = xxx
     *    val bar: Bar = xxx
     *    collect("foo", foo)
     *    collect("bar", bar)
     * }.finish {
     *    val foo = it["foo"].cast<Foo>()
     *    val bar: Bar = it["bar"].cast()
     * }
     * ```
     * @param key 键名称
     */
    fun <T: Any> collect(key: String, any: T) {
        capturedList[key] = any
    }

    /**
     * 同 [receive] 阻塞对话过程来监听下一条消息，但不存入捕获列表。
     *
     * 通常用于判断消息。
     *
     * @return 返回监听到的消息。
     * @see receive
     */
    suspend fun judge(
        tryLimit: Int = tryCountLimitation,
        timeoutLimit: Long = timeoutLimitation,
        checkBlock: ((String) -> Boolean)? = null
    ) : String = receive(tryLimit, timeoutLimit, null, checkBlock)

    /**
     * 阻塞对话过程来监听下一条消息，并存储到捕获列表中。
     *
     * [checkBlock] 不为 `null`时会对消息进行判断，判断为 `false` 或出现错误时会反复询问，直到符合要求。
     *
     * 使用：
     * ```
     * interactiveConversation {
     *    //不进行判断，直接存储
     *    receive(key = "foo")
     *    //正则匹配
     *    receive(key = "bar") {
     *      Pattern.compile("...").matcher(it).find()
     *    }
     *    ///转换为 Int 进行判断。
     *    receive(key = "baz") { it.toInt() > 0 }
     * }
     * ```
     *
     * @param tryLimit 设置尝试最大次数。会覆盖在 [interactiveConversation] 中设置的 `eachTryLimit`。
     * @param timeoutLimit 设置每次监听的最大时长限制，会覆盖在 [interactiveConversation] 中设置的 `eachTimeLimit`。
     * @param key 键名称
     *
     * @return 返回监听到的消息。
     *
     * @see [judge]
     * @see [select]
     */
    suspend fun receive(
        tryLimit: Int = tryCountLimitation,
        timeoutLimit: Long = timeoutLimitation,
        key: String? = null,
        checkBlock: ((String) -> Boolean)? = null,
    ): String {
        if(checkBlock == null) {
            (eventContext.nextMessage(timeoutLimit).firstIsInstanceOrNull<PlainText>()?.content ?: "").also {
                if(key != null) capturedList[key] = it
                return it
            }
        } else {
            if(tryLimit == -1) {
                while (true) {
                    val plainText = eventContext.nextMessage(timeoutLimit).firstIsInstanceOrNull<PlainText>()?.content ?: ""
                    kotlin.runCatching {
                        if(checkBlock(plainText)) {
                            if(key != null) capturedList[key] = plainText
                            return plainText
                        }
                    }
                    send("输入不符合要求，请重新输入！")
                }
            } else {
                repeat(tryLimit) {
                    val plainText = eventContext.nextMessage(timeoutLimit).firstIsInstanceOrNull<PlainText>()?.content ?: ""
                    kotlin.runCatching {
                        if(checkBlock(plainText)) {
                            if(key != null) capturedList[key] = plainText
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

    /**
     * 阻塞对话过程来监听下一条消息，并进行类似 `when` 一样的选择判断
     *
     * 可以经过初步筛选后再判断，筛选实际上是委托给 [receive] 进行的。
     *
     * 使用：
     * ```
     * interactiveConversation {
     *    //无筛选条件监听
     *    select {
     *       //当消息为 foo 时执行
     *       "foo" { }
     *       //当消息为 bar 时执行
     *       "bar" { }
     *       //当上面任何都不匹配时执行
     *       default { }
     *    }
     *    //初步筛选后监听
     *    select({ it == "foo" || it == "bar" }) {
     *       //当消息为 foo 时执行
     *       "foo" { }
     *       //当消息为 bar 时执行
     *       "bar" { }
     *       //不必需要 default，因为经过初步筛选只有上述两种情况
     *    }
     * }
     * ```
     * @param key 当不为 `null` 时将监听到的消息存储进捕获列表。
     * @see receive
     */
    suspend fun select(
        checkBlock: ((String) -> Boolean)? = null,
        tryLimit: Int = tryCountLimitation,
        timeoutLimit: Long = timeoutLimitation,
        key: String? = null,
        runBlock: suspend SelectionLambdaExpression.(String) -> Unit
    ) = SelectionLambdaExpression(receive(tryLimit, timeoutLimit, key, checkBlock))(runBlock)

    class SelectionLambdaExpression (private val content: String) {
        private var matches = false

        /**
         * 在 [select] 中判断消息为这个字符串时执行
         */
        suspend operator fun String.invoke(caseLambda: suspend () -> Unit) {
            if(content == this) {
                matches = true
                caseLambda()
            }
        }

        /**
         * 当任何字符都不匹配时执行。
         *
         * 只有放到最底部才有效果。
         */
        suspend fun default(caseLambda: suspend () -> Unit) {
            if(!matches) caseLambda()
        }
        //For execute
        suspend operator fun invoke(runBlock: suspend SelectionLambdaExpression.(String) -> Unit) = runBlock(content)
    }

    /**
     * 提前结束会话。
     * 并在 [exception] 中抛出 [QuitConversationExceptions.AdvancedQuitException]
     */
    fun finish() { throw QuitConversationExceptions.AdvancedQuitException() }
}

sealed class QuitConversationExceptions : Exception() {
    /**
     * 提前结束会话时抛出
     * @see InteractiveConversationBuilder.finish
     */
    class AdvancedQuitException : QuitConversationExceptions()

    /**
     * 超过了尝试次数后未捕获到合适的消息时抛出
     */
    class IllegalInputException : QuitConversationExceptions()

    /**
     * 超过了时间未捕获到适合的消息时抛出
     */
    class TimeoutException : QuitConversationExceptions()
}

/**
 * 交互式对话的创建器
 *
 * 适用于任何继承 [MessageEvent] 的消息事件
 *
 * 使用：
 * ```
 * interactiveConversation { } //创建
 * .finish { } //正常结束
 * .exception { } //异常结束
 *
 * ```
 *
 * @param scope 协程作用域，默认为全局作用域 [GlobalScope]。
 * 建议制定为*插件作用域*或自定义协程作用域。
 * @param eachTimeLimit 每一次捕获消息的最大等待时间，默认为 `-1L` 即无限等待。
 * 若超过了这个时间未捕获到适合的消息，则在 [exception] 中抛出 [QuitConversationExceptions.IllegalInputException]。
 * @param eachTryLimit 每一次捕获消息时允许尝试的最大次数。
 * 若超过了这个次数未捕获到适合的消息，则在 [exception] 中抛出 [QuitConversationExceptions.TimeoutException]。
 * @see InteractiveConversationBuilder.receive
 * @see InteractiveConversationBuilder.judge
 * @see InteractiveConversationBuilder.select
 * @see InteractiveConversationBuilder.collect
 * @see InteractiveConversationBuilder.finish
 * @see finish
 * @see exception
 */
inline fun <T : MessageEvent> T.interactiveConversation(
    scope: CoroutineScope = GlobalScope,
    eachTimeLimit: Long = -1L,
    eachTryLimit: Int = -1,
    crossinline block: suspend InteractiveConversationBuilder.() -> Unit
): Pair<CoroutineScope, Deferred<Either<InteractiveConversationBuilder, QuitConversationExceptions>>> = scope to scope.async (
    scope.coroutineContext
) {
    try {
        InteractiveConversationBuilder(
            eventContext = this@interactiveConversation,
            coroutineScope = scope,
            tryCountLimitation = eachTryLimit,
            timeoutLimitation = eachTimeLimit
        ).also { block(it) }.let { Either.Left(it) }
    } catch (ex: Exception) {
        when(ex) {
            is TimeoutCancellationException -> Either.Right(QuitConversationExceptions.TimeoutException())
            else -> Either.Right(ex as QuitConversationExceptions)
        }
    }
}

/**
 * 会话非正常退出时调用这个函数
 * 处理方式：
 * ```
 * .exception { when(it) {
 *    is QuitConversationExceptions.TimeoutException -> {} //超时结束
 *    is QuitConversationExceptions.AdvancedQuitException -> {} //提前结束会话
 *    is QuitConversationExceptions.IllegalInputException -> {} //输入有误次数过多
 * } }
 * ```
 * 不必为 `when` 添加 `else` 分支，因为 [QuitConversationExceptions] 是一个 `sealed class`
 */
fun Pair<CoroutineScope, Deferred<Either<InteractiveConversationBuilder, QuitConversationExceptions>>>.exception(
    failed: suspend (QuitConversationExceptions) -> Unit = {  }
) : Pair<CoroutineScope, Deferred<Either<InteractiveConversationBuilder, QuitConversationExceptions>>> {
    first.launch(first.coroutineContext) {
        when(val icBuilder = this@exception.second.await()) { is Either.Right -> failed(icBuilder.value) }
    }
    return this
}

/**
 * 会话正常退出时调用这个函数。
 *
 * 在 [interactiveConversation] 中通过 [InteractiveConversationBuilder.receive], [InteractiveConversationBuilder.collect] 和 [InteractiveConversationBuilder.select] (指定 `key`) 的接收最终都会在这里捕获。
 *
 * 捕获结果是一个 [Map]，`Key` 为 `String` 类型， `Value` 为 `Any` 类型。
 *
 * 使用方式：
 * ```
 * .finish {
 *    //通过 `checkBlock` 筛选你希望捕获一个 Number。
 *    val number = it["key1"].cast<String>().toInt()
 *    //通过 `checkBlock` 筛选你希望捕获一个 String
 *    val str: String = it.cast()
 * }
 * ```
 * 使用 [cast] 做类型转换，即把 `Any` 类型转换为你需要的类型。
 *
 * 除了使用 [InteractiveConversationBuilder.collect] 捕获的，其他捕获存储于 `Map` 中的类型实际都为 `String` (因为消息都为文本消息)。
 *
 * 所以如果想捕获一个 `Number`，你可以这样：
 * ```
 * interactiveConversation {
 *    receive(key = "number1") { it.toInt() }
 *    collect(key = "number2", judge { it.toInt() }.toInt())
 * }.finish {
 *    val number1 = it["number1"].cast<String>().toInt()
 *    val number2 = it["number2"].cast<Int>()
 * }
 * ```
 * 注意：[cast] 会忽略 `Map` 中的 `null` 检查！
 *
 */
fun Pair<CoroutineScope, Deferred<Either<InteractiveConversationBuilder, QuitConversationExceptions>>>.finish(
    success: suspend (Map<String, Any>) -> Unit = {  }
) : Pair<CoroutineScope, Deferred<Either<InteractiveConversationBuilder, QuitConversationExceptions>>> {
    first.launch(first.coroutineContext) {
        when(val icBuilder = this@finish.second.await()) { is Either.Left -> success(icBuilder.value.capturedList.toMap()) }
    }
    return this
}

/**
 * Perform this as T.
 *
 * `a.cast<B>()` 相当于 `a as B`
 * @see cast
 */
@ExperimentalContracts
inline fun <reified T : Any> Any?.cast(): T {
    contract { returns() implies (this@cast is T) }
    return this as T
}