package com.tpwalke2.bluemapsignmarkers.core.reactive;

import com.tpwalke2.bluemapsignmarkers.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class ReactiveQueue<T> {
    private static final Logger LOGGER = LoggerFactory.getLogger(Constants.MOD_ID);
    public static final long DEFAULT_SHUTDOWN_AWAIT_SECONDS = 5;
    // Bounded in practice today by sign count, but this class is documented (see AGENTS.md) as reusable
    // for other message types, so enqueue() needs a defensive cap rather than accepting unbounded backlog
    // (finding 61, agent-context/reviews/full-codebase-review_2026-09-07_0900.md). Callers dispatch from
    // the main server thread and can't tolerate enqueue() blocking, so overflow rejects (logs + drops)
    // rather than blocking. Set high (rather than a tighter number) because SignManager.reset() (fired by
    // /bluemap reload) re-dispatches one message per cached sign, and region-sharded persistence exists
    // specifically to support servers with many tracked signs - a low cap would risk silently dropping
    // markers on reload for exactly the large-server case this mod is meant to scale to.
    public static final int DEFAULT_CAPACITY = 100_000;
    // How often, at most, a run of rejected-for-capacity messages logs a WARN - sustained overflow (e.g.
    // BlueMap down for a long stretch under continued sign edits) must not flood the log with one line per
    // rejected message.
    private static final long CAPACITY_WARNING_THROTTLE_MILLIS = 1_000;

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
    private final int capacity;
    private final AtomicInteger queuedCount = new AtomicInteger(0);
    private final AtomicLong lastCapacityWarningAtMillis = new AtomicLong(0);
    // Guards against a burst of enqueue() calls each submitting their own redundant drain-loop task
    // (finding 62, agent-context/reviews/full-codebase-review_2026-09-07_0900.md). Only the thread that
    // wins the compareAndSet actually submits processMessages(); everyone else's message is still safe
    // because it's already on `queue` and the active drain loop drains until the queue is empty.
    private final AtomicBoolean draining = new AtomicBoolean(false);

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
        this(shouldRunCallback, messageProcessorCallback, messageProcessorErrorCallback, shutdownAwaitSeconds, null, DEFAULT_CAPACITY);
    }

    // Visible for testing: lets tests inject a controllable executor (e.g. one that runs tasks
    // synchronously, or one that simulates submission failures) instead of the lazily-created
    // fixed thread pool, so processing order/timing can be made deterministic.
    ReactiveQueue(
            ShouldRunCallback shouldRunCallback,
            MessageProcessorCallback<T> messageProcessorCallback,
            MessageProcessorErrorCallback messageProcessorErrorCallback,
            ExecutorService executor) {
        this(shouldRunCallback, messageProcessorCallback, messageProcessorErrorCallback, DEFAULT_SHUTDOWN_AWAIT_SECONDS, executor, DEFAULT_CAPACITY);
    }

    // Visible for testing: same as above, plus lets a test control the shutdown-await timeout (e.g. to
    // verify a short configured timeout is actually honored rather than the 5s default).
    ReactiveQueue(
            ShouldRunCallback shouldRunCallback,
            MessageProcessorCallback<T> messageProcessorCallback,
            MessageProcessorErrorCallback messageProcessorErrorCallback,
            long shutdownAwaitSeconds,
            ExecutorService executor) {
        this(shouldRunCallback, messageProcessorCallback, messageProcessorErrorCallback, shutdownAwaitSeconds, executor, DEFAULT_CAPACITY);
    }

    // Visible for testing: lets a test use a small capacity so overflow behavior can be exercised
    // without actually enqueuing DEFAULT_CAPACITY messages.
    ReactiveQueue(
            ShouldRunCallback shouldRunCallback,
            MessageProcessorCallback<T> messageProcessorCallback,
            MessageProcessorErrorCallback messageProcessorErrorCallback,
            ExecutorService executor,
            int capacity) {
        this(shouldRunCallback, messageProcessorCallback, messageProcessorErrorCallback, DEFAULT_SHUTDOWN_AWAIT_SECONDS, executor, capacity);
    }

    private ReactiveQueue(
            ShouldRunCallback shouldRunCallback,
            MessageProcessorCallback<T> messageProcessorCallback,
            MessageProcessorErrorCallback messageProcessorErrorCallback,
            long shutdownAwaitSeconds,
            ExecutorService executor,
            int capacity) {
        this.queue = new ConcurrentLinkedQueue<>();
        this.shouldRunCallback = shouldRunCallback;
        this.messageProcessorCallback = messageProcessorCallback;
        this.messageProcessorErrorCallback = messageProcessorErrorCallback;
        this.shutdownAwaitSeconds = shutdownAwaitSeconds;
        this.executor = executor;
        this.capacity = capacity;
    }

    public void enqueue(T message) {
        if (!tryReserveSlot()) {
            warnAtCapacity(message);
            return;
        }

        queue.offer(message);
        this.process();
    }

    private void warnAtCapacity(T message) {
        var now = System.currentTimeMillis();
        var last = lastCapacityWarningAtMillis.get();
        if (now - last < CAPACITY_WARNING_THROTTLE_MILLIS
                || !lastCapacityWarningAtMillis.compareAndSet(last, now)) {
            return;
        }

        LOGGER.warn("ReactiveQueue at capacity ({}); rejecting enqueued message (further rejections within {}ms "
                + "will not be individually logged): {}", capacity, CAPACITY_WARNING_THROTTLE_MILLIS, message);
    }

    // Reserves a capacity slot only if one is free. Not perfectly atomic with queue.offer() (a slot could
    // in theory be reserved slightly ahead of the item actually landing on `queue`), but that's fine here:
    // it can only ever under-count concurrent in-flight enqueues by a small, bounded margin, never let the
    // queue grow unbounded.
    private boolean tryReserveSlot() {
        while (true) {
            int current = queuedCount.get();
            if (current >= capacity) {
                return false;
            }
            if (queuedCount.compareAndSet(current, current + 1)) {
                return true;
            }
        }
    }

    public void process() {
        if (shutdownRequested || !shouldRunCallback.shouldRun()) {
            return;
        }

        if (!draining.compareAndSet(false, true)) {
            // A drain loop is already active (or about to be submitted); it will drain this message too,
            // so submitting another one would be redundant.
            return;
        }

        var currentExecutor = getExecutor();
        if (currentExecutor == null) {
            draining.set(false);
            return;
        }

        try {
            currentExecutor.submit(this::processMessages);
        } catch (RejectedExecutionException e) {
            // Shut down concurrently between the checks above and this submission; this instance is
            // retired, nothing more to schedule.
            draining.set(false);
        }
    }

    // Shared by processMessages()'s while-condition and its post-drain recheck below, so the two can't
    // drift apart if this condition is ever changed in one place but not the other.
    private boolean canContinueDraining() {
        return !shutdownRequested && !queue.isEmpty() && shouldRunCallback.shouldRun();
    }

    private void processMessages() {
        try {
            while (canContinueDraining()) {
                T message = queue.poll();
                if (message == null) continue;
                queuedCount.decrementAndGet();

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
        } finally {
            draining.set(false);
            // Closes the race window between the loop above observing "nothing left to do" (queue empty,
            // or shouldRun() false) and this flag actually clearing: a message enqueue()'d or a process()
            // call landing in that window would otherwise see draining still true, skip submitting, and
            // never get drained. Re-checking after the flag clears guarantees this thread (or whichever
            // enqueue() call raced it) picks it back up instead.
            if (canContinueDraining()) {
                process();
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
