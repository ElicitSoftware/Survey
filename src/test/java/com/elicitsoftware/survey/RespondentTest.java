package com.elicitsoftware.survey;

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

import com.elicitsoftware.model.Respondent;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
public class RespondentTest {

    private final Respondent respondent = new Respondent();

    @Test
    public void testElapsedTimeNotCalculated() {
        assertEquals("Not calculated", respondent.getElapsedTime());
    }

    @Test
    public void testGetElapsedTime() {
        Respondent respondent = new Respondent();

        ZonedDateTime startDateTime = ZonedDateTime.parse("2023-10-10T10:00:00Z");
        ZonedDateTime endDateTime = ZonedDateTime.parse("2023-10-10T12:30:45Z");

        respondent.firstAccessDt = startDateTime.toOffsetDateTime();
        respondent.finalizedDt = endDateTime.toOffsetDateTime();

        String elapsedTime = respondent.getElapsedTime();
        assertEquals("02:30:45", elapsedTime);
    }

    @Test
    public void testGetElapsedTime_notCalculated() {
        Respondent respondent = new Respondent();
        String elapsedTime = respondent.getElapsedTime();
        assertEquals("Not calculated", elapsedTime);
    }
}
