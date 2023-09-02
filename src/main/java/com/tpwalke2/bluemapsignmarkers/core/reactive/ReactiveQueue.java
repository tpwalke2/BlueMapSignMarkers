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
    private final MessageProcessorErrorCallback messageProcessorErrorCallback;

    public ReactiveQueue(
            @NotNull ShouldRunCallback shouldRunCallback,
            @NotNull MessageProcessorCallback<T> messageProcessorCallback,
            @NotNull MessageProcessorErrorCallback messageProcessorErrorCallback) {
        this.queue = new ConcurrentLinkedQueue<>();
        this.executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        this.shouldRunCallback = shouldRunCallback;
        this.messageProcessorCallback = messageProcessorCallback;
        this.messageProcessorErrorCallback = messageProcessorErrorCallback;
    }

    public void enqueue(T message) {
        queue.offer(message);
        this.process();
    }

    public void process() {
        if (shouldRunCallback.shouldRun()) {
            executor.submit(this::processMessages);
        }
    }

    private void processMessages() {
        while (!queue.isEmpty() && shouldRunCallback.shouldRun()) {
            T message = queue.poll();
            if (message != null) {
                try {
                    executor.submit(() -> messageProcessorCallback.processMessage(message));
                } catch (Exception e) {
                    messageProcessorErrorCallback.onError(e);
                }
            }
        }
    }

    public void shutdown() {
        executor.shutdown();
    }
}
