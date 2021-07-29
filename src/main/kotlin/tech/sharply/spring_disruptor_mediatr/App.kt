package tech.sharply.spring_disruptor_mediatr

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@EnableScheduling
@SpringBootApplication
class App

fun main(args: Array<String>) {
    runApplication<App>(*args)
}
