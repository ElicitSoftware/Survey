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

import javax.sql.DataSource;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The database part of the startup report (UC-007 step 3, A2) against hand-written JDBC
 * stand-ins: both connections are probed, the latest migration is read as the application user
 * and through the owner connection when that user cannot read the history, and every failure is
 * reported instead of thrown (BR-002).
 */
class DatabaseDiagnosticsProbeTest {

    /** A data source whose one query answers {@code row} (or no row when null), or whose connect fails. */
    private static final class StubDataSource {
        final List<String> queries = new ArrayList<>();
        final Object[] row;
        final SQLException connectFailure;
        int closed;

        StubDataSource(Object[] row, SQLException connectFailure) {
            this.row = row;
            this.connectFailure = connectFailure;
        }

        static StubDataSource answering(Object... row) {
            return new StubDataSource(row, null);
        }

        static StubDataSource empty() {
            return new StubDataSource(null, null);
        }

        static StubDataSource failing(String message) {
            return new StubDataSource(null, new SQLException(message));
        }

        DataSource dataSource() {
            return proxy(DataSource.class, (method, args) -> {
                if (method.equals("getConnection")) {
                    if (connectFailure != null) {
                        throw connectFailure;
                    }
                    return connection();
                }
                throw new UnsupportedOperationException(method);
            });
        }

        private Connection connection() {
            return proxy(Connection.class, (method, args) -> switch (method) {
                case "createStatement" -> statement();
                case "close" -> {
                    closed++;
                    yield null;
                }
                default -> throw new UnsupportedOperationException(method);
            });
        }

        private Statement statement() {
            return proxy(Statement.class, (method, args) -> switch (method) {
                case "executeQuery" -> {
                    queries.add((String) args[0]);
                    yield resultSet();
                }
                case "close" -> null;
                default -> throw new UnsupportedOperationException(method);
            });
        }

        private ResultSet resultSet() {
            boolean[] read = {false};
            return proxy(ResultSet.class, (method, args) -> switch (method) {
                case "next" -> {
                    boolean has = row != null && !read[0];
                    read[0] = true;
                    yield has;
                }
                case "getString" -> row[(Integer) args[0] - 1];
                case "getBoolean" -> row[(Integer) args[0] - 1];
                case "close" -> null;
                default -> throw new UnsupportedOperationException(method);
            });
        }

        private interface Handler {
            Object handle(String method, Object[] args) throws Throwable;
        }

        @SuppressWarnings("unchecked")
        private static <T> T proxy(Class<T> type, Handler handler) {
            return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                    (p, method, args) -> handler.handle(method.getName(), args));
        }
    }

    private static DatabaseDiagnostics diagnostics(StubDataSource app, StubDataSource owner) {
        DatabaseDiagnostics diagnostics = new DatabaseDiagnostics();
        diagnostics.appDataSource = app.dataSource();
        diagnostics.ownerDataSource = owner.dataSource();
        diagnostics.appUser = "survey_user";
        diagnostics.ownerUser = "elicit_owner";
        diagnostics.historySchema = "survey";
        diagnostics.historyTable = "flyway_history";
        return diagnostics;
    }

    @Test
    void given_anAnsweringServer_when_checkApplicationConnection_then_upWithWhoAndWhere() {
        StubDataSource app = StubDataSource.answering("survey_user", "survey", "PostgreSQL 17.2");

        DatabaseDiagnostics.ConnectionReport report = diagnostics(app, StubDataSource.empty()).checkApplicationConnection();

        assertEquals("application", report.label());
        assertEquals("survey_user", report.configuredUser());
        assertEquals("survey_user", report.connectedAs());
        assertEquals("survey", report.database());
        assertTrue(report.result().isUp());
        assertEquals("application database connection", report.result().name());
        assertEquals("PostgreSQL 17.2", report.result().detail());
        assertTrue(report.result().durationMs() >= 0);
        assertEquals(List.of("SELECT current_user, current_database(), version()"), app.queries);
        assertEquals(1, app.closed, "the connection is closed");
    }

    @Test
    void given_aRefusingServer_when_checkOwnerConnection_then_downWithTheReasonAndTheConfiguredUser() {
        DatabaseDiagnostics.ConnectionReport report = diagnostics(StubDataSource.empty(), StubDataSource.failing("password authentication failed"))
                .checkOwnerConnection();

        assertEquals("owner", report.label());
        assertEquals("elicit_owner", report.configuredUser());
        assertEquals(CheckResult.Status.DOWN, report.result().status());
        assertEquals("owner database connection", report.result().name());
        assertEquals("password authentication failed", report.result().detail());
        assertNull(report.connectedAs());
        assertNull(report.database());
    }

    @Test
    void given_theApplicationUserReadsTheHistory_when_latestMigration_then_itIsReadThroughThatConnectionOnly() {
        StubDataSource app = StubDataSource.answering("19", "2026-09-01 10:00:00", true);
        StubDataSource owner = StubDataSource.answering("0", "never", false);

        DatabaseDiagnostics.MigrationReport report = diagnostics(app, owner).latestMigration();

        assertEquals(new DatabaseDiagnostics.MigrationReport("19", "2026-09-01 10:00:00", true, null, null), report);
        assertEquals(List.of("SELECT version, installed_on::text, success FROM \"survey\".\"flyway_history\""
                + " WHERE version IS NOT NULL ORDER BY installed_rank DESC LIMIT 1"), app.queries);
        assertTrue(owner.queries.isEmpty());
    }

    @Test
    void given_theApplicationUserCannotReadTheHistory_when_latestMigration_then_theOwnerReadsItAndTheFailureIsKept() {
        DatabaseDiagnostics.MigrationReport report = diagnostics(StubDataSource.failing("permission denied for table flyway_history"),
                StubDataSource.answering("16", "2026-01-01", true)).latestMigration();

        assertEquals(new DatabaseDiagnostics.MigrationReport("16", "2026-01-01", true,
                "permission denied for table flyway_history", null), report);
    }

    @Test
    void given_neitherConnectionReadsTheHistory_when_latestMigration_then_bothReasonsAreReported() {
        DatabaseDiagnostics.MigrationReport report = diagnostics(StubDataSource.failing("permission denied"),
                StubDataSource.failing("connection refused")).latestMigration();

        assertEquals(new DatabaseDiagnostics.MigrationReport(null, null, false, "permission denied", "connection refused"), report);
    }

    @Test
    void given_aHistoryWithoutVersionedMigrations_when_latestMigration_then_anErrorNamingTheTable() {
        DatabaseDiagnostics diagnostics = diagnostics(StubDataSource.empty(), StubDataSource.empty());
        diagnostics.historySchema = "elicit";
        diagnostics.historyTable = "history";

        DatabaseDiagnostics.MigrationReport report = diagnostics.latestMigration();

        assertEquals(new DatabaseDiagnostics.MigrationReport(null, null, false, null, "elicit.history has no versioned migration"), report);
    }

    @Test
    void given_aFailedLatestMigration_when_latestMigration_then_successIsFalse() {
        DatabaseDiagnostics.MigrationReport report = diagnostics(StubDataSource.answering("20", "today", false),
                StubDataSource.empty()).latestMigration();

        assertFalse(report.success());
        assertEquals("20", report.version());
    }

    @Test
    void given_identifiersWithQuotes_when_latestMigration_then_theyAreQuotedSafely() {
        StubDataSource app = StubDataSource.answering("1", "x", true);
        DatabaseDiagnostics diagnostics = diagnostics(app, StubDataSource.empty());
        diagnostics.historySchema = "my\"schema";
        diagnostics.historyTable = "t";

        diagnostics.latestMigration();

        assertTrue(app.queries.get(0).contains(" FROM \"my\"\"schema\".\"t\" WHERE "), app.queries.get(0));
    }
}
