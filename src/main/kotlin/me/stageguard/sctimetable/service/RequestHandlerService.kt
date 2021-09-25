/*
 * Copyright 2020-2021 StageGuard.
 *
 *  此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 *  Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 *  https://github.com/KonnyakuCamp/SuperCourseTimetableBot/blob/main/LICENSE
 */
package me.stageguard.sctimetable.service

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import me.stageguard.sctimetable.AbstractPluginManagedService

/**
 * RequestHandlerService 负责处理各种请求，
 *
 * 对于[handlerChannel]，它接收一个[IRequest]并通过它的具体类型来处理不同类型的请求
 **/
object RequestHandlerService : AbstractPluginManagedService(Dispatchers.IO) {

    override val TAG: String = "RequestHandlerService"

    private val handlerChannel = Channel<IRequest>(20) {
        warning("Request is not handled. request = $it")
    }

    override suspend fun main() {
        for(request in handlerChannel) {
            if(this@RequestHandlerService.isActive) {
                info("Handle Request: $request")
                with(request) { handle() }
            } else {
                handlerChannel.close(IllegalStateException("Plugin supervisor job is closed."))
            }
        }
    }
    fun sendRequest(request: IRequest) {
        launch(coroutineContext) {
            handlerChannel.send(request)
        }
    }
}

/**
 * Request是事件对象，并传输到[RequestHandlerService.handlerChannel]
 *
 * 可以根据根据不同的类型来解析不同的需求，避免创建多个[Channel]
 *
 * 不过这也意味着[RequestHandlerService.handlerChannel]将承载更多的工作，所以它的容量应该设置得大一点
 **/
interface IRequest {
    suspend fun RequestHandlerService.handle()
}