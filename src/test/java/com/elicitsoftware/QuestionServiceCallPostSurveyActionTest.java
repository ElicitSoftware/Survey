package com.elicitsoftware;

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
 * UC-004 (post-survey webhook notification): CallPostSurveyAction is exercised directly
 * against a real local HttpServer fixture rather than through the full
 * PostSurveyActions()/Respondent/RespondentPSA persistence path, since none of its logic
 * touches the database. Package-visibility was loosened in QuestionService.java specifically
 * to make this possible (see the comment there).
 */
class QuestionServiceCallPostSurveyActionTest {

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
                new QuestionService().CallPostSurveyAction(psa("Notify", null), 1));
        assertTrue(ex.getMessage().contains("URL is null or empty"));
    }

    @Test
    void callPostSurveyAction_blankUrl_throwsConfigurationError() {
        Exception ex = assertThrows(Exception.class, () ->
                new QuestionService().CallPostSurveyAction(psa("Notify", "   "), 1));
        assertTrue(ex.getMessage().contains("URL is null or empty"));
    }

    @Test
    void callPostSurveyAction_nonPositiveRespondentId_throwsConfigurationError() {
        Exception ex = assertThrows(Exception.class, () ->
                new QuestionService().CallPostSurveyAction(psa("Notify", "http://localhost/x"), 0));
        assertTrue(ex.getMessage().contains("Invalid respondent ID"));
    }

    @Test
    void callPostSurveyAction_2xxResponse_returnsResponseBody() throws Exception {
        String url = startServer(200, "{\"ok\":true}");
        String result = new QuestionService().CallPostSurveyAction(psa("Notify", url), 42);
        assertEquals("{\"ok\":true}", result);
    }

    @Test
    void callPostSurveyAction_400NoBody_throwsBadRequestMessage() throws IOException {
        String url = startServer(400, null);
        Exception ex = assertThrows(Exception.class, () ->
                new QuestionService().CallPostSurveyAction(psa("Notify", url), 42));
        assertTrue(ex.getMessage().contains("Bad Request (400)"));
    }

    @Test
    void callPostSurveyAction_401NoBody_throwsUnauthorizedMessage() throws IOException {
        String url = startServer(401, null);
        Exception ex = assertThrows(Exception.class, () ->
                new QuestionService().CallPostSurveyAction(psa("Notify", url), 42));
        assertTrue(ex.getMessage().contains("Unauthorized (401)"));
    }

    @Test
    void callPostSurveyAction_404NoBody_throwsNotFoundMessage() throws IOException {
        String url = startServer(404, null);
        Exception ex = assertThrows(Exception.class, () ->
                new QuestionService().CallPostSurveyAction(psa("Notify", url), 42));
        assertTrue(ex.getMessage().contains("Not Found (404)"));
    }

    @Test
    void callPostSurveyAction_500NoBody_throwsInternalServerErrorMessage() throws IOException {
        String url = startServer(500, null);
        Exception ex = assertThrows(Exception.class, () ->
                new QuestionService().CallPostSurveyAction(psa("Notify", url), 42));
        assertTrue(ex.getMessage().contains("Internal Server Error (500)"));
    }

    @Test
    void callPostSurveyAction_502NoBody_throwsBadGatewayMessage() throws IOException {
        String url = startServer(502, null);
        Exception ex = assertThrows(Exception.class, () ->
                new QuestionService().CallPostSurveyAction(psa("Notify", url), 42));
        assertTrue(ex.getMessage().contains("Bad Gateway (502)"));
    }

    @Test
    void callPostSurveyAction_503NoBody_throwsServiceUnavailableMessage() throws IOException {
        String url = startServer(503, null);
        Exception ex = assertThrows(Exception.class, () ->
                new QuestionService().CallPostSurveyAction(psa("Notify", url), 42));
        assertTrue(ex.getMessage().contains("Service Unavailable (503)"));
    }

    @Test
    void callPostSurveyAction_unmappedStatusNoBody_throwsGenericStatusMessage() throws IOException {
        String url = startServer(418, null);
        Exception ex = assertThrows(Exception.class, () ->
                new QuestionService().CallPostSurveyAction(psa("Notify", url), 42));
        assertTrue(ex.getMessage().contains("HTTP 418"));
    }

    @Test
    void callPostSurveyAction_403WithBodyAndPremm5Url_appendsLicenseSuffix() throws IOException {
        String url = startServer(403, "Access denied");
        Exception ex = assertThrows(Exception.class, () ->
                new QuestionService().CallPostSurveyAction(psa("Notify", url + "premm5"), 42));
        assertTrue(ex.getMessage().contains("License validation failed"));
        assertTrue(ex.getMessage().contains("PREMM5 license is valid"));
    }

    @Test
    void callPostSurveyAction_403WithBodyNonPremm5Url_licenseMessageWithoutSuffix() throws IOException {
        String url = startServer(403, "Access denied");
        Exception ex = assertThrows(Exception.class, () ->
                new QuestionService().CallPostSurveyAction(psa("Notify", url), 42));
        assertTrue(ex.getMessage().contains("License validation failed"));
        assertTrue(ex.getMessage().contains("Access denied"));
    }

    @Test
    void callPostSurveyAction_nonLicenseBodyOn400_throwsHttpStatusWithBody() throws IOException {
        String url = startServer(400, "field 'id' is required");
        Exception ex = assertThrows(Exception.class, () ->
                new QuestionService().CallPostSurveyAction(psa("Notify", url), 42));
        assertTrue(ex.getMessage().contains("HTTP 400"));
        assertTrue(ex.getMessage().contains("field 'id' is required"));
    }

    @Test
    void callPostSurveyAction_bodyMentionsLicenseOnNon403Status_stillTreatedAsLicenseError() throws IOException {
        String url = startServer(500, "license check failed unexpectedly");
        Exception ex = assertThrows(Exception.class, () ->
                new QuestionService().CallPostSurveyAction(psa("Notify", url), 42));
        assertTrue(ex.getMessage().contains("License validation failed"));
    }

    @Test
    void callPostSurveyAction_malformedUrl_wrappedAsUnexpectedError() {
        Exception ex = assertThrows(Exception.class, () ->
                new QuestionService().CallPostSurveyAction(psa("Notify", "not a url"), 42));
        assertTrue(ex.getMessage().contains("Post Survey Action 'Notify'"));
    }

    @Test
    void callPostSurveyAction_nothingListening_throwsNetworkCommunicationError() throws IOException {
        int port = unusedPort();
        String url = "http://localhost:" + port + "/";
        Exception ex = assertThrows(Exception.class, () ->
                new QuestionService().CallPostSurveyAction(psa("Notify", url), 42));
        assertTrue(ex.getMessage().contains("Network communication failed"));
    }
}
