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
        try {
            permits.acquire();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new RejectedExecutionException("Interrupted while waiting for workflow capacity", interrupted);
        }

        try {
            delegate.execute(() -> {
                try {
                    task.run();
                } finally {
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
