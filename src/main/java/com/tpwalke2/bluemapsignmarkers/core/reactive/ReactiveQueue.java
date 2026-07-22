package com.tpwalke2.bluemapsignmarkers.core.reactive;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

public class ReactiveQueue<T> {
    private final ConcurrentLinkedQueue<T> queue;
    private ExecutorService executor;
    private volatile boolean shutdownRequested;
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
        if (shutdownRequested || !shouldRunCallback.shouldRun()) {
            return;
        }

        var currentExecutor = getExecutor();
        if (currentExecutor == null) {
            return;
        }

        try {
            currentExecutor.submit(this::processMessages);
        } catch (RejectedExecutionException e) {
            // Shut down concurrently between the checks above and this submission; this instance is
            // retired, nothing more to schedule.
        }
    }

    private void processMessages() {
        while (!shutdownRequested && !queue.isEmpty() && shouldRunCallback.shouldRun()) {
            T message = queue.poll();
            if (message == null) continue;

            var currentExecutor = getExecutor();
            if (currentExecutor == null) return;

            try {
                currentExecutor.submit(() -> messageProcessorCallback.processMessage(message));
            } catch (RejectedExecutionException e) {
                // Shut down concurrently between poll() and this submission; expected during a normal
                // shutdown race, not a processing failure worth reporting to the error callback.
                return;
            } catch (Exception e) {
                messageProcessorErrorCallback.onError(e);
            }
        }
    }

    public boolean isShutdown() {
        return shutdownRequested || executor == null || executor.isShutdown();
    }

    // synchronized with getExecutor() so a shutdown() racing a lazy executor creation can't leave a
    // freshly-created executor un-shut-down: either shutdownRequested is visible before the new
    // executor is assigned (so it's never created), or the new executor is assigned first and this
    // call observes and shuts it down.
    public synchronized void shutdown() {
        shutdownRequested = true;
        if (executor != null) {
            executor.shutdown();
        }
    }

    // Once shutdownRequested is set, this never creates a replacement executor — a shut-down queue is
    // permanently retired rather than self-healing (see finding #2, plans/codebase-review-2026-07-11.md).
    private synchronized ExecutorService getExecutor() {
        if (executor == null && !shutdownRequested) {
            executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        }

        return executor;
    }
}
