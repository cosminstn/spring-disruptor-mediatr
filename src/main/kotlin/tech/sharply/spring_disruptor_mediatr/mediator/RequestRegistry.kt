package tech.sharply.spring_disruptor_mediatr.mediator

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import org.springframework.core.GenericTypeResolver
import org.springframework.stereotype.Component
import java.util.function.BiConsumer
import java.util.function.Consumer

interface RequestRegistry {

    fun <TRequest : Request<TResponse>, TResponse> getRequestHandler(requestClass: Class<TRequest>)
            : RequestHandler<TRequest, TResponse>?



}

@Component
class RequestRegistryImpl(
    @Autowired
    private val context: ApplicationContext
) : RequestRegistry {

    private val requestRegistry: MutableMap<Class<Request<Any>>, RequestHandler<Request<Any>, Any>> = HashMap()
    private var initialized = false

    override fun <TRequest : Request<TResponse>, TResponse> getRequestHandler(requestClass: Class<TRequest>): RequestHandler<TRequest, TResponse>? {
        if (!initialized) {
            initializeHandlers()
        }
        return requestRegistry[requestClass as Class<Request<Any>>] as RequestHandler<TRequest, TResponse>?
    }

    private fun initializeHandlers() {
        synchronized(this) {
            if (!initialized) {
                val handlers: Array<String> = context.getBeanNamesForType(RequestHandler::class.java)
                for (handler in handlers) {
                    registerRequestHandler(handler)
                }
                initialized = true
            }
        }
    }

    private fun registerRequestHandler(name: String) {
        val handler = context.getBean(name) as RequestHandler<Request<Any>, Any>

        val requestType = handler.getRequestClass()
        if (requestRegistry.containsKey(requestType)) {
            throw IllegalArgumentException(
                requestType.simpleName + " already has a registered handler. " +
                        "Each request must have a single request handler!"
            )
        }
        requestRegistry[requestType] = handler
        MonoDisruptorMediatorImpl.log.info(
            "Registered RequestHandler " + handler.javaClass.toString() + " to handle Request "
                    + requestType.simpleName
        )
    }
}

//interface Request<TResponse>
//
//open class RequestWrapper<TRequest : Request<*>>(
//    var payload: TRequest?,
//    var callback: BiConsumer<TRequest, *>?
//)
//
//fun <TRequest : Request<*>> RequestWrapper<TRequest>.getRequestClass(): Class<TRequest> {
//    return (GenericTypeResolver.resolveTypeArgument(javaClass, RequestWrapper::class.java) as Class<TRequest>?)!!
//}
//
//interface RequestHandler<TRequest : Request<TResponse>, TResponse> {
//
//    fun execute(request: RequestWrapper<TRequest>): TResponse
//
//}
//
//fun <TRequest : Request<TResponse>, TResponse> RequestHandler<Request<TResponse>, TResponse>.getRequestClass(): Class<TRequest> {
//    return (GenericTypeResolver.resolveTypeArgument(javaClass, RequestHandler::class.java) as Class<TRequest>?)!!
//}