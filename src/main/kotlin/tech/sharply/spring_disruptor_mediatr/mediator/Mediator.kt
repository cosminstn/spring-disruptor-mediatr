package tech.sharply.spring_disruptor_mediatr.mediator

import com.lmax.disruptor.EventFactory
import com.lmax.disruptor.EventHandler
import com.lmax.disruptor.EventTranslatorOneArg
import com.lmax.disruptor.dsl.Disruptor
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.ThreadFactory
import java.util.function.BiConsumer
import java.util.function.Consumer
import javax.annotation.PostConstruct

interface Mediator {

    /**
     * Command Handling
     */
    fun <TCommand : Command> dispatchBlocking(command: TCommand)

    fun <TCommand : Command> dispatchAsync(command: TCommand)

    fun <TCommand : Command> dispatchAsync(command: TCommand, callback: Consumer<TCommand>?)

    /**
     * Request Handling
     */
    fun <TRequest : Request<TResponse>, TResponse> dispatchBlocking(request: TRequest): TResponse

    fun <TRequest : Request<TResponse>, TResponse> dispatchAsync(request: TRequest)

    fun <TRequest : Request<TResponse>, TResponse> dispatchAsync(request: TResponse,
                                                                 callback: BiConsumer<TRequest, TResponse>? )

}

/**
 * Mediator implementation that uses the same disruptor for both requests and commands.
 */
class MonoDisruptorMediatorImpl(
    private val commandRegistry: CommandRegistry,
    private val requestRegistry: RequestRegistry
) : Mediator {

    companion object {
        private val log: Logger = LoggerFactory.getLogger(MonoDisruptorMediatorImpl::class.java)
    }

    private val disruptor = Disruptor(
        EventFactory { CommandWrapper<Command>(null, null) },
        1024,
        ThreadFactory { r -> Thread(r) }
    )

    @PostConstruct
    private fun init() {
        disruptor.handleEventsWith(EventHandler { commandWrapper, sequence, endOfBatch ->
            if (commandWrapper.payload == null) {
                println("Null command")
                return@EventHandler
            }
            val command = commandWrapper.payload!!
            val handler = commandRegistry.getCommandHandler(command.javaClass)
            if (handler == null) {
                println("No command handler found for command type: " + commandWrapper.getCommandClass())
                return@EventHandler
            }
            handler.execute(commandWrapper)
            println(command.toString() + " executed on thread: " + Thread.currentThread().id)

            commandWrapper.callback?.accept(commandWrapper.payload!!)
        })

        disruptor.start()
    }

    /**
     * Dispatches the command sync, i.e. without using the disruptor.
     */
    override fun <TCommand : Command> dispatchBlocking(command: TCommand) {
        val handler = commandRegistry.getCommandHandler(command.javaClass)
            ?: throw IllegalArgumentException("No command handler found for command type: " + command.javaClass)

        handler.execute(CommandWrapper(command, null))
        println(command.toString() + " executed on thread: " + Thread.currentThread().id)
    }

    override fun <TRequest : Request<TResponse>, TResponse> dispatchBlocking(request: TRequest): TResponse {
        TODO("Not yet implemented")
    }

    /**
     * Dispatches the command with no callback.
     */
    override fun <TCommand : Command> dispatchAsync(command: TCommand) {
        dispatchAsync(command, null)
    }

    /**
     * Dispatches the command to the disruptor, calling the specified callback after the command has been executed.
     */
    override fun <TCommand : Command> dispatchAsync(command: TCommand, callback: Consumer<TCommand>?) {
        val translator =
            EventTranslatorOneArg<CommandWrapper<Command>, TCommand> { commandWrapper, sequence, input ->
                if (commandWrapper == null) {
                    return@EventTranslatorOneArg
                }
                commandWrapper.payload = input
                commandWrapper.callback = callback as Consumer<Command>?
            }
        disruptor.publishEvent(translator, command)
    }

    override fun <TRequest : Request<TResponse>, TResponse> dispatchAsync(request: TRequest) {
        TODO("Not yet implemented")
    }

    override fun <TRequest : Request<TResponse>, TResponse> dispatchAsync(
        request: TResponse,
        callback: BiConsumer<TRequest, TResponse>?
    ) {
        TODO("Not yet implemented")
    }
}