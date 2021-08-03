package tech.sharply.spring_disruptor_mediatr.samples

import org.springframework.stereotype.Component
import tech.sharply.spring_disruptor_mediatr.mediator.Query
import tech.sharply.spring_disruptor_mediatr.mediator.QueryHandler

class FindNextNumberRequest(
    val number: Int
) : Query<Int>

@Component
class FindNextNumberRequestHandler : QueryHandler<FindNextNumberRequest, Int> {

    override fun handle(request: FindNextNumberRequest): Int {
        return request.number + 1
    }

}