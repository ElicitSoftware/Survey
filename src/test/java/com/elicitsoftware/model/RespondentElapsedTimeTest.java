package com.elicitsoftware.model;

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

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * UC-004: Finalize Survey — the time a respondent took, from first access to finalization, as
 * the respondent list shows it ({@code getElapsedTime}). Plain field arithmetic; the finders on
 * this entity need a database.
 */
class RespondentElapsedTimeTest {

    private static final OffsetDateTime START = OffsetDateTime.of(2026, 3, 1, 10, 0, 0, 0, ZoneOffset.UTC);

    @Test
    void given_aFinalizedRespondent_when_getElapsedTime_then_hoursMinutesAndSecondsArePadded() {
        Respondent respondent = new Respondent();
        respondent.firstAccessDt = START;
        respondent.finalizedDt = START.plusHours(1).plusMinutes(2).plusSeconds(3);

        assertEquals("01:02:03", respondent.getElapsedTime());
    }

    @Test
    void given_aLongSession_when_getElapsedTime_then_minutesAndSecondsStayBelowSixty() {
        Respondent respondent = new Respondent();
        respondent.firstAccessDt = START;
        respondent.finalizedDt = START.plusHours(26).plusMinutes(59).plusSeconds(59).plusNanos(999_000_000);

        assertEquals("26:59:59", respondent.getElapsedTime());
    }

    @Test
    void given_aMissingEnd_when_getElapsedTime_then_notCalculated() {
        Respondent notStarted = new Respondent();
        notStarted.finalizedDt = START;
        Respondent notFinished = new Respondent();
        notFinished.firstAccessDt = START;

        assertEquals("Not calculated", notStarted.getElapsedTime());
        assertEquals("Not calculated", notFinished.getElapsedTime());
        assertEquals("Not calculated", new Respondent().getElapsedTime());
    }
}
