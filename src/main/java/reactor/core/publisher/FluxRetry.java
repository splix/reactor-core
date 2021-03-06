/*
 * Copyright (c) 2011-2016 Pivotal Software Inc, All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package reactor.core.publisher;

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import reactor.core.subscriber.MultiSubscriptionSubscriber;

/**
 * Repeatedly subscribes to the source sequence if it signals any error
 * either indefinitely or a fixed number of times.
 * <p>
 * The times == Long.MAX_VALUE is treated as infinite retry.
 *
 * @param <T> the value type
 */

/**
 * @see <a href="https://github.com/reactor/reactive-streams-commons">Reactive-Streams-Commons</a>
 * @since 2.5
 */
final class FluxRetry<T> extends FluxSource<T, T> {

	final long times;

	public FluxRetry(Publisher<? extends T> source, long times) {
		super(source);
		if (times < 0L) {
			throw new IllegalArgumentException("times >= 0 required");
		}
		this.times = times;
	}

	@Override
	public long getCapacity() {
		return -1L;
	}

	@Override
	public void subscribe(Subscriber<? super T> s) {
		RetrySubscriber<T> parent = new RetrySubscriber<>(source, s, times);

		s.onSubscribe(parent);

		if (!parent.isCancelled()) {
			parent.resubscribe();
		}
	}

	static final class RetrySubscriber<T>
			extends MultiSubscriptionSubscriber<T, T> {

		final Publisher<? extends T> source;

		long remaining;

		volatile int wip;
		@SuppressWarnings("rawtypes")
		static final AtomicIntegerFieldUpdater<RetrySubscriber> WIP =
		  AtomicIntegerFieldUpdater.newUpdater(RetrySubscriber.class, "wip");

		long produced;

		public RetrySubscriber(Publisher<? extends T> source, Subscriber<? super T> actual, long remaining) {
			super(actual);
			this.source = source;
			this.remaining = remaining;
		}

		@Override
		public void onNext(T t) {
			produced++;

			subscriber.onNext(t);
		}

		@Override
		public void onError(Throwable t) {
			long r = remaining;
			if (r != Long.MAX_VALUE) {
				if (r == 0) {
					subscriber.onError(t);
					return;
				}
				remaining = r - 1;
			}

			resubscribe();
		}

		void resubscribe() {
			if (WIP.getAndIncrement(this) == 0) {
				do {
					if (isCancelled()) {
						return;
					}

					long c = produced;
					if (c != 0L) {
						produced = 0L;
						produced(c);
					}

					source.subscribe(this);

				} while (WIP.decrementAndGet(this) != 0);
			}
		}
	}
}
