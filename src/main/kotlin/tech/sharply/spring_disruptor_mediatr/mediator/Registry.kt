package tech.sharply.spring_disruptor_mediatr.mediator

import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationContext
import org.springframework.core.GenericTypeResolver
import java.util.function.BiConsumer

interface Registry {

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
    private val commandWithResultHandlersByType:
            MutableMap<Class<CommandWithResult<*>>, CommandWithResultHandler<CommandWithResult<*>, *>> = HashMap()
    private val queryHandlersByType: MutableMap<Class<Query<*>>, QueryHandler<Query<*>, *>> = HashMap()

    init {
        for (handler in context.getBeansOfType(CommandWithResultHandler::class.java)) {
            registerCommandWithResultHandler(
                handler.key, handler.value as CommandWithResultHandler<CommandWithResult<*>, *>
            )
        }
        for (handler in context.getBeansOfType(QueryHandler::class.java)) {
            registerQueryHandler(handler.key, handler.value as QueryHandler<Query<*>, *>)
        }
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
        queryHandlersByType[queryType as Class<Query<*>>] = handler as QueryHandler<Query<*>, *>
        log.info(
            "Registered QueryHandler " + handler.javaClass.toString() + " to handle Query "
                    + queryType.simpleName
        )
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

enum class RequestType {

    COMMAND,
    QUERY

}

fun <TResponse> Request<TResponse>.getType(): RequestType {
    if (this is Query) {
        return RequestType.QUERY
    }
    return RequestType.COMMAND
}

open class RequestWrapper<TRequest : Request<*>>(
    var payload: TRequest?,
    var consumer: BiConsumer<TRequest, Any?>?
) {
    companion object {
        val FACTORY: () -> RequestWrapper<Request<*>> = { RequestWrapper(null, null) }
    }
}

private fun <TRequest : Request<*>> RequestWrapper<TRequest>.getRequestClass(): Class<TRequest> {
    return (GenericTypeResolver.resolveTypeArgument(javaClass, RequestWrapper::class.java) as Class<TRequest>?)!!
}

interface RequestHandler<TRequest : Request<TResponse>, TResponse> {
    fun handle(request: TRequest): TResponse
}

// endregion

// region Command

interface CommandWithResult<TResponse> : Request<TResponse>

interface CommandWithResultHandler<TCommand : CommandWithResult<TResponse>, TResponse> :
    RequestHandler<TCommand, TResponse>

fun <TCommand : CommandWithResult<TResponse>, TResponse> CommandWithResultHandler<TCommand, TResponse>.getCommandClass(): Class<TCommand> {
//    return (javaClass as ParameterizedType).actualTypeArguments[0].javaClass as Class<TCommand>
    val firstGeneric = GenericTypeResolver.resolveTypeArguments(javaClass, CommandWithResultHandler::class.java)?.get(0)
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