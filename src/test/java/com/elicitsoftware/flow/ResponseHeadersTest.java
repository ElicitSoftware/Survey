package com.elicitsoftware.flow;

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

import com.elicitsoftware.PostgresTestResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * NFR-016: every response carries the same four headers, whether it is a page the Vaadin
 * servlet renders, a file under {@code /brand} or a REST answer under {@code /api}.
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class ResponseHeadersTest {

    @TestHTTPResource("/")
    URL root;

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private HttpResponse<Void> get(String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(root.toString()).resolve(path))
                .GET()
                .timeout(Duration.ofSeconds(60))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.discarding());
    }

    private static void assertHeaders(HttpResponse<Void> response) {
        String where = response.uri().getPath() + " (" + response.statusCode() + ")";
        assertEquals("max-age=31536000; includeSubDomains",
                response.headers().firstValue("Strict-Transport-Security").orElse(null), where);
        assertEquals("nosniff", response.headers().firstValue("X-Content-Type-Options").orElse(null), where);
        assertEquals("no-referrer", response.headers().firstValue("Referrer-Policy").orElse(null), where);
        assertEquals("frame-ancestors 'self'",
                response.headers().firstValue("Content-Security-Policy").orElse(null), where);
    }

    @Test
    void theLoginPageCarriesTheHeaders() throws Exception {
        HttpResponse<Void> response = get("login");
        assertEquals(200, response.statusCode());
        assertHeaders(response);
    }

    @Test
    void aRestAnswerCarriesTheHeaders() throws Exception {
        // No such key: the answer is the "expired" 404, which carries the headers like any other.
        HttpResponse<Void> response = get("api/pdf/download?key=none");
        assertEquals(404, response.statusCode());
        assertHeaders(response);
    }

    @Test
    void aBrandFileCarriesTheHeaders() throws Exception {
        assertHeaders(get("brand/theme.css"));
    }

    @Test
    void anAddressNothingServesCarriesTheHeaders() throws Exception {
        assertHeaders(get("VAADIN/no-such-file.js"));
    }
}
