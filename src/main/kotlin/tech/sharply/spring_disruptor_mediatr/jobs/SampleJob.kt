package tech.sharply.spring_disruptor_mediatr.jobs

import com.lmax.disruptor.EventTranslatorOneArg
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import tech.sharply.spring_disruptor_mediatr.commands.Command
import tech.sharply.spring_disruptor_mediatr.commands.DisruptorCommandBus
import tech.sharply.spring_disruptor_mediatr.sample.display_thing_command.PrintThingCommand
import javax.annotation.PostConstruct
import kotlinx.serialization.*
import kotlinx.serialization.json.*

@Component
class SampleJob(
    @Autowired
    val mediator: DisruptorCommandBus
) {

    @PostConstruct
    fun init() {
        mediator.dispatchAsync(PrintThingCommand("penish"), object: EventTranslatorOneArg<Command, PrintThingCommand> {
            override fun translateTo(event: Command?, sequence: Long, input: PrintThingCommand?) {
                if (input == null) {
                    return
                }
                // clone the input to remove any object references
                var inputClone = deepClone(input)
                var parsed = event as PrintThingCommand
                parsed.thing = inputClone.thing
            }
        })
    }

    companion object {
        inline fun <reified T> deepClone(input: T): T {
            return Json.decodeFromString(Json.encodeToString(input))
        }
    }
}