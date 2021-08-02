package tech.sharply.spring_disruptor_mediatr.jobs

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import tech.sharply.spring_disruptor_mediatr.mediator.Mediator
import tech.sharply.spring_disruptor_mediatr.samples.PrintThingCommand
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
        mediator.dispatchAsync(PrintThingCommand("3 - async with callback")) { command, _ ->
            println("Executed callback for $command on thread: " + Thread.currentThread().id)
        }
    }

    @Scheduled(fixedRate = 60_000)
    private fun periodic() {
        println("App still alive")
    }
}