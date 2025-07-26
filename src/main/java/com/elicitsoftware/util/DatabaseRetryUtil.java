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

import io.quarkus.logging.Log;
import jakarta.persistence.PersistenceException;
import jakarta.transaction.TransactionRequiredException;

import java.sql.SQLException;
import java.sql.SQLTransientException;
import java.util.function.Supplier;

/**
 * Utility class for handling database operations with retry logic.
 * This class provides retry functionality for database operations that may fail
 * due to transient errors such as connection timeouts, lock conflicts, or temporary
 * database unavailability.
 */
public class DatabaseRetryUtil {
    
    private static final int MAX_RETRIES = 5;
    private static final long RETRY_DELAY_MS = 200;
    
    /**
     * Executes a database operation with retry logic.
     * 
     * @param operation the database operation to execute
     * @param operationName a descriptive name for the operation (for logging)
     * @param <T> the return type of the operation
     * @return the result of the operation
     * @throws RuntimeException if all retry attempts fail
     */
    public static <T> T executeWithRetry(Supplier<T> operation, String operationName) {
        Exception lastException = null;
        
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                Log.debug("Executing " + operationName + " (attempt " + attempt + "/" + MAX_RETRIES + ")");
                return operation.get();
            } catch (Exception e) {
                lastException = e;
                
                if (isRetriableException(e)) {
                    Log.warn("Database operation '" + operationName + "' failed on attempt " + attempt + 
                            "/" + MAX_RETRIES + ": " + e.getMessage());
                    
                    if (attempt < MAX_RETRIES) {
                        try {
                            Thread.sleep(RETRY_DELAY_MS);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            throw new RuntimeException("Interrupted during retry delay for " + operationName, ie);
                        }
                    }
                } else {
                    // Non-retriable exception, don't retry
                    Log.error("Non-retriable exception during " + operationName + ": " + e.getMessage(), e);
                    throw new RuntimeException("Non-retriable exception during " + operationName, e);
                }
            }
        }
        
        Log.error("All " + MAX_RETRIES + " retry attempts failed for " + operationName);
        throw new RuntimeException("All retry attempts failed for " + operationName + 
                ". Last exception: " + (lastException != null ? lastException.getMessage() : "unknown"), lastException);
    }
    
    /**
     * Executes a database operation with retry logic (void return type).
     * 
     * @param operation the database operation to execute
     * @param operationName a descriptive name for the operation (for logging)
     * @throws RuntimeException if all retry attempts fail
     */
    public static void executeWithRetry(Runnable operation, String operationName) {
        executeWithRetry(() -> {
            operation.run();
            return null;
        }, operationName);
    }
    
    /**
     * Determines if an exception is retriable based on its type and characteristics.
     * 
     * @param e the exception to check
     * @return true if the exception is considered retriable, false otherwise
     */
    private static boolean isRetriableException(Exception e) {
        // Database connection issues
        if (e instanceof SQLException) {
            SQLException sqlException = (SQLException) e;
            String sqlState = sqlException.getSQLState();
            
            // Check for transient SQL exceptions
            if (e instanceof SQLTransientException) {
                return true;
            }
            
            // Common retriable SQL states
            if (sqlState != null) {
                // Connection failure, timeout, or temporary unavailability
                return sqlState.startsWith("08") ||  // Connection exception
                       sqlState.startsWith("40") ||  // Transaction rollback (serialization failure)
                       sqlState.startsWith("53") ||  // Insufficient resources
                       sqlState.startsWith("54") ||  // Program limit exceeded
                       sqlState.startsWith("57") ||  // Operator intervention
                       sqlState.startsWith("58");    // System error
            }
            
            // Check error codes for common retriable conditions
            int errorCode = sqlException.getErrorCode();
            // PostgreSQL specific error codes that are often retriable
            return errorCode == 53200 ||  // out_of_memory
                   errorCode == 53300 ||  // too_many_connections
                   errorCode == 40001;    // serialization_failure
        }
        
        // JPA/Hibernate exceptions that might be retriable
        if (e instanceof PersistenceException) {
            Throwable cause = e.getCause();
            if (cause instanceof SQLException) {
                return isRetriableException((SQLException) cause);
            }
            
            String message = e.getMessage();
            if (message != null) {
                String lowerMessage = message.toLowerCase();
                return lowerMessage.contains("timeout") ||
                       lowerMessage.contains("connection") ||
                       lowerMessage.contains("deadlock") ||
                       lowerMessage.contains("lock") ||
                       lowerMessage.contains("serialization") ||
                       lowerMessage.contains("constraint violation") ||
                       lowerMessage.contains("could not serialize access");
            }
        }
        
        // Transaction-related exceptions that might be retriable
        if (e instanceof TransactionRequiredException) {
            return true;
        }
        
        // Check for general connection or timeout related messages
        String message = e.getMessage();
        if (message != null) {
            String lowerMessage = message.toLowerCase();
            return lowerMessage.contains("timeout") ||
                   lowerMessage.contains("connection reset") ||
                   lowerMessage.contains("connection closed") ||
                   lowerMessage.contains("broken pipe") ||
                   lowerMessage.contains("connection refused") ||
                   lowerMessage.contains("no route to host");
        }
        
        return false;
    }
}
