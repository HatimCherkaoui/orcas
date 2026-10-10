package com.github.orcas.orchestrator.autoconfigure.core;

import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;

/** Limits concurrent workflow tasks while retaining virtual-thread support. */
final class BoundedWorkflowExecutor implements Executor, AutoCloseable {
    private final ExecutorService delegate;
    private final Semaphore permits;
    private final ThreadLocal<Boolean> worker = new ThreadLocal<>();

    BoundedWorkflowExecutor(boolean virtualThreads, int concurrency) {
        if (concurrency < 1) throw new IllegalArgumentException("Workflow concurrency must be at least 1");
        delegate = virtualThreads
                ? Executors.newVirtualThreadPerTaskExecutor()
                : Executors.newFixedThreadPool(concurrency);
        permits = new Semaphore(concurrency);
    }

    @Override
    public void execute(Runnable task) {
        Objects.requireNonNull(task, "task");
        // CompletableFuture completion callbacks can submit to this executor
        // before their parent task releases its permit. Run nested submissions
        // on that same worker so a saturated pool cannot wait on itself.
        if (Boolean.TRUE.equals(worker.get())) {
            task.run();
            return;
        }
        try {
            permits.acquire();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new RejectedExecutionException("Interrupted while waiting for workflow capacity", interrupted);
        }

        try {
            delegate.execute(() -> {
                worker.set(true);
                try {
                    task.run();
                } finally {
                    worker.remove();
                    permits.release();
                }
            });
        } catch (RuntimeException rejected) {
            permits.release();
            throw rejected;
        }
    }

    @Override
    public void close() {
        delegate.close();
    }
}
