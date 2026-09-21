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

import io.smallrye.config.PropertiesConfigSource;
import io.smallrye.config.SmallRyeConfig;
import io.smallrye.config.SmallRyeConfigBuilder;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Settings are reported by value except secrets, which are present or absent, and the
 * test-only ones carry a warning.
 *
 * <p>Traceability: UC-007 step 6, A3, BR-001, NFR-008.</p>
 */
class SettingsReportTest {

    private static List<SettingsReport.Setting> settings(Map<String, String> properties) {
        SmallRyeConfig config = new SmallRyeConfigBuilder()
                .withSources(new PropertiesConfigSource(properties, "test", 500))
                .build();
        return new SettingsReport().settings(config);
    }

    private static SettingsReport.Setting setting(List<SettingsReport.Setting> settings, String property) {
        return settings.stream().filter(s -> s.property().equals(property)).findFirst().orElseThrow();
    }

    /** BR-001: a password is present or absent, never its value. */
    @Test
    void passwordsAreReportedAsPresentOrAbsent() {
        List<SettingsReport.Setting> settings = settings(Map.of(
                "quarkus.datasource.password", "hunter2"));

        assertEquals("present", setting(settings, "quarkus.datasource.password").value());
        assertNull(setting(settings, "quarkus.datasource.password").warning());
        assertEquals("absent", setting(settings, "quarkus.datasource.owner.password").value());
        assertNotNull(setting(settings, "quarkus.datasource.owner.password").warning());
        assertTrue(settings.stream().noneMatch(s -> s.value().contains("hunter2")));
    }

    /** BR-001: a password embedded in a JDBC URL is masked. */
    @Test
    void passwordInsideAJdbcUrlIsMasked() {
        List<SettingsReport.Setting> settings = settings(Map.of(
                "quarkus.datasource.jdbc.url", "jdbc:postgresql://db:5432/survey?user=u&password=hunter2&ssl=true"));

        String value = setting(settings, "quarkus.datasource.jdbc.url").value();
        assertEquals("jdbc:postgresql://db:5432/survey?user=u&password=***&ssl=true", value);
    }

    /** UC-007 A3: auto-registration on is a warning; off is not. */
    @Test
    void autoRegisterOnIsAWarning() {
        assertNotNull(setting(settings(Map.of("accessCode.autoRegister", "true")), "accessCode.autoRegister").warning());
        assertNull(setting(settings(Map.of("accessCode.autoRegister", "false")), "accessCode.autoRegister").warning());
        assertNull(setting(settings(Map.of()), "accessCode.autoRegister").warning());
    }

    /** UC-007 step 6: a disabled ETL or telemetry is named so an operator knows it is deliberate. */
    @Test
    void disabledEtlAndTelemetryAreWarnings() {
        List<SettingsReport.Setting> settings = settings(Map.of(
                "elicit.etl.enabled", "false",
                "quarkus.otel.enabled", "false"));

        assertNotNull(setting(settings, "elicit.etl.enabled").warning());
        assertNotNull(setting(settings, "quarkus.otel.enabled").warning());
        assertNull(setting(settings(Map.of()), "elicit.etl.enabled").warning());
    }

    /** UC-007 step 6: an unset value says so instead of failing. */
    @Test
    void unsetValuesAreShownAsUnset() {
        assertEquals("(unset)", setting(settings(Map.of()), "brand.file.system.path").value());
    }
}
