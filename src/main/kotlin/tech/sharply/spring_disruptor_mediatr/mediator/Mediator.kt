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
 * [Mediator] implementation that uses one disruptor for commands and requests and another one for events.
 * TODO: Use the same disruptor for both requests and events.
 */
class DisruptorMediatorImpl(
    context: ApplicationContext,
) : Mediator {

    companion object {
        private val log: Logger = LoggerFactory.getLogger(DisruptorMediatorImpl::class.java)
    }

    private val registry: Registry = RegistryImpl(context)

    private val disruptor = Disruptor(
        EventFactory { CompletableRequestWrapper.empty() },
        1024,
        ThreadFactory { r -> Thread(r) }
    )

    private val eventsDisruptor = Disruptor(
        EventFactory { EventWrapper.empty() },
        1024,
        ThreadFactory { r -> Thread(r) }
    )

    init {
        disruptor.handleEventsWith(EventHandler { wrapper, _, _ ->
            if (wrapper.payload == null) {
                wrapper.completableFuture.completeExceptionally(Exception("Null payload"))
                return@EventHandler
            }

            val request = wrapper.payload!!

            val handler = getRequestHandler(request)
            if (handler == null) {
                wrapper.completableFuture.completeExceptionally(Exception("No handler found for request: " + request.javaClass))
                return@EventHandler
            }
            try {
                val result = handler.handle(request)
                wrapper.completableFuture.complete(result)
            } catch (ex: Exception) {
                wrapper.completableFuture.completeExceptionally(ex)
            }

            log.info("Consumer for request: " + wrapper.payload!! + " consumed on " + Thread.currentThread().id)
        })

        disruptor.start()

        eventsDisruptor.handleEventsWith(EventHandler { wrapper, _, _ ->
            if (wrapper.payload == null) {
                return@EventHandler
            }

            val handlers = registry.getEventHandlers(wrapper.payload!!.javaClass)
            if (handlers.isEmpty()) {
                log.info("No handler found for request type: " + wrapper.payload!!.javaClass)
                return@EventHandler
            }

            for (handler in handlers) {
                handler.handle(wrapper.payload!!)
            }
            log.info("Handled event " + wrapper.payload!! + " on " + Thread.currentThread().id)
        })
        eventsDisruptor.start()
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
        disruptor.publishEvent(getTranslator(future), request)
        return future
    }

    override fun <TEvent : AppEvent> publishEvent(event: TEvent) {
        eventsDisruptor.publishEvent(getTranslator(), event)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <TRequest : Request<TResponse>, TResponse> getTranslator(
        completableFuture: CompletableFuture<TResponse>?
    ): EventTranslatorOneArg<CompletableRequestWrapper<Request<Any?>, Any?>, TRequest> {
        return EventTranslatorOneArg<CompletableRequestWrapper<Request<Any?>, Any?>, TRequest> { wrapper, _, input ->
            if (wrapper == null) {
                return@EventTranslatorOneArg
            }

            wrapper.payload = input as Request<Any?>
            wrapper.completableFuture = completableFuture as CompletableFuture<Any?>? ?: CompletableFuture<Any?>()
        }
    }

    private fun <TEvent : AppEvent> getTranslator(): EventTranslatorOneArg<EventWrapper<AppEvent>, TEvent> {
        return EventTranslatorOneArg<EventWrapper<AppEvent>, TEvent> { wrapper, _, input ->
            if (wrapper == null) {
                return@EventTranslatorOneArg
            }
            wrapper.payload = input as AppEvent
        }
    }

    private class CompletableRequestWrapper<TRequest : Request<TResponse>, TResponse>(
        var payload: TRequest?,
        var completableFuture: CompletableFuture<TResponse> = CompletableFuture()
    ) {

        companion object {
            fun empty(): CompletableRequestWrapper<Request<Any?>, Any?> {
                return CompletableRequestWrapper(null)
            }
        }

        override fun toString(): String {
            return "CompletableRequestWrapper(payload=$payload)"
        }
    }

    private class EventWrapper<T : AppEvent>(
        var payload: T?
    ) {

        companion object {
            fun empty(): EventWrapper<AppEvent> {
                return EventWrapper(null)
            }
        }

        override fun toString(): String {
            return "EventWrapper(payload=$payload)"
        }

    }

}

