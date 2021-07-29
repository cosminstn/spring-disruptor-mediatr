package tech.sharply.spring_disruptor_mediatr.mediator

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import org.springframework.core.GenericTypeResolver
import org.springframework.stereotype.Component
import java.util.function.Consumer

interface CommandRegistry {

    fun <TCommand : Command> getCommandHandler(commandClass: Class<TCommand>): CommandHandler<TCommand>?

}

@Component
class CommandRegistryImpl(
    @Autowired
    private val context: ApplicationContext
) : CommandRegistry {

    private val commandRegistry: MutableMap<Class<Command>, CommandHandler<Command>> = HashMap()
    private var initialized = false

    override fun <TCommand : Command> getCommandHandler(commandClass: Class<TCommand>): CommandHandler<TCommand>? {
        if (!initialized) {
            initializeHandlers()
        }
        return commandRegistry[commandClass as Class<Command>] as CommandHandler<TCommand>?
    }

    private fun initializeHandlers() {
        synchronized(this) {
            if (!initialized) {
                val handlers: Array<String> = context.getBeanNamesForType(CommandHandler::class.java)
                for (handler in handlers) {
                    registerCommandHandler(handler)
                }
                initialized = true
            }
        }
    }

    private fun registerCommandHandler(name: String) {
        MediatorImpl.log.debug("Registering CommandHandler with name $name")
        val handler = context.getBean(name) as CommandHandler<Command>

        val commandType = handler.getCommandClass()
        if (commandRegistry.containsKey(commandType)) {
            throw IllegalArgumentException(
                commandType.simpleName + " already has a registered handler. " +
                        "Each command must have a single command handler!"
            )
        }
        commandRegistry[commandType] = handler
        MediatorImpl.log.info(
            "Registered CommandHandler " + handler.javaClass.toString() + " to handle Command "
                    + commandType.simpleName
        )
    }
}

interface Command

open class CommandWrapper<TCommand : Command>(
    var payload: TCommand?,
    var callback: Consumer<TCommand>?
)

fun <TCommand : Command> CommandWrapper<TCommand>.getCommandClass(): Class<TCommand> {
    return (GenericTypeResolver.resolveTypeArgument(javaClass, CommandWrapper::class.java) as Class<TCommand>?)!!
}

interface CommandHandler<TCommand : Command> {

    fun execute(command: CommandWrapper<TCommand>)

}

fun <TCommand : Command> CommandHandler<TCommand>.getCommandClass(): Class<TCommand> {
    return (GenericTypeResolver.resolveTypeArgument(javaClass, CommandHandler::class.java) as Class<TCommand>?)!!
}