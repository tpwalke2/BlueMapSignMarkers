package com.tpwalke2.bluemapsignmarkers.core.reactive;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReactiveQueueTest {

    @Test
    void enqueueDeliversMessageToProcessor() throws Exception {
        var received = new ConcurrentLinkedQueue<String>();
        var delivered = new CountDownLatch(1);
        var queue = new ReactiveQueue<String>(
                () -> true,
                message -> {
                    received.add(message);
                    delivered.countDown();
                },
                error -> { });

        queue.enqueue("hello");

        assertTrue(delivered.await(5, TimeUnit.SECONDS), "message was never delivered to the processor");
        assertEquals(List.of("hello"), List.copyOf(received));
    }

    @Test
    void enqueueDeliversEachOfMultipleMessagesExactlyOnce() throws Exception {
        var expected = IntStream.range(0, 5).mapToObj(i -> "message-" + i).collect(Collectors.toSet());
        var received = new ConcurrentLinkedQueue<String>();
        var delivered = new CountDownLatch(expected.size());
        var queue = new ReactiveQueue<String>(
                () -> true,
                message -> {
                    received.add(message);
                    delivered.countDown();
                },
                error -> { });

        expected.forEach(queue::enqueue);

        assertTrue(delivered.await(5, TimeUnit.SECONDS), "not all messages were delivered");
        assertEquals(expected, Set.copyOf(received));
    }

    @Test
    void isShutdownIsTrueBeforeAnyWorkHasBeenScheduled() {
        var queue = new ReactiveQueue<String>(() -> true, message -> { }, error -> { });

        assertTrue(queue.isShutdown());
    }

    @Test
    void isShutdownIsFalseOnceWorkHasBeenScheduled() throws Exception {
        var delivered = new CountDownLatch(1);
        var queue = new ReactiveQueue<String>(() -> true, message -> delivered.countDown(), error -> { });

        queue.enqueue("hello");
        assertTrue(delivered.await(5, TimeUnit.SECONDS));

        assertFalse(queue.isShutdown());
    }

    @Test
    void shutdownMarksTheQueueAsShutdown() throws Exception {
        var delivered = new CountDownLatch(1);
        var queue = new ReactiveQueue<String>(() -> true, message -> delivered.countDown(), error -> { });
        queue.enqueue("hello");
        assertTrue(delivered.await(5, TimeUnit.SECONDS));

        queue.shutdown();

        assertTrue(queue.isShutdown());
    }

    // Fixed for finding #2 (plans/codebase-review-2026-07-11.md): shutdown() permanently retires the
    // queue. getExecutor() no longer resurrects a fresh executor just because the old one was shut down,
    // so a later enqueue is left queued but never drained by this instance.
    @Test
    void shutdownPermanentlyStopsTheQueueFromProcessingLaterEnqueues() {
        var executor = new SynchronousExecutorService();
        var received = new ArrayList<String>();
        var queue = new ReactiveQueue<String>(() -> true, received::add, error -> { }, executor);
        queue.shutdown();

        queue.enqueue("hello");

        assertTrue(received.isEmpty(),
                "shutdown queue should not self-heal a new executor and process later enqueues");
        assertTrue(queue.isShutdown());
    }

    // Regression test for finding #2: previously, a shutdown() call racing with a still-draining
    // processMessages() loop caused the loop's next iteration to observe the now-shut-down executor and
    // silently spin up a brand-new one, un-retiring the queue and leaking non-daemon threads. shutdown()
    // and getExecutor() now share the same monitor, so once shutdownRequested flips, nothing on this
    // instance can create a replacement executor.
    @Test
    void shutdownRacingMidDrainStopsTheLoopWithoutSpawningAReplacementExecutor() {
        var executor = new SynchronousExecutorService();
        var received = new ArrayList<String>();
        var queueRef = new AtomicReference<ReactiveQueue<String>>();
        var queue = new ReactiveQueue<String>(
                () -> true,
                message -> {
                    received.add(message);
                    if (message.equals("first")) {
                        queueRef.get().shutdown();
                    }
                },
                error -> { },
                executor);
        queueRef.set(queue);

        queue.enqueue("first");
        queue.enqueue("second");

        assertEquals(List.of("first"), received,
                "queue should stop draining as soon as it's shut down mid-loop, not self-heal and process more");
        assertTrue(queue.isShutdown(),
                "queue should remain shut down rather than silently spinning up a fresh executor");
    }

    @Test
    void processDoesNotScheduleWorkWhileShouldRunIsFalse() {
        var invocations = new AtomicInteger();
        var queue = new ReactiveQueue<String>(() -> false, message -> invocations.incrementAndGet(), error -> { });

        queue.enqueue("hello");

        // shouldRun() is checked before any executor is touched, so nothing was ever scheduled.
        assertEquals(0, invocations.get());
        assertTrue(queue.isShutdown());
    }

    @Test
    void processResumesQueuedMessagesOnceShouldRunBecomesTrue() throws Exception {
        var shouldRun = new AtomicBoolean(false);
        var delivered = new CountDownLatch(1);
        var received = new ConcurrentLinkedQueue<String>();
        var queue = new ReactiveQueue<String>(
                shouldRun::get,
                message -> {
                    received.add(message);
                    delivered.countDown();
                },
                error -> { });
        queue.enqueue("hello");
        assertEquals(1, delivered.getCount(), "message should not yet be processed while shouldRun is false");

        shouldRun.set(true);
        queue.process();

        assertTrue(delivered.await(5, TimeUnit.SECONDS), "message queued before shouldRun flipped true was never processed");
        assertEquals(List.of("hello"), List.copyOf(received));
    }

    @Test
    void shouldRunGoingFalseMidDrainLeavesTheRestOfTheBacklogQueuedUntilResumed() {
        var received = new ArrayList<String>();
        var remainingTrueAnswers = new AtomicInteger(0);
        var resumed = new AtomicBoolean(false);
        var queue = new ReactiveQueue<String>(
                () -> resumed.get() || remainingTrueAnswers.getAndUpdate(v -> Math.max(v - 1, 0)) > 0,
                received::add,
                error -> { },
                new SynchronousExecutorService());
        // shouldRun is false so both enqueues just queue the messages without draining anything yet.
        queue.enqueue("first");
        queue.enqueue("second");
        assertTrue(received.isEmpty());

        // Allow exactly two "true" answers: one for process()'s own check, one for the drain loop's
        // first iteration. The loop's second-iteration check then sees zero remaining and stops,
        // leaving "second" backlogged.
        remainingTrueAnswers.set(2);
        queue.process();
        assertEquals(List.of("first"), received, "drain loop should have stopped after shouldRun turned false");

        resumed.set(true);
        queue.process();
        assertEquals(List.of("first", "second"), received, "remaining backlog should drain once shouldRun is true again");
    }

    @Test
    void submissionFailureForOneMessageInvokesErrorCallbackAndLeavesLaterMessagesUnaffected() {
        var executor = new SynchronousExecutorService();
        var errors = new ArrayList<Throwable>();
        var received = new ArrayList<String>();
        var shouldRun = new AtomicBoolean(false);
        var queue = new ReactiveQueue<String>(shouldRun::get, received::add, errors::add, executor);
        // Queue both messages before any draining starts, so a single process() call below performs
        // exactly one outer drain-loop submission followed by one inner per-message submission each.
        queue.enqueue("first");
        queue.enqueue("second");

        shouldRun.set(true);
        // Call #1 is process()'s own outer submission (succeeds); call #2 is the inner submission for
        // "first" (made to fail here, simulating a submission failure); call #3, the inner submission
        // for "second", succeeds normally.
        executor.throwOnNthExecuteFromNow(2);
        queue.process();

        assertEquals(1, errors.size(), "the simulated submission failure should have reached the error callback");
        assertEquals(List.of("second"), received, "the failure on the first message should not stop later messages");
    }

    // Documents current behavior: messageProcessorCallback is invoked inside a task handed to
    // ExecutorService.submit(), so an exception it throws is captured on that task's Future and never
    // surfaces to the try/catch in processMessages() (nothing ever calls Future.get()). The error callback
    // is only reachable via a submission-time failure (see the test above), not a processing exception.
    // Known gap for the concurrency-hardening pass.
    @Test
    void exceptionThrownByProcessorCallbackIsNotSurfacedToTheErrorCallback() throws Exception {
        var errors = new ArrayList<Throwable>();
        var secondMessageDelivered = new CountDownLatch(1);
        var queue = new ReactiveQueue<String>(
                () -> true,
                message -> {
                    if (message.equals("first")) {
                        throw new RuntimeException("boom");
                    }
                    secondMessageDelivered.countDown();
                },
                errors::add);

        queue.enqueue("first");
        queue.enqueue("second");

        assertTrue(secondMessageDelivered.await(5, TimeUnit.SECONDS), "later message should still be processed");
        assertTrue(errors.isEmpty(), "current implementation swallows processor exceptions rather than reporting them");
    }

    // Characterizes the current "before" behavior ahead of the concurrency-hardening pass: enqueue()
    // unconditionally submits a fresh drain loop on every call rather than checking whether one is already
    // running, so a burst of concurrent enqueues spawns more drain-loop submissions than there are messages.
    // Despite that redundancy, every message is still delivered exactly once.
    @Test
    void concurrentEnqueueBurstDeliversEveryMessageExactlyOnceDespiteRedundantDrainLoopFanOut() throws Exception {
        final int messageCount = 20;
        var submissionCount = new AtomicInteger();
        var delegate = Executors.newFixedThreadPool(Math.max(4, Runtime.getRuntime().availableProcessors()));
        var countingExecutor = new CountingExecutorService(delegate, submissionCount);
        var delivered = new CountDownLatch(messageCount);
        var received = new ConcurrentLinkedQueue<Integer>();
        var queue = new ReactiveQueue<Integer>(
                () -> true,
                message -> {
                    received.add(message);
                    delivered.countDown();
                },
                error -> { },
                countingExecutor);

        var starters = Executors.newFixedThreadPool(messageCount);
        var ready = new CountDownLatch(messageCount);
        var go = new CountDownLatch(1);
        try {
            for (int i = 0; i < messageCount; i++) {
                final int message = i;
                starters.submit(() -> {
                    ready.countDown();
                    awaitUninterruptibly(go);
                    queue.enqueue(message);
                });
            }
            assertTrue(ready.await(5, TimeUnit.SECONDS), "a starter thread failed to start in time");
            go.countDown();

            assertTrue(delivered.await(5, TimeUnit.SECONDS), "not all messages were delivered");
            assertEquals(
                    IntStream.range(0, messageCount).boxed().collect(Collectors.toSet()),
                    Set.copyOf(received));
            assertTrue(
                    submissionCount.get() > messageCount,
                    "expected more executor submissions than messages (redundant drain-loop fan-out), got "
                            + submissionCount.get());
        } finally {
            starters.shutdownNow();
            delegate.shutdownNow();
        }
    }

    private static void awaitUninterruptibly(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Runs every submitted task synchronously on the calling thread, so a ReactiveQueue wired to this
     * executor behaves deterministically for tests: enqueue()/process() only return once the entire
     * drain loop (and every message it submitted) has finished running.
     */
    private static final class SynchronousExecutorService extends AbstractExecutorService {
        private volatile boolean shutdown = false;
        private final AtomicInteger callsUntilThrow = new AtomicInteger(0);

        /** Makes the n-th execute() call from now (1-based) throw instead of running its task. */
        void throwOnNthExecuteFromNow(int n) {
            callsUntilThrow.set(n);
        }

        @Override
        public void execute(Runnable command) {
            if (callsUntilThrow.get() > 0 && callsUntilThrow.decrementAndGet() == 0) {
                throw new RuntimeException("simulated submission failure");
            }
            command.run();
        }

        @Override
        public void shutdown() {
            shutdown = true;
        }

        @Override
        public List<Runnable> shutdownNow() {
            shutdown = true;
            return List.of();
        }

        @Override
        public boolean isShutdown() {
            return shutdown;
        }

        @Override
        public boolean isTerminated() {
            return shutdown;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return true;
        }
    }

    /** Delegates every submission to a real executor while counting how many were made. */
    private static final class CountingExecutorService extends AbstractExecutorService {
        private final ExecutorService delegate;
        private final AtomicInteger submissionCount;

        private CountingExecutorService(ExecutorService delegate, AtomicInteger submissionCount) {
            this.delegate = delegate;
            this.submissionCount = submissionCount;
        }

        @Override
        public void execute(Runnable command) {
            submissionCount.incrementAndGet();
            delegate.execute(command);
        }

        @Override
        public void shutdown() {
            delegate.shutdown();
        }

        @Override
        public List<Runnable> shutdownNow() {
            return delegate.shutdownNow();
        }

        @Override
        public boolean isShutdown() {
            return delegate.isShutdown();
        }

        @Override
        public boolean isTerminated() {
            return delegate.isTerminated();
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
            return delegate.awaitTermination(timeout, unit);
        }
    }
}
