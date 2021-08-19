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
 * Allow specifying the execution group (thread pool) when dispatching requests.
 *
 * E.g.: If you want to dispatch a command C1 and a query Q1, and you want to know that they will not be handled
 * on the same thread you can call dispatchAsync(C1, 1), dispatchAsync(Q1, 2).
 *
 * This way you know for certain that their respective handling methods will be executed on separate threads.
 * Also, for each executor group there is only 1 thread assigned, so if you dispatch two requests with the same
 * **executorGroupId** then you can be sure they will be executed on the same thread.
 *
 * By default, the standard [DisruptorMediatorImpl] uses a single executor group, so all requests are
 * handled on the same thread.
 */
interface ExecutorGroupingMediator : Mediator {

    fun <TRequest : Request<TResponse>, TResponse> dispatchBlocking(
        request: TRequest,
        executorGroupId: Int
    ): TResponse

    override fun <TRequest : Request<TResponse>, TResponse> dispatchBlocking(request: TRequest): TResponse {
        return dispatchBlocking(request, 1)
    }

    fun <TRequest : Request<TResponse>, TResponse> dispatchAsync(
        request: TRequest,
        executorGroupId: Int
    ): CompletableFuture<TResponse>

    override fun <TRequest : Request<TResponse>, TResponse> dispatchAsync(request: TRequest): CompletableFuture<TResponse> {
        return dispatchAsync(request, 1)
    }

}

/**
 * Event handler that handles a category of events.
 * That category is specified by the [getHandledExecutionGroup] function.
 */
private interface IdentifiableDisruptorEventHandler<T> : EventHandler<T> {

    fun getHandledExecutionGroup(): String

}

/**
 * [Mediator] implementation that uses one disruptor to handle commands, queries and events.
 */
class DisruptorMediatorImpl(
    context: ApplicationContext,
    // This should be at least 1, otherwise it will throw an exception
    executorGroupsSize: Int = 1
) : ExecutorGroupingMediator {

    companion object {

        private val log: Logger = LoggerFactory.getLogger(DisruptorMediatorImpl::class.java)

        fun build(context: ApplicationContext): DisruptorMediatorImpl{
            return DisruptorMediatorImpl(context)
        }

        fun build(context: ApplicationContext, executorGroupsSize: Int): DisruptorMediatorImpl {
            return DisruptorMediatorImpl(context, executorGroupsSize)
        }

    }

    private val registry: Registry = RegistryImpl(context)

    private val disruptor = Disruptor(
        EventFactory { MessageWrapper.empty() },
        1024,
        ThreadFactory { r -> Thread(r) }
    )

    init {
        if (executorGroupsSize < 1) {
            throw IllegalArgumentException("executorGroupsSize must be >= 1")
        }
        /**
         * A single thread is assigned for each disruptor event handler.
         * If we want multiple threads to handle events then we have to implement multiple handlers or multiple
         * disruptors.
         */
        for (executorGroupId in 1..executorGroupsSize) {
            disruptor.handleEventsWith(EventHandler { wrapper, _, _ ->
                if (wrapper.executorGroupId != executorGroupId) {
                    return@EventHandler
                }

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
        }


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

    override fun <TRequest : Request<TResponse>, TResponse> dispatchBlocking(
        request: TRequest,
        executorGroupId: Int
    ): TResponse {
        return dispatchAsync(request, executorGroupId).get(1, TimeUnit.MINUTES)
    }

    override fun <TRequest : Request<TResponse>, TResponse> dispatchAsync(
        request: TRequest,
        executorGroupId: Int
    ): CompletableFuture<TResponse> {
        val future = CompletableFuture<TResponse>()
        disruptor.publishEvent(getRequestTranslator(future, executorGroupId), request)
        return future
    }

    override fun <TEvent : AppEvent> publishEvent(event: TEvent) {
        // TODO: Should we also give the possibility to dispatch events to different executor groups?
        disruptor.publishEvent(getEventTranslator(1), event)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <TRequest : Request<TResponse>, TResponse> getRequestTranslator(
        completableFuture: CompletableFuture<TResponse>?,
        executorGroupId: Int
    ): EventTranslatorOneArg<MessageWrapper<Request<Any?>, Any?, AppEvent>, TRequest> {
        return EventTranslatorOneArg<MessageWrapper<Request<Any?>, Any?, AppEvent>, TRequest> { wrapper, _, input ->
            if (wrapper == null) {
                return@EventTranslatorOneArg
            }

            wrapper.clear()

            wrapper.request = input as Request<Any?>
            wrapper.requestCompletableFuture =
                completableFuture as CompletableFuture<Any?>? ?: CompletableFuture<Any?>()
            wrapper.executorGroupId = executorGroupId
        }
    }

    private fun <TEvent : AppEvent> getEventTranslator(executorGroupId: Int): EventTranslatorOneArg<MessageWrapper<Request<Any?>, Any?, AppEvent>, TEvent> {
        return EventTranslatorOneArg<MessageWrapper<Request<Any?>, Any?, AppEvent>, TEvent> { wrapper, _, input ->
            if (wrapper == null) {
                return@EventTranslatorOneArg
            }

            wrapper.clear()

            wrapper.event = input as AppEvent
            wrapper.executorGroupId = executorGroupId
        }
    }

    private class MessageWrapper<TRequest : Request<TResponse>, TResponse, TEvent : AppEvent>(
        // request info
        var request: TRequest?,
        var requestCompletableFuture: CompletableFuture<TResponse> = CompletableFuture(),
        // event info
        var event: TEvent?,
        var executorGroupId: Int = 1
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
            this.executorGroupId = 1
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

