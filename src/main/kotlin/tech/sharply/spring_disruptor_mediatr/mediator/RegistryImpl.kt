package tech.sharply.spring_disruptor_mediatr.mediator

import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationContext
import org.springframework.core.GenericTypeResolver
import java.lang.reflect.ParameterizedType
import java.util.function.BiConsumer

interface Registry {

    fun <TCommand : Command> getCommandHandler(commandClass: Class<TCommand>): CommandHandler<TCommand>?

    fun <TCommand : CommandWithResult<TResponse>, TResponse> getCommandHandler(commandClass: Class<TCommand>)
            : CommandWithResultHandler<TCommand, TResponse>?

    fun <TQuery : Query<TResponse>, TResponse> getQueryHandler(queryClass: Class<TQuery>)
            : QueryHandler<TQuery, TResponse>?

}

class RegistryImpl(
    private val context: ApplicationContext
) : Registry {

    companion object {
        private val log = LoggerFactory.getLogger(RegistryImpl::class.java)
    }

    private val requestHandlersByName = HashMap<String, RequestHandler<*, *>>()
    private val commandHandlersByType: MutableMap<Class<Command>, CommandHandler<Command>> = HashMap()
    private val commandWithResultHandlersByType:
            MutableMap<Class<CommandWithResult<*>>, CommandWithResultHandler<CommandWithResult<*>, *>> = HashMap()
    private val queryHandlersByType: MutableMap<Class<Query<*>>, QueryHandler<Query<*>, *>> = HashMap()

    private var initialized = false

    private fun initializeHandlers() {
        synchronized(this) {
            if (!initialized) {
                for (handler in context.getBeansOfType(CommandHandler::class.java)) {
                    registerCommandHandler(handler.key, handler.value as CommandHandler<Command>)
                }
                for (handler in context.getBeansOfType(CommandWithResultHandler::class.java)) {
                    registerCommandWithResultHandler(
                        handler.key, handler.value as CommandWithResultHandler<CommandWithResult<*>, *>
                    )
                }
                for (handler in context.getBeansOfType(QueryHandler::class.java)) {
                    registerQueryHandler(handler.key, handler.value as QueryHandler<Query<*>, *>)
                }
                initialized = true
            }
        }
    }

    private fun <TCommand : Command> registerCommandHandler(name: String, handler: CommandHandler<TCommand>) {
        log.debug("Registering CommandHandler with name $handler")

        if (requestHandlersByName.containsKey(name)) {
            throw IllegalArgumentException("There is already a request handler with the name $name registered!")
        }

        val commandType = handler.getCommandClass()
        if (getCommandHandler(commandType) != null) {
            throw IllegalArgumentException(
                commandType.simpleName + " already has a registered handler. " +
                        "Each command must have a single command handler!"
            )
        }
        commandHandlersByType[commandType as Class<Command>] = handler as CommandHandler<Command>
        log.info(
            "Registered CommandHandler " + handler.javaClass.toString() + " to handle Command "
                    + commandType.simpleName
        )
    }

    private fun <TCommand : CommandWithResult<TResponse>, TResponse> registerCommandWithResultHandler(
        name: String,
        handler: CommandWithResultHandler<TCommand, TResponse>
    ) {
        log.debug("Registering CommandWithResultHandler with name $handler")

        if (requestHandlersByName.containsKey(name)) {
            throw IllegalArgumentException("There is already a request handler with the name $name registered!")
        }

        val commandType = handler.getCommandClass()
        if (getCommandHandler(commandType) != null) {
            throw IllegalArgumentException(
                commandType.simpleName + " already has a registered handler. " +
                        "Each command must have a single command handler!"
            )
        }
        commandWithResultHandlersByType[commandType as Class<CommandWithResult<*>>] =
            handler as CommandWithResultHandler<CommandWithResult<*>, *>
        log.info(
            "Registered CommandWithResultHandler " + handler.javaClass.toString() + " to handle CommandWithResult "
                    + commandType.simpleName
        )
    }

    private fun <TQuery : Query<TResponse>, TResponse> registerQueryHandler(
        name: String,
        handler: QueryHandler<TQuery, TResponse>
    ) {
        log.debug("Registering CommandWithResultHandler with name $handler")

        if (requestHandlersByName.containsKey(name)) {
            throw IllegalArgumentException("There is already a request handler with the name $name registered!")
        }

        val queryType = handler.getCommandClass()
        if (getQueryHandler(queryType) != null) {
            throw IllegalArgumentException(
                queryType.simpleName + " already has a registered handler. " +
                        "Each command must have a single command handler!"
            )
        }
        commandWithResultHandlersByType[queryType as Class<CommandWithResult<*>>] =
            handler as CommandWithResultHandler<CommandWithResult<*>, *>
        log.info(
            "Registered QueryHandler " + handler.javaClass.toString() + " to handle Query "
                    + queryType.simpleName
        )
    }

    override fun <TCommand : Command> getCommandHandler(commandClass: Class<TCommand>): CommandHandler<TCommand>? {
        return commandHandlersByType[commandClass as Class<Command>] as CommandHandler<TCommand>?
    }

    override fun <TCommand : CommandWithResult<TResponse>, TResponse> getCommandHandler(commandClass: Class<TCommand>)
            : CommandWithResultHandler<TCommand, TResponse>? {
        return commandWithResultHandlersByType[commandClass as Class<CommandWithResult<*>>]
                as CommandWithResultHandler<TCommand, TResponse>?
    }

    override fun <TQuery : Query<TResponse>, TResponse> getQueryHandler(queryClass: Class<TQuery>)
            : QueryHandler<TQuery, TResponse>? {
        return queryHandlersByType[queryClass as Class<Query<*>>] as QueryHandler<TQuery, TResponse>?
    }

}

// region Request
// TODO: This interface should not be exposed
interface Request<TResponse>

open class RequestWrapper<TRequest : Request<*>>(
    var payload: TRequest?,
    var consumer: BiConsumer<TRequest, *>?
) {
    companion object {
        val FACTORY: () -> RequestWrapper<Request<*>> = { RequestWrapper(null, null) }
    }
}

private fun <TRequest : Request<*>> RequestWrapper<TRequest>.getRequestClass(): Class<TRequest> {
    return (GenericTypeResolver.resolveTypeArgument(javaClass, RequestWrapper::class.java) as Class<TRequest>?)!!
}

interface RequestHandler<TRequest : Request<TResponse>, TResponse>

// endregion

// region Command

interface Command : Request<Unit>

interface CommandHandler<TCommand : Command> : RequestHandler<TCommand, Unit> {

    fun handle(command: TCommand)

}

fun <TCommand : Command> CommandHandler<TCommand>.getCommandClass(): Class<TCommand> {
    return (GenericTypeResolver.resolveTypeArgument(javaClass, CommandHandler::class.java) as Class<TCommand>?)!!
}

// endregion Command

// region CommandWithResult

interface CommandWithResult<TResponse> : Request<TResponse>

interface CommandWithResultHandler<TCommand : CommandWithResult<TResponse>, TResponse> :
    RequestHandler<TCommand, TResponse> {

    fun handle(command: TCommand): TResponse
}

fun <TCommand : CommandWithResult<TResponse>, TResponse> CommandWithResultHandler<TCommand, TResponse>.getCommandClass(): Class<TCommand> {
    return (javaClass as ParameterizedType).actualTypeArguments[0].javaClass as Class<TCommand>
}

// endregion CommandWithResult

// region Query

interface Query<TResponse> : Request<TResponse>

interface QueryHandler<TQuery : Query<TResponse>, TResponse> : RequestHandler<TQuery, TResponse> {

    fun handle(query: TQuery)

}

fun <TQuery : Query<TResponse>, TResponse> QueryHandler<TQuery, TResponse>.getCommandClass(): Class<TQuery> {
    return (javaClass as ParameterizedType).actualTypeArguments[0].javaClass as Class<TQuery>
}
// endregion