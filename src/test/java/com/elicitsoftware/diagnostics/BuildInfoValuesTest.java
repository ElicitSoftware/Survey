package com.elicitsoftware.diagnostics;

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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the first line of the startup report says is running (UC-007 step 2): the version and
 * build time as Maven filtered them, "unknown" when the build did not (A3), and the uptime.
 * The configuration fields are set directly, as CDI would inject them.
 */
class BuildInfoValuesTest {

    private static BuildInfo info(Optional<String> version, Optional<String> timestamp) {
        BuildInfo info = new BuildInfo();
        info.applicationName = "elicit-survey";
        info.version = version;
        info.buildTimestamp = timestamp;
        return info;
    }

    @Test
    void given_filteredValues_when_read_then_theyAreReported() {
        BuildInfo info = info(Optional.of("3.1.0"), Optional.of("2026-10-01T12:00:00Z"));

        assertEquals("elicit-survey", info.applicationName());
        assertEquals("3.1.0", info.version());
        assertEquals("2026-10-01T12:00:00Z", info.buildTimestamp());
    }

    @ParameterizedTest
    @ValueSource(strings = {"@project.version@", "", "   ", "@timestamp@"})
    void given_anUnfilteredOrBlankValue_when_read_then_unknown(String value) {
        BuildInfo info = info(Optional.of(value), Optional.of(value));

        assertEquals(BuildInfo.UNKNOWN, info.version());
        assertEquals(BuildInfo.UNKNOWN, info.buildTimestamp());
    }

    @Test
    void given_noValue_when_read_then_unknown() {
        BuildInfo info = info(Optional.empty(), Optional.empty());

        assertEquals("unknown", info.version());
        assertEquals("unknown", info.buildTimestamp());
    }

    @Test
    void given_aValueContainingAnAtSign_when_read_then_itIsKept() {
        // Only a value that starts with the Maven placeholder's "@" is unfiltered.
        BuildInfo info = info(Optional.of("3.1.0@abc"), Optional.of("x@y"));

        assertEquals("3.1.0@abc", info.version());
        assertEquals("x@y", info.buildTimestamp());
    }

    @Test
    void given_theBeanStarted_when_startedAt_then_theStartIsRecorded() {
        Instant before = Instant.now();
        BuildInfo info = info(Optional.empty(), Optional.empty());

        info.recordStart();

        assertNotNull(info.startedAt());
        assertFalse(info.startedAt().isBefore(before));
        assertFalse(info.startedAt().isAfter(Instant.now()));
    }

    @Test
    void given_aStartAMinuteAgo_when_uptime_then_atLeastAMinute() throws Exception {
        BuildInfo info = info(Optional.empty(), Optional.empty());
        Field startedAt = BuildInfo.class.getDeclaredField("startedAt");
        startedAt.setAccessible(true);
        startedAt.set(info, Instant.now().minusSeconds(60));

        Duration uptime = info.uptime();

        assertTrue(uptime.toSeconds() >= 60 && uptime.toSeconds() < 120, uptime.toString());
    }

    @Test
    void given_anyConfiguration_when_profiles_then_atLeastOneIsActive() {
        assertFalse(new BuildInfo().profiles().isEmpty());
    }
}
