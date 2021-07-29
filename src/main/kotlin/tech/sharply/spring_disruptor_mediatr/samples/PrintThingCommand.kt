package tech.sharply.spring_disruptor_mediatr.samples

import org.springframework.stereotype.Component
import tech.sharply.spring_disruptor_mediatr.commands.Command
import tech.sharply.spring_disruptor_mediatr.commands.CommandHandler
import tech.sharply.spring_disruptor_mediatr.commands.CommandWrapper

class PrintThingCommand(val thing: String) : Command {

    override fun toString(): String {
        return "PrintThingCommand(thing=${thing})"
    }
}

@Component
class DisplayThingCommandHandler : CommandHandler<PrintThingCommand> {

    override fun execute(commandWrapper: CommandWrapper<PrintThingCommand>) {
        println(commandWrapper.payload?.thing)
    }

}