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

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * The effective values of the settings a deployment is expected to supply (UC-007 step 6),
 * with a warning for each one that is set the way a test environment needs and a production
 * one must not. Secrets are reported as present or absent and never by value (BR-001, NFR-008).
 */
@ApplicationScoped
public class SettingsReport {

    /**
     * One setting as the running application sees it.
     *
     * @param property the configuration property
     * @param value    the effective value, or "present"/"absent" for a secret
     * @param warning  why an operator should look at it, or null when it needs no attention
     */
    public record Setting(String property, String value, String warning) {
    }

    private static final Pattern URL_PASSWORD = Pattern.compile("(?i)(password=)[^&;]*");

    public SettingsReport() {
        // CDI managed bean
    }

    public List<Setting> settings() {
        return settings(ConfigProvider.getConfig());
    }

    List<Setting> settings(Config config) {
        List<Setting> settings = new ArrayList<>();

        settings.add(plain(config, "quarkus.datasource.jdbc.url"));
        settings.add(plain(config, "quarkus.datasource.username"));
        settings.add(secret(config, "quarkus.datasource.password"));
        settings.add(plain(config, "quarkus.datasource.owner.jdbc.url"));
        settings.add(plain(config, "quarkus.datasource.owner.username"));
        settings.add(secret(config, "quarkus.datasource.owner.password"));

        boolean autoRegister = config.getOptionalValue("accessCode.autoRegister", Boolean.class).orElse(false);
        settings.add(new Setting("accessCode.autoRegister", Boolean.toString(autoRegister), autoRegister
                ? "any access code a visitor types creates a respondent; remove this setting for production"
                : null));

        boolean etl = config.getOptionalValue("elicit.etl.enabled", Boolean.class).orElse(true);
        settings.add(new Setting("elicit.etl.enabled", Boolean.toString(etl), etl
                ? null
                : "the reporting ETL is off: this instance does not build or update the reporting schema"));

        settings.add(plain(config, "brand.file.system.path"));

        boolean otel = config.getOptionalValue("quarkus.otel.enabled", Boolean.class).orElse(true);
        settings.add(new Setting("quarkus.otel.enabled", Boolean.toString(otel), otel
                ? null
                : "telemetry is off: no traces or metrics leave this instance (NFR-002)"));
        settings.add(plain(config, "quarkus.otel.exporter.otlp.endpoint"));
        settings.add(plain(config, "quarkus.log.level"));
        return settings;
    }

    private static Setting plain(Config config, String property) {
        String value = value(config, property);
        return new Setting(property, value == null ? "(unset)" : URL_PASSWORD.matcher(value).replaceAll("$1***"), null);
    }

    private static Setting secret(Config config, String property) {
        String value = value(config, property);
        return new Setting(property, value == null ? "absent" : "present",
                value == null ? "no value is configured; the connection will fail" : null);
    }

    private static String value(Config config, String property) {
        try {
            return config.getOptionalValue(property, String.class).filter(v -> !v.isBlank()).orElse(null);
        } catch (RuntimeException e) {
            // An unexpandable value (a ${VAR} with no default and no VAR) counts as absent.
            return null;
        }
    }
}
