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
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * UC-016 over real HTTP against the booted application, whose test profile configures a
 * platform service token: every path under {@code /api/} but the PDF download answers 401
 * without it (A1) and is served with it (main scenario); the respondent-facing paths need none
 * (BR-001, BR-008); and neither the token nor a presented header reaches the log (BR-005).
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class ServiceTokenFilterTest {

    @TestHTTPResource("/")
    URL root;

    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    /** One protected request: its method, its path and its body, or null for none. */
    private record Call(String method, String path, String body) {
    }

    private static final String UNKNOWN_KEY = "00000000-0000-0000-0000-00000000dead";

    private static final List<Call> PROTECTED = List.of(
            new Call("POST", "/api/etl/build?survey=" + UNKNOWN_KEY, null),
            new Call("POST", "/api/etl/schema/" + UNKNOWN_KEY + "/rename?name=report_x", null),
            new Call("DELETE", "/api/etl/schema/" + UNKNOWN_KEY, null),
            new Call("POST", "/api/reports/respondent-summary", "{\"id\":-1}"));

    private HttpResponse<String> send(Call call, String authorization) throws IOException, InterruptedException {
        return send(call.method(), call.path(), call.body(), authorization);
    }

    private HttpResponse<String> send(String method, String path, String body, String authorization)
            throws IOException, InterruptedException {
        String base = root.toString().replaceAll("/$", "");
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(base + path))
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body))
                .header("Accept", "*/*")
                .timeout(Duration.ofSeconds(60));
        if (body != null) {
            request.header("Content-Type", "application/json");
        }
        if (authorization != null) {
            request.header("Authorization", authorization);
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static void assertUnauthorized(HttpResponse<String> response, String what) {
        assertEquals(401, response.statusCode(), what);
        assertEquals("Bearer", response.headers().firstValue("WWW-Authenticate").orElse(null), what);
        assertEquals("", response.body(), "an empty body: " + what);
    }

    @Test
    void uc016_a1_everyProtectedPathAnswers401WithoutTheToken() throws Exception {
        for (Call call : PROTECTED) {
            assertUnauthorized(send(call, null), call + " with no header");
            assertUnauthorized(send(call, "Bearer not-the-token-not-the-token-not-the-token"), call + " with a wrong token");
            assertUnauthorized(send(call, "Bearer " + TestServiceToken.VALUE + "x"), call + " with a longer token");
            assertUnauthorized(send(call, "Bearer " + ServiceToken.DEVELOPMENT_TOKEN), call + " with the development token");
            assertUnauthorized(send(call, "Basic " + TestServiceToken.VALUE), call + " with another scheme");
            assertUnauthorized(send(call, TestServiceToken.VALUE), call + " with a bare token");
        }
    }

    @Test
    void uc016_main_everyProtectedPathIsServedWithTheToken() throws Exception {
        for (Call call : PROTECTED) {
            HttpResponse<String> response = send(call, TestServiceToken.AUTHORIZATION);

            // Each resource's own answer for a survey or respondent that does not exist.
            assertEquals(404, response.statusCode(), call + ": " + response.body());
        }
    }

    @Test
    void uc016_a1_aRefusedRequestChangesNothing() throws Exception {
        // A rename that would be refused as invalid by the resource answers 401 first: the
        // resource never ran.
        HttpResponse<String> response = send("POST", "/api/etl/schema/not-a-uuid/rename?name=Bad Name".replace(" ", "%20"), null, null);

        assertUnauthorized(response, "the filter answers before the resource");
    }

    @Test
    void uc016_br001_thePdfDownloadNeedsNoToken() throws Exception {
        HttpResponse<String> missingKey = send("GET", "/api/pdf/download", null, null);
        HttpResponse<String> unknownKey = send("GET", "/api/pdf/download?key=pdf_" + UUID.randomUUID(), null, null);

        assertEquals(400, missingKey.statusCode(), "the download's own answer, not the filter's");
        assertEquals(404, unknownKey.statusCode());
    }

    @Test
    void uc016_br008_brandFilesAndPagesNeedNoToken() throws Exception {
        assertEquals(200, send("GET", "/brand/theme.css", null, null).statusCode());
        assertEquals(200, send("GET", "/", null, null).statusCode());
    }

    @Test
    void uc016_br001_noSpellingOfAProtectedPathIsServedWithoutTheToken() throws Exception {
        for (String path : List.of(
                "//api/etl/build",
                "/api//etl/build",
                "/api/etl/build/",
                "/api/./etl/build",
                "/api/pdf/../etl/build",
                "/api/pdf/download/../../etl/build",
                "/brand/../api/etl/build",
                "/api/etl/build;x=1",
                "/api;x=1/etl/build",
                "/api/%65tl/build",
                "/%61pi/etl/build",
                "/api%2Fetl%2Fbuild",
                "/API/etl/build",
                "/api/pdf/download/../../reports/respondent-summary")) {
            HttpResponse<String> response = send("POST", path + "?survey=" + UNKNOWN_KEY, null, null);

            // Either the filter refused it, or it never matched a REST resource at all (the
            // answer is then a page or an error, never the build's JSON).
            assertFalse(response.body().contains("\"status\""), path + " reached the build: " + response.body());
            assertFalse(response.headers().firstValue("content-type").orElse("").startsWith("application/json"),
                    path + " answered JSON with " + response.statusCode());
        }
    }

    @Test
    void uc016_br005_neitherTheTokenNorAPresentedHeaderIsLogged() throws Exception {
        List<String> lines = new CopyOnWriteArrayList<>();
        Handler handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                lines.add(record.getMessage() + " " + java.util.Arrays.toString(record.getParameters())
                        + (record.getThrown() == null ? "" : " " + record.getThrown()));
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        handler.setLevel(java.util.logging.Level.ALL);
        Logger rootLogger = Logger.getLogger("");
        rootLogger.addHandler(handler);
        String presented = "presented-but-wrong-" + UUID.randomUUID();
        try {
            for (Call call : PROTECTED) {
                send(call, TestServiceToken.AUTHORIZATION);
                send(call, "Bearer " + presented);
                send(call, null);
            }
        } finally {
            rootLogger.removeHandler(handler);
        }

        assertFalse(lines.isEmpty(), "the access log at least recorded the requests");
        for (String line : lines) {
            assertFalse(line.contains(TestServiceToken.VALUE), line);
            assertFalse(line.contains(presented), line);
        }
    }
}
