package com.elicitsoftware.security;

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

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;

/**
 * UC-016 A2: an instance with no platform service token is not ready. Its protected paths are
 * closed and it makes no call that needs the token, so a deployment that looked healthy while
 * no module could call another would be the worse failure. The answer is a fixed reason
 * (NFR-014).
 */
@Readiness
@ApplicationScoped
public class ServiceTokenHealthCheck implements HealthCheck {

    static final String NAME = "service-token";

    @Inject
    ServiceToken serviceToken;

    @Override
    public HealthCheckResponse call() {
        if (serviceToken.configured()) {
            return HealthCheckResponse.up(NAME);
        }
        return HealthCheckResponse.named(NAME).down().withData("reason", ServiceToken.NOT_SET).build();
    }
}
