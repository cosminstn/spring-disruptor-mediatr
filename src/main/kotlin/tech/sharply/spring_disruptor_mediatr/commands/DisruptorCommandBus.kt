package tech.sharply.spring_disruptor_mediatr.commands

import com.lmax.disruptor.EventFactory
import com.lmax.disruptor.EventHandler
import com.lmax.disruptor.EventTranslatorOneArg
import com.lmax.disruptor.dsl.Disruptor
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.util.concurrent.ThreadFactory
import javax.annotation.PostConstruct

@Component
class DisruptorCommandBus(
    @Autowired
    private val commandRegistry: CommandRegistry
) {

    companion object {
        val log: Logger = LoggerFactory.getLogger(DisruptorCommandBus.javaClass)
    }

    private val disruptor = Disruptor(
        EventFactory { CommandWrapper<Command>(null) },
        1024,
        ThreadFactory { r -> Thread(r) }
    )

    @PostConstruct
    private fun init() {
        disruptor.handleEventsWith(EventHandler { commandWrapper, sequence, endOfBatch ->
            val handler = commandWrapper.payload?.let { commandRegistry.getCommandHandler(it.javaClass) }
            if (handler == null) {
                println("No command handler found for command type: " + commandWrapper.getCommandClass())
                return@EventHandler
            }
            handler.execute(commandWrapper)
        })

        disruptor.start()
    }

    fun <TCommand : Command> dispatch(command: TCommand) {
        val handler = commandRegistry.getCommandHandler(command.javaClass)
            ?: throw IllegalArgumentException("No command handler found for command type: " + command.javaClass)

        handler.execute(CommandWrapper(command))
    }

    fun <TCommand : Command> dispatchAsync(command: TCommand) {
        val translator =
            EventTranslatorOneArg<CommandWrapper<Command>, TCommand> { event, sequence, input ->
                if (event == null) {
                    return@EventTranslatorOneArg
                }
                event.payload = input
            }
        disruptor.publishEvent(translator, command)
    }
}
