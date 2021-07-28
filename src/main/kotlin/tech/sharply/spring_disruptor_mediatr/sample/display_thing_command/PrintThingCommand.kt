package tech.sharply.spring_disruptor_mediatr.sample.display_thing_command

import org.springframework.stereotype.Component
import tech.sharply.spring_disruptor_mediatr.commands.Command
import tech.sharply.spring_disruptor_mediatr.commands.CommandHandler

class PrintThingCommand(val thing: String) : Command

@Component
class DisplayThingCommandHandler : CommandHandler<PrintThingCommand> {

    override fun execute(command: PrintThingCommand) {
        println(command.thing)
    }

}