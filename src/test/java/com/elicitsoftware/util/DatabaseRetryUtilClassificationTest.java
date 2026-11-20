package com.elicitsoftware.util;

/*-
 * ***LICENSE_START***
 * Elicit Survey
 * %%
 * Copyright (C) 2025 - 2026 The Regents of the University of Michigan - Rogel Cancer Center
 * %%
 * PolyForm Noncommercial License 1.0.0
 * <https://polyformproject.org/licenses/noncommercial/1.0.0>
 * ***LICENSE_END***
 */

import jakarta.persistence.PersistenceException;
import jakarta.transaction.TransactionRequiredException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which failures {@link DatabaseRetryUtil} treats as transient, the persistence behind every
 * respondent write (UC-001 auto-registration and login, UC-008 reporting schema build).
 * <p>
 * The retry waits 200 ms between attempts and the delay is not injectable, so these tests
 * classify a failure without waiting: the calling thread is interrupted before the operation
 * runs, which makes the first retry delay end at once. A retriable failure therefore surfaces
 * as "Interrupted during retry delay" after exactly one attempt; a non-retriable one as
 * "Non-retriable exception", also after one attempt. A checked {@link SQLException} reaches the
 * retry loop directly through {@link #sneaky(Throwable)}, as a driver exception would through a
 * JDBC call inside the operation.
 */
class DatabaseRetryUtilClassificationTest {

    @AfterEach
    void clearInterrupt() {
        Thread.interrupted();
    }

    @SuppressWarnings("unchecked")
    private static <E extends Throwable> void sneaky(Throwable t) throws E {
        throw (E) t;
    }

    /** Runs one failing operation on an interrupted thread and returns what came out. */
    private static RuntimeException failOnce(Throwable failure, AtomicInteger attempts) {
        Thread.currentThread().interrupt();
        return assertThrows(RuntimeException.class, () -> DatabaseRetryUtil.executeWithRetry(() -> {
            attempts.incrementAndGet();
            sneaky(failure);
            return null;
        }, "probe-op"));
    }

    private static void assertRetriable(Throwable failure) {
        AtomicInteger attempts = new AtomicInteger();
        RuntimeException thrown = failOnce(failure, attempts);
        assertEquals("Interrupted during retry delay for probe-op", thrown.getMessage());
        assertTrue(thrown.getCause() instanceof InterruptedException, String.valueOf(thrown.getCause()));
        assertEquals(1, attempts.get(), "the interrupted delay ends the retries");
        assertTrue(Thread.interrupted(), "the interrupt is restored for the caller");
    }

    private static void assertNotRetriable(Throwable failure) {
        AtomicInteger attempts = new AtomicInteger();
        RuntimeException thrown = failOnce(failure, attempts);
        assertEquals("Non-retriable exception during probe-op", thrown.getMessage());
        assertSame(failure, thrown.getCause());
        assertEquals(1, attempts.get());
    }

    @ParameterizedTest
    @ValueSource(strings = {"08001", "40P01", "53100", "54000", "57014", "58030"})
    void given_sqlStateOfATransientClass_when_executeWithRetry_then_retried(String sqlState) {
        assertRetriable(new SQLException("driver failure", sqlState));
    }

    @ParameterizedTest
    @ValueSource(strings = {"23505", "42P01", "22001"})
    void given_sqlStateOfAPermanentClass_when_executeWithRetry_then_notRetried(String sqlState) {
        assertNotRetriable(new SQLException("driver failure", sqlState));
    }

    @ParameterizedTest
    @ValueSource(ints = {53200, 53300, 40001})
    void given_noSqlStateAndATransientErrorCode_when_executeWithRetry_then_retried(int errorCode) {
        assertRetriable(new SQLException("driver failure", null, errorCode));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 23505, 53201})
    void given_noSqlStateAndAnotherErrorCode_when_executeWithRetry_then_notRetried(int errorCode) {
        assertNotRetriable(new SQLException("driver failure", null, errorCode));
    }

    @Test
    void given_persistenceExceptionWrappingAPermanentSqlException_when_executeWithRetry_then_notRetried() {
        // The cause decides, even though the wrapper's own message says "connection".
        assertNotRetriable(new PersistenceException("connection lost", new SQLException("dup", "23505")));
    }

    @Test
    void given_persistenceExceptionWrappingATransientSqlException_when_executeWithRetry_then_retried() {
        assertRetriable(new PersistenceException("db failure", new SQLException("gone", "08006")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Query TIMEOUT expired", "Connection is closed", "deadlock detected", "could not obtain lock",
            "serialization failure", "constraint violation on insert", "could not serialize access"})
    void given_persistenceExceptionMessageNamingATransientCondition_when_executeWithRetry_then_retried(String message) {
        assertRetriable(new PersistenceException(message));
    }

    @Test
    void given_persistenceExceptionWithAnotherMessage_when_executeWithRetry_then_notRetried() {
        assertNotRetriable(new PersistenceException("no entity found for query"));
    }

    @Test
    void given_persistenceExceptionWithoutMessage_when_executeWithRetry_then_notRetried() {
        assertNotRetriable(new PersistenceException());
    }

    @Test
    void given_transactionRequired_when_executeWithRetry_then_retried() {
        assertRetriable(new TransactionRequiredException("no transaction"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"read timeout", "Connection reset by peer", "connection closed", "Broken pipe",
            "Connection refused", "No route to host"})
    void given_otherExceptionMessageNamingANetworkFailure_when_executeWithRetry_then_retried(String message) {
        assertRetriable(new IllegalStateException(message));
    }

    @ParameterizedTest
    @ValueSource(strings = {"connection pool exhausted", "lock held", "deadlock"})
    void given_otherExceptionMessageNamingOnlyAPersistenceCondition_when_executeWithRetry_then_notRetried(String message) {
        // These words make a PersistenceException retriable, but not any other exception.
        assertNotRetriable(new IllegalStateException(message));
    }

    @Test
    void given_otherExceptionWithoutMessage_when_executeWithRetry_then_notRetried() {
        assertNotRetriable(new IllegalStateException());
    }

    @Test
    void given_retriableFailureThenSuccess_when_executeWithRetryOnInterruptedThread_then_noSecondAttempt() {
        // Without the delay the loop would try again and succeed; the interrupted delay stops it.
        AtomicInteger attempts = new AtomicInteger();
        Thread.currentThread().interrupt();
        assertThrows(RuntimeException.class, () -> DatabaseRetryUtil.executeWithRetry(() -> {
            if (attempts.incrementAndGet() == 1) {
                throw new PersistenceException("lock timeout");
            }
            return "second";
        }, "probe-op"));
        assertEquals(1, attempts.get());
    }
}
