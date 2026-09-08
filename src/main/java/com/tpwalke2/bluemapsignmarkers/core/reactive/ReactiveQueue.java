package com.tpwalke2.bluemapsignmarkers.core.reactive;

import com.tpwalke2.bluemapsignmarkers.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

public class ReactiveQueue<T> {
    private static final Logger LOGGER = LoggerFactory.getLogger(Constants.MOD_ID);
    public static final long DEFAULT_SHUTDOWN_AWAIT_SECONDS = 5;

    private final ConcurrentLinkedQueue<T> queue;
    // volatile so isShutdown() (called with no lock held, from any thread) sees getExecutor()'s
    // synchronized write without needing its own synchronization (finding #12,
    // plans/codebase-review-2026-07-11.md).
    private volatile ExecutorService executor;
    private volatile boolean shutdownRequested;
    private final ShouldRunCallback shouldRunCallback;
    private final MessageProcessorCallback<T> messageProcessorCallback;
    private final MessageProcessorErrorCallback messageProcessorErrorCallback;
    private final long shutdownAwaitSeconds;

    public ReactiveQueue(
            ShouldRunCallback shouldRunCallback,
            MessageProcessorCallback<T> messageProcessorCallback,
            MessageProcessorErrorCallback messageProcessorErrorCallback) {
        this(shouldRunCallback, messageProcessorCallback, messageProcessorErrorCallback, DEFAULT_SHUTDOWN_AWAIT_SECONDS);
    }

    // Lets a caller (BlueMapAPIConnector, wiring in BMSMConfigV2.getShutdownAwaitSeconds()) configure how
    // long shutdown() waits for in-flight tasks instead of the hardcoded 5s (findings 26/27,
    // agent-context/reviews/full-codebase-review_2026-09-07_0900.md).
    public ReactiveQueue(
            ShouldRunCallback shouldRunCallback,
            MessageProcessorCallback<T> messageProcessorCallback,
            MessageProcessorErrorCallback messageProcessorErrorCallback,
            long shutdownAwaitSeconds) {
        this(shouldRunCallback, messageProcessorCallback, messageProcessorErrorCallback, shutdownAwaitSeconds, null);
    }

    // Visible for testing: lets tests inject a controllable executor (e.g. one that runs tasks
    // synchronously, or one that simulates submission failures) instead of the lazily-created
    // fixed thread pool, so processing order/timing can be made deterministic.
    ReactiveQueue(
            ShouldRunCallback shouldRunCallback,
            MessageProcessorCallback<T> messageProcessorCallback,
            MessageProcessorErrorCallback messageProcessorErrorCallback,
            ExecutorService executor) {
        this(shouldRunCallback, messageProcessorCallback, messageProcessorErrorCallback, DEFAULT_SHUTDOWN_AWAIT_SECONDS, executor);
    }

    // Visible for testing: same as above, plus lets a test control the shutdown-await timeout (e.g. to
    // verify a short configured timeout is actually honored rather than the 5s default).
    ReactiveQueue(
            ShouldRunCallback shouldRunCallback,
            MessageProcessorCallback<T> messageProcessorCallback,
            MessageProcessorErrorCallback messageProcessorErrorCallback,
            long shutdownAwaitSeconds,
            ExecutorService executor) {
        this.queue = new ConcurrentLinkedQueue<>();
        this.shouldRunCallback = shouldRunCallback;
        this.messageProcessorCallback = messageProcessorCallback;
        this.messageProcessorErrorCallback = messageProcessorErrorCallback;
        this.shutdownAwaitSeconds = shutdownAwaitSeconds;
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
                currentExecutor.submit(() -> {
                    try {
                        messageProcessorCallback.processMessage(message);
                    } catch (Exception e) {
                        try {
                            messageProcessorErrorCallback.onError(e);
                        } catch (Exception errorCallbackException) {
                            // A broken error callback must not kill this worker thread mid-drain (leaving
                            // later messages unprocessed) or propagate back to enqueue()'s caller.
                        }
                    }
                });
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

    // Blocks (up to shutdownAwaitSeconds) until every task already submitted to this generation's
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
    //
    // Returns whether a clean stop was actually confirmed. A task that ignores Thread.interrupt() (blocked
    // on non-interruptible I/O, or CPU-bound with no poll point) can still be running after shutdownNow()'s
    // own await times out — this method can't force that task to stop, so it can't uphold the "no straggler
    // touches shared state after shutdown() returns" guarantee. Returning false makes that violation
    // observable to the caller instead of silently returning as if shutdown succeeded cleanly (findings
    // 26/27, agent-context/reviews/full-codebase-review_2026-09-07_0900.md).
    public boolean shutdown() {
        ExecutorService toAwait;
        synchronized (this) {
            shutdownRequested = true;
            toAwait = executor;
            if (toAwait != null) {
                toAwait.shutdown();
            }
        }

        if (toAwait == null) {
            return true;
        }

        try {
            if (toAwait.awaitTermination(shutdownAwaitSeconds, TimeUnit.SECONDS)) {
                return true;
            }

            toAwait.shutdownNow();
            // Give the forced cancellation a real chance to converge — returning immediately after
            // shutdownNow() would still let a task caught mid-run touch shared state after this
            // method returns, the exact guarantee this method exists to provide.
            if (toAwait.awaitTermination(shutdownAwaitSeconds, TimeUnit.SECONDS)) {
                return true;
            }

            LOGGER.warn("ReactiveQueue shutdown could not confirm all in-flight tasks stopped within {}s of "
                    + "shutdownNow(); a straggler task ignored interruption and may still be running, and could "
                    + "observe or mutate shared state after this shutdown() call returns", shutdownAwaitSeconds);
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            toAwait.shutdownNow();
            LOGGER.warn("Interrupted while awaiting ReactiveQueue shutdown; could not confirm all in-flight "
                    + "tasks stopped before returning");
            return false;
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
