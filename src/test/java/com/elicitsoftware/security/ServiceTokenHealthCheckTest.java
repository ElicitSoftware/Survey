package com.elicitsoftware.security;

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

import org.eclipse.microprofile.health.HealthCheckResponse;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** UC-016 A2: an instance with no platform service token reports not ready, with a fixed reason. */
class ServiceTokenHealthCheckTest {

    private static HealthCheckResponse call(String token) {
        ServiceTokenHealthCheck check = new ServiceTokenHealthCheck();
        check.serviceToken = ServiceToken.of(token, null);
        return check.call();
    }

    @Test
    void uc016_a2_noTokenIsDownWithTheReason() {
        HealthCheckResponse response = call(null);

        assertEquals("service-token", response.getName());
        assertEquals(HealthCheckResponse.Status.DOWN, response.getStatus());
        assertEquals(Optional.of(Map.<String, Object>of("reason", "ELICIT_SERVICE_TOKEN is not set")), response.getData());
    }

    @Test
    void uc016_main_aConfiguredTokenIsUpAndSaysNothingMore() {
        HealthCheckResponse response = call(TestServiceToken.VALUE);

        assertEquals("service-token", response.getName());
        assertEquals(HealthCheckResponse.Status.UP, response.getStatus());
        assertEquals(Optional.empty(), response.getData());
    }
}
