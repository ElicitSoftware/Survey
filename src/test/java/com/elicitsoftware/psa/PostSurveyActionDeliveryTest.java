package com.elicitsoftware.psa;

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
import com.elicitsoftware.model.PostSurveyAction;
import com.elicitsoftware.model.Respondent;
import com.elicitsoftware.model.RespondentPSA;
import com.elicitsoftware.model.Survey;
import com.sun.net.httpserver.HttpServer;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UC-015 Deliver Post-Survey Actions, against the database: Survey's delivery states (BR-004),
 * the scheduled retry and its limit (A3, BR-005), refusals (A2), a service's own status left alone,
 * re-finishing (A5), independence (BR-006) and the External ID lookup (BR-001).
 * <p>
 * Every write of a delivery commits on its own, so these tests commit their fixtures too and delete
 * them afterwards, rather than rolling back a test transaction.
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class PostSurveyActionDeliveryTest {

    private static final int SURVEY_ID = 1;

    @Inject
    PostSurveyActionDelivery delivery;

    @Inject
    EntityManager em;

    /** The schema owner: the application role may not create the stand-in for Admin's view. */
    @Inject
    @io.quarkus.agroal.DataSource("owner")
    javax.sql.DataSource owner;

    private void asOwner(String... statements) throws java.sql.SQLException {
        try (java.sql.Connection c = owner.getConnection(); java.sql.Statement st = c.createStatement()) {
            for (String sql : statements) {
                st.execute(sql);
            }
        }
    }

    private final List<HttpServer> servers = new ArrayList<>();
    private final List<Integer> respondents = new ArrayList<>();
    private final List<Integer> actions = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        servers.forEach(s -> s.stop(0));
        servers.clear();
        QuarkusTransaction.requiringNew().run(() -> {
            for (Integer id : respondents) {
                em.createNativeQuery("DELETE FROM survey.respondent_psa WHERE respondent_id = ?1").setParameter(1, id).executeUpdate();
            }
            for (Integer id : actions) {
                em.createNativeQuery("DELETE FROM survey.respondent_psa WHERE post_survey_action_id = ?1").setParameter(1, id).executeUpdate();
                em.createNativeQuery("DELETE FROM survey.post_survey_actions WHERE id = ?1").setParameter(1, id).executeUpdate();
            }
            for (Integer id : respondents) {
                em.createNativeQuery("DELETE FROM survey.respondents WHERE id = ?1").setParameter(1, id).executeUpdate();
            }
        });
        respondents.clear();
        actions.clear();
    }

    // ── fixtures ────────────────────────────────────────────────────────────

    /** A respondent of a survey of its own, so its survey's actions are exactly the test's. */
    private int respondent() {
        int id = QuarkusTransaction.requiringNew().call(() -> {
            Respondent r = new Respondent();
            r.survey = Survey.findById(SURVEY_ID);
            r.accessCode = "psa_" + System.nanoTime();
            r.active = false;
            r.logins = 0;
            r.persist();
            return r.id;
        });
        respondents.add(id);
        return id;
    }

    private int action(String name, String url, int order, String method, boolean sendsExternalId) {
        int id = QuarkusTransaction.requiringNew().call(() -> {
            PostSurveyAction psa = new PostSurveyAction();
            psa.survey = Survey.findById(SURVEY_ID);
            psa.name = name + " " + System.nanoTime();
            psa.description = name;
            psa.url = url;
            psa.executionOrder = order;
            psa.persistAndFlush();
            em.createNativeQuery("UPDATE survey.post_survey_actions SET method = ?1, sends_external_id = ?2 WHERE id = ?3")
                    .setParameter(1, method).setParameter(2, sendsExternalId).setParameter(3, psa.id).executeUpdate();
            return psa.id;
        });
        actions.add(id);
        return id;
    }

    private int action(String name, String url) {
        return action(name, url, 1, "POST", false);
    }

    /** Answers every call with the status the counter's current value selects. */
    private String server(int... statuses) throws IOException {
        AtomicInteger call = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            int i = Math.min(call.getAndIncrement(), statuses.length - 1);
            exchange.sendResponseHeaders(statuses[i], -1);
            exchange.close();
        });
        server.start();
        servers.add(server);
        return "http://localhost:" + server.getAddress().getPort() + "/";
    }

    private RespondentPSA row(int respondentId, int actionId) {
        return QuarkusTransaction.requiringNew().call(() ->
                RespondentPSA.<RespondentPSA>find("respondentId = ?1 and psaId = ?2", (long) respondentId, (long) actionId).firstResult());
    }

    /**
     * Survey's actions are those of the respondent's survey (survey 1 in the test data), so the
     * test's own actions are delivered beside any the data already holds; assertions read only
     * the test's rows.
     */
    private void finish(int respondentId) {
        delivery.deliverAll(respondentId);
    }

    // ── main scenario, A1 ───────────────────────────────────────────────────

    @Test
    void uc015_step6_aSuccessfulCallIsDelivered() throws IOException {
        int r = respondent();
        int a = action("ok", server(200));
        finish(r);
        RespondentPSA saved = row(r, a);
        assertEquals(PostSurveyActionDelivery.DELIVERED, saved.status, "BR-004: success is recorded");
        assertEquals(1, saved.tries, "the attempt is counted");
        assertNull(saved.error);
    }

    @Test
    void uc015_a1_a3_aFailedCallIsRecordedAndRetriedUntilDelivered() throws IOException {
        int r = respondent();
        int a = action("flaky", server(503, 200));
        finish(r);
        RespondentPSA failed = row(r, a);
        assertEquals(PostSurveyActionDelivery.CALL_FAILED, failed.status);
        assertEquals(1, failed.tries);
        assertTrue(failed.error.contains("503"), failed.error);

        delivery.retryFailedCalls();
        RespondentPSA retried = row(r, a);
        assertEquals(PostSurveyActionDelivery.DELIVERED, retried.status, "A3: the retry delivers it");
        assertEquals(2, retried.tries, "BR-005: the retry is an attempt too");
    }

    @Test
    void uc015_br005_aDeliveryAtTheLimitIsNotRetried() throws IOException {
        int r = respondent();
        int a = action("down", server(500));
        finish(r);
        QuarkusTransaction.requiringNew().run(() -> em.createNativeQuery(
                        "UPDATE survey.respondent_psa SET tries = ?1 WHERE respondent_id = ?2 AND post_survey_action_id = ?3")
                .setParameter(1, delivery.maxAttempts()).setParameter(2, r).setParameter(3, a).executeUpdate());
        delivery.retryFailedCalls();
        RespondentPSA saved = row(r, a);
        assertEquals(PostSurveyActionDelivery.CALL_FAILED, saved.status);
        assertEquals(delivery.maxAttempts(), saved.tries, "not called again");
    }

    @Test
    void uc015_a2_aRefusedCallIsRecordedAndNeverRetried() throws IOException {
        int r = respondent();
        int a = action("plain", server(200), 1, "POST", true); // sends the External ID over http
        finish(r);
        RespondentPSA refused = row(r, a);
        assertEquals(PostSurveyActionDelivery.CALL_REFUSED, refused.status, "BR-002");
        assertEquals(0, refused.tries, "no call was made");
        delivery.retryFailedCalls();
        assertEquals(PostSurveyActionDelivery.CALL_REFUSED, row(r, a).status, "BR-005: a refused call is never retried");
    }

    /** BR-004: the service writes its own status on the record during the call; Survey leaves it. */
    @Test
    void uc015_br004_aServiceStatusIsNeverOverwritten() throws IOException {
        int r = respondent();
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            QuarkusTransaction.requiringNew().run(() -> em.createNativeQuery(
                            "UPDATE survey.respondent_psa SET status = 'STARTED' WHERE respondent_id = ?1").setParameter(1, r).executeUpdate());
            exchange.sendResponseHeaders(500, -1);
            exchange.close();
        });
        server.start();
        servers.add(server);
        int a = action("service", "http://localhost:" + server.getAddress().getPort() + "/");
        finish(r);
        RespondentPSA saved = row(r, a);
        assertEquals("STARTED", saved.status, "the call reached the service, so its status stands");
        delivery.retryFailedCalls();
        assertEquals("STARTED", row(r, a).status, "and Survey does not retry it");
    }

    @Test
    void uc015_a5_finishingAgainResendsAndClearsTheError() throws IOException {
        int r = respondent();
        int a = action("again", server(500, 200));
        finish(r);
        assertEquals(PostSurveyActionDelivery.CALL_FAILED, row(r, a).status);
        finish(r);
        RespondentPSA saved = row(r, a);
        assertEquals(PostSurveyActionDelivery.DELIVERED, saved.status);
        assertNull(saved.error, "A5: the prior error is cleared");
    }

    @Test
    void uc015_br006_actionsAreIndependentAndInOrder() throws IOException {
        int r = respondent();
        int failing = action("first", server(500), 1, "POST", false);
        int working = action("second", server(200), 2, "GET", false);
        finish(r);
        assertEquals(PostSurveyActionDelivery.CALL_FAILED, row(r, failing).status);
        assertEquals(PostSurveyActionDelivery.DELIVERED, row(r, working).status, "one failure never stops the next");
    }

    /** BR-001: the External ID and department code come through survey.status, Admin's view. */
    @Test
    void uc015_br001_theExternalIdIsReadThroughTheStatusView() throws java.sql.SQLException {
        int r = respondent();
        asOwner("CREATE TABLE IF NOT EXISTS survey.test_status_stand_in "
                        + "(respondent_id integer, xid varchar(50), department_code varchar(100))",
                "CREATE OR REPLACE VIEW survey.status AS "
                        + "SELECT respondent_id, xid, department_code FROM survey.test_status_stand_in",
                "GRANT SELECT ON survey.status TO survey_user",
                "INSERT INTO survey.test_status_stand_in VALUES (" + r + ", 'MRN00123', 'CARD')");
        try {
            PostSurveyActionCaller.Identity identity = delivery.identity(r);
            assertEquals("MRN00123", identity.externalId());
            assertEquals("CARD", identity.departmentCode());
            assertNull(delivery.identity(respondent()).externalId(), "a respondent with no subject has none");
        } finally {
            asOwner("DROP VIEW IF EXISTS survey.status", "DROP TABLE IF EXISTS survey.test_status_stand_in");
        }
    }

    @Inject
    PostSurveyActionCredentials credentials;

    /** BR-003, UC-007 BR-005: with nothing configured an action has no credential and sends none. */
    @Test
    void uc015_br003_noCredentialConfiguredMeansNoneAndNoHeader() throws Exception {
        java.util.UUID key = java.util.UUID.randomUUID();
        assertEquals("none", credentials.describe(key));
        assertTrue(credentials.authorization(key, "Notify", java.time.Duration.ofSeconds(1)).isEmpty());
        assertEquals("elicit.post-survey-action.\"" + key + "\".bearer-token",
                PostSurveyActionCredentials.setting(key, "bearer-token"), "the setting is keyed by the action's key");
    }
}
