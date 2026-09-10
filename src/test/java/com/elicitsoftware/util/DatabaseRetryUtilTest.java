package com.elicitsoftware.util;

/*-
 * ***LICENSE_START***
 * Elicit Survey
 * %%
 * Copyright (C) 2025 The Regents of the University of Michigan - Rogel Cancer Center
 * %%
 * PolyForm Noncommercial License 1.0.0
 * <https://polyformproject.org/licenses/noncommercial/1.0.0>
 * ***LICENSE_END***
 */

import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.sql.SQLTransientException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * isRetriableException() is private and only reachable through executeWithRetry(), so
 * these tests exercise it black-box via the retry behavior it drives. A raw SQLException
 * can't be thrown directly from a Supplier (Supplier#get() declares no checked exceptions),
 * but PersistenceException(String, Throwable) is unchecked and is exactly how Hibernate
 * really surfaces driver-level SQLExceptions -- wrapping one as the cause exercises the
 * SQLException-classification branches the same way production code would.
 */
class DatabaseRetryUtilTest {

    private static PersistenceException wrapping(SQLException cause) {
        return new PersistenceException("db failure", cause);
    }

    // UC-002/BR-004 adjacent: DatabaseRetryUtil backs answer/respondent persistence retries
    @Test
    void executeWithRetry_succeedsOnFirstAttempt_returnsValue() {
        String result = DatabaseRetryUtil.executeWithRetry(() -> "ok", "first-try");
        assertEquals("ok", result);
    }

    @Test
    void executeWithRetry_runnableOverload_succeedsOnFirstAttempt() {
        AtomicInteger ran = new AtomicInteger();
        DatabaseRetryUtil.executeWithRetry((Runnable) ran::incrementAndGet, "runnable-op");
        assertEquals(1, ran.get());
    }

    @Test
    void executeWithRetry_transientSqlException_retriesThenSucceeds() {
        AtomicInteger attempts = new AtomicInteger();
        String result = DatabaseRetryUtil.executeWithRetry(() -> {
            if (attempts.incrementAndGet() == 1) {
                throw wrapping(new SQLTransientException("connection dropped"));
            }
            return "recovered";
        }, "transient-op");
        assertEquals("recovered", result);
        assertEquals(2, attempts.get());
    }

    @Test
    void executeWithRetry_connectionExceptionSqlState_retriesThenSucceeds() {
        AtomicInteger attempts = new AtomicInteger();
        String result = DatabaseRetryUtil.executeWithRetry(() -> {
            if (attempts.incrementAndGet() == 1) {
                // sqlState prefix "08" = connection exception
                throw wrapping(new SQLException("connection exception", "08006"));
            }
            return "recovered";
        }, "sqlstate-op");
        assertEquals("recovered", result);
        assertEquals(2, attempts.get());
    }

    @Test
    void executeWithRetry_serializationFailureErrorCode_retriesThenSucceeds() {
        AtomicInteger attempts = new AtomicInteger();
        String result = DatabaseRetryUtil.executeWithRetry(() -> {
            if (attempts.incrementAndGet() == 1) {
                // null sqlState falls through to the errorCode check
                throw wrapping(new SQLException("serialization failure", null, 40001));
            }
            return "recovered";
        }, "errorcode-op");
        assertEquals("recovered", result);
        assertEquals(2, attempts.get());
    }

    @Test
    void executeWithRetry_persistenceExceptionMessageKeyword_retriesThenSucceeds() {
        AtomicInteger attempts = new AtomicInteger();
        String result = DatabaseRetryUtil.executeWithRetry(() -> {
            if (attempts.incrementAndGet() == 1) {
                throw new PersistenceException("could not serialize access due to concurrent update");
            }
            return "recovered";
        }, "message-op");
        assertEquals("recovered", result);
        assertEquals(2, attempts.get());
    }

    @Test
    void executeWithRetry_genericConnectionRefusedMessage_retriesThenSucceeds() {
        AtomicInteger attempts = new AtomicInteger();
        String result = DatabaseRetryUtil.executeWithRetry(() -> {
            if (attempts.incrementAndGet() == 1) {
                throw new RuntimeException("Connection refused by remote host");
            }
            return "recovered";
        }, "generic-message-op");
        assertEquals("recovered", result);
        assertEquals(2, attempts.get());
    }

    @Test
    void executeWithRetry_nonRetriableException_throwsImmediatelyWithoutRetrying() {
        AtomicInteger attempts = new AtomicInteger();
        RuntimeException thrown = assertThrows(RuntimeException.class, () ->
                DatabaseRetryUtil.executeWithRetry(() -> {
                    attempts.incrementAndGet();
                    throw new IllegalStateException("business rule violation, not a db problem");
                }, "non-retriable-op"));
        assertEquals(1, attempts.get());
        assertTrue(thrown.getMessage().contains("Non-retriable exception"));
    }

    @Test
    void executeWithRetry_alwaysFailingRetriableException_exhaustsRetriesThenThrows() {
        AtomicInteger attempts = new AtomicInteger();
        RuntimeException thrown = assertThrows(RuntimeException.class, () ->
                DatabaseRetryUtil.executeWithRetry(() -> {
                    attempts.incrementAndGet();
                    throw wrapping(new SQLTransientException("always fails"));
                }, "exhausted-op"));
        assertEquals(5, attempts.get());
        assertTrue(thrown.getMessage().contains("All retry attempts failed"));
    }
}
