package tech.sharply.spring_disruptor_mediatr.samples

import org.springframework.context.ApplicationEvent
import org.springframework.stereotype.Component
import tech.sharply.spring_disruptor_mediatr.mediator.ApplicationEventHandler

class NumberEvent(source: Any, val number: Long) : ApplicationEvent(source)

class StringEvent(source: Any, val string: String) : ApplicationEvent(source)

@Component
class NumberEventHandler : ApplicationEventHandler<NumberEvent> {
    override fun handle(event: NumberEvent) {
        println("Handled number event ${event.number} on thread: " + Thread.currentThread().id)
    }
}

@Component
class StringEventHandler : ApplicationEventHandler<StringEvent> {
    override fun handle(event: StringEvent) {
        println("Handled string event ${event.string} on thread: " + Thread.currentThread().id)
    }
}