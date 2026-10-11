package com.github.orcas.orchestrator.jdbc.autoconfigure;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.logging.Logger;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class JdbcTransactionRunnerTest {
    private final PlatformTransactionManager local = mock(PlatformTransactionManager.class);
    private final PlatformTransactionManager application = mock(PlatformTransactionManager.class);
    private final JdbcTransactionRunner runner = new JdbcTransactionRunner(local, application,
            Logger.getLogger(JdbcTransactionRunnerTest.class.getName()));

    @AfterEach
    void clearTransactionState() {
        TransactionSynchronizationManager.clear();
    }

    @Test
    void standaloneMutationUsesJdbcManager() {
        var status = new SimpleTransactionStatus();
        when(local.getTransaction(any(TransactionDefinition.class))).thenReturn(status);
        Runnable operation = mock(Runnable.class);

        runner.execute(operation);

        verify(operation).run();
        verify(local).commit(status);
        verifyNoInteractions(application);
    }

    @Test
    void businessTransactionUsesApplicationManager() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        var status = new SimpleTransactionStatus();
        when(application.getTransaction(any(TransactionDefinition.class))).thenReturn(status);
        Runnable operation = mock(Runnable.class);

        runner.execute(operation);

        verify(operation).run();
        verify(application).commit(status);
        verifyNoInteractions(local);
    }
}
