package com.tpwalke2.bluemapsignmarkers.core.reactive;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ReactiveQueue<T> {
    private final ConcurrentLinkedQueue<T> queue;
    private ExecutorService executor;
    private final ShouldRunCallback shouldRunCallback;
    private final MessageProcessorCallback<T> messageProcessorCallback;
    private final MessageProcessorErrorCallback messageProcessorErrorCallback;

    public ReactiveQueue(
            ShouldRunCallback shouldRunCallback,
            MessageProcessorCallback<T> messageProcessorCallback,
            MessageProcessorErrorCallback messageProcessorErrorCallback) {
        this(shouldRunCallback, messageProcessorCallback, messageProcessorErrorCallback, null);
    }

    // Visible for testing: lets tests inject a controllable executor (e.g. one that runs tasks
    // synchronously, or one that simulates submission failures) instead of the lazily-created
    // fixed thread pool, so processing order/timing can be made deterministic.
    ReactiveQueue(
            ShouldRunCallback shouldRunCallback,
            MessageProcessorCallback<T> messageProcessorCallback,
            MessageProcessorErrorCallback messageProcessorErrorCallback,
            ExecutorService executor) {
        this.queue = new ConcurrentLinkedQueue<>();
        this.shouldRunCallback = shouldRunCallback;
        this.messageProcessorCallback = messageProcessorCallback;
        this.messageProcessorErrorCallback = messageProcessorErrorCallback;
        this.executor = executor;
    }

    public void enqueue(T message) {
        queue.offer(message);
        this.process();
    }

    public void process() {
        if (shouldRunCallback.shouldRun()) {
            getExecutor().submit(this::processMessages);
        }
    }

    private void processMessages() {
        while (!queue.isEmpty() && shouldRunCallback.shouldRun()) {
            T message = queue.poll();
            if (message != null) {
                try {
                    getExecutor().submit(() -> messageProcessorCallback.processMessage(message));
                } catch (Exception e) {
                    messageProcessorErrorCallback.onError(e);
                }
            }
        }
    }

    public boolean isShutdown() {
        return executor == null || executor.isShutdown();
    }

    public void shutdown() {
        getExecutor().shutdown();
    }

    private synchronized ExecutorService getExecutor() {
        if (isShutdown()) {
            executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        }

        return executor;
    }
}
