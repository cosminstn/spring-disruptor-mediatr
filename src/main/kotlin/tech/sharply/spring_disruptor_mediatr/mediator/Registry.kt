package tech.sharply.spring_disruptor_mediatr.mediator

import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationContext
import org.springframework.core.GenericTypeResolver

interface Registry {

    fun <TCommand : Command<TResponse>, TResponse> getCommandHandler(commandClass: Class<TCommand>)
            : CommandHandler<TCommand, TResponse>?

    fun <TQuery : Query<TResponse>, TResponse> getQueryHandler(queryClass: Class<TQuery>)
            : QueryHandler<TQuery, TResponse>?

}

@Suppress("UNCHECKED_CAST")
class RegistryImpl(
    private val context: ApplicationContext
) : Registry {

    companion object {
        private val log = LoggerFactory.getLogger(RegistryImpl::class.java)
    }

    private val requestHandlersByName = HashMap<String, RequestHandler<*, *>>()
    private val commandHandlersByType:
            MutableMap<Class<Command<*>>, CommandHandler<Command<*>, *>> = HashMap()
    private val queryHandlersByType: MutableMap<Class<Query<*>>, QueryHandler<Query<*>, *>> = HashMap()

    init {
        for (handler in context.getBeansOfType(CommandHandler::class.java)) {
            registerCommandHandler(
                handler.key, handler.value as CommandHandler<Command<*>, *>
            )
        }
        for (handler in context.getBeansOfType(QueryHandler::class.java)) {
            registerQueryHandler(handler.key, handler.value as QueryHandler<Query<*>, *>)
        }
    }

    private fun <TCommand : Command<TResponse>, TResponse> registerCommandHandler(
        name: String,
        handler: CommandHandler<TCommand, TResponse>
    ) {
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
        commandHandlersByType[commandType as Class<Command<*>>] =
            handler as CommandHandler<Command<*>, *>
        log.info(
            "Registered CommandHandler " + handler.javaClass.toString() + " to handle Command "
                    + commandType.simpleName
        )
    }

    private fun <TQuery : Query<TResponse>, TResponse> registerQueryHandler(
        name: String,
        handler: QueryHandler<TQuery, TResponse>
    ) {
        log.debug("Registering CommandHandler with name $handler")

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
        queryHandlersByType[queryType as Class<Query<*>>] = handler as QueryHandler<Query<*>, *>
        log.info(
            "Registered QueryHandler " + handler.javaClass.toString() + " to handle Query "
                    + queryType.simpleName
        )
    }

    override fun <TCommand : Command<TResponse>, TResponse> getCommandHandler(commandClass: Class<TCommand>)
            : CommandHandler<TCommand, TResponse>? {
        return commandHandlersByType[commandClass as Class<Command<*>>]
                as CommandHandler<TCommand, TResponse>?
    }

    override fun <TQuery : Query<TResponse>, TResponse> getQueryHandler(queryClass: Class<TQuery>)
            : QueryHandler<TQuery, TResponse>? {
        return queryHandlersByType[queryClass as Class<Query<*>>] as QueryHandler<TQuery, TResponse>?
    }

}

// region Request
// TODO: This interface should not be exposed
interface Request<TResponse>

interface RequestHandler<TRequest : Request<TResponse>, TResponse> {
    fun handle(request: TRequest): TResponse
}

// endregion

// region Command

interface Command<TResponse> : Request<TResponse>

interface CommandHandler<TCommand : Command<TResponse>, TResponse> :
    RequestHandler<TCommand, TResponse>

fun <TCommand : Command<TResponse>, TResponse> CommandHandler<TCommand, TResponse>.getCommandClass(): Class<TCommand> {
//    return (javaClass as ParameterizedType).actualTypeArguments[0].javaClass as Class<TCommand>
    val firstGeneric = GenericTypeResolver.resolveTypeArguments(javaClass, CommandHandler::class.java)?.get(0)
    return firstGeneric as Class<TCommand>
}

// endregion Command

// region Query

interface Query<TResponse> : Request<TResponse>

interface QueryHandler<TQuery : Query<TResponse>, TResponse> : RequestHandler<TQuery, TResponse>

fun <TQuery : Query<TResponse>, TResponse> QueryHandler<TQuery, TResponse>.getCommandClass(): Class<TQuery> {
    val firstGeneric = GenericTypeResolver.resolveTypeArguments(javaClass, QueryHandler::class.java)?.get(0)
    return firstGeneric as Class<TQuery>
}
// endregion