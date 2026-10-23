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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How the startup diagnostics report turns its probes' findings into log lines (UC-007 steps 2
 * to 7, A2 to A4, BR-002): what each line says, which ones are warnings, and the closing count.
 * Every collaborator is a stub answering a fixed finding, so each line can be checked exactly.
 */
class StartupDiagnosticsReportLinesTest {

    private static final Instant STARTED = Instant.parse("2026-10-23T08:00:00Z");

    /** The findings the stubs answer; each test changes the ones it is about. */
    private DatabaseDiagnostics.ConnectionReport application = up("application");
    private DatabaseDiagnostics.ConnectionReport owner = up("owner");
    private DatabaseDiagnostics.MigrationReport migration = new DatabaseDiagnostics.MigrationReport("19", "2026-09-01", true, null, null);
    private RuntimeException surveyCountFailure;
    private long surveys = 2;
    private List<BrandDiagnostics.AssetReport> assets = List.of(
            new BrandDiagnostics.AssetReport("theme.css", "theme stylesheet", BrandDiagnostics.Source.EXTERNAL, "/opt/brand/theme.css"));
    private List<ConnectionChecks.Target> targets = List.of(
            new ConnectionChecks.Target("Report service", "Pedigree (FHHS)", "http://pedigree:8080/svg", ConnectionChecks.Kind.HTTP));
    private CheckResult targetResult = CheckResult.up("Pedigree (FHHS)", "reachable, HTTP 200", 12);
    private List<SettingsReport.Setting> settings = List.of(new SettingsReport.Setting("accessCode.autoRegister", "false", null));

    private final List<LogRecord> logged = Collections.synchronizedList(new ArrayList<>());
    private final Handler capture = new Handler() {
        @Override
        public void publish(LogRecord record) {
            logged.add(record);
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    };
    private Logger julLogger;
    private Level previousLevel;

    @BeforeEach
    void captureLog() {
        julLogger = Logger.getLogger(StartupDiagnosticsReport.class.getName());
        previousLevel = julLogger.getLevel();
        julLogger.setLevel(Level.INFO);
        julLogger.addHandler(capture);
    }

    @AfterEach
    void releaseLog() {
        julLogger.removeHandler(capture);
        julLogger.setLevel(previousLevel);
    }

    private static DatabaseDiagnostics.ConnectionReport up(String label) {
        return new DatabaseDiagnostics.ConnectionReport(label, label + "_user",
                CheckResult.up(label + " database connection", "PostgreSQL 17", 3), label + "_user", "survey");
    }

    private StartupDiagnosticsReport report() {
        StartupDiagnosticsReport report = new StartupDiagnosticsReport();
        report.buildInfo = new BuildInfo() {
            @Override
            public String applicationName() {
                return "elicit-survey";
            }

            @Override
            public String version() {
                return "3.1.0";
            }

            @Override
            public String buildTimestamp() {
                return "2026-10-01";
            }

            @Override
            public List<String> profiles() {
                return List.of("prod", "docker");
            }

            @Override
            public Instant startedAt() {
                return STARTED;
            }

            @Override
            public Duration uptime() {
                return Duration.ofMillis(42_900);
            }
        };
        report.database = new DatabaseDiagnostics() {
            @Override
            public ConnectionReport checkApplicationConnection() {
                return application;
            }

            @Override
            public ConnectionReport checkOwnerConnection() {
                return owner;
            }

            @Override
            public MigrationReport latestMigration() {
                return migration;
            }

            @Override
            public long surveyCount() {
                if (surveyCountFailure != null) {
                    throw surveyCountFailure;
                }
                return surveys;
            }
        };
        report.brand = new BrandDiagnostics() {
            @Override
            public BrandReport report() {
                return new BrandReport("/opt/brand", "brand", true, false, "Elicit", assets);
            }
        };
        report.connections = new ConnectionChecks() {
            @Override
            public List<Target> targets() {
                return targets;
            }

            @Override
            public CheckResult check(Target target) {
                return targetResult;
            }
        };
        report.settings = new SettingsReport() {
            @Override
            public List<Setting> settings() {
                return settings;
            }
        };
        return report;
    }

    private static List<String> texts(List<StartupDiagnosticsReport.Line> lines) {
        return lines.stream().map(StartupDiagnosticsReport.Line::text).toList();
    }

    private static List<String> warnings(List<StartupDiagnosticsReport.Line> lines) {
        return lines.stream().filter(StartupDiagnosticsReport.Line::isWarning).map(StartupDiagnosticsReport.Line::text).toList();
    }

    @Test
    void given_aHealthyDeployment_when_lines_then_everyFindingIsReportedAtInfo() {
        List<StartupDiagnosticsReport.Line> lines = report().lines();

        assertEquals(List.of(
                "elicit-survey 3.1.0 built 2026-10-01; profile prod,docker; started 2026-10-23T08:00:00Z; up 42 s",
                "database (application): UP as application_user on survey, PostgreSQL 17 (3 ms)",
                "database (owner): UP as owner_user on survey, PostgreSQL 17 (3 ms)",
                "schema: latest migration V19 applied 2026-09-01",
                "surveys installed: 2",
                "brand: mounted brand \"Elicit\" at /opt/brand",
                "brand asset theme.css (theme stylesheet): EXTERNAL /opt/brand/theme.css",
                "Report service Pedigree (FHHS) http://pedigree:8080/svg: UP reachable, HTTP 200 (12 ms)",
                "setting accessCode.autoRegister=false",
                "report complete: no warnings"), texts(lines));
        assertTrue(warnings(lines).isEmpty(), String.valueOf(warnings(lines)));
        assertEquals(StartupDiagnosticsReport.Level.INFO, lines.get(0).level());
    }

    @Test
    void given_aConnectionDown_when_lines_then_aWarningWithTheConfiguredUserAndReason() {
        owner = new DatabaseDiagnostics.ConnectionReport("owner", "elicit_owner",
                CheckResult.down("owner database connection", "password authentication failed", 7), null, null);

        List<StartupDiagnosticsReport.Line> lines = report().lines();

        assertEquals(List.of("database (owner): DOWN as elicit_owner: password authentication failed (7 ms)",
                "report complete: 1 warning above"), warnings(lines));
    }

    @Test
    void given_theHistoryUnreadable_when_lines_then_aWarningWithTheReason() {
        migration = new DatabaseDiagnostics.MigrationReport(null, null, false, "denied", "connection refused");

        assertTrue(warnings(report().lines()).contains("schema: the latest migration could not be read: connection refused"));
    }

    @Test
    void given_aFailedLatestMigration_when_lines_then_aWarningThatTheSchemaIsUnknown() {
        migration = new DatabaseDiagnostics.MigrationReport("20", "2026-10-01", false, null, null);

        assertTrue(warnings(report().lines()).contains(
                "schema: the latest migration V20 applied 2026-10-01 FAILED; the schema is in an unknown state"));
    }

    @Test
    void given_theHistoryReadOnlyAsOwner_when_lines_then_aWarningAboutTheMissingGrant() {
        migration = new DatabaseDiagnostics.MigrationReport("16", "2026-01-01", true, "permission denied", null);

        assertTrue(warnings(report().lines()).contains("schema: latest migration V16 applied 2026-01-01; read through the owner"
                + " connection because the application user cannot read the Flyway history (permission denied): the Author"
                + " module's System page cannot report it either until the V017 grant is applied"));
    }

    @Test
    void given_noSurveyInstalled_when_lines_then_aWarningToImportOne() {
        surveys = 0;

        assertTrue(warnings(report().lines()).contains("surveys installed: 0; import a survey definition through Admin, then restart"));
    }

    @Test
    void given_theSurveysCannotBeCounted_when_lines_then_aWarningWithTheReason() {
        surveyCountFailure = new IllegalStateException("relation survey.surveys does not exist");

        assertTrue(warnings(report().lines()).contains("surveys installed: could not be counted: relation survey.surveys does not exist"));
    }

    @Test
    void given_absentAndUnreadableBrandAssets_when_lines_then_onlyThoseAreWarnings() {
        assets = List.of(
                new BrandDiagnostics.AssetReport("theme.css", "theme stylesheet", BrandDiagnostics.Source.ABSENT, "nowhere"),
                new BrandDiagnostics.AssetReport("logo.svg", "logo", BrandDiagnostics.Source.UNREADABLE, "denied"),
                new BrandDiagnostics.AssetReport("favicon.ico", "icon", BrandDiagnostics.Source.EMBEDDED, "classpath"));

        List<StartupDiagnosticsReport.Line> lines = report().lines();

        assertEquals(List.of("brand asset theme.css (theme stylesheet): ABSENT nowhere",
                "brand asset logo.svg (logo): UNREADABLE denied",
                "report complete: 2 warnings above"), warnings(lines));
        assertTrue(texts(lines).contains("brand asset favicon.ico (icon): EMBEDDED classpath"));
    }

    @Test
    void given_anUnreachableTarget_when_lines_then_aWarning() {
        targetResult = CheckResult.down("Pedigree (FHHS)", "Connection refused", 4);

        assertEquals(List.of("Report service Pedigree (FHHS) http://pedigree:8080/svg: DOWN Connection refused (4 ms)",
                "report complete: 1 warning above"), warnings(report().lines()));
    }

    @Test
    void given_noTargets_when_lines_then_saysNoneConfigured() {
        targets = List.of();

        List<String> texts = texts(report().lines());

        assertTrue(texts.contains("outbound targets: none configured"));
        assertFalse(texts.stream().anyMatch(t -> t.startsWith("Report service")));
    }

    @Test
    void given_aSettingWithAWarning_when_lines_then_aWarningWithTheAdvice() {
        settings = List.of(new SettingsReport.Setting("accessCode.autoRegister", "true", "anyone can register"));

        assertEquals(List.of("setting accessCode.autoRegister=true: anyone can register", "report complete: 1 warning above"),
                warnings(report().lines()));
    }

    @Test
    void given_mixedLines_when_log_then_warningsAtWarnAndTheRestAtInfoWithThePrefix() {
        report().log(List.of(new StartupDiagnosticsReport.Line(StartupDiagnosticsReport.Level.INFO, "all good"),
                new StartupDiagnosticsReport.Line(StartupDiagnosticsReport.Level.WARN, "act now")));

        assertEquals(2, logged.size());
        assertEquals("Survey diagnostics: all good", logged.get(0).getMessage());
        assertEquals(Level.INFO, logged.get(0).getLevel());
        assertEquals("Survey diagnostics: act now", logged.get(1).getMessage());
        assertEquals(Level.WARNING, logged.get(1).getLevel());
    }

    @Test
    void given_theReportEnabled_when_onStart_then_itIsWrittenOnADaemonThreadAfterTheDelay() throws InterruptedException {
        CountDownLatch written = new CountDownLatch(1);
        StartupDiagnosticsReport report = report();
        report.settings = new SettingsReport() {
            @Override
            public List<Setting> settings() {
                written.countDown();
                return List.of();
            }
        };
        report.enabled = true;
        report.delay = Duration.ZERO;

        report.onStart(null);

        assertTrue(written.await(5, TimeUnit.SECONDS), "the report is built");
        assertTrue(awaitLogged("Survey diagnostics: report complete: no warnings"), String.valueOf(logged));
    }

    @Test
    void given_aDelay_when_onStart_then_theReportWaitsForIt() throws InterruptedException {
        CountDownLatch written = new CountDownLatch(1);
        StartupDiagnosticsReport report = report();
        report.settings = new SettingsReport() {
            @Override
            public List<Setting> settings() {
                written.countDown();
                return List.of();
            }
        };
        report.enabled = true;
        report.delay = Duration.ofMillis(150);

        report.onStart(null);

        assertFalse(written.await(40, TimeUnit.MILLISECONDS), "startup is not kept waiting, and the report waits (BR-002)");
        assertTrue(written.await(5, TimeUnit.SECONDS));
    }

    @Test
    void given_theReportDisabled_when_onStart_then_nothingIsBuilt() throws InterruptedException {
        CountDownLatch written = new CountDownLatch(1);
        StartupDiagnosticsReport report = report();
        report.connections = new ConnectionChecks() {
            @Override
            public List<Target> targets() {
                written.countDown();
                return List.of();
            }
        };
        report.enabled = false;
        report.delay = Duration.ZERO;

        report.onStart(null);

        assertFalse(written.await(100, TimeUnit.MILLISECONDS));
    }

    @Test
    void given_aProbeThatThrows_when_onStart_then_theFailureIsLoggedNotPropagated() throws InterruptedException {
        StartupDiagnosticsReport report = report();
        report.brand = new BrandDiagnostics() {
            @Override
            public BrandReport report() {
                throw new IllegalStateException("brand exploded");
            }
        };
        report.enabled = true;
        report.delay = Duration.ZERO;

        report.onStart(null);

        assertTrue(awaitLogged("Survey diagnostics: report failed: java.lang.IllegalStateException: brand exploded"),
                String.valueOf(logged));
    }

    private boolean awaitLogged(String message) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            synchronized (logged) {
                if (logged.stream().anyMatch(r -> message.equals(r.getMessage()))) {
                    return true;
                }
            }
            Thread.sleep(10);
        }
        return false;
    }
}
