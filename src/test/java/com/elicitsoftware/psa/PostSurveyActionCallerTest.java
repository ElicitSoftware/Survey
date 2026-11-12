package com.elicitsoftware.psa;

/*-
 * ***LICENSE_START***
 * Elicit Survey
 * %%
 * Copyright (C) 2025 The Regents of the University of Michigan - Rogel Cancer Center
 * %%
 * PolyForm Noncommercial License 1.0.0
 * <https://polyformproject.org/licenses/noncommercial/1.0.0>
 * ***LICENSE_END***
 */

import com.elicitsoftware.model.PostSurveyAction;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UC-015 (formerly UC-004 step 4): the post-survey action call is exercised directly
 * against a real local HttpServer fixture rather than through the full
 * PostSurveyActions()/Respondent/RespondentPSA persistence path, since none of its logic
 * touches the database. Package-visibility was loosened in PostSurveyActionCaller.java specifically
 * to make this possible (see the comment there).
 */
class PostSurveyActionCallerTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    private static PostSurveyAction psa(String name, String url) {
        PostSurveyAction psa = new PostSurveyAction();
        psa.name = name;
        psa.url = url;
        return psa;
    }

    private String startServer(int statusCode, String responseBody) throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            byte[] bytes = responseBody == null ? new byte[0] : responseBody.getBytes();
            exchange.sendResponseHeaders(statusCode, bytes.length == 0 ? 0 : bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.start();
        return "http://localhost:" + server.getAddress().getPort() + "/";
    }

    private static int unusedPort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    @Test
    void callPostSurveyAction_nullUrl_throwsConfigurationError() {
        Exception ex = assertThrows(Exception.class, () ->
                new PostSurveyActionCaller().call(psa("Notify", null), 1));
        assertTrue(ex.getMessage().contains("URL is null or empty"));
    }

    @Test
    void callPostSurveyAction_blankUrl_throwsConfigurationError() {
        Exception ex = assertThrows(Exception.class, () ->
                new PostSurveyActionCaller().call(psa("Notify", "   "), 1));
        assertTrue(ex.getMessage().contains("URL is null or empty"));
    }

    @Test
    void callPostSurveyAction_nonPositiveRespondentId_throwsConfigurationError() {
        Exception ex = assertThrows(Exception.class, () ->
                new PostSurveyActionCaller().call(psa("Notify", "http://localhost/x"), 0));
        assertTrue(ex.getMessage().contains("Invalid respondent ID"));
    }

    @Test
    void callPostSurveyAction_2xxResponse_returnsResponseBody() throws Exception {
        String url = startServer(200, "{\"ok\":true}");
        String result = new PostSurveyActionCaller().call(psa("Notify", url), 42);
        assertEquals("{\"ok\":true}", result);
    }

    @Test
    void callPostSurveyAction_400NoBody_throwsBadRequestMessage() throws IOException {
        String url = startServer(400, null);
        Exception ex = assertThrows(Exception.class, () ->
                new PostSurveyActionCaller().call(psa("Notify", url), 42));
        assertTrue(ex.getMessage().contains("Bad Request (400)"));
    }

    @Test
    void callPostSurveyAction_401NoBody_throwsUnauthorizedMessage() throws IOException {
        String url = startServer(401, null);
        Exception ex = assertThrows(Exception.class, () ->
                new PostSurveyActionCaller().call(psa("Notify", url), 42));
        assertTrue(ex.getMessage().contains("Unauthorized (401)"));
    }

    @Test
    void callPostSurveyAction_404NoBody_throwsNotFoundMessage() throws IOException {
        String url = startServer(404, null);
        Exception ex = assertThrows(Exception.class, () ->
                new PostSurveyActionCaller().call(psa("Notify", url), 42));
        assertTrue(ex.getMessage().contains("Not Found (404)"));
    }

    @Test
    void callPostSurveyAction_500NoBody_throwsInternalServerErrorMessage() throws IOException {
        String url = startServer(500, null);
        Exception ex = assertThrows(Exception.class, () ->
                new PostSurveyActionCaller().call(psa("Notify", url), 42));
        assertTrue(ex.getMessage().contains("Internal Server Error (500)"));
    }

    @Test
    void callPostSurveyAction_502NoBody_throwsBadGatewayMessage() throws IOException {
        String url = startServer(502, null);
        Exception ex = assertThrows(Exception.class, () ->
                new PostSurveyActionCaller().call(psa("Notify", url), 42));
        assertTrue(ex.getMessage().contains("Bad Gateway (502)"));
    }

    @Test
    void callPostSurveyAction_503NoBody_throwsServiceUnavailableMessage() throws IOException {
        String url = startServer(503, null);
        Exception ex = assertThrows(Exception.class, () ->
                new PostSurveyActionCaller().call(psa("Notify", url), 42));
        assertTrue(ex.getMessage().contains("Service Unavailable (503)"));
    }

    @Test
    void callPostSurveyAction_unmappedStatusNoBody_throwsGenericStatusMessage() throws IOException {
        String url = startServer(418, null);
        Exception ex = assertThrows(Exception.class, () ->
                new PostSurveyActionCaller().call(psa("Notify", url), 42));
        assertTrue(ex.getMessage().contains("HTTP 418"));
    }

    @Test
    void callPostSurveyAction_403WithBodyAndPremm5Url_appendsLicenseSuffix() throws IOException {
        String url = startServer(403, "Access denied");
        Exception ex = assertThrows(Exception.class, () ->
                new PostSurveyActionCaller().call(psa("Notify", url + "premm5"), 42));
        assertTrue(ex.getMessage().contains("License validation failed"));
        assertTrue(ex.getMessage().contains("PREMM5 license is valid"));
    }

    @Test
    void callPostSurveyAction_403WithBodyNonPremm5Url_licenseMessageWithoutSuffix() throws IOException {
        String url = startServer(403, "Access denied");
        Exception ex = assertThrows(Exception.class, () ->
                new PostSurveyActionCaller().call(psa("Notify", url), 42));
        assertTrue(ex.getMessage().contains("License validation failed"));
        assertTrue(ex.getMessage().contains("Access denied"));
    }

    @Test
    void callPostSurveyAction_nonLicenseBodyOn400_throwsHttpStatusWithBody() throws IOException {
        String url = startServer(400, "field 'id' is required");
        Exception ex = assertThrows(Exception.class, () ->
                new PostSurveyActionCaller().call(psa("Notify", url), 42));
        assertTrue(ex.getMessage().contains("HTTP 400"));
        assertTrue(ex.getMessage().contains("field 'id' is required"));
    }

    @Test
    void callPostSurveyAction_bodyMentionsLicenseOnNon403Status_stillTreatedAsLicenseError() throws IOException {
        String url = startServer(500, "license check failed unexpectedly");
        Exception ex = assertThrows(Exception.class, () ->
                new PostSurveyActionCaller().call(psa("Notify", url), 42));
        assertTrue(ex.getMessage().contains("License validation failed"));
    }

    @Test
    void callPostSurveyAction_malformedUrl_wrappedAsUnexpectedError() {
        Exception ex = assertThrows(Exception.class, () ->
                new PostSurveyActionCaller().call(psa("Notify", "not a url"), 42));
        assertTrue(ex.getMessage().contains("Post Survey Action 'Notify'"));
    }

    @Test
    void callPostSurveyAction_nothingListening_throwsNetworkCommunicationError() throws IOException {
        int port = unusedPort();
        String url = "http://localhost:" + port + "/";
        Exception ex = assertThrows(Exception.class, () ->
                new PostSurveyActionCaller().call(psa("Notify", url), 42));
        assertTrue(ex.getMessage().contains("Network communication failed"));
    }

    @Test
    void given_a300Response_when_callPostSurveyAction_then_itIsAnErrorNotASuccess() throws IOException {
        // Only 2xx is success; 300 is the first status past the range.
        String url = startServer(300, "pick one");
        Exception ex = assertThrows(Exception.class, () ->
                new PostSurveyActionCaller().call(psa("Notify", url), 42));
        assertEquals("Post Survey Action 'Notify' Error: HTTP 300 - pick one", ex.getMessage());
    }

    @Test
    void given_a299Response_when_callPostSurveyAction_then_itIsASuccess() throws Exception {
        String url = startServer(299, "fine");
        assertEquals("fine", new PostSurveyActionCaller().call(psa("Notify", url), 42));
    }

    @Test
    void given_anErrorStatus_when_callPostSurveyAction_then_itsOwnMessageIsRethrownUnwrapped() throws IOException {
        String url = startServer(400, null);
        Exception ex = assertThrows(Exception.class, () ->
                new PostSurveyActionCaller().call(psa("Notify", url), 42));
        assertEquals("Post Survey Action 'Notify' Error: Bad Request (400) - Invalid request data for respondent ID 42",
                ex.getMessage());
    }

    @Test
    void given_anInterruptedCaller_when_callPostSurveyAction_then_theInterruptIsRestoredAndReported()
            throws Exception {
        java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            try {
                // Never answer while the caller waits, so the call is still pending when interrupted.
                release.await(10, java.util.concurrent.TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            exchange.close();
        });
        server.start();
        String url = "http://localhost:" + server.getAddress().getPort() + "/";

        Thread.currentThread().interrupt();
        Exception ex;
        boolean interruptedAfter;
        try {
            ex = assertThrows(Exception.class, () ->
                    new PostSurveyActionCaller().call(psa("Notify", url), 42));
        } finally {
            // Thread.interrupted() reads and clears the flag, so the test thread is left clean.
            interruptedAfter = Thread.interrupted();
            release.countDown();
        }
        assertTrue(interruptedAfter, "the interrupt status must be restored");
        assertTrue(ex.getMessage().startsWith("Post Survey Action 'Notify' Error: Request was interrupted"),
                ex.getMessage());
    }

    @Test
    void callPostSurveyAction_serviceNeverAnswers_givesUpAtTheTimeout() throws IOException {
        // UC-004 A1: a service that accepts the call and never answers is a failure like any
        // other, and must not hold the respondent's finalize.
        java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            try {
                release.await(10, java.util.concurrent.TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            exchange.close();
        });
        server.start();
        String url = "http://localhost:" + server.getAddress().getPort() + "/";

        PostSurveyActionCaller service = new PostSurveyActionCaller();
        service.timeout = java.time.Duration.ofMillis(300);
        long start = System.nanoTime();
        Exception ex;
        try {
            ex = assertThrows(Exception.class, () -> service.call(psa("Notify", url), 42));
        } finally {
            release.countDown();
        }
        long elapsedMillis = (System.nanoTime() - start) / 1_000_000;
        assertTrue(elapsedMillis >= 250, "waited for the service, " + elapsedMillis + " ms");
        assertTrue(elapsedMillis < 5_000, "gave up after " + elapsedMillis + " ms");
        assertTrue(ex.getMessage().startsWith("Post Survey Action 'Notify' Error: No answer from " + url),
                ex.getMessage());
    }

    @Test
    void postSurveyActionTimeouts_areBounded() {
        assertEquals(java.time.Duration.ofSeconds(5), PostSurveyActionCaller.CONNECT_TIMEOUT, "UC-015 BR-008");
        assertEquals(java.time.Duration.ofSeconds(60), PostSurveyActionCaller.TIMEOUT, "UC-015 BR-008");
        assertEquals(PostSurveyActionCaller.TIMEOUT, new PostSurveyActionCaller().timeout);
    }

    // ── UC-015 BR-001, BR-002, BR-003: method, placeholders, body, https, credential ──

    private static PostSurveyAction action(String url, String method, boolean sendsExternalId) {
        PostSurveyAction psa = psa("Notify", url);
        psa.method = method;
        psa.sendsExternalId = sendsExternalId;
        return psa;
    }

    @Test
    void uc015_br001_postCarriesTheIdBodyAndFillsTheRespondentPlaceholder() throws Exception {
        PostSurveyActionCaller.Built built = new PostSurveyActionCaller()
                .build(action("http://svc.example/r/{respondentId}", "POST", false), 42, PostSurveyActionCaller.Identity.NONE);
        assertEquals("POST", built.method());
        assertEquals("http://svc.example/r/42", built.uri().toString());
        assertEquals("{\"id\":42}", built.body());
    }

    @Test
    void uc015_br001_getHasNoBodyAndAnUnknownMethodIsRefused() throws Exception {
        PostSurveyActionCaller caller = new PostSurveyActionCaller();
        PostSurveyActionCaller.Built get = caller.build(action("http://svc.example/n?r={respondentId}", "get", false), 7,
                PostSurveyActionCaller.Identity.NONE);
        assertEquals("GET", get.method());
        assertEquals(null, get.body(), "a GET carries no body");
        assertEquals("http://svc.example/n?r=7", get.uri().toString());
        assertThrows(PostSurveyActionCaller.CallRefused.class,
                () -> caller.build(action("http://svc.example/", "DELETE", false), 7, PostSurveyActionCaller.Identity.NONE));
    }

    @Test
    void uc015_br001_anExternalIdActionCarriesTheExternalIdAndDepartmentCode() throws Exception {
        PostSurveyActionCaller.Built put = new PostSurveyActionCaller().build(
                action("https://svc.example/patients/{externalId}", "PUT", true), 9,
                new PostSurveyActionCaller.Identity("MRN 00/1", "CARD"));
        assertEquals("https://svc.example/patients/MRN%2000%2F1", put.uri().toString(), "URL-encoded");
        assertEquals("{\"id\":9,\"externalId\":\"MRN 00/1\",\"departmentCode\":\"CARD\"}", put.body());

        PostSurveyActionCaller.Built none = new PostSurveyActionCaller().build(
                action("https://svc.example/n", "POST", true), 9, PostSurveyActionCaller.Identity.NONE);
        assertEquals("{\"id\":9,\"externalId\":null,\"departmentCode\":null}", none.body(),
                "a subject with no External ID sends null");
    }

    @Test
    void uc015_a2_refusals() {
        PostSurveyActionCaller caller = new PostSurveyActionCaller();
        PostSurveyActionCaller.Identity id = new PostSurveyActionCaller.Identity("MRN1", "CARD");
        assertTrue(assertThrows(PostSurveyActionCaller.CallRefused.class,
                () -> caller.build(action("http://svc.example/{externalId}", "POST", true), 1, id))
                .getMessage().contains("must be https"), "BR-002: the External ID only over https");
        assertTrue(assertThrows(PostSurveyActionCaller.CallRefused.class,
                () -> caller.build(action("https://svc.example/{externalId}", "POST", false), 1, id))
                .getMessage().contains("does not send the External ID"));
        assertTrue(assertThrows(PostSurveyActionCaller.CallRefused.class,
                () -> caller.build(action("https://svc.example/{externalId}", "POST", true), 1, PostSurveyActionCaller.Identity.NONE))
                .getMessage().contains("has none"), "A2: an address that needs the External ID");
        assertTrue(assertThrows(PostSurveyActionCaller.CallRefused.class,
                () -> caller.build(action("https://svc.example/{surveyKey}", "POST", false), 1, id))
                .getMessage().contains("placeholder"));
    }

    @Test
    void uc015_br003_theCredentialIsSentAsTheAuthorizationHeader() throws Exception {
        java.util.concurrent.atomic.AtomicReference<String> seen = new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicReference<String> seenMethod = new java.util.concurrent.atomic.AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            seen.set(exchange.getRequestHeaders().getFirst("Authorization"));
            seenMethod.set(exchange.getRequestMethod());
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        server.start();
        String url = "http://localhost:" + server.getAddress().getPort() + "/n/{respondentId}";
        new PostSurveyActionCaller().call(action(url, "GET", false), 5, PostSurveyActionCaller.Identity.NONE,
                java.util.Optional.of("Bearer s3cret"));
        assertEquals("Bearer s3cret", seen.get());
        assertEquals("GET", seenMethod.get());
        new PostSurveyActionCaller().call(action(url, "POST", false), 5);
        assertEquals(null, seen.get(), "no credential configured, no header");
    }
}
