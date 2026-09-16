package com.elicitsoftware.flyway;

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

import io.quarkus.agroal.DataSource;
import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.exception.FlywayValidateException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// TEMPORARY — DO NOT CARRY THIS FORWARD INDEFINITELY.
// This class exists solely to support upgrading existing pre-Kimball ("V2.x") Survey
// deployments to V3.0.0 (Kimball Type 2 SCD) via db/migration-v3. Once every real Survey
// deployment has been upgraded to V3 (confirmed by every environment's flyway_history having
// converged onto db/migration — see the repair() call below), this class, db/migration-v3/,
// ManualSchemaMigratorUpgradeTest.java, and ManualSchemaMigratorScaleTest.java (which only
// times db/migration-v3's ALTER migration — see its own header) should ALL be deleted, and
// quarkus.flyway.owner.migrate-at-start should revert to the plain Quarkus-managed
// auto-migration this class replaced (see application.properties). Tracked in
// research/Kimball_type_2.md and the repo-root DeploymentScript.md — check those before
// removing, and update them when this class is actually deleted.
//
// Runs Flyway manually against a database-detected brownfield/greenfield/converged location,
// replacing Quarkus's migrate-at-start (deliberately disabled — see the comment on
// quarkus.flyway.owner.migrate-at-start in application.properties for why a
// FlywayConfigurationCustomizer cannot do this: Quarkus resolves classpath: migration locations
// at BUILD time, so mutating FluentConfiguration.locations() at runtime has no effect on which
// files actually get executed — confirmed by testing against a real pre-Kimball database).
//
// Tries db/migration (the greenfield v3 schema) first. If it validates cleanly there — either a
// genuinely fresh database, or a v2.x database that has already been through the upgrade-and-repair
// cycle below on a prior boot — it stays there permanently. Note that "stays there" describes
// where an ALREADY-CONVERGED database reads from; it does NOT mean a new migration can be added
// to db/migration alone. Every version must exist in BOTH locations at the same version number,
// because a database still on the upgrade path runs db/migration-v3 and is then repaired against
// db/migration: any version present only in db/migration would be left unapplied, and the
// post-repair validate() would fail on it as a pending migration. V011, V012 and V013 all follow
// this rule — same version number in both, with track-specific wording in each. Adding one to
// only db/migration breaks ManualSchemaMigratorUpgradeTest, which exists to catch exactly that.
// (src/test/resources/db/test/'s test-only data
// fixtures live in a separate Flyway location layered on top of this one only under the
// %test profile, numbered from V9xxx specifically so they never collide with a real
// migration version here.) If db/migration's checksums DON'T match (a v2.x
// database that hasn't been upgraded yet), it migrates via db/migration-v3 (the ALTER-based
// upgrade path) instead, then immediately repairs its history against db/migration so the NEXT
// boot's validate() against db/migration succeeds and this database converges onto it for good.
// The two locations are siblings, not nested — Flyway's classpath location scanning is recursive,
// so db/migration-v3 must NOT live under db/migration.
// See research/Kimball_type_2.md ("Migration Strategy for Existing Data") and the plan at
// /Users/mdemerat/.claude/plans/mighty-roaming-tide.md.
@ApplicationScoped
public class ManualSchemaMigrator {

    private static final String GREENFIELD_LOCATION_SUFFIX = "db/migration";
    private static final String UPGRADE_LOCATION_SUFFIX = "db/migration-v3";

    @Inject
    @DataSource("owner")
    javax.sql.DataSource ownerDataSource;

    @ConfigProperty(name = "quarkus.flyway.owner.schemas")
    String schemas;

    @ConfigProperty(name = "quarkus.flyway.owner.table")
    String table;

    @ConfigProperty(name = "quarkus.flyway.owner.locations")
    String configuredLocations;

    @ConfigProperty(name = "quarkus.flyway.owner.baseline-on-migrate")
    boolean baselineOnMigrate;

    @ConfigProperty(name = "quarkus.flyway.owner.baseline-version")
    String baselineVersion;

    @ConfigProperty(name = "quarkus.flyway.owner.baseline-description")
    String baselineDescription;

    @ConfigProperty(name = "quarkus.flyway.owner.validate-on-migrate")
    boolean validateOnMigrate;

    @ConfigProperty(name = "quarkus.flyway.owner.connect-retries")
    int connectRetries;

    @ConfigProperty(name = "quarkus.flyway.owner.placeholders.survey_user")
    String surveyUser;

    @ConfigProperty(name = "quarkus.flyway.owner.placeholders.surveyadmin_user")
    String surveyAdminUser;

    @ConfigProperty(name = "quarkus.flyway.owner.placeholders.surveyreport_user")
    String surveyReportUser;

    // Explicit @Priority beats any unprioritized StartupEvent observer (e.g. ETLService.init(),
    // AppConfig — both plain @Startup with no declared priority) regardless of the numeric value
    // chosen; a low value is used anyway to be unambiguous about intent.
    void migrate(@Observes @Priority(1) StartupEvent event) {
        List<String> greenfieldLocations = Arrays.stream(configuredLocations.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();

        if (validatesCleanly(greenfieldLocations)) {
            // Either a genuinely fresh database, or a v2.x database that already converged on a
            // prior boot (see the repair() call below) — db/migration alone is correct either way.
            buildFlyway(greenfieldLocations).migrate();
            return;
        }

        Log.infof("db/migration checksum mismatch (unupgraded v2.x history) — routing Flyway to %s",
                UPGRADE_LOCATION_SUFFIX);
        List<String> upgradeLocations = greenfieldLocations.stream().map(this::toUpgradeLocation).toList();
        buildFlyway(upgradeLocations).migrate();

        Log.infof("Upgrade complete — realigning history against %s so future boots use it directly",
                GREENFIELD_LOCATION_SUFFIX);
        buildFlyway(greenfieldLocations).repair();
    }

    // True if this database's currently recorded history has no checksum conflicts with
    // db/migration — i.e. it's safe to treat db/migration as authoritative going forward.
    private boolean validatesCleanly(List<String> greenfieldLocations) {
        if (!schemaExists()) {
            // Genuinely fresh database (the schema itself hasn't been created yet) — nothing to
            // validate. Flyway's validate(), unlike migrate(), does NOT tolerate a missing schema
            // and throws FlywayValidateException("Schema ... doesn't exist yet") for this case,
            // which is a different situation entirely from a real checksum mismatch — checking
            // first avoids misreading "fresh database" as "needs the v2.x upgrade path".
            return true;
        }
        try {
            buildFlyway(greenfieldLocations).validate();
            return true;
        } catch (FlywayValidateException e) {
            return false;
        }
    }

    private boolean schemaExists() {
        try (Connection connection = ownerDataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT 1 FROM information_schema.schemata WHERE schema_name = ?")) {
            ps.setString(1, schemas);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            return false;
        }
    }

    private Flyway buildFlyway(List<String> locations) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("survey_user", surveyUser);
        placeholders.put("surveyadmin_user", surveyAdminUser);
        placeholders.put("surveyreport_user", surveyReportUser);

        return Flyway.configure()
                .dataSource(ownerDataSource)
                .schemas(schemas)
                .table(table)
                .locations(locations.toArray(new String[0]))
                .baselineOnMigrate(baselineOnMigrate)
                .baselineVersion(baselineVersion)
                .baselineDescription(baselineDescription)
                .validateOnMigrate(validateOnMigrate)
                .connectRetries(connectRetries)
                .placeholders(placeholders)
                .load();
    }

    private String toUpgradeLocation(String location) {
        if (!location.endsWith(GREENFIELD_LOCATION_SUFFIX)) {
            return location;
        }
        return location.substring(0, location.length() - GREENFIELD_LOCATION_SUFFIX.length()) + UPGRADE_LOCATION_SUFFIX;
    }
}
