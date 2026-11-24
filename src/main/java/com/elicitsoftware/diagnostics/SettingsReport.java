package com.elicitsoftware.diagnostics;

/*-
 * ***LICENSE_START***
 * Elicit Survey
 * %%
 * Copyright (C) 2025 - 2026 Elicit Software LLC
 * %%
 * PolyForm Noncommercial License 1.0.0
 * <https://polyformproject.org/licenses/noncommercial/1.0.0>
 * ***LICENSE_END***
 */

import com.elicitsoftware.security.ServiceToken;
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

    static final String FORWARDING = "quarkus.http.proxy.proxy-address-forwarding";
    static final String TRUSTED_PROXIES = "quarkus.http.proxy.trusted-proxies";
    static final String SERVICE_TOKEN = "elicit.service.token";
    static final String TRUSTED_ORIGINS = "elicit.service.trusted-origins";

    private static final Pattern URL_PASSWORD = Pattern.compile("(?i)(password=)[^&;]*");

    /** Creates the bean. */
    public SettingsReport() {
        // CDI managed bean
    }

    /**
     * The settings the report shows, read from the running application's configuration, in
     * report order.
     *
     * @return the settings, each with its warning or null
     */
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
        // A site's choice, not a test-only switch (UC-001 BR-002, UC-007 A3 retired)
        settings.add(new Setting("accessCode.autoRegister", Boolean.toString(autoRegister), null));

        boolean etl = config.getOptionalValue("elicit.etl.enabled", Boolean.class).orElse(true);
        settings.add(new Setting("elicit.etl.enabled", Boolean.toString(etl), etl
                ? null
                : "the reporting ETL is off: this instance does not build or update the reporting schema"));

        settings.add(plain(config, "brand.file.system.path"));

        // UC-007 BR-006: forwarded headers are honored from whoever sends them when the list of
        // trusted proxies is empty.
        boolean forwarding = Boolean.parseBoolean(value(config, FORWARDING));
        String trustedProxies = value(config, TRUSTED_PROXIES);
        settings.add(new Setting(FORWARDING, Boolean.toString(forwarding), null));
        settings.add(new Setting(TRUSTED_PROXIES, trustedProxies == null ? "(unset)" : trustedProxies,
                forwarding && trustedProxies == null
                        ? "forwarded headers are honored from any client, which can then set its own address, host"
                        + " and scheme; set " + TRUSTED_PROXIES + " (ELICIT_TRUSTED_PROXIES) to the proxy's address,"
                        + " or turn " + FORWARDING + " off if there is no proxy"
                        : null));

        // UC-007 BR-007: the service token is reported as set or not set, never by value.
        String serviceToken = value(config, SERVICE_TOKEN);
        settings.add(new Setting(SERVICE_TOKEN, serviceToken == null ? "not set" : "set",
                serviceToken == null
                        ? ServiceToken.NOT_SET + ": every path under /api/ except the PDF download answers 503, this"
                        + " instance reports not ready, and no call that needs the service token is made"
                        : serviceToken.strip().equals(ServiceToken.DEVELOPMENT_TOKEN)
                        ? "this is the well-known development value; a real site must replace it"
                        : null));
        settings.add(plain(config, TRUSTED_ORIGINS));

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
