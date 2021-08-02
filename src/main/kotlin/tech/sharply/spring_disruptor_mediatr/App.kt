package tech.sharply.spring_disruptor_mediatr

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.scheduling.annotation.EnableScheduling
import tech.sharply.spring_disruptor_mediatr.mediator.Mediator
import tech.sharply.spring_disruptor_mediatr.mediator.MonoDisruptorMediatorImpl

@EnableScheduling
@SpringBootApplication
class App(
    @Autowired
    private val context: ApplicationContext
) {

    @Bean
    fun mediator(): Mediator {
        return MonoDisruptorMediatorImpl(context)
    }

}

fun main(args: Array<String>) {
    runApplication<App>(*args)
}



