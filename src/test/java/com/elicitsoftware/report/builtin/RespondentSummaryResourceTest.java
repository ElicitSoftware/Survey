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
package com.elicitsoftware.report.builtin;

import com.elicitsoftware.PostgresTestResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** UC-014 A1: the built-in report over HTTP, as the Admin module's print calls it. */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class RespondentSummaryResourceTest {

    @TestHTTPResource(RespondentSummaryService.PATH)
    URL url;

    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    private HttpResponse<String> post(String body) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(java.net.URI.create(url.toString()))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(60))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void given_aFinishedRespondent_when_posted_then_theReportComesBackAsJson() throws Exception {
        HttpResponse<String> response = post("{\"id\":1,\"language\":\"en\"}");
        assertEquals(200, response.statusCode(), response.body());
        assertTrue(response.body().contains("\"title\""), response.body());
        assertTrue(response.body().contains("respondent-summary"), "the card HTML: " + response.body());
        assertTrue(response.body().contains("\"pdf\""), "the PDF part: " + response.body());
        assertTrue(response.body().contains("\"content\""), response.body());
    }

    @Test
    void given_noLanguage_when_posted_then_theRespondentsLanguageApplies() throws Exception {
        HttpResponse<String> response = post("{\"id\":1}");
        assertEquals(200, response.statusCode(), response.body());
    }

    @Test
    void given_anUnknownRespondent_when_posted_then_notFound() throws Exception {
        assertEquals(404, post("{\"id\":999999}").statusCode());
    }
}
