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
import org.springframework.scheduling.annotation.Scheduled

@Component
class SampleJob(
    @Autowired
    val mediator: DisruptorCommandBus
) {

    @PostConstruct
    fun init() {
        mediator.dispatch(PrintThingCommand("sync command execution"))
        mediator.dispatchAsync(PrintThingCommand("async command execution"))
    }

    @Scheduled(fixedRate = 60_000)
    private fun periodic() {
        println("App still alive")
    }
}