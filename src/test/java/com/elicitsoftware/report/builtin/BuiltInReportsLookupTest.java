package com.elicitsoftware.report.builtin;

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
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which report addresses name a service built into Survey (UC-005 BR-015, UC-014 BR-001): a
 * path is looked up in process, ignoring its query and trailing slashes; an absolute or
 * protocol-relative address is an external service and is never looked up.
 */
class BuiltInReportsLookupTest {

    private final RespondentSummaryService summary = new RespondentSummaryService();

    private BuiltInReports reports() {
        BuiltInReports reports = new BuiltInReports();
        reports.respondentSummary = summary;
        return reports;
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/reports/respondent-summary", "/", "/x"})
    void given_aPath_when_isPath_then_true(String url) {
        assertTrue(BuiltInReports.isPath(url));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "//reports.example.org/x", "https://reports.example.org/x", "api/reports/respondent-summary"})
    void given_anExternalOrMissingAddress_when_isPath_then_false(String url) {
        assertFalse(BuiltInReports.isPath(url));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/reports/respondent-summary", "/api/reports/respondent-summary?lang=es",
            "/api/reports/respondent-summary/", "/api/reports/respondent-summary///?x=1"})
    void given_theRespondentSummaryPath_when_find_then_theService(String url) {
        assertSame(summary, reports().find(url).orElseThrow());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"/", "/?x", "/api/reports/other", "/api/reports", "//api/reports/respondent-summary",
            "http://localhost:8080/api/reports/respondent-summary"})
    void given_anotherAddress_when_find_then_empty(String url) {
        assertTrue(reports().find(url).isEmpty());
    }

    @Test
    void given_repeatedLookups_when_find_then_theSameServiceEachTime() {
        BuiltInReports reports = reports();

        assertTrue(reports.find("/api/reports/other").isEmpty());
        assertSame(summary, reports.find("/api/reports/respondent-summary").orElseThrow());
        assertSame(summary, reports.find("/api/reports/respondent-summary").orElseThrow());
    }
}
