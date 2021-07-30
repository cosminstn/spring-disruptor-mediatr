package tech.sharply.spring_disruptor_mediatr.mediator

import org.springframework.core.GenericTypeResolver
import sun.security.timestamp.TSResponse
import java.util.function.BiConsumer
import java.util.function.Consumer

interface Registry {

    fun <TCommand : Command> getCommandHandler(commandClass: Class<TCommand>): CommandHandler<TCommand>

    fun <TCommand : CommandWithResult<TResponse>, TResponse> getCommandHandler(commandClass: Class<TCommand>)
            : CommandWithResultHandler<TCommand, TResponse>

    fun <TQuery : Query<TResponse>, TResponse> getQueryHandler(queryClass: Class<TQuery>)
            : QueryHandler<TQuery, TResponse>

}

class RegistryImpl {
}

// region Request
// TODO: This interface should not be exposed
interface Request<TResponse>

open class RequestWrapper<TRequest : Request<*>>(
     var payload: TRequest?,
     var consumer: BiConsumer<TRequest, *>
)

private fun <TRequest : Request<*>> RequestWrapper<TRequest>.getRequestClass(): Class<TRequest> {
    return (GenericTypeResolver.resolveTypeArgument(javaClass, RequestWrapper::class.java) as Class<TRequest>?)!!
}

interface RequestHandler<TRequest : Request<TResponse>, TResponse> {

    fun execute(request: RequestWrapper<TRequest>): TResponse

}
// endregion

// region Command

interface Command : Request<Unit>

interface CommandHandler<TCommand : Command> {

    fun handle(command: RequestWrapper<TCommand>)

}

// endregion Command

// region CommandWithResult

interface CommandWithResult<TResponse> : Request<TResponse>

interface CommandWithResultHandler<TCommand : CommandWithResult<TResponse>, TResponse> {

    fun handle(command: RequestWrapper<TCommand>) : TResponse

}

// endregion CommandWithResult

// region Query

interface Query<TResponse> : Request<TResponse>

interface QueryHandler<TQuery : Query<TResponse>, TResponse> {

    fun handle(query: RequestWrapper<TQuery>)

}

// endregion