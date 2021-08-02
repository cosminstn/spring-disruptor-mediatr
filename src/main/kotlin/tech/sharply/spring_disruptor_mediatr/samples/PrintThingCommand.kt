package tech.sharply.spring_disruptor_mediatr.samples

import org.springframework.stereotype.Component
import tech.sharply.spring_disruptor_mediatr.mediator.Command
import tech.sharply.spring_disruptor_mediatr.mediator.CommandHandler

class PrintThingCommand(val thing: String) : Command<Unit> {

    override fun toString(): String {
        return "PrintThingCommand(thing=${thing})"
    }
}

@Component
class PrintThingCommandHandler : CommandHandler<PrintThingCommand, Unit> {

    override fun handle(request: PrintThingCommand) {
        println(request.thing)
    }

}