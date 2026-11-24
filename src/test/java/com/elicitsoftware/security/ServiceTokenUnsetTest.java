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

import com.elicitsoftware.PostgresTestResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UC-016 A2: an application started with no platform service token. It starts, its protected
 * paths answer 503 whatever the request carries, the respondent-facing paths still answer, and
 * readiness reports the {@code service-token} check down with its reason.
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
@TestProfile(ServiceTokenUnsetTest.NoToken.class)
class ServiceTokenUnsetTest {

    public static class NoToken implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "elicit.service.token", "",
                    // The test profile turns health off with the metrics; this test reads it.
                    "quarkus.smallrye-health.enabled", "true");
        }
    }

    @TestHTTPResource("/")
    URL root;

    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    private HttpResponse<String> send(String method, String path, String authorization) throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(root.toString().replaceAll("/$", "") + path))
                .method(method, HttpRequest.BodyPublishers.noBody())
                .timeout(Duration.ofSeconds(60));
        if (authorization != null) {
            request.header("Authorization", authorization);
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void uc016_a2_protectedPathsAnswer503WhateverTheRequestCarries() throws Exception {
        for (String authorization : new String[]{null, TestServiceToken.AUTHORIZATION, "Bearer ", "Bearer " + ServiceToken.DEVELOPMENT_TOKEN}) {
            for (String[] call : new String[][]{
                    {"POST", "/api/etl/build"},
                    {"POST", "/api/etl/schema/00000000-0000-0000-0000-00000000dead/rename?name=report_x"},
                    {"DELETE", "/api/etl/schema/00000000-0000-0000-0000-00000000dead"},
                    {"POST", "/api/reports/respondent-summary"}}) {
                HttpResponse<String> response = send(call[0], call[1], authorization);

                assertEquals(503, response.statusCode(), call[1]);
                assertEquals("{\"error\":\"service token not configured\"}", response.body(), call[1]);
            }
        }
    }

    @Test
    void uc016_br008_theRespondentFacingPathsStillAnswer() throws Exception {
        assertEquals(400, send("GET", "/api/pdf/download", null).statusCode(), "the download's own answer");
        assertEquals(200, send("GET", "/", null).statusCode());
    }

    @Test
    void uc016_a2_readinessIsDownWithTheReason() throws Exception {
        HttpResponse<String> ready = send("GET", "/q/health/ready", null);

        assertEquals(503, ready.statusCode(), ready.body());
        String body = ready.body().replaceAll("\\s+", "");
        assertTrue(body.contains("\"name\":\"service-token\",\"status\":\"DOWN\",\"data\":{\"reason\":\"ELICIT_SERVICE_TOKEN is not set\"}"
                .replace(" ", "")), ready.body());
        assertEquals(200, send("GET", "/q/health/live", null).statusCode(), "alive, only not ready");
    }
}
