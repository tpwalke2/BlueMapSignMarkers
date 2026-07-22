package com.tpwalke2.bluemapsignmarkers.core.reactive;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

public class ReactiveQueue<T> {
    private static final long SHUTDOWN_AWAIT_SECONDS = 5;

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

    // Blocks (up to SHUTDOWN_AWAIT_SECONDS) until every task already submitted to this generation's
    // executor has finished, so a caller that awaits shutdown() returning can rely on there being no
    // straggler still able to touch shared state afterward — otherwise such a straggler could run after
    // a subsequent resetQueue()/fireReset() replay and clobber the state that replay just established
    // (finding #10, plans/codebase-review-2026-07-11.md). Falls back to shutdownNow() if the timeout
    // elapses, rather than blocking indefinitely on a stuck task.
    //
    // The flag flip + executor.shutdown() call is synchronized with getExecutor() (same reasoning as
    // before: a shutdown() racing a lazy executor creation can't leave a freshly-created executor
    // un-shut-down), but the lock is released before awaitTermination() blocks — held across the wait,
    // it would deadlock against an in-flight task's own getExecutor() call needing the same monitor.
    public void shutdown() {
        ExecutorService toAwait;
        synchronized (this) {
            shutdownRequested = true;
            toAwait = executor;
            if (toAwait != null) {
                toAwait.shutdown();
            }
        }

        if (toAwait == null) {
            return;
        }

        try {
            if (!toAwait.awaitTermination(SHUTDOWN_AWAIT_SECONDS, TimeUnit.SECONDS)) {
                toAwait.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            toAwait.shutdownNow();
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
