package com.github.orcas.orchestrator.autoconfigure.core;

import org.junit.jupiter.api.Test;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReferenceArray;
import static org.assertj.core.api.Assertions.assertThat;

class BoundedWorkflowExecutorTest {
    @Test
    void saturatedWorkersCanSubmitAndAwaitTheirOwnContinuations() throws Exception {
        for (boolean virtualThreads : new boolean[]{true, false}) {
            var executor = new BoundedWorkflowExecutor(virtualThreads, 2);
            var ready = new CountDownLatch(2);
            var release = new CountDownLatch(1);
            var workers = new AtomicReferenceArray<Thread>(2);
            var futures = new CompletableFuture<?>[2];
            try {
                for (int index = 0; index < 2; index++) {
                    int slot = index;
                    futures[index] = CompletableFuture.runAsync(() -> {
                        workers.set(slot, Thread.currentThread());
                        ready.countDown();
                        try { release.await(); }
                        catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new RuntimeException(error); }
                        CompletableFuture.supplyAsync(() -> 42, executor)
                                .thenAcceptAsync(value -> assertThat(value).isEqualTo(42), executor).join();
                    }, executor);
                }
                assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
                release.countDown();
                CompletableFuture.allOf(futures).get(5, TimeUnit.SECONDS);
            } finally {
                release.countDown();
                // Also cleans up a regressed implementation that blocks acquiring
                // a second permit, without hanging the test JVM on close().
                for (int index = 0; index < 2; index++) {
                    Thread thread = workers.get(index);
                    if (thread != null) thread.interrupt();
                }
                executor.close();
            }
        }
    }
}
