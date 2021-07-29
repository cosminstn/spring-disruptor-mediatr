package tech.sharply.spring_disruptor_mediatr.mediator

import com.lmax.disruptor.EventFactory
import com.lmax.disruptor.EventHandler
import com.lmax.disruptor.EventTranslatorOneArg
import com.lmax.disruptor.dsl.Disruptor
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.util.concurrent.ThreadFactory
import java.util.function.Consumer
import javax.annotation.PostConstruct

interface Mediator {

    fun <TCommand : Command> dispatch(command: TCommand)

    fun <TCommand : Command> dispatchAsync(command: TCommand)

    fun <TCommand : Command> dispatchAsync(command: TCommand, callback: Consumer<TCommand>?)

}

@Component
class MediatorImpl(
    @Autowired
    private val commandRegistry: CommandRegistry
) : Mediator {

    companion object {
        val log: Logger = LoggerFactory.getLogger(MediatorImpl::class.java)
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
    override fun <TCommand : Command> dispatch(command: TCommand) {
        val handler = commandRegistry.getCommandHandler(command.javaClass)
            ?: throw IllegalArgumentException("No command handler found for command type: " + command.javaClass)

        handler.execute(CommandWrapper(command, null))
        println(command.toString() + " executed on thread: " + Thread.currentThread().id)
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
}