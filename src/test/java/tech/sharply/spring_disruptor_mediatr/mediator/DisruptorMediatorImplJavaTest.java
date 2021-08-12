package tech.sharply.spring_disruptor_mediatr.mediator;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import javax.annotation.PostConstruct;
import java.util.concurrent.atomic.AtomicInteger;

@SpringBootTest
public class DisruptorMediatorImplJavaTest {

	@SpringBootApplication
	public static class Config {
	}

	@Autowired
	private ApplicationContext context;

	private Mediator mediator;

	@PostConstruct
	public void init() {
		this.mediator = new DisruptorMediatorImpl(context);
	}

	@Test
	public void dispatchBlocking() {
		AtomicInteger atomic = new AtomicInteger(5);
		mediator.dispatchBlocking(new DecrementNumberCommand(atomic));
		assert(atomic.get() == 4);
	}

}

