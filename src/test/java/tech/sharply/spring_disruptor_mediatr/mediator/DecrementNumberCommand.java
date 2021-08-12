package tech.sharply.spring_disruptor_mediatr.mediator;

import java.util.concurrent.atomic.AtomicInteger;

public class DecrementNumberCommand implements Command<Integer> {

	private final AtomicInteger number;

	public DecrementNumberCommand(AtomicInteger number) {
		this.number = number;
	}

	public AtomicInteger getNumber() {
		return number;
	}

}