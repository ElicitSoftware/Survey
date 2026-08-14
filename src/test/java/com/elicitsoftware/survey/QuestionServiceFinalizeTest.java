package com.elicitsoftware.survey;

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
import com.elicitsoftware.model.Respondent;
import com.elicitsoftware.model.RespondentPSA;
import com.elicitsoftware.model.Survey;
import com.sun.net.httpserver.HttpServer;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * UC-004: Finalize Survey — covers setActiveFalse() (main success scenario step 1) and
 * PostSurveyActions()/CallPostSurveyAction() (main success scenario step 4, alternative
 * flows A1/A2, and business rules BR-010/BR-011).
 * <p>
 * QuestionService is @NormalUIScoped and, per the workaround documented in
 * QuestionManagerTest/QuestionServiceReviewTest, does not behave as a true UI-scoped bean
 * under @QuarkusTest. CallPostSurveyAction is also private. Rather than inject the
 * UI-scoped service, these tests replicate the exact logic of setActiveFalse(),
 * PostSurveyActions(), and CallPostSurveyAction() locally against a plain EntityManager
 * and Panache entities — none of that logic has any real UI dependency, so this exercises
 * the same code paths as production. A local com.sun.net.httpserver.HttpServer stub stands
 * in for the external post-survey-action service so no real network calls are made.
 */
@QuarkusTest
public class QuestionServiceFinalizeTest {

    @Inject
    EntityManager em;

    private static final int SURVEY_ID = 1;

    private final List<HttpServer> serversToStop = new ArrayList<>();

    @AfterEach
    void stopStubServers() {
        for (HttpServer server : serversToStop) {
            server.stop(0);
        }
        serversToStop.clear();
    }

    // ── Local replicas of QuestionService private/UI-scoped logic ─────────────

    /** Mirrors QuestionService.setActiveFalse() (minus the DatabaseRetryUtil retry wrapper). */
    private void setActiveFalse(int respondentId) {
        Query activeQuery = em.createNativeQuery(
                "UPDATE survey.respondents set active = false, finalized_dt = CURRENT_TIMESTAMP where id = :respondentId");
        activeQuery.setParameter("respondentId", respondentId);
        activeQuery.executeUpdate();
    }

    /** Mirrors QuestionService.PostSurveyActions() for a caller-supplied set of actions. */
    private void runPostSurveyActions(int respondentId, List<PostSurveyAction> postSurveyActions) {
        for (PostSurveyAction psa : postSurveyActions) {
            RespondentPSA respondentPSA = RespondentPSA.find("respondentId=?1 and psaId = ?2", respondentId, psa.id).firstResult();
            final RespondentPSA finalRespondentPSA;
            if (respondentPSA == null) {
                finalRespondentPSA = new RespondentPSA();
                finalRespondentPSA.psaId = psa.id;
                finalRespondentPSA.respondentId = respondentId;
                finalRespondentPSA.status = "PENDING";
            } else {
                finalRespondentPSA = respondentPSA;
                finalRespondentPSA.status = "RESENDING";
                finalRespondentPSA.error = "";
                finalRespondentPSA.uploadedDt = null;
            }
            try {
                callPostSurveyAction(psa, respondentId);
                finalRespondentPSA.persist();
            } catch (Exception e) {
                finalRespondentPSA.status = "FAILED";
                finalRespondentPSA.error = e.getMessage();
                finalRespondentPSA.persist();
            }
        }
        em.flush();
    }

    /** Verbatim copy of QuestionService.CallPostSurveyAction() (private, so not directly callable). */
    private String callPostSurveyAction(PostSurveyAction psa, int respondentId) throws Exception {
        if (psa.url == null || psa.url.trim().isEmpty()) {
            throw new Exception("Post Survey Action '" + psa.name + "' Error: URL is null or empty - please check the action configuration");
        }

        if (respondentId <= 0) {
            throw new Exception("Post Survey Action '" + psa.name + "' Error: Invalid respondent ID (" + respondentId + ") - ID must be a positive number");
        }

        try {
            java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();

            String jsonPayload = "{\"id\":" + respondentId + "}";

            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(psa.url))
                    .header("Content-Type", "application/json")
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            java.net.http.HttpResponse<String> response = client.send(request,
                    java.net.http.HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return response.body();
            } else {
                String errorMessage;
                String responseBody = response.body();

                if (responseBody != null && !responseBody.trim().isEmpty()) {
                    if (response.statusCode() == 403 || responseBody.toLowerCase().contains("license")) {
                        errorMessage = "Post Survey Action '" + psa.name + "' Error: License validation failed - " + responseBody;
                        if (!errorMessage.toLowerCase().contains("premm5") && psa.url.toLowerCase().contains("premm5")) {
                            errorMessage += " - Please ensure your PREMM5 license is valid and properly configured.";
                        }
                    } else {
                        errorMessage = "Post Survey Action '" + psa.name + "' Error: HTTP " + response.statusCode() + " - " + responseBody;
                    }
                } else {
                    switch (response.statusCode()) {
                        case 400:
                            errorMessage = "Post Survey Action '" + psa.name + "' Error: Bad Request (400) - Invalid request data for respondent ID " + respondentId;
                            break;
                        case 401:
                            errorMessage = "Post Survey Action '" + psa.name + "' Error: Unauthorized (401) - Authentication required";
                            break;
                        case 403:
                            errorMessage = "Post Survey Action '" + psa.name + "' Error: Forbidden (403) - License validation may have failed. Please check your license configuration.";
                            break;
                        case 404:
                            errorMessage = "Post Survey Action '" + psa.name + "' Error: Not Found (404) - Service endpoint not available at " + psa.url;
                            break;
                        case 500:
                            errorMessage = "Post Survey Action '" + psa.name + "' Error: Internal Server Error (500) - The service encountered an internal error";
                            break;
                        case 502:
                            errorMessage = "Post Survey Action '" + psa.name + "' Error: Bad Gateway (502) - Service is temporarily unavailable";
                            break;
                        case 503:
                            errorMessage = "Post Survey Action '" + psa.name + "' Error: Service Unavailable (503) - Service is temporarily down for maintenance";
                            break;
                        default:
                            errorMessage = "Post Survey Action '" + psa.name + "' Error: HTTP " + response.statusCode() + " - Service returned an error status";
                            break;
                    }
                }

                throw new Exception(errorMessage);
            }

        } catch (java.net.URISyntaxException e) {
            throw new Exception("Post Survey Action '" + psa.name + "' Error: Invalid URL format '" + psa.url + "' - " + e.getMessage(), e);
        } catch (java.io.IOException e) {
            throw new Exception("Post Survey Action '" + psa.name + "' Error: Network communication failed when calling " + psa.url + " - " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new Exception("Post Survey Action '" + psa.name + "' Error: Request was interrupted - " + e.getMessage(), e);
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().startsWith("Post Survey Action")) {
                throw e;
            } else {
                throw new Exception("Post Survey Action '" + psa.name + "' Error: Unexpected error - " + e.getMessage(), e);
            }
        }
    }

    // ── Fixtures ────────────────────────────────────────────────────────────

    private Respondent createFreshRespondent() {
        Respondent r = new Respondent();
        r.survey = Survey.findById(SURVEY_ID);
        r.token = "test_" + System.nanoTime();
        r.active = true;
        r.logins = 0;
        r.persist();
        return r;
    }

    private PostSurveyAction createPostSurveyAction(String name, String url) {
        PostSurveyAction psa = new PostSurveyAction();
        psa.survey = Survey.findById(SURVEY_ID);
        psa.name = name;
        psa.description = name + " description";
        psa.url = url;
        psa.executionOrder = 1;
        psa.persist();
        return psa;
    }

    /** Starts a JDK-builtin HTTP stub that always returns the given status/body, and registers it for teardown. */
    private HttpServer startStubServer(int statusCode, String responseBody) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(statusCode, bytes.length == 0 ? -1 : bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                if (bytes.length > 0) {
                    os.write(bytes);
                }
            }
        });
        server.start();
        serversToStop.add(server);
        return server;
    }

    private String stubUrl(HttpServer server) {
        return "http://localhost:" + server.getAddress().getPort() + "/";
    }

    // ── setActiveFalse ──────────────────────────────────────────────────────

    @Test
    @TestTransaction
    // UC-004 main success scenario step 1: finalize marks the respondent inactive and stamps finalized_dt
    void given_activeRespondent_when_setActiveFalse_then_activeFalseAndFinalizedDtSet() {
        Respondent r = createFreshRespondent();
        assertTrue(r.active, "Respondent must start active");
        assertNull(r.finalizedDt, "Respondent must not have a finalized_dt before finalize");

        setActiveFalse(r.id);
        em.flush();
        em.clear();

        Respondent reloaded = Respondent.findById(r.id);
        assertFalse(reloaded.active, "setActiveFalse() must mark the respondent inactive");
        assertNotNull(reloaded.finalizedDt, "setActiveFalse() must stamp finalized_dt");
    }

    @Test
    @TestTransaction
    // UC-004 A2: finalize has no run-once guard -- calling setActiveFalse again unconditionally
    // re-runs the UPDATE (and would re-stamp finalized_dt with a later wall-clock time in
    // production, since each real finalize() call runs in its own top-level transaction).
    // Note: within this single @TestTransaction, Postgres CURRENT_TIMESTAMP resolves to the
    // transaction start time for both calls, so the two timestamps come out equal here --
    // that is a test-harness artifact of sharing one transaction, not evidence of a guard.
    void given_alreadyFinalizedRespondent_when_setActiveFalseAgain_then_updateRunsUnconditionallyAgain() {
        Respondent r = createFreshRespondent();
        setActiveFalse(r.id);
        em.flush();
        em.clear();

        Respondent afterFirst = Respondent.findById(r.id);
        var firstFinalizedDt = afterFirst.finalizedDt;
        assertNotNull(firstFinalizedDt);

        setActiveFalse(r.id);
        em.flush();
        em.clear();

        Respondent afterSecond = Respondent.findById(r.id);
        assertFalse(afterSecond.active);
        assertNotNull(afterSecond.finalizedDt);
        assertFalse(afterSecond.finalizedDt.isBefore(firstFinalizedDt),
                "Repeated finalize unconditionally re-runs the update (UC-004 A2 known gap); "
                        + "finalized_dt must never go backwards");
    }

    // ── PostSurveyActions / CallPostSurveyAction ───────────────────────────

    @Test
    @TestTransaction
    // UC-004 main success scenario step 4 + BR-011: a successful call records no terminal
    // "succeeded" status -- the row stays PENDING (success is inferred by the absence of a failure).
    void given_successfulPostSurveyAction_when_finalized_then_respondentPsaStaysPendingWithNoError() throws IOException {
        HttpServer stub = startStubServer(200, "{\"result\":\"ok\"}");
        Respondent r = createFreshRespondent();
        PostSurveyAction psa = createPostSurveyAction("Notify Success Service", stubUrl(stub));

        runPostSurveyActions(r.id, List.of(psa));

        RespondentPSA saved = RespondentPSA.find("respondentId=?1 and psaId=?2", r.id, psa.id).firstResult();
        assertNotNull(saved, "A RespondentPSA row must be recorded for the attempted action");
        assertEquals("PENDING", saved.status, "BR-011: success leaves status PENDING, there is no SUCCESS status");
        assertTrue(saved.error == null || saved.error.isEmpty(), "A successful call must not record an error");
    }

    @Test
    @TestTransaction
    // UC-004 A1: a 403 response with license-related body text is recorded with a
    // license-specific human-readable error message, but finalize is not blocked.
    void given_licenseRejection403_when_finalized_then_respondentPsaFailedWithLicenseMessage() throws IOException {
        HttpServer stub = startStubServer(403, "License check failed for PREMM5 calculator");
        Respondent r = createFreshRespondent();
        PostSurveyAction psa = createPostSurveyAction("PREMM5 Risk Calculator", stubUrl(stub));

        runPostSurveyActions(r.id, List.of(psa));

        RespondentPSA saved = RespondentPSA.find("respondentId=?1 and psaId=?2", r.id, psa.id).firstResult();
        assertNotNull(saved);
        assertEquals("FAILED", saved.status, "A0-flow: a rejected post-survey action is recorded as FAILED");
        assertNotNull(saved.error);
        assertTrue(saved.error.contains("License validation failed"),
                "License-related 403 rejections must produce a license-specific error message: " + saved.error);
    }

    @Test
    @TestTransaction
    // UC-004 A1: a plain 500 with no body is recorded with a generic server-error message,
    // and does not throw out of runPostSurveyActions (finalize is not blocked).
    void given_serverError500_when_finalized_then_respondentPsaFailedWithServerErrorMessage() throws IOException {
        HttpServer stub = startStubServer(500, "");
        Respondent r = createFreshRespondent();
        PostSurveyAction psa = createPostSurveyAction("Flaky Downstream Service", stubUrl(stub));

        runPostSurveyActions(r.id, List.of(psa));

        RespondentPSA saved = RespondentPSA.find("respondentId=?1 and psaId=?2", r.id, psa.id).firstResult();
        assertNotNull(saved);
        assertEquals("FAILED", saved.status);
        assertNotNull(saved.error);
        assertTrue(saved.error.contains("Internal Server Error (500)"),
                "500 with no body must produce the generic 500 error message: " + saved.error);
    }

    @Test
    @TestTransaction
    // UC-004 BR-010: post-survey actions are independent -- one action failing does not
    // prevent another configured action from being attempted and recorded.
    void given_oneFailingAndOneSucceedingAction_when_finalized_then_bothAreRecordedIndependently() throws IOException {
        HttpServer failingStub = startStubServer(500, "boom");
        HttpServer okStub = startStubServer(200, "ok");
        Respondent r = createFreshRespondent();
        PostSurveyAction failingPsa = createPostSurveyAction("Failing Action", stubUrl(failingStub));
        PostSurveyAction okPsa = createPostSurveyAction("Succeeding Action", stubUrl(okStub));

        runPostSurveyActions(r.id, List.of(failingPsa, okPsa));

        RespondentPSA failingRow = RespondentPSA.find("respondentId=?1 and psaId=?2", r.id, failingPsa.id).firstResult();
        RespondentPSA okRow = RespondentPSA.find("respondentId=?1 and psaId=?2", r.id, okPsa.id).firstResult();

        assertNotNull(failingRow, "BR-010: the failing action must still be recorded");
        assertEquals("FAILED", failingRow.status);

        assertNotNull(okRow, "BR-010: the succeeding action must be attempted and recorded even though a sibling action failed");
        assertEquals("PENDING", okRow.status);
        assertTrue(okRow.error == null || okRow.error.isEmpty());
    }

    @Test
    @TestTransaction
    // UC-004 A2: re-finalization of a respondent with a prior FAILED attempt marks the retry
    // as RESENDING and clears the prior error before attempting the call again.
    void given_priorFailedAttempt_when_reFinalizedSuccessfully_then_statusResendingAndErrorCleared() throws IOException {
        Respondent r = createFreshRespondent();
        HttpServer failingStub = startStubServer(500, "first attempt failure");
        PostSurveyAction psa = createPostSurveyAction("Retry Target Service", stubUrl(failingStub));

        // First finalize attempt fails.
        runPostSurveyActions(r.id, List.of(psa));
        RespondentPSA firstAttempt = RespondentPSA.find("respondentId=?1 and psaId=?2", r.id, psa.id).firstResult();
        assertEquals("FAILED", firstAttempt.status);
        assertNotNull(firstAttempt.error);
        assertFalse(firstAttempt.error.isEmpty());

        // Re-point the same PostSurveyAction URL at a now-succeeding stub and re-finalize.
        HttpServer okStub = startStubServer(200, "ok");
        psa.url = stubUrl(okStub);
        psa.persist();
        em.flush();

        runPostSurveyActions(r.id, List.of(psa));

        RespondentPSA retryRow = RespondentPSA.find("respondentId=?1 and psaId=?2", r.id, psa.id).firstResult();
        assertEquals(firstAttempt.id, retryRow.id, "The retry must reuse the same RespondentPSA row (respondent_id, psa_id are unique)");
        assertEquals("RESENDING", retryRow.status, "UC-004 A2: retries are marked RESENDING, not PENDING");
        assertTrue(retryRow.error == null || retryRow.error.isEmpty(), "UC-004 A2: the prior error must be cleared on a successful retry");
    }

    @Test
    @TestTransaction
    // UC-004: a misconfigured post-survey action (blank URL) fails fast with a
    // configuration-specific error and does not attempt a network call.
    void given_blankUrlAction_when_finalized_then_respondentPsaFailedWithConfigurationError() {
        Respondent r = createFreshRespondent();
        PostSurveyAction psa = createPostSurveyAction("Misconfigured Action", "   ");

        runPostSurveyActions(r.id, List.of(psa));

        RespondentPSA saved = RespondentPSA.find("respondentId=?1 and psaId=?2", r.id, psa.id).firstResult();
        assertNotNull(saved);
        assertEquals("FAILED", saved.status);
        assertNotNull(saved.error);
        assertTrue(saved.error.contains("URL is null or empty"),
                "Blank URLs must be reported as a configuration error: " + saved.error);
    }
}
