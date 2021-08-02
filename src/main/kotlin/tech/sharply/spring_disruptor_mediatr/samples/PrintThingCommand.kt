package tech.sharply.spring_disruptor_mediatr.samples

import org.springframework.stereotype.Component
import tech.sharply.spring_disruptor_mediatr.mediator.CommandWithResult
import tech.sharply.spring_disruptor_mediatr.mediator.CommandWithResultHandler

class PrintThingCommand(val thing: String) : CommandWithResult<Unit> {

    override fun toString(): String {
        return "PrintThingCommand(thing=${thing})"
    }
}

@Component
class PrintThingCommandHandler : CommandWithResultHandler<PrintThingCommand, Unit> {

    override fun handle(request: PrintThingCommand) {
        println(request.thing)
    }

}