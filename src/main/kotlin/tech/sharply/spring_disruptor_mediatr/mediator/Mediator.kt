package tech.sharply.spring_disruptor_mediatr.mediator

import com.lmax.disruptor.EventFactory
import com.lmax.disruptor.EventHandler
import com.lmax.disruptor.EventTranslatorOneArg
import com.lmax.disruptor.dsl.Disruptor
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationContext
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit

/**
 * The Mediator is the component that dispatches commands, queries and events to their respective handlers.
 * There are two types of requests: [Command] and [Query].
 */
interface Mediator {

    fun <TRequest : Request<TResponse>, TResponse> dispatchBlocking(request: TRequest): TResponse

    fun <TRequest : Request<TResponse>, TResponse> dispatchAsync(request: TRequest): CompletableFuture<TResponse>

    fun <TEvent : AppEvent> publishEvent(event: TEvent)

}

/**
 * [Mediator] implementation that uses the same disruptor commands, queries and events.
 */
class DisruptorMediatorImpl(
    context: ApplicationContext,
) : Mediator {

    companion object {
        private val log: Logger = LoggerFactory.getLogger(DisruptorMediatorImpl::class.java)
    }

    private val registry: Registry = RegistryImpl(context)

    private val disruptor = Disruptor(
        EventFactory { MessageWrapper.empty() },
        1024,
        ThreadFactory { r -> Thread(r) }
    )

    init {
        disruptor.handleEventsWith(EventHandler { wrapper, _, _ ->
            if (wrapper.isEmpty()) {
                wrapper.requestCompletableFuture.completeExceptionally(Exception("Null payload"))
                return@EventHandler
            }

            if (wrapper.isRequestWrapper()) {
                val request = wrapper.request!!
                val handler = getRequestHandler(request)
                if (handler == null) {
                    wrapper.requestCompletableFuture.completeExceptionally(Exception("No handler found for request: " + request.javaClass))
                    return@EventHandler
                }
                try {
                    val result = handler.handle(request)
                    wrapper.requestCompletableFuture.complete(result)
                } catch (ex: Exception) {
                    wrapper.requestCompletableFuture.completeExceptionally(ex)
                }

                log.info("Consumer for request: " + request + " consumed on " + Thread.currentThread().id)
            } else if (wrapper.isEventWrapper()) {
                val event = wrapper.event!!
                val handlers = registry.getEventHandlers(event.javaClass)
                if (handlers.isEmpty()) {
                    log.info("No handler found for request type: " + event.javaClass)
                    return@EventHandler
                }

                for (handler in handlers) {
                    try {
                        handler.handle(event)
                    } catch (ex: Exception) {
                        log.error("Could not handle event " + event + " in handler " + handler.javaClass.simpleName)
                    }
                }
                log.info("Handled event " + event + " on " + Thread.currentThread().id)
            }


        })

        disruptor.start()

    }

    @Suppress("UNCHECKED_CAST")
    private fun <TRequest : Request<TResponse>, TResponse> getRequestHandler(request: TRequest): RequestHandler<TRequest, TResponse>? {
        return when (request) {
            is Command<*> -> {
                registry.getCommandHandler((request as Command<*>).javaClass) as RequestHandler<TRequest, TResponse>
            }
            is Query<*> -> {
                registry.getQueryHandler((request as Query<*>).javaClass) as RequestHandler<TRequest, TResponse>
            }
            else -> {
                null
            }
        }
    }

    override fun <TRequest : Request<TResponse>, TResponse> dispatchBlocking(request: TRequest): TResponse {
        return dispatchAsync(request).get(1, TimeUnit.MINUTES)
    }

    override fun <TRequest : Request<TResponse>, TResponse> dispatchAsync(request: TRequest): CompletableFuture<TResponse> {
        val future = CompletableFuture<TResponse>()
        disruptor.publishEvent(getRequestTranslator(future), request)
        return future
    }

    override fun <TEvent : AppEvent> publishEvent(event: TEvent) {
        disruptor.publishEvent(getEventTranslator(), event)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <TRequest : Request<TResponse>, TResponse> getRequestTranslator(
        completableFuture: CompletableFuture<TResponse>?
    ): EventTranslatorOneArg<MessageWrapper<Request<Any?>, Any?, AppEvent>, TRequest> {
        return EventTranslatorOneArg<MessageWrapper<Request<Any?>, Any?, AppEvent>, TRequest> { wrapper, _, input ->
            if (wrapper == null) {
                return@EventTranslatorOneArg
            }

            wrapper.clear()

            wrapper.request = input as Request<Any?>
            wrapper.requestCompletableFuture =
                completableFuture as CompletableFuture<Any?>? ?: CompletableFuture<Any?>()
        }
    }

    private fun <TEvent : AppEvent> getEventTranslator(): EventTranslatorOneArg<MessageWrapper<Request<Any?>, Any?, AppEvent>, TEvent> {
        return EventTranslatorOneArg<MessageWrapper<Request<Any?>, Any?, AppEvent>, TEvent> { wrapper, _, input ->
            if (wrapper == null) {
                return@EventTranslatorOneArg
            }

            wrapper.clear()

            wrapper.event = input as AppEvent
        }
    }

    private class MessageWrapper<TRequest : Request<TResponse>, TResponse, TEvent : AppEvent>(
        // request info
        var request: TRequest?,
        var requestCompletableFuture: CompletableFuture<TResponse> = CompletableFuture(),
        // event info
        var event: TEvent?
    ) {

        companion object {
            fun empty(): MessageWrapper<Request<Any?>, Any?, AppEvent> {
                return MessageWrapper(request = null, event = null)
            }
        }

        fun clear() {
            this.request = null
            this.requestCompletableFuture = CompletableFuture()
            this.event = null
        }

        fun isEmpty(): Boolean {
            return request == null && event == null
        }

        fun isRequestWrapper(): Boolean {
            return request != null
        }

        fun isEventWrapper(): Boolean {
            return event != null
        }

        override fun toString(): String {
            return when {
                isEmpty() -> {
                    "EmptyWrapper"
                }
                isRequestWrapper() -> {
                    "CompletableRequestWrapper(payload=$request)"
                }
                isEventWrapper() -> {
                    "EventWrapper(event=$event)"
                }
                else -> "NullWrapper"
            }
        }
    }

}

