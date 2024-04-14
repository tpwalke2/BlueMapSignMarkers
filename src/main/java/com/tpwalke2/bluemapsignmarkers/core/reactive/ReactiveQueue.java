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
        this.queue = new ConcurrentLinkedQueue<>();
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
