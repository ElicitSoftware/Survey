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

import com.elicitsoftware.PostgresTestResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The startup report says what is running and what is wrong, and never leaks a secret.
 *
 * <p>Traceability: UC-007 steps 2 to 7, A2, A3, BR-001, NFR-008.</p>
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class StartupDiagnosticsReportTest {

    /** The password every test datasource uses; it must never appear in the report. */
    private static final String TEST_PASSWORD = "SURVEYPW";

    @Inject
    StartupDiagnosticsReport report;

    @Inject
    BuildInfo buildInfo;

    private static String text(List<StartupDiagnosticsReport.Line> lines) {
        return String.join("\n", lines.stream().map(StartupDiagnosticsReport.Line::text).toList());
    }

    /** UC-007 step 2: the first line names the application and its version. */
    @Test
    void firstLineSaysWhatIsRunning() {
        StartupDiagnosticsReport.Line first = report.lines().get(0);

        assertEquals(StartupDiagnosticsReport.Level.INFO, first.level());
        assertTrue(first.text().startsWith(buildInfo.applicationName() + " " + buildInfo.version()), first.text());
        assertTrue(first.text().contains("profile test"), first.text());
    }

    /** UC-007 step 3: both connections are probed and the latest migration is read as survey_user. */
    @Test
    void databaseLinesReportBothConnectionsAndTheLatestMigration() {
        String text = text(report.lines());

        assertTrue(text.contains("database (application): UP as survey_user"), text);
        assertTrue(text.contains("database (owner): UP as elicit_owner"), text);
        assertTrue(text.contains("schema: latest migration V"), text);
        // V017 grants survey_user SELECT on the history, and V9013 the test-schema USAGE that
        // survey_test needs; the report must not have fallen back to the owner connection.
        assertFalse(text.contains("read through the owner connection"), text);
        assertTrue(text.contains("surveys installed: "), text);
    }

    /** UC-007 step 4: the brand summary and every expected asset are listed. */
    @Test
    void brandLinesListEverySource() {
        String text = text(report.lines());

        assertTrue(text.contains("brand: "), text);
        for (var expected : BrandDiagnostics.EXPECTED) {
            assertTrue(text.contains("brand asset " + expected.path()), expected.path());
        }
    }

    /** UC-007 step 6 / A3: a test-only setting is a warning an operator can act on. */
    @Test
    void autoRegisterIsWarnedAbout() {
        List<StartupDiagnosticsReport.Line> lines = report.lines();

        assertTrue(lines.stream().anyMatch(l -> l.isWarning()
                && l.text().startsWith("setting accessCode.autoRegister=true")), text(lines));
    }

    /** UC-007 step 7: the last line counts the warnings above it. */
    @Test
    void lastLineCountsWarnings() {
        List<StartupDiagnosticsReport.Line> lines = report.lines();
        long warnings = lines.subList(0, lines.size() - 1).stream().filter(StartupDiagnosticsReport.Line::isWarning).count();
        StartupDiagnosticsReport.Line last = lines.get(lines.size() - 1);

        assertTrue(last.text().startsWith("report complete: "), last.text());
        assertEquals(warnings > 0, last.isWarning());
        if (warnings > 0) {
            assertTrue(last.text().contains(String.valueOf(warnings)), last.text());
        }
    }

    /** BR-001 / NFR-008: no secret appears anywhere in the report. */
    @Test
    void reportNeverContainsAPassword() {
        String text = text(report.lines());

        assertFalse(text.contains(TEST_PASSWORD), "the datasource password leaked into the report");
        assertTrue(text.contains("setting quarkus.datasource.password=present"), text);
        assertTrue(text.contains("setting quarkus.datasource.owner.password=present"), text);
    }

    /** The report is only logged, so logging it must not throw. */
    @Test
    void reportCanBeLogged() {
        report.log(report.lines());
    }
}
