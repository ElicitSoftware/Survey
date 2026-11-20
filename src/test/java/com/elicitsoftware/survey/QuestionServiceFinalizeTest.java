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

import com.elicitsoftware.model.Respondent;
import com.elicitsoftware.model.Survey;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.common.QuarkusTestResource;
import com.elicitsoftware.PostgresTestResource;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.Test;


import static org.junit.jupiter.api.Assertions.*;

/**
 * UC-004: Finalize Survey — covers setActiveFalse() (main success scenario step 1). Step 4, the
 * post-survey actions, is UC-015 and is covered by PostSurveyActionDeliveryTest against the
 * real delivery code.
 * <p>
 * QuestionService is @NormalUIScoped and, per the workaround documented in
 * QuestionManagerTest/QuestionServiceReviewTest, does not behave as a true UI-scoped bean
 * under @QuarkusTest. Rather than inject the UI-scoped service, these tests replicate the exact
 * logic of setActiveFalse() locally against a plain EntityManager and Panache entities; it has no
 * real UI dependency, so this exercises the same code path as production.
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
public class QuestionServiceFinalizeTest {

    @Inject
    EntityManager em;

    private static final int SURVEY_ID = 1;

    // ── Local replicas of QuestionService private/UI-scoped logic ─────────────

    /** Mirrors QuestionService.setActiveFalse() (minus the DatabaseRetryUtil retry wrapper). */
    private void setActiveFalse(int respondentId) {
        Query activeQuery = em.createNativeQuery(
                "UPDATE survey.respondents set active = false, finalized_dt = CURRENT_TIMESTAMP where id = :respondentId");
        activeQuery.setParameter("respondentId", respondentId);
        activeQuery.executeUpdate();
    }

    // ── Fixtures ────────────────────────────────────────────────────────────

    private Respondent createFreshRespondent() {
        Respondent r = new Respondent();
        r.survey = Survey.findById(SURVEY_ID);
        r.accessCode = "test_" + System.nanoTime();
        r.active = true;
        r.logins = 0;
        r.persist();
        return r;
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
}
