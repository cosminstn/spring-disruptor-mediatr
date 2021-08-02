package tech.sharply.spring_disruptor_mediatr.mediator

import com.lmax.disruptor.EventFactory
import com.lmax.disruptor.EventHandler
import com.lmax.disruptor.EventTranslatorOneArg
import com.lmax.disruptor.dsl.Disruptor
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationContext
import java.util.concurrent.ThreadFactory
import java.util.function.BiConsumer
import javax.annotation.PostConstruct

interface Mediator {

    /**
     * Request handling
     */
    fun <TRequest : Request<TResponse>, TResponse> dispatchBlocking(request: TRequest): TResponse

    fun <TRequest : Request<TResponse>, TResponse> dispatchAsync(request: TRequest)

    fun <TRequest : Request<TResponse>, TResponse> dispatchAsync(
        request: TRequest,
        callback: BiConsumer<TRequest, TResponse>?
    )

}

/**
 * Mediator implementation that uses the same disruptor for both requests and commands.
 */
class MonoDisruptorMediatorImpl(
    context: ApplicationContext,
) : Mediator {

    companion object {
        private val log: Logger = LoggerFactory.getLogger(MonoDisruptorMediatorImpl::class.java)
    }

    private val registry = RegistryImpl(context)

    private val disruptor = Disruptor<RequestWrapper<Request<Any?>>>(
        EventFactory { RequestWrapper(null, null) },
        1024,
        ThreadFactory { r -> Thread(r) }
    )

    @PostConstruct
    private fun init() {
        disruptor.handleEventsWith(EventHandler { wrapper, _, _ ->
            if (wrapper.payload == null) {
                log.info("Null command")
                return@EventHandler
            }

            val request = wrapper.payload!!

            val result: Any?
            when (request) {
                is Command -> {
                    val handler = registry.getCommandHandler(request.javaClass)
                    if (handler == null) {
                        log.info("No command handler found for command type: " + request.javaClass)
                        return@EventHandler
                    }
                    result = handler.handle(request)
                }
                is Query -> {
                    val handler = registry.getQueryHandler(request.javaClass)
                    if (handler == null) {
                        log.info("No command handler found for command type: " + request.javaClass)
                        return@EventHandler
                    }
                    result = handler.handle(request)
                }
                else -> {
                    return@EventHandler
                }
            }

            wrapper.consumer?.accept(wrapper.payload!!, result)
            log.info("Consumer for request: " + wrapper.payload!! + " consumed on " + Thread.currentThread().id)
        })

        disruptor.start()
    }


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
        // find the handle and execute it on the current thread
        val handler = getRequestHandler(request)
            ?: throw IllegalArgumentException("No handler found for request type " + request.javaClass)
        log.info("Executing request $request blocking on thread: ${Thread.currentThread().id}")
        return handler.handle(request)
    }

    override fun <TRequest : Request<TResponse>, TResponse> dispatchAsync(request: TRequest) {
        dispatchAsync(request, null)
    }

    override fun <TRequest : Request<TResponse>, TResponse> dispatchAsync(
        request: TRequest,
        callback: BiConsumer<TRequest, TResponse>?
    ) {
        disruptor.publishEvent(getTranslator(callback), request)
    }

    private fun <TRequest : Request<TResponse>, TResponse> getTranslator(callback: BiConsumer<TRequest, TResponse>?):
            EventTranslatorOneArg<RequestWrapper<Request<Any?>>, TRequest> {
        return EventTranslatorOneArg<RequestWrapper<Request<Any?>>, TRequest> { wrapper, _, input ->
            if (wrapper == null) {
                return@EventTranslatorOneArg
            }

            wrapper.payload = input as Request<Any?>
            wrapper.consumer = callback as BiConsumer<Request<Any?>, Any?>?
        }
    }

}