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

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Writes a one-shot diagnostics report to the log shortly after the application starts
 * (UC-007). Survey has no administrator login, so this report is how an operator learns
 * whether a deployment is wired correctly: what is running, both database connections, the
 * latest migration, the brand that resolved, whether every report service and post-survey
 * action is reachable from here, and the settings that matter.
 * <p>
 * The report is written after {@code elicit.diagnostics.startup.delay} on a daemon thread so
 * that startup is never delayed or failed by it (BR-002). Each line is logged on its own:
 * findings an operator must act on at WARN, everything else at INFO. The
 * {@code com.elicitsoftware.diagnostics} category is pinned to INFO in
 * {@code application.properties} so the whole report appears even though containers run at
 * {@code LOG_LEVEL=WARN}.
 */
@ApplicationScoped
public class StartupDiagnosticsReport {

    private static final Logger LOG = Logger.getLogger(StartupDiagnosticsReport.class);

    static final String PREFIX = "Survey diagnostics: ";

    /** How a line is logged. */
    public enum Level {
        /** Informational; visible because the category is pinned to INFO. */
        INFO,
        /** Something an operator has to act on. */
        WARN
    }

    /**
     * One line of the report.
     *
     * @param level how it is logged
     * @param text  what it says, without the prefix
     */
    public record Line(Level level, String text) {
        public boolean isWarning() {
            return level == Level.WARN;
        }
    }

    @ConfigProperty(name = "elicit.diagnostics.startup.enabled", defaultValue = "true")
    boolean enabled;

    @ConfigProperty(name = "elicit.diagnostics.startup.delay", defaultValue = "10s")
    Duration delay;

    @Inject
    BuildInfo buildInfo;

    @Inject
    DatabaseDiagnostics database;

    @Inject
    BrandDiagnostics brand;

    @Inject
    ConnectionChecks connections;

    @Inject
    SettingsReport settings;

    public StartupDiagnosticsReport() {
        // CDI managed bean
    }

    void onStart(@Observes StartupEvent event) {
        if (!enabled) {
            LOG.debug(PREFIX + "disabled (elicit.diagnostics.startup.enabled=false)");
            return;
        }
        Thread.ofPlatform().daemon(true).name("survey-diagnostics").start(() -> {
            try {
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            try {
                log(lines());
            } catch (RuntimeException e) {
                // The report must never take the application down with it (BR-002).
                LOG.warn(PREFIX + "report failed: " + e, e);
            }
        });
    }

    /** Builds the report. Every probe is bounded (NFR-009) and no secret is ever included (NFR-008). */
    public List<Line> lines() {
        List<Line> lines = new ArrayList<>();

        lines.add(info(buildInfo.applicationName() + " " + buildInfo.version() + " built " + buildInfo.buildTimestamp()
                + "; profile " + String.join(",", buildInfo.profiles()) + "; started " + buildInfo.startedAt()
                + "; up " + buildInfo.uptime().toSeconds() + " s"));

        connection(lines, database.checkApplicationConnection());
        connection(lines, database.checkOwnerConnection());

        DatabaseDiagnostics.MigrationReport migration = database.latestMigration();
        if (migration.error() != null) {
            lines.add(warn("schema: the latest migration could not be read: " + migration.error()));
        } else if (!migration.success()) {
            lines.add(warn("schema: the latest migration V" + migration.version() + " applied " + migration.installedOn()
                    + " FAILED; the schema is in an unknown state"));
        } else if (migration.applicationError() != null) {
            lines.add(warn("schema: latest migration V" + migration.version() + " applied " + migration.installedOn()
                    + "; read through the owner connection because the application user cannot read the Flyway"
                    + " history (" + migration.applicationError() + "): the Author module's System page cannot"
                    + " report it either until the V017 grant is applied"));
        } else {
            lines.add(info("schema: latest migration V" + migration.version() + " applied " + migration.installedOn()));
        }

        try {
            long surveys = database.surveyCount();
            if (surveys == 0) {
                lines.add(warn("surveys installed: 0; import a survey definition through Admin, then restart"));
            } else {
                lines.add(info("surveys installed: " + surveys));
            }
        } catch (RuntimeException e) {
            lines.add(warn("surveys installed: could not be counted: " + e.getMessage()));
        }

        BrandDiagnostics.BrandReport brandReport = brand.report();
        lines.add(info("brand: " + brandReport.summary()));
        for (BrandDiagnostics.AssetReport asset : brandReport.assets()) {
            String text = "brand asset " + asset.path() + " (" + asset.role() + "): " + asset.source() + " " + asset.detail();
            boolean problem = asset.source() == BrandDiagnostics.Source.ABSENT
                    || asset.source() == BrandDiagnostics.Source.UNREADABLE;
            lines.add(problem ? warn(text) : info(text));
        }

        List<ConnectionChecks.Target> targets = connections.targets();
        if (targets.isEmpty()) {
            lines.add(info("outbound targets: none configured"));
        }
        for (ConnectionChecks.Target target : targets) {
            CheckResult result = connections.check(target);
            String text = target.group() + " " + target.name() + " " + target.address() + ": " + result.status()
                    + " " + result.detail() + " (" + result.durationMs() + " ms)";
            lines.add(result.isUp() ? info(text) : warn(text));
        }

        for (SettingsReport.Setting setting : settings.settings()) {
            String text = "setting " + setting.property() + "=" + setting.value();
            lines.add(setting.warning() == null ? info(text) : warn(text + ": " + setting.warning()));
        }

        long warnings = lines.stream().filter(Line::isWarning).count();
        lines.add(warnings == 0
                ? info("report complete: no warnings")
                : warn("report complete: " + warnings + (warnings == 1 ? " warning" : " warnings") + " above"));
        return lines;
    }

    private static void connection(List<Line> lines, DatabaseDiagnostics.ConnectionReport report) {
        CheckResult result = report.result();
        if (result.isUp()) {
            lines.add(info("database (" + report.label() + "): UP as " + report.connectedAs() + " on " + report.database()
                    + ", " + result.detail() + " (" + result.durationMs() + " ms)"));
        } else {
            lines.add(warn("database (" + report.label() + "): DOWN as " + report.configuredUser() + ": " + result.detail()
                    + " (" + result.durationMs() + " ms)"));
        }
    }

    void log(List<Line> lines) {
        for (Line line : lines) {
            if (line.isWarning()) {
                LOG.warn(PREFIX + line.text());
            } else {
                LOG.info(PREFIX + line.text());
            }
        }
    }

    private static Line info(String text) {
        return new Line(Level.INFO, text);
    }

    private static Line warn(String text) {
        return new Line(Level.WARN, text);
    }
}
