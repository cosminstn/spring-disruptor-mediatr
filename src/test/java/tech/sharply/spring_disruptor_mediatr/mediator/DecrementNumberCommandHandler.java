package tech.sharply.spring_disruptor_mediatr.mediator;

import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

@Component
public class DecrementNumberCommandHandler implements CommandHandler<DecrementNumberCommand, Integer> {

	@Override
	public Integer handle(@NotNull DecrementNumberCommand request) {
		return request.getNumber().decrementAndGet();
	}

}