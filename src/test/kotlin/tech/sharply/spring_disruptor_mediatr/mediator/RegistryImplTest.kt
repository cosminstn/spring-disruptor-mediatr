package tech.sharply.spring_disruptor_mediatr.mediator

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import javax.annotation.PostConstruct

@SpringBootTest
internal class RegistryImplTest(
    @Autowired
    private val context: ApplicationContext
) {

    internal class PrintNumberCommand(val number: Int) : Command

    internal class GenerateNumberCommand : Command<Int>

    internal class FindNextNumberQuery(val number: Int) : Query<Int>

    @TestConfiguration
    internal class Config {

        @Bean
        fun printNumberCommandHandler(): CommandHandler<PrintNumberCommand> {
            return object : CommandHandler<PrintNumberCommand> {
                override fun handle(request: PrintNumberCommand) {
                    println(request.number)
                }
            }
        }

        @Bean
        fun generateNumberCommandHandler(): CommandHandler<GenerateNumberCommand, Int> {
            return object : CommandHandler<GenerateNumberCommand, Int> {
                override fun handle(request: GenerateNumberCommand): Int {
                    return (Math.random() * 100).toInt()
                }
            }
        }

        @Bean
        fun findNextNumberQueryHandler(): QueryHandler<FindNextNumberQuery, Int> {
            return object : QueryHandler<FindNextNumberQuery, Int> {
                override fun handle(request: FindNextNumberQuery): Int {
                    return request.number + 1
                }
            }
        }
    }

    private lateinit var registry: Registry

    @PostConstruct
    private fun init() {
        this.registry = RegistryImpl(context)
    }

    @Test
    fun getHandler() {
        val commandHandler = registry.getCommandHandler(PrintNumberCommand::class.java)
        assertNotNull(commandHandler)
        assert(commandHandler is CommandHandler<PrintNumberCommand>)
    }

    @Test
    fun testGetCommandHandler() {
    }

    @Test
    fun getQueryHandler() {
    }


}