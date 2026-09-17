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

import com.elicitsoftware.flow.MainView;
import com.elicitsoftware.model.Respondent;
import com.elicitsoftware.model.Survey;
import com.elicitsoftware.response.NavResponse;
import com.vaadin.browserless.quarkus.QuarkusBrowserlessTest;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * UC-006 / browser-refresh survival: SessionPersistenceServiceTest deliberately covers only
 * the null-VaadinSession guard clauses (see its Javadoc). This class covers the real
 * "session present" branches -- persistSessionData()/restoreSessionData()/clearSessionData()
 * actually reading and writing VaadinSession attributes -- now that Browserless Testing gives
 * this project a real UI/session to run them against.
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class SessionPersistenceServiceBrowserlessTest extends QuarkusBrowserlessTest {

    private static final int SURVEY_ID = 1;

    @Inject
    SessionPersistenceService sessionPersistenceService;

    @Inject
    QuestionService questionService;

    @Inject
    EntityManager em;

    @Test
    // Persisting then restoring must round-trip the survey/respondent/nav-path triple through
    // the real VaadinSession attached by Browserless Testing's navigate().
    void persistThenRestore_roundTripsSessionData() {
        navigate(MainView.class);
        Respondent respondent = QuarkusTransaction.requiringNew().call(this::createFreshRespondent);
        try {
            NavResponse navResponse = questionService.init(respondent.id, respondent.survey.initialDisplayKey);

            sessionPersistenceService.persistSessionData(SURVEY_ID, respondent, navResponse);
            SessionPersistenceService.SessionData restored = sessionPersistenceService.restoreSessionData();

            assertNotNull(restored);
            assertEquals(SURVEY_ID, restored.getSurveyId());
            assertEquals(respondent.id, restored.getRespondent().id);
            assertNotNull(restored.getNavResponse());
        } finally {
            cleanup(respondent.id);
        }
    }

    @Test
    // No prior persistSessionData() call means the session attributes are all still unset,
    // so restoration must report "incomplete" (null) rather than throwing.
    void restoreSessionData_noPriorPersist_returnsNull() {
        navigate(MainView.class);
        assertNull(sessionPersistenceService.restoreSessionData());
    }

    @Test
    // clearSessionData() must remove previously-persisted attributes so a subsequent
    // restoreSessionData() call finds nothing.
    void clearSessionData_removesPersistedAttributes() {
        navigate(MainView.class);
        Respondent respondent = QuarkusTransaction.requiringNew().call(this::createFreshRespondent);
        try {
            NavResponse navResponse = questionService.init(respondent.id, respondent.survey.initialDisplayKey);
            sessionPersistenceService.persistSessionData(SURVEY_ID, respondent, navResponse);

            sessionPersistenceService.clearSessionData();

            assertNull(sessionPersistenceService.restoreSessionData());
        } finally {
            cleanup(respondent.id);
        }
    }

    private Respondent createFreshRespondent() {
        Respondent r = new Respondent();
        r.survey = Survey.findById(SURVEY_ID);
        r.accessCode = "test_" + System.nanoTime();
        r.active = true;
        r.logins = 0;
        r.persist();
        return r;
    }

    private void cleanup(Integer respondentId) {
        QuarkusTransaction.requiringNew().run(() -> {
            em.createNativeQuery("DELETE FROM survey.dependents WHERE respondent_id = ?1")
                    .setParameter(1, respondentId).executeUpdate();
            em.createNativeQuery("DELETE FROM survey.answers WHERE respondent_id = ?1")
                    .setParameter(1, respondentId).executeUpdate();
            em.createNativeQuery("DELETE FROM survey.respondents WHERE id = ?1")
                    .setParameter(1, respondentId).executeUpdate();
        });
    }
}
