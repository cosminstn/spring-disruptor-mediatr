package tech.sharply.spring_disruptor_mediatr.mediator

import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationEvent
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.annotation.PostConstruct

@SpringBootTest
internal class DisruptorMediatorImplTest(
    @Autowired
    private val context: ApplicationContext
) {

    @SpringBootApplication
    internal class Config {

        class IncrementNumberCommand(val atomic: AtomicInteger) : Command<Unit>

        @Component
        class IncrementNumberCommandHandler : CommandHandler<IncrementNumberCommand, Unit> {
            override fun handle(request: IncrementNumberCommand) {
                request.atomic.incrementAndGet()
            }
        }

        class GetHandlerThreadCommand : Command<Thread>

        @Component
        class GetHandlerThreadCommandHandler : CommandHandler<GetHandlerThreadCommand, Thread> {
            override fun handle(request: GetHandlerThreadCommand): Thread {
                return Thread.currentThread()
            }
        }

        class NothingHappenedEvent(val handled: AtomicBoolean, source: Any) : ApplicationEvent(source)

        @Component
        class AtomicEventHandler : ApplicationEventHandler<NothingHappenedEvent> {
            override fun handle(event: NothingHappenedEvent) {
                event.handled.set(true)
            }
        }
    }

    private lateinit var mediator: Mediator

    @PostConstruct
    private fun init() {
        this.mediator = DisruptorMediatorImpl(context)
    }

    @Test
    fun dispatchBlocking() {
        val atomic = AtomicInteger(5)
        mediator.dispatchBlocking(Config.IncrementNumberCommand(atomic))
        assert(atomic.get() == 6)
    }

    @Test
    fun dispatchAsync() {
        val number = AtomicInteger(1)
        mediator.dispatchAsync(Config.IncrementNumberCommand(number))
        await().atMost(Duration.ofSeconds(1))
            .until { number.get() == 2 }
    }

    @Test
    fun publishEvent() {
        val handled = AtomicBoolean(false)
        mediator.publishEvent(Config.NothingHappenedEvent(handled, this))
        await().atMost(Duration.ofSeconds(1))
            .until { handled.get() }
    }

    @Test
    fun testCommandsDispatchedFromDifferentThreadsAreAllHandledOnSameThread() {
        val threadsCount = 10
        val requestsCount = 1000

        val callingThreads = ConcurrentHashMap.newKeySet<Thread>()
        val handlingThreads = ConcurrentHashMap.newKeySet<Thread>()

        val threadsPool = Executors.newFixedThreadPool(threadsCount)
        val countDownLatch = CountDownLatch(requestsCount)
        for (requestIndex in 1..requestsCount) {
            threadsPool.submit {
                callingThreads.add(Thread.currentThread())
                handlingThreads.add(mediator.dispatchBlocking(Config.GetHandlerThreadCommand()))
                countDownLatch.countDown()
            }
        }

        countDownLatch.await()

        assert(callingThreads.size > 1)
        assert(handlingThreads.size == 1)
    }

}