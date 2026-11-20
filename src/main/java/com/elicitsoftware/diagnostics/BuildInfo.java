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

    /** Creates the bean; CDI instantiates it at startup. */
    public BuildInfo() {
        // CDI managed bean
    }

    @PostConstruct
    void recordStart() {
        startedAt = Instant.now();
    }

    /**
     * The application name.
     *
     * @return {@code quarkus.application.name}, or "elicit-survey" when it is not set
     */
    public String applicationName() {
        return applicationName;
    }

    /**
     * The Maven project version.
     *
     * @return the version, or "unknown" when it is unset, blank or an unfiltered placeholder
     */
    public String version() {
        return version.filter(v -> !v.isBlank() && !v.startsWith("@")).orElse(UNKNOWN);
    }

    /**
     * The Maven build timestamp.
     *
     * @return the timestamp, or "unknown" when it is unset, blank or an unfiltered placeholder
     */
    public String buildTimestamp() {
        return buildTimestamp.filter(v -> !v.isBlank() && !v.startsWith("@")).orElse(UNKNOWN);
    }

    /**
     * The active configuration profiles.
     *
     * @return the profiles Quarkus is running with
     */
    public List<String> profiles() {
        return ConfigUtils.getProfiles();
    }

    /**
     * When this bean was initialized, which stands for the application's start.
     *
     * @return the start instant
     */
    public Instant startedAt() {
        return startedAt;
    }

    /**
     * How long the application has been running.
     *
     * @return the time from {@link #startedAt()} to now
     */
    public Duration uptime() {
        return Duration.between(startedAt, Instant.now());
    }
}
