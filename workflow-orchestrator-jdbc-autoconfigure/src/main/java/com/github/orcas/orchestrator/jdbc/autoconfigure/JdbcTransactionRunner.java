package com.github.orcas.orchestrator.jdbc.autoconfigure;

import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

/** Runs short JDBC mutations in retried transactions. */
final class JdbcTransactionRunner {
    private static final int MAX_ATTEMPTS = 4;
    private static final long BASE_DELAY_MILLIS = 20L;

    private final TransactionTemplate transactionTemplate;
    private final Logger log;

    JdbcTransactionRunner(PlatformTransactionManager transactionManager, Logger log) {
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.log = log;
    }

    void execute(Runnable operation) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                transactionTemplate.executeWithoutResult(status -> operation.run());
                return;
            } catch (ConcurrencyFailureException error) {
                if (attempt == MAX_ATTEMPTS) {
                    log.fine(
                            "Giving up after " + attempt + " attempts due to a persistent transient concurrency failure: " + error.toString());
                    throw error;
                }

                long delay = BASE_DELAY_MILLIS * attempt
                        + ThreadLocalRandom.current().nextLong(BASE_DELAY_MILLIS);
                log.fine(
                        "Transient concurrency failure on attempt " + attempt + "/" + MAX_ATTEMPTS + " (" + error.getMessage() + "); retrying in " + delay + "ms");

                sleep(delay, error);
            }
        }
    }

    private static void sleep(long delay, ConcurrencyFailureException error) {
        try {
            Thread.sleep(delay);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw error;
        }
    }
}
