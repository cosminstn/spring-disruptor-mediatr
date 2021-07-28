package tech.sharply.spring_disruptor_mediatr.commands

import com.lmax.disruptor.EventFactory
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

    private val commandHandlerRegistry =  HashMap<Class<out Command>, CommandHandler<Command>>()

    private val commandFactory = EventFactory<Command> {
        object: Command {}
    }

    private val disruptor = Disruptor(commandFactory, 1024, ThreadFactory {
            r -> Thread(r)
    })

    fun <T : Command> run(command: T) {
        // find command handler

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
        val handler = applicationContext.getBean(name) as CommandHandler<*>

        val commandType = handler.getCommandClass()
        if (commandHandlerRegistry.containsKey(commandType)) {
            throw IllegalArgumentException(
                commandType.simpleName + " already has a registered handler. " +
                        "Each command must have a single command handler!"
            )
        }
        commandHandlerRegistry.put(commandType as Class<Command>, handler)
        log.info(
            ("Registered CommandHandler " + handler.getClass().getSimpleName()
                .toString() + " to handle Command " + commandType.simpleName)
        )
    }


}

interface Command

interface CommandHandler<T : Command> {

    fun execute(command: T)

}

fun <T: Command> CommandHandler<T>.getCommandClass(): Class<T> {
    return (GenericTypeResolver.resolveTypeArgument(javaClass, CommandHandler::class.java) as Class<T>?)!!
}