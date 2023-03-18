package io.github.pellse.reactive.assembler;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;

@FunctionalInterface
public interface LifeCycleEventSource {

    static LifeCycleEventListener concurrentLifeCycleEventListener(LifeCycleEventListener listener) {

        return new LifeCycleEventListener() {

            private final AtomicBoolean isStarted = new AtomicBoolean();

            @Override
            public void start() {
                if (isStarted.compareAndSet(false, true)) {
                    listener.start();
                }
            }

            @Override
            public void stop() {
                if (isStarted.get()) {
                    listener.stop();
                }
            }
        };
    }

    static <T, U> LifeCycleEventListener lifeCycleEventAdapter(T eventSource, Function<T, U> start, Consumer<U> stop) {

        return new LifeCycleEventListener() {

            private U stopObj;

            @Override
            public void start() {
                stopObj = start.apply(eventSource);
            }

            @Override
            public void stop() {
                stop.accept(stopObj);
            }
        };
    }

    void addLifeCycleEventListener(LifeCycleEventListener listener);

    interface LifeCycleEventListener {
        void start();

        void stop();
    }
}