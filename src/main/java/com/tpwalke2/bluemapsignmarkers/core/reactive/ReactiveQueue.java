package com.tpwalke2.bluemapsignmarkers.core.reactive;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ReactiveQueue<T> {
    private final ConcurrentLinkedQueue<T> queue;
    private final ExecutorService executor;
    private final ShouldRunCallback shouldRunCallback;
    private final MessageProcessorCallback<T> messageProcessorCallback;

    public ReactiveQueue(
            @NotNull ShouldRunCallback shouldRunCallback,
            @NotNull MessageProcessorCallback<T> messageProcessorCallback) {
        this.queue = new ConcurrentLinkedQueue<>();
        this.executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        this.shouldRunCallback = shouldRunCallback;
        this.messageProcessorCallback = messageProcessorCallback;
    }

    public void enqueue(T message) {
        queue.offer(message);
        if (shouldRunCallback.shouldRun()) {
            executor.submit(this::processMessages);
        }
    }

    private void processMessages() {
        while (!queue.isEmpty() && shouldRunCallback.shouldRun()) {
            T message = queue.poll();
            if (message != null) {
                executor.submit(() -> messageProcessorCallback.processMessage(message));
            }
        }
    }

    public void shutdown() {
        executor.shutdown();
    }
}
