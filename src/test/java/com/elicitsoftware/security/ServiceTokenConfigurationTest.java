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

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * UC-016 BR-002, BR-005: the platform service token in {@code application.properties} as
 * packaged. No value ships outside the dev and test profiles, and nothing is configured that
 * would write a request's {@code Authorization} header to a log or a span.
 */
class ServiceTokenConfigurationTest {

    private static final Properties CONFIG = new Properties();

    @BeforeAll
    static void load() throws IOException {
        try (InputStream in = ServiceTokenConfigurationTest.class.getResourceAsStream("/application.properties")) {
            assertNotNull(in, "application.properties is on the classpath");
            CONFIG.load(in);
        }
    }

    @Test
    void uc016_br002_noTokenShips() {
        assertEquals("${ELICIT_SERVICE_TOKEN:}", CONFIG.getProperty("elicit.service.token"));
        assertEquals("${ELICIT_SERVICE_TRUSTED_ORIGINS:}", CONFIG.getProperty("elicit.service.trusted-origins"));
        for (String key : CONFIG.stringPropertyNames()) {
            if (key.endsWith("elicit.service.token") && !key.equals("elicit.service.token")) {
                assertFalse(!key.equals("%dev.elicit.service.token") && !key.equals("%test.elicit.service.token"),
                        "only the dev and test profiles may carry a value: " + key);
            }
        }
        assertNull(CONFIG.getProperty("%prod.elicit.service.token"));
        assertNull(CONFIG.getProperty("%docker.elicit.service.token"));
    }

    @Test
    void uc016_a5_theDevProfileUsesTheDevelopmentStacksValue() {
        assertEquals(ServiceToken.DEVELOPMENT_TOKEN, CONFIG.getProperty("%dev.elicit.service.token"));
        assertEquals("elicit-dev-service-token-not-for-a-real-site", ServiceToken.DEVELOPMENT_TOKEN);
    }

    @Test
    void theTestProfilesTokenIsTheOneTestsSend() {
        assertEquals(TestServiceToken.VALUE, CONFIG.getProperty("%test.elicit.service.token"));
    }

    @Test
    void uc016_br005_noRequestHeaderIsCapturedOrLogged() {
        for (String key : CONFIG.stringPropertyNames()) {
            String lower = key.toLowerCase();
            assertFalse(lower.contains("capture-headers") || lower.contains("capture-request-headers"),
                    "telemetry must not capture request headers: " + key);
            assertFalse(lower.contains("rest-client") && lower.contains("logging"),
                    "REST client logging writes headers: " + key);
        }
        String accessLog = CONFIG.getProperty("quarkus.http.access-log.pattern");
        assertNotNull(accessLog);
        assertFalse(accessLog.toLowerCase().contains("authorization"), accessLog);
    }
}
