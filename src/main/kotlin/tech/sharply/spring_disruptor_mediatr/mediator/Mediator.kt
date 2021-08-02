package tech.sharply.spring_disruptor_mediatr.mediator

import com.lmax.disruptor.EventFactory
import com.lmax.disruptor.EventHandler
import com.lmax.disruptor.dsl.Disruptor
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationContext
import java.util.concurrent.ThreadFactory
import java.util.function.BiConsumer
import java.util.function.Consumer
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
    private val context: ApplicationContext,
) : Mediator {

    companion object {
        private val log: Logger = LoggerFactory.getLogger(MonoDisruptorMediatorImpl::class.java)
    }

    private val registry = RegistryImpl(context)

    private val disruptor = Disruptor<RequestWrapper<Request<*>>>(
        EventFactory { RequestWrapper(null, null) },
        1024,
        ThreadFactory { r -> Thread(r) }
    )

    @PostConstruct
    private fun init() {
        disruptor.handleEventsWith(EventHandler { wrapper, _, _ ->
            if (wrapper.payload == null) {
                println("Null command")
                return@EventHandler
            }

            val command = wrapper.payload!!

            var result: Any? = null
            if (command is Command) {
                val handler = registry.getCommandHandler(command.javaClass)
                if (handler == null) {
                    println("No command handler found for command type: " + command.javaClass)
                    return@EventHandler
                }
                result = handler.handle(command)
                println(command.toString() + " executed on thread: " + Thread.currentThread().id)
            } else if (command is CommandWithResult) {
                val handler = registry.getCommandHandler(command.javaClass)
                if (handler == null) {
                    println("No command handler found for command type: " + command.javaClass)
                    return@EventHandler
                }
                result = handler.handle(command)
            } else if (command is Query) {
                val handler = registry.getQueryHandler(command.javaClass)
                if (handler == null) {
                    println("No command handler found for command type: " + command.javaClass)
                    return@EventHandler
                }
                result = handler.handle(command)
            } else {
                return@EventHandler
            }

            wrapper.consumer?.accept(wrapper.payload!!, result)
        })

        disruptor.start()
    }

    override fun <TRequest : Request<TResponse>, TResponse> dispatchBlocking(request: TRequest): TResponse {

    }

    override fun <TRequest : Request<TResponse>, TResponse> dispatchAsync(request: TRequest) {
        TODO("Not yet implemented")
    }

    override fun <TRequest : Request<TResponse>, TResponse> dispatchAsync(
        request: TRequest,
        callback: BiConsumer<TRequest, TResponse>?
    ) {
        TODO("Not yet implemented")
    }


//    /**
//     * Dispatches the command with no callback.
//     */
//    override fun <TCommand : Command> dispatchAsync(command: TCommand) {
//        dispatchAsync(command, null)
//    }
//
//    /**
//     * Dispatches the command to the disruptor, calling the specified callback after the command has been executed.
//     */
//    override fun <TCommand : Command> dispatchAsync(command: TCommand, callback: Consumer<TCommand>?) {
//        val translator =
//            EventTranslatorOneArg<RequestWrapper<Command>, TCommand> { commandWrapper, sequence, input ->
//                if (commandWrapper == null) {
//                    return@EventTranslatorOneArg
//                }
//                commandWrapper.payload = input
//                commandWrapper.consumer = callback as Consumer<Command>?
//            }
//        disruptor.publishEvent(translator, command)
//    }

}