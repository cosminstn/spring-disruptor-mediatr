package tech.sharply.spring_disruptor_mediatr.mediator

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.stereotype.Component
import javax.annotation.PostConstruct

@SpringBootTest
internal class RegistryImplTest(
    @Autowired
    private val context: ApplicationContext
) {

    internal class PrintNumberCommand(val number: Int) : Command<Unit>

    internal class GenerateNumberCommand : Command<Int>

    internal class FindPreviousNumberQuery(val number: Int) : Query<Int>

    internal class FindNextNumberQuery(val number: Int) : Query<Int>


    @TestConfiguration
    internal class Config {

        @Component
        class PrintNumberCommandHandler : CommandHandler<PrintNumberCommand, Unit> {
            override fun handle(request: PrintNumberCommand) {
                println(request.number)
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

        @Component
        class FindPreviousNumberQueryHandler : QueryHandler<FindPreviousNumberQuery, Int> {
            override fun handle(request: FindPreviousNumberQuery): Int {
                return request.number - 1
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
    fun getCommandHandler() {
        val printNumberCommandHandler = registry.getCommandHandler(PrintNumberCommand::class.java)
        assertNotNull(printNumberCommandHandler)
        assert(printNumberCommandHandler is CommandHandler<PrintNumberCommand, Unit>)
        assert(printNumberCommandHandler is Config.PrintNumberCommandHandler)

        val generateNumberCommandHandler = registry.getCommandHandler(GenerateNumberCommand::class.java)
        assertNotNull(generateNumberCommandHandler)
        assert(generateNumberCommandHandler is CommandHandler<GenerateNumberCommand, Int>)
    }

    @Test
    fun getQueryHandler() {
        val findPreviousNumberQueryHandler = registry.getQueryHandler(FindPreviousNumberQuery::class.java)
        assertNotNull(findPreviousNumberQueryHandler)
        assert(findPreviousNumberQueryHandler is Config.FindPreviousNumberQueryHandler)

        val findNextNumberQueryHandler = registry.getQueryHandler(FindNextNumberQuery::class.java)
        assertNotNull(findNextNumberQueryHandler)
        assert(findNextNumberQueryHandler is QueryHandler<FindNextNumberQuery, Int>)
    }

}