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
import net.mamoe.mirai.message.data.*
import net.mamoe.mirai.message.nextMessage
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

/**
 * 交互式对话对象。
 *
 * 使用 [interactiveConversation] 创建一个交互式对话
 *
 * 限定 [receivePlain], [receive], [select] 和 [collect] 在这个对象中使用。
 *
 * @see interactiveConversation
 * @see receivePlain
 * @see receive
 * @see select
 * @see collect
 */
class InteractiveConversationBuilder(
    private val eventContext: MessageEvent,
    private val coroutineScope: CoroutineScope?,
    private val timeoutLimitation: Long,
    private val tryCountLimitation: Int,
    val conversationBlock: suspend InteractiveConversationBuilder.() -> Unit
) {
    private val target: Contact = eventContext.subject
    private val bot: Bot = eventContext.bot
    val capturedList: MutableMap<String, Any> = mutableMapOf()

    private val UNLIMITED_REPEAT: Boolean = tryCountLimitation == -1
    private val UNLIMITED_TIME: Boolean = timeoutLimitation == -1L

    /**
     * 发送一条文本消息
     */
    suspend fun send(msg: String) { if (bot.isOnline) target.sendMessage(msg) }
    /**
     * 发送一条消息(消息链)
     */
    suspend fun send(msg: Message) { if (bot.isOnline) target.sendMessage(msg) }
    /**
     * 发送一条消息(消息链拓展)
     */
    suspend fun send(block: MessageChainBuilder.() -> Unit) {
        send(buildMessageChain { block(this) })
    }

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
     * 阻塞对话过程来监听下一条消息包含指定类型的消息。
     *
     * [checkBlock] 会对消息链中每一个这个类型的消息进行判断。
     *
     * 当消息中不含有这个类型的消息或其中一个 [checkBlock] 为 `false` 时时会反复询问。
     *
     * 使用：
     * ```
     * interactiveConversation {
     *    //当消息中有为 Image 时存储。
     *    receive<Image>(key = "foo")
     *    //当消息中有 At 并且其中一个 At 的对象为 xxx 时存储。
     *    receive<At>(key = "bar") {
     *      it.target = xxx
     *    }
     * }
     * ```
     *
     * @param tryLimit 设置尝试最大次数。会覆盖在 [interactiveConversation] 中设置的 `eachTryLimit`。
     * @param timeoutLimit 设置每次监听的最大时长限制，会覆盖在 [interactiveConversation] 中设置的 `eachTimeLimit`。
     * @param key 键名称，不为 `null` 时会存储进捕获列表。
     *
     * @return 返回这个类型的消息的列表 [List]。
     *
     * @see [select]
     * @see [receivePlain]
     */
    suspend inline fun <reified SM : SingleMessage> receive(
        tryLimit: Int = -2,
        timeoutLimit: Long = -2L,
        key: String? = null,
        noinline checkBlock: ((SM) -> Boolean) = { true }
    ): List<SM> = recvImpl(tryLimit, timeoutLimit, { chain ->
        chain.any { it is SM } && run {
            var satisfied = false
            chain.filterIsInstance<SM>().forEach { satisfied = checkBlock(it) }
            satisfied
        }
    }, { chain -> chain.filterIsInstance<SM>() }).also { if(key != null) capturedList[key] = it }

    /**
     * 阻塞对话过程来监听下一条消息纯文本。
     *
     * [checkBlock] 会对整个文本消息进行判断。
     * 当不为纯文本消息或其中一个 [checkBlock] 判断为 `false` 或出现错误时会反复询问，直到符合要求。
     *
     * 使用：
     * ```
     * interactiveConversation {
     *    //只判断是否为纯文本消息
     *    receivePlain(key = "foo")
     *    //判断是否为文本消息且判断是否符合要求
     *    receivePlain(key = "bar") {
     *      Pattern.compile("...").matcher(it).find()
     *    }
     *    ///转换为 Int 进行判断。
     *    receivePlain(key = "baz") { it.toInt() > 0 }
     * }
     * ```
     *
     * @param tryLimit 设置尝试最大次数。会覆盖在 [interactiveConversation] 中设置的 `eachTryLimit`。
     * @param timeoutLimit 设置每次监听的最大时长限制，会覆盖在 [interactiveConversation] 中设置的 `eachTimeLimit`。
     * @param key 键名称，不为 `null` 时会存储进捕获列表。
     *
     * @return 返回监听到的消息的文本。
     *
     * @see [receive]
     * @see [select]
     */
    suspend inline fun receivePlain(
        tryLimit: Int = -2,
        timeoutLimit: Long = -2L,
        key: String? = null,
        noinline checkBlock: ((String) -> Boolean) = { true }
    ): String = recvImpl(tryLimit, timeoutLimit, {
        it.subList(1, it.size).let { single -> single.size == 1 && single[0] is PlainText && checkBlock(single[0].content) }
    }, { it.contentToString() }).also { if(key != null) capturedList[key] = it }

    /**
     * [receive] 和 [receivePlain] 的具体实现
     */
    suspend fun <R> recvImpl(
        tryLimit: Int = tryCountLimitation,
        timeoutLimit: Long = timeoutLimitation,
        checkBlock: (MessageChain) -> Boolean,
        mapBlock: (MessageChain) -> R
    ): R {
        val calculatedTryLimit = if(tryLimit == -2) (if(UNLIMITED_REPEAT) Int.MAX_VALUE else tryCountLimitation) else (if(tryLimit == -1) Int.MAX_VALUE else tryLimit)
        val calculatedTimeLimit = if(timeoutLimit == -2L) (if(UNLIMITED_TIME) Long.MAX_VALUE else timeoutLimitation) else (if(timeoutLimit == -1L) Long.MAX_VALUE else timeoutLimit)
        repeat(calculatedTryLimit) {
            val nextMsg = eventContext.nextMessage(calculatedTimeLimit)
            runCatching {
                if(checkBlock(nextMsg)) return mapBlock(nextMsg)
            }
            send("mismatched message.")
        }
        throw QuitConversationExceptions.IllegalInputException()
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
     * @see SelectionLambdaExpression.invoke
     * @see SelectionLambdaExpression.containRun
     * @see SelectionLambdaExpression.matchRun
     * @see SelectionLambdaExpression.has
     */
    suspend fun select(
        timeoutLimit: Long = timeoutLimitation,
        runBlock: suspend SelectionLambdaExpression.(MessageChain) -> Unit
    ) = SelectionLambdaExpression(recvImpl(tryCountLimitation, timeoutLimit, { true }) { it })(runBlock)

    @Suppress("DuplicatedCode")
    class SelectionLambdaExpression(
        private val chain: MessageChain
    ) {
        private var matches = false

        /**
         * 只有文本消息且消息为这个字符串时执行
         */
        suspend inline operator fun String.invoke(crossinline caseLambda: suspend (String) -> Unit) {
            when(val content = contentImpl({
                it.subList(1, it.size).let { single -> single.size == 1 && single[0] is PlainText && single[0].content == this }
            }) { it.contentToString() }) { is Either.Left -> caseLambda(content.value) }
        }

        /**
         * 只有文本消息且消息包含这个字符串时执行
         */
        suspend inline infix fun String.containRun(crossinline caseLambda: suspend (String) -> Unit) {
            when(val content = contentImpl({
                it.subList(1, it.size).let { single -> single.size == 1 && single[0] is PlainText && single[0].content.contains(this) }
            }) { it.contentToString() }) { is Either.Left -> {
                caseLambda(content.value)
            } }
        }

        /**
         * 只有文本消息且消息符合正则表达式时执行
         */
        suspend inline infix fun Regex.matchRun(crossinline caseLambda: suspend (String) -> Unit) {
            when(val content = contentImpl({
                it.subList(1, it.size).let { single -> single.size == 1 && single[0] is PlainText && matches(single[0].content) }
            }) { it.contentToString() }) { is Either.Left -> caseLambda(content.value) }
        }

        /**
         * 包含指定类型消息且满足条件时执行，lambda 参数为第一个满足条件的该类型消息
         */
        suspend inline fun <reified SM : SingleMessage> has(
            crossinline checkBlock: (SM) -> Boolean = { true },
            crossinline caseLambda: suspend (SM) -> Unit
        ) {
            when(val content = contentImpl({
                it.any { sm -> sm is SM } && run {
                    var satisfied = false
                    it.filterIsInstance<SM>().forEach { sm -> satisfied = checkBlock(sm) }
                    satisfied
                }
            }) { it.filterIsInstance<SM>().first(checkBlock) }) { is Either.Left -> caseLambda(content.value) }
        }

        /**
         * 条件的具体实现
         */
        fun <R> contentImpl(
            condition: (MessageChain) -> Boolean,
            mapBlock: (MessageChain) -> R
        ) : Either<R, Unit> = if(condition(chain)) {
            matches = true
            Either.Left(mapBlock(chain))
        } else Either.Right(Unit)

        /**
         * 当任何字符都不匹配时执行。
         *
         * 只有放到最底部才有效果。
         */
        suspend fun default(caseLambda: suspend () -> Unit) {
            if(!matches) caseLambda()
        }
        fun finish() { throw QuitConversationExceptions.AdvancedQuitException() }

        //For execute
        suspend operator fun invoke(runBlock: suspend SelectionLambdaExpression.(MessageChain) -> Unit) = runBlock(chain)
     }
    fun finish() { throw QuitConversationExceptions.AdvancedQuitException() }

    suspend operator fun invoke() = conversationBlock()
}

sealed class QuitConversationExceptions : Exception() {
    /**
     * 提前结束会话时抛出
     * @see InteractiveConversationBuilder.finish
     * @see InteractiveConversationBuilder.SelectionLambdaExpression.finish
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

typealias EIQ = Either<InteractiveConversationBuilder, QuitConversationExceptions>
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
 * @param scope 协程作用域，默认为 `null` 即阻塞当前协程。
 * @param eachTimeLimit 每一次捕获消息的最大等待时间，默认为 `-1L` 即无限等待。
 * 若超过了这个时间未捕获到适合的消息，则在 [exception] 中抛出 [QuitConversationExceptions.IllegalInputException]。
 * @param eachTryLimit 每一次捕获消息时允许尝试的最大次数。
 * 若超过了这个次数未捕获到适合的消息，则在 [exception] 中抛出 [QuitConversationExceptions.TimeoutException]。
 * @see InteractiveConversationBuilder.receive
 * @see InteractiveConversationBuilder.receivePlain
 * @see InteractiveConversationBuilder.select
 * @see InteractiveConversationBuilder.collect
 * @see InteractiveConversationBuilder.finish
 * @see finish
 * @see exception
 */
suspend fun <T : MessageEvent> T.interactiveConversation(
    scope: CoroutineScope? = null,
    eachTimeLimit: Long = -1L,
    eachTryLimit: Int = -1,
    block: suspend InteractiveConversationBuilder.() -> Unit
): Pair<CoroutineScope?, Either<EIQ, Deferred<EIQ>>> {
    suspend fun executeICB() = try {
        InteractiveConversationBuilder(
            eventContext = this@interactiveConversation,
            coroutineScope = scope,
            tryCountLimitation = eachTryLimit,
            timeoutLimitation = eachTimeLimit,
            conversationBlock = block
        ).also { it() }.let { Either.Left(it) }
    } catch (ex: Exception) { when(ex) {
        is TimeoutCancellationException -> Either.Right(QuitConversationExceptions.TimeoutException())
        else -> Either.Right(ex as QuitConversationExceptions)
    } }
    return if(scope == null) {
        null to Either.Left(executeICB())
    } else {
        scope to Either.Right(scope.async(scope.coroutineContext) { executeICB() })
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
suspend fun Pair<CoroutineScope?, Either<EIQ, Deferred<EIQ>>>.exception(
    failed: suspend (QuitConversationExceptions) -> Unit = {  }
) : Pair<CoroutineScope?, Either<EIQ, Deferred<EIQ>>> {
    if(first != null) {
        first!!.launch(first!!.coroutineContext) {
            when(val icBuilder = (second as Either.Right).value.await()) {
                is Either.Right -> failed(icBuilder.value)
            }
        }
    } else {
        when(val icBuilder = (second as Either.Left).value) {
            is Either.Right -> failed(icBuilder.value)
        }
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
 *    collect(key = "number2", receive { it.toInt() }.toInt())
 * }.finish {
 *    val number1 = it["number1"].cast<String>().toInt()
 *    val number2 = it["number2"].cast<Int>()
 * }
 * ```
 * 注意：[cast] 会忽略 `Map` 中的 `null` 检查！
 *
 */
suspend fun Pair<CoroutineScope?, Either<EIQ, Deferred<EIQ>>>.finish(
    success: suspend (Map<String, Any>) -> Unit = {  }
) : Pair<CoroutineScope?, Either<EIQ, Deferred<EIQ>>> {
    if(first != null) {
        first!!.launch(first!!.coroutineContext) {
            when(val icBuilder = (second as Either.Right).value.await()) {
                is Either.Left -> success(icBuilder.value.capturedList)
            }
        }
    } else {
        when(val icBuilder = (second as Either.Left).value) {
            is Either.Left -> success(icBuilder.value.capturedList)
        }
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