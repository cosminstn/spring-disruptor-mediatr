package tech.sharply.spring_disruptor_mediatr.mediator

import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationEvent
import org.springframework.core.GenericTypeResolver

interface Registry {

    fun <TCommand : Command<TResponse>, TResponse> getCommandHandler(commandClass: Class<TCommand>)
            : CommandHandler<TCommand, TResponse>?

    fun <TQuery : Query<TResponse>, TResponse> getQueryHandler(queryClass: Class<TQuery>)
            : QueryHandler<TQuery, TResponse>?

    fun <TEvent : AppEvent> getEventHandlers(eventClass: Class<TEvent>)
            : List<AppEventHandler<TEvent>>

}

@Suppress("UNCHECKED_CAST")
class RegistryImpl(
    private val context: ApplicationContext
) : Registry {

    companion object {
        private val log = LoggerFactory.getLogger(RegistryImpl::class.java)
    }

    private val handlersByName = HashMap<String, Handler>()
    private val commandHandlersByType:
            MutableMap<Class<Command<*>>, CommandHandler<Command<*>, *>> = HashMap()
    private val queryHandlersByType: MutableMap<Class<Query<*>>, QueryHandler<Query<*>, *>> = HashMap()
    private val eventHandlersByType: MutableMap<Class<ApplicationEvent>, MutableList<AppEventHandler<AppEvent>>> =
        HashMap()

    init {
        registerHandlers()
    }

    /**
     * Iterates through all the CommandHandler and QueryHandler beans in the ApplicationContext and maps them
     * to their request class.
     */
    private fun registerHandlers() {
        for (handler in context.getBeansOfType(CommandHandler::class.java)) {
            registerCommandHandler(handler.key, handler.value)
        }
        for (handler in context.getBeansOfType(QueryHandler::class.java)) {
            registerQueryHandler(handler.key, handler.value)
        }
        for (handler in context.getBeansOfType(AppEventHandler::class.java)) {
            registerEventHandler(handler.key, handler.value)
        }
    }

    private fun <TCommand : Command<TResponse>, TResponse> registerCommandHandler(
        name: String,
        handler: CommandHandler<TCommand, TResponse>
    ) {
        log.debug("Registering CommandHandler with name $name")

        if (handlersByName.containsKey(name)) {
            if (handler != handlersByName[name]) {
                throw IllegalArgumentException("There is already a request handler with the name $name registered!")
            }
        }

        val commandType = handler.getCommandClass()
        val registeredHandler = commandHandlersByType[commandType as Class<Command<*>>]
        if (registeredHandler != null) {
            throw IllegalArgumentException(
                commandType.simpleName + " already has a registered handler. " +
                        "Each command must have a single command handler!"
            )
        }
        commandHandlersByType[commandType] = handler as CommandHandler<Command<*>, *>
        handlersByName[name] = handler
        log.info(
            "Registered CommandHandler " + handler.javaClass.toString() + " to handle Command "
                    + commandType.simpleName
        )
    }

    private fun <TQuery : Query<TResponse>, TResponse> registerQueryHandler(
        name: String,
        handler: QueryHandler<TQuery, TResponse>
    ) {
        log.debug("Registering CommandHandler with name $name")

        if (handlersByName.containsKey(name)) {
            if (handler != handlersByName[name]) {
                throw IllegalArgumentException("There is already a request handler with the name $name registered!")
            }
        }

        val queryType = handler.getCommandClass()
        val registeredHandler = queryHandlersByType[queryType as Class<Query<*>>]
        if (registeredHandler != null) {
            throw IllegalArgumentException(
                queryType.simpleName + " already has a registered handler. " +
                        "Each command must have a single command handler!"
            )
        }
        queryHandlersByType[queryType] = handler as QueryHandler<Query<*>, *>
        handlersByName[name] = handler
        log.info(
            "Registered QueryHandler " + handler.javaClass.toString() + " to handle Query "
                    + queryType.simpleName
        )
    }

    private fun <TEvent : AppEvent> registerEventHandler(
        name: String,
        handler: AppEventHandler<TEvent>
    ) {
        log.debug("Registering EventHandler with name $")

        if (handlersByName.containsKey(name)) {
            if (handler != handlersByName[name]) {
                throw IllegalArgumentException("There is already a request handler with the name $name registered!")
            }
        }

        val eventType = handler.getEventClass()
        if (!eventHandlersByType.containsKey(eventType as Class<ApplicationEvent>)) {
            eventHandlersByType[eventType] = mutableListOf()
        }
        val handlersList = eventHandlersByType[eventType]
        handlersList!!.add(handler as AppEventHandler<AppEvent>)
    }

    override fun <TCommand : Command<TResponse>, TResponse> getCommandHandler(commandClass: Class<TCommand>)
            : CommandHandler<TCommand, TResponse>? {
        val handler = commandHandlersByType[commandClass as Class<Command<*>>]
                as CommandHandler<TCommand, TResponse>?
        if (handler == null) {
            registerHandlers()
            // search again for the handler
            return commandHandlersByType[commandClass] as CommandHandler<TCommand, TResponse>?
        }
        return handler
    }

    override fun <TQuery : Query<TResponse>, TResponse> getQueryHandler(queryClass: Class<TQuery>)
            : QueryHandler<TQuery, TResponse>? {
        val handler = queryHandlersByType[queryClass as Class<Query<*>>] as QueryHandler<TQuery, TResponse>?
        if (handler == null) {
            registerHandlers()
            // search again for the handler
            return queryHandlersByType[queryClass] as QueryHandler<TQuery, TResponse>?
        }
        return handler
    }

    override fun <TEvent : AppEvent> getEventHandlers(eventClass: Class<TEvent>): List<AppEventHandler<TEvent>> {
        val handlers =
            eventHandlersByType[eventClass as Class<ApplicationEvent>] as List<AppEventHandler<TEvent>>?
        if (handlers == null) {
            registerHandlers()
            // search again for the handlers
            return eventHandlersByType[eventClass] as List<AppEventHandler<TEvent>>?
                ?: return listOf()
        }
        return handlers
    }

}

interface Message

interface Handler

// region Request

sealed interface Request<TResponse> : Message

sealed interface RequestHandler<TRequest : Request<TResponse>, TResponse> : Handler {
    fun handle(request: TRequest): TResponse
}

// endregion

// region Command
/**
 * A command represents a unit of work that changes the internal system state.
 * Commands
 */
interface Command<TResponse> : Request<TResponse>

interface CommandHandler<TCommand : Command<TResponse>, TResponse> :
    RequestHandler<TCommand, TResponse>

@Suppress("UNCHECKED_CAST")
fun <TCommand : Command<TResponse>, TResponse> CommandHandler<TCommand, TResponse>.getCommandClass(): Class<TCommand> {
    val firstGeneric = GenericTypeResolver.resolveTypeArguments(javaClass, CommandHandler::class.java)?.get(0)
    return firstGeneric as Class<TCommand>
}

// endregion Command

// region Query
/**
 * A query represents a unit ot work that returns a result without changing the internal system state.
 * If one unit of work changes the system state than that unit is a command, not a query.
 */
interface Query<TResponse> : Request<TResponse>

interface QueryHandler<TQuery : Query<TResponse>, TResponse> : RequestHandler<TQuery, TResponse>

@Suppress("UNCHECKED_CAST")
fun <TQuery : Query<TResponse>, TResponse> QueryHandler<TQuery, TResponse>.getCommandClass(): Class<TQuery> {
    val firstGeneric = GenericTypeResolver.resolveTypeArguments(javaClass, QueryHandler::class.java)?.get(0)
    return firstGeneric as Class<TQuery>
}
// endregion

abstract class AppEvent(source: Any) : ApplicationEvent(source), Message

interface AppEventHandler<TEvent : AppEvent> : Handler {
    fun handle(event: TEvent)
}

@Suppress("UNCHECKED_CAST")
fun <TEvent : AppEvent> AppEventHandler<TEvent>.getEventClass(): Class<TEvent> {
    return GenericTypeResolver.resolveTypeArgument(javaClass, AppEventHandler::class.java) as Class<TEvent>
}