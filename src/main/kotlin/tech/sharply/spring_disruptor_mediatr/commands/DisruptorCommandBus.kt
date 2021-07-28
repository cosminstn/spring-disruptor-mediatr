package tech.sharply.spring_disruptor_mediatr.commands

import com.lmax.disruptor.EventFactory
import com.lmax.disruptor.EventTranslator
import com.lmax.disruptor.EventTranslatorOneArg
import com.lmax.disruptor.dsl.Disruptor
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import org.springframework.core.GenericTypeResolver
import org.springframework.stereotype.Component
import java.util.concurrent.ThreadFactory

@Component
class DisruptorCommandBus(
    @Autowired
    private val applicationContext: ApplicationContext
) {

    companion object {
        val log: Logger = LoggerFactory.getLogger(DisruptorCommandBus.javaClass)
    }

    private val commandHandlerRegistry = HashMap<Class<out Command>, CommandHandler<Command>>()

    private val disruptor = Disruptor(
        EventFactory<Command> { object : Command {} },
        1024,
        ThreadFactory { r -> Thread(r) }
    )

    private var initialized = false

    private fun initializeHandlers() {
        synchronized(this) {
            if (!initialized) {
//                val queryHandlers: Array<String> =
//                    applicationContext.getBeanNamesForType(RequestHandler::class.java)
//                for (handler: String? in queryHandlers) {
//                    registerQueryHandler(handler)
//                }
                val commandHandlers: Array<String> =
                    applicationContext.getBeanNamesForType(CommandHandler::class.java)
                for (handler: String in commandHandlers) {
                    registerCommandHandler(handler)
                }
                initialized = true
            }
        }
    }

    private fun registerCommandHandler(name: String) {
        log.debug("Registering CommandHandler with name $name")
        val handler = applicationContext.getBean(name) as CommandHandler<Command>

        val commandType = handler.getCommandClass()
        if (commandHandlerRegistry.containsKey(commandType)) {
            throw IllegalArgumentException(
                commandType.simpleName + " already has a registered handler. " +
                        "Each command must have a single command handler!"
            )
        }
        commandHandlerRegistry.put(commandType, handler)
        log.info(
            "Registered CommandHandler " + handler.javaClass.toString() + " to handle Command "
                    + commandType.simpleName
        )
    }

    private fun <TCommand : Command> getCommandHandler(clazz: Class<TCommand>): CommandHandler<TCommand>? {
        if (!this.initialized) {
            this.initializeHandlers()
        }
        return commandHandlerRegistry[clazz]
    }

    fun <TCommand : Command> dispatch(command: TCommand) {
        val handler = this.getCommandHandler(command.javaClass)
            ?: throw java.lang.IllegalArgumentException("No handler registered for " + command.javaClass.simpleName)
        return handler.execute(command)
    }

    fun <TCommand : Command> dispatchAsync(command: TCommand, translator: EventTranslatorOneArg<Command, TCommand>) {
//        TODO("Save the command into the ring buffer")
        disruptor.publishEvent(translator, command)
    }
}

interface Command

interface CommandHandler<in T : Command> {

    fun execute(command: T)

}

fun <T : Command> CommandHandler<T>.getCommandClass(): Class<T> {
    return (GenericTypeResolver.resolveTypeArgument(javaClass, CommandHandler::class.java) as Class<T>?)!!
}