package tech.sharply.spring_disruptor_mediatr.jobs

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import tech.sharply.spring_disruptor_mediatr.commands.DisruptorCommandBus
import tech.sharply.spring_disruptor_mediatr.sample.display_thing_command.PrintThingCommand
import javax.annotation.PostConstruct

@Component
class SampleJob(
    @Autowired
    val mediator: DisruptorCommandBus
) {

    @PostConstruct
    fun init() {
        mediator.dispatch(PrintThingCommand("penish"))
    }

}