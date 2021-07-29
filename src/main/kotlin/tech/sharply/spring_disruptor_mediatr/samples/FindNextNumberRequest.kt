package tech.sharply.spring_disruptor_mediatr.samples

import org.springframework.stereotype.Component
import tech.sharply.spring_disruptor_mediatr.mediator.Request
import tech.sharply.spring_disruptor_mediatr.mediator.RequestHandler
import tech.sharply.spring_disruptor_mediatr.mediator.RequestWrapper

class FindNextNumberRequest(
    val number: Int
) : Request<Int>

@Component
class FindNextNumberRequestHandler : RequestHandler<FindNextNumberRequest, Int> {
    override fun execute(request: RequestWrapper<FindNextNumberRequest>): Int {
        return request.payload!!.number + 1
    }
}