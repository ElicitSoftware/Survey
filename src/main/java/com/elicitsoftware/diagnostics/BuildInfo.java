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

import io.quarkus.runtime.Startup;
import io.quarkus.runtime.configuration.ConfigUtils;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * What is running: name, version, build time, active profiles and uptime (UC-007 step 2).
 * <p>
 * The version and build time are Maven-filtered into {@code application.properties}
 * ({@code quarkus.application.version=@project.version@}, {@code build.timestamp=@timestamp@});
 * a build that skips filtering leaves them unset and the report says "unknown" (UC-007 A3).
 */
@Startup
@ApplicationScoped
public class BuildInfo {

    static final String UNKNOWN = "unknown";

    @ConfigProperty(name = "quarkus.application.name", defaultValue = "elicit-survey")
    String applicationName;

    @ConfigProperty(name = "quarkus.application.version")
    Optional<String> version;

    @ConfigProperty(name = "build.timestamp")
    Optional<String> buildTimestamp;

    private Instant startedAt;

    public BuildInfo() {
        // CDI managed bean
    }

    @PostConstruct
    void recordStart() {
        startedAt = Instant.now();
    }

    public String applicationName() {
        return applicationName;
    }

    /** The Maven project version, or "unknown" when the build did not record it. */
    public String version() {
        return version.filter(v -> !v.isBlank() && !v.startsWith("@")).orElse(UNKNOWN);
    }

    /** The Maven build timestamp, or "unknown" when the build did not record it. */
    public String buildTimestamp() {
        return buildTimestamp.filter(v -> !v.isBlank() && !v.startsWith("@")).orElse(UNKNOWN);
    }

    public List<String> profiles() {
        return ConfigUtils.getProfiles();
    }

    public Instant startedAt() {
        return startedAt;
    }

    public Duration uptime() {
        return Duration.between(startedAt, Instant.now());
    }
}
