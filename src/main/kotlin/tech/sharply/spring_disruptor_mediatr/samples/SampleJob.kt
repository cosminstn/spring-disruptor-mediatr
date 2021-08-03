package tech.sharply.spring_disruptor_mediatr.samples

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import tech.sharply.spring_disruptor_mediatr.mediator.Mediator
import javax.annotation.PostConstruct

@Component
class SampleJob(
    @Autowired
    val mediator: Mediator
) {

    @PostConstruct
    fun init() {
        mediator.dispatchBlocking(PrintThingCommand("1 - sync"))
        mediator.dispatchAsync(PrintThingCommand("2 - async"))

        val completableCommand = PrintThingCommand("4 - future")
        println("Launching Completable future command: $completableCommand")
        mediator.dispatchAsync(completableCommand).get()
        println("Completable future command finished executing$completableCommand")


        mediator.publishEvent(NumberEvent(this, 5))
        mediator.publishEvent(StringEvent(this, "abc"))
    }


    @Scheduled(fixedRate = 60_000)
    private fun periodic() {
        println("App still alive")
    }

}