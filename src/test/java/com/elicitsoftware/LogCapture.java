package com.elicitsoftware;

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

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Test support for plain unit tests: collects what {@code io.quarkus.logging.Log} writes for one
 * class. Outside a Quarkus application {@code Log} delegates to a JBoss logger named after the
 * calling class, which is a {@code java.util.logging} logger under the JBoss log manager, so a
 * handler attached here sees every record. Used where a log line is the observable outcome
 * (a warning the respondent never sees, an error that is swallowed).
 */
public final class LogCapture extends Handler implements AutoCloseable {

    private final Logger logger;
    private final Level previousLevel;
    private final List<LogRecord> records = new CopyOnWriteArrayList<>();

    private LogCapture(Class<?> source) {
        this.logger = Logger.getLogger(source.getName());
        this.previousLevel = logger.getLevel();
        setLevel(Level.ALL);
        logger.setLevel(Level.ALL);
        logger.addHandler(this);
    }

    /**
     * Starts capturing the log records of one class.
     *
     * @param source the class whose {@code Log} calls are captured
     * @return the capture; close it to detach
     */
    public static LogCapture of(Class<?> source) {
        return new LogCapture(source);
    }

    @Override
    public void publish(LogRecord record) {
        records.add(record);
    }

    @Override
    public void flush() {
        // Nothing buffered.
    }

    @Override
    public void close() {
        logger.removeHandler(this);
        logger.setLevel(previousLevel);
    }

    /**
     * The messages logged at exactly the given severity (compared by level value, so JBoss's
     * WARN and JUL's WARNING are the same level).
     *
     * @param level the severity
     * @return the messages, in the order logged
     */
    public List<String> messages(Level level) {
        return records.stream()
                .filter(r -> r.getLevel().intValue() == level.intValue())
                .map(r -> String.valueOf(r.getMessage()))
                .toList();
    }

    /**
     * Whether a message containing the fragment was logged at the given severity.
     *
     * @param level    the severity
     * @param fragment the text the message must contain
     * @return true when one was logged
     */
    public boolean contains(Level level, String fragment) {
        return messages(level).stream().anyMatch(m -> m.contains(fragment));
    }

    /** JBoss's ERROR level value (JUL SEVERE). */
    public static final Level ERROR = Level.SEVERE;

    /** JBoss's WARN level value (JUL WARNING). */
    public static final Level WARN = Level.WARNING;

    /** JBoss's INFO level value. */
    public static final Level INFO = Level.INFO;

    /** JBoss's DEBUG level value (JUL FINE). */
    public static final Level DEBUG = Level.FINE;
}
