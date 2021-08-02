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
import javax.annotation.PostConstruct

interface Mediator {

    /**
     * Request handling
     */
    fun <TRequest : Request<TResponse>, TResponse> dispatchBlocking(request: TRequest): TResponse

    fun <TRequest : Request<TResponse>, TResponse> dispatchAsync(request: TRequest): CompletableFuture<TResponse>

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

    private val disruptor = Disruptor(
        EventFactory { CompletableRequestWrapper.empty() },
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

            val handler = getRequestHandler(request)
            if (handler == null) {
                log.info("No handler found for request type: " + request.javaClass)
                return@EventHandler
            }
            val result = handler.handle(request)

            wrapper.completableFuture.complete(result)
            log.info("Consumer for request: " + wrapper.payload!! + " consumed on " + Thread.currentThread().id)
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
        // find the handle and execute it on the current thread
        val handler = getRequestHandler(request)
            ?: throw IllegalArgumentException("No handler found for request type " + request.javaClass)
        log.info("Executing request $request blocking on thread: ${Thread.currentThread().id}")
        return handler.handle(request)
    }

    override fun <TRequest : Request<TResponse>, TResponse> dispatchAsync(request: TRequest): CompletableFuture<TResponse> {
        val future = CompletableFuture<TResponse>()
        disruptor.publishEvent(getTranslator(future), request)
        return future
    }

    @Suppress("UNCHECKED_CAST")
    private fun <TRequest : Request<TResponse>, TResponse> getTranslator(
        completableFuture: CompletableFuture<TResponse>?
    ):
            EventTranslatorOneArg<CompletableRequestWrapper<Request<Any?>, Any?>, TRequest> {
        return EventTranslatorOneArg<CompletableRequestWrapper<Request<Any?>, Any?>, TRequest> { wrapper, _, input ->
            if (wrapper == null) {
                return@EventTranslatorOneArg
            }

            wrapper.payload = input as Request<Any?>
            wrapper.completableFuture = completableFuture as CompletableFuture<Any?>? ?: CompletableFuture<Any?>()
        }
    }

    private class CompletableRequestWrapper<TRequest : Request<TResponse>, TResponse>(
        var payload: TRequest?,
        var completableFuture: CompletableFuture<TResponse> = CompletableFuture()
    ) {

        fun clear() {
            this.payload = null
            this.completableFuture = CompletableFuture<TResponse>()
        }

        companion object {
            fun empty(): CompletableRequestWrapper<Request<Any?>, Any?> {
                return CompletableRequestWrapper(null)
            }
        }

    }
}