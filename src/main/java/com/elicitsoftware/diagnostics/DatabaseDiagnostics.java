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

import com.elicitsoftware.model.Survey;
import io.quarkus.agroal.DataSource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Probes both of Survey's database connections and reads the state of the schema it owns
 * (UC-007 step 3).
 * <p>
 * Survey connects twice: as the application user ({@code survey_user}, the default datasource)
 * for respondent traffic, and as the schema owner ({@code elicit_owner}, the {@code owner}
 * datasource) to run its Flyway migrations. The latest applied migration is read through the
 * application connection on purpose: it proves that V017's grant is in place as well as which
 * migration created the schema.
 */
@ApplicationScoped
public class DatabaseDiagnostics {

    /**
     * Outcome of probing one connection.
     *
     * @param label          which connection: "application" or "owner"
     * @param configuredUser the username the configuration names
     * @param result         the probe outcome; the detail carries the server version when up
     * @param connectedAs    the user the server reports, or null when the probe failed
     * @param database       the database the server reports, or null when the probe failed
     */
    public record ConnectionReport(String label, String configuredUser, CheckResult result, String connectedAs,
                                   String database) {
    }

    /**
     * The latest Survey migration in this database.
     *
     * @param version          the latest version applied, or null when it could not be read at all
     * @param installedOn      when it was applied, as the server formats it, or null
     * @param success          whether that migration succeeded
     * @param applicationError why the application user could not read the history, or null when it
     *                         could; when set and {@code version} is present, the owner connection
     *                         read it instead (UC-007 A2)
     * @param error            why neither connection could read the history, or null
     */
    public record MigrationReport(String version, String installedOn, boolean success, String applicationError,
                                  String error) {
    }

    @Inject
    javax.sql.DataSource appDataSource;

    @Inject
    @DataSource("owner")
    javax.sql.DataSource ownerDataSource;

    @ConfigProperty(name = "quarkus.datasource.username", defaultValue = "")
    String appUser;

    @ConfigProperty(name = "quarkus.datasource.owner.username", defaultValue = "")
    String ownerUser;

    @ConfigProperty(name = "quarkus.flyway.owner.schemas", defaultValue = "survey")
    String historySchema;

    @ConfigProperty(name = "quarkus.flyway.owner.table", defaultValue = "flyway_history")
    String historyTable;

    public DatabaseDiagnostics() {
        // CDI managed bean
    }

    /** The application connection, which serves respondents. */
    public ConnectionReport checkApplicationConnection() {
        return probe("application", appUser, appDataSource);
    }

    /** The owner connection, which runs the migrations. */
    public ConnectionReport checkOwnerConnection() {
        return probe("owner", ownerUser, ownerDataSource);
    }

    private static ConnectionReport probe(String label, String configuredUser, javax.sql.DataSource dataSource) {
        long start = System.nanoTime();
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT current_user, current_database(), version()")) {
            rs.next();
            return new ConnectionReport(label, configuredUser,
                    CheckResult.up(label + " database connection", rs.getString(3), elapsed(start)),
                    rs.getString(1), rs.getString(2));
        } catch (SQLException | RuntimeException e) {
            return new ConnectionReport(label, configuredUser,
                    CheckResult.down(label + " database connection", e.getMessage(), elapsed(start)), null, null);
        }
    }

    /**
     * The latest Survey migration, read as the application user so that the report also proves
     * V017's grant is in place: the Author module's System page reads this table as the same
     * user. A schema that predates the grant is read through the owner connection instead, and
     * the application user's failure is reported alongside (UC-007 A2).
     */
    public MigrationReport latestMigration() {
        String sql = "SELECT version, installed_on::text, success FROM " + quote(historySchema) + "." + quote(historyTable)
                + " WHERE version IS NOT NULL ORDER BY installed_rank DESC LIMIT 1";
        String applicationError;
        try {
            return read(appDataSource, sql, null);
        } catch (SQLException | RuntimeException e) {
            // Typically "permission denied": the schema predates V017, which grants the read.
            applicationError = e.getMessage();
        }
        try {
            return read(ownerDataSource, sql, applicationError);
        } catch (SQLException | RuntimeException e) {
            return new MigrationReport(null, null, false, applicationError, e.getMessage());
        }
    }

    private MigrationReport read(javax.sql.DataSource dataSource, String sql, String applicationError) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            if (rs.next()) {
                return new MigrationReport(rs.getString(1), rs.getString(2), rs.getBoolean(3), applicationError, null);
            }
            return new MigrationReport(null, null, false, applicationError,
                    historySchema + "." + historyTable + " has no versioned migration");
        }
    }

    /** How many surveys are installed; zero means the reporting ETL has nothing to build (UC-007 A4). */
    @ActivateRequestContext
    public long surveyCount() {
        return Survey.count();
    }

    private static String quote(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    private static long elapsed(long start) {
        return (System.nanoTime() - start) / 1_000_000;
    }
}
