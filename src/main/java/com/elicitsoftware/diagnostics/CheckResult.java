package com.elicitsoftware.diagnostics;

/*-
 * ***LICENSE_START***
 * Elicit Survey
 * %%
 * Copyright (C) 2025 - 2026 Elicit Software LLC
 * %%
 * PolyForm Noncommercial License 1.0.0
 * <https://polyformproject.org/licenses/noncommercial/1.0.0>
 * ***LICENSE_END***
 */

/**
 * Outcome of one diagnostic probe, as written to the startup diagnostics report (UC-007).
 *
 * @param name       what was checked
 * @param status     whether it passed
 * @param detail     human-readable detail: a version, an address, or the failure reason
 * @param durationMs how long the probe took, or 0 when timing is meaningless
 */
public record CheckResult(String name, Status status, String detail, long durationMs) {

    /** Probe outcome. */
    public enum Status {
        /** The target answered as expected. */
        UP,
        /** The target failed, refused, or timed out. */
        DOWN,
        /** The target could not be checked, usually because nothing is configured. */
        UNKNOWN
    }

    /**
     * A probe that passed.
     *
     * @param name       what was checked
     * @param detail     human-readable detail, such as a version or an address
     * @param durationMs how long the probe took
     * @return the result with status {@link Status#UP}
     */
    public static CheckResult up(String name, String detail, long durationMs) {
        return new CheckResult(name, Status.UP, detail, durationMs);
    }

    /**
     * A probe that failed.
     *
     * @param name       what was checked
     * @param detail     the failure reason
     * @param durationMs how long the probe took
     * @return the result with status {@link Status#DOWN}
     */
    public static CheckResult down(String name, String detail, long durationMs) {
        return new CheckResult(name, Status.DOWN, detail, durationMs);
    }

    /**
     * A probe that could not be run, with no duration.
     *
     * @param name   what was checked
     * @param detail why it could not be checked
     * @return the result with status {@link Status#UNKNOWN} and a duration of 0
     */
    public static CheckResult unknown(String name, String detail) {
        return new CheckResult(name, Status.UNKNOWN, detail, 0);
    }

    /**
     * Whether the probe passed.
     *
     * @return {@code true} when the status is {@link Status#UP}
     */
    public boolean isUp() {
        return status == Status.UP;
    }
}
