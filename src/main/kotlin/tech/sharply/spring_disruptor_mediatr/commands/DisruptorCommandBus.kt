package tech.sharply.spring_disruptor_mediatr.commands

import com.lmax.disruptor.EventFactory
import com.lmax.disruptor.EventHandler
import com.lmax.disruptor.EventTranslatorOneArg
import com.lmax.disruptor.dsl.Disruptor
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import org.springframework.core.GenericTypeResolver
import org.springframework.stereotype.Component
import java.util.concurrent.ThreadFactory
import javax.annotation.PostConstruct

@Component
class DisruptorCommandBus(
    @Autowired
    private val applicationContext: ApplicationContext
) {

    companion object {
        val log: Logger = LoggerFactory.getLogger(DisruptorCommandBus.javaClass)
    }

    private val commandHandlerRegistry = HashMap<Class<Any>, CommandHandler<Any>>()

    private val disruptor = Disruptor(
        EventFactory { Command<Any>() },
        1024,
        ThreadFactory { r -> Thread(r) }
    )

    private var initialized = false

    @PostConstruct
    private fun init() {
        disruptor.handleEventsWith(object : EventHandler<Command<out Any>> {
            override fun onEvent(command: Command<out Any>, sequence: Long, endOfBatch: Boolean) {
                val handler = getCommandHandler(command)
                if (handler == null) {
                    println("No command handler found for command type: " + command.javaClass)
                    return
                }
                handler.execute(command)
                println("Ran command $command")
            }
        })
    }


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

    private fun <T> getCommandHandler(clazz: Class<T>): CommandHandler<T>? {
        if (!this.initialized) {
            this.initializeHandlers()
        }
        return commandHandlerRegistry[clazz] as CommandHandler<T>?
    }

    private fun <T> getCommandHandler(command: Command<T>): CommandHandler<T>? {
        return getCommandHandler(command.getCommandClass())
    }

    fun <T> dispatch(command: Command<T>) {
        val handler = this.getCommandHandler(command.getCommandClass())
            ?: throw java.lang.IllegalArgumentException("No handler registered for " + command.javaClass.simpleName)
        return handler.execute(command)
    }

    fun <T> dispatchAsync(
        command: Command<T>,
        translator: EventTranslatorOneArg<Command<*>, Command<T>>
    ) {
//        TODO("Save the command into the ring buffer")
        disruptor.publishEvent(translator, command)
    }
}

open class Command<T> {
    protected var payload: T? = null
}

interface CommandHandler<T> {

    fun execute(command: Command<T>)

}

fun <T> Command<T>.getCommandClass(): Class<T> {
    return (GenericTypeResolver.resolveTypeArgument(javaClass, CommandHandler::class.java) as Class<T>?)!!
}

fun <T> CommandHandler<T>.getCommandClass(): Class<T> {
    return (GenericTypeResolver.resolveTypeArgument(javaClass, CommandHandler::class.java) as Class<T>?)!!
}