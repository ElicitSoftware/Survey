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

import com.elicitsoftware.model.Respondent;
import com.elicitsoftware.model.Step;
import com.elicitsoftware.response.NavResponse;
import com.elicitsoftware.response.NavigationItem;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * UC-006: Log Out — shallow unit-test-only coverage of {@code UISessionDataService.clear()},
 * the method {@code LogoutView.onAttach(...)} calls to satisfy UC-006 step 2 ("System clears
 * all session-held survey and respondent state").
 * <p>
 * Scope note, updated: the class previously said exercising {@code LogoutView} itself — a
 * {@code @NormalUIScoped} Vaadin component whose behavior only runs via
 * {@code onAttach(AttachEvent)} — would require a UI-testing framework such as
 * Karibu-Testing or Vaadin TestBench, neither a dependency here, and was out of scope for
 * UC-006. That's stale: this project has since adopted Vaadin Browserless Testing (see
 * {@code MainViewTest}/{@code SectionViewTest}), which does run a real UI/session.
 * {@link com.elicitsoftware.flow.LogoutViewTest} now attaches {@code LogoutView} through it
 * and confirms {@code clear()} actually runs on attach. The tests below still cover the
 * non-UI logic directly (field-nulling on {@code UISessionDataService} and the
 * null-VaadinSession guards on {@link SessionPersistenceService}, see
 * {@link SessionPersistenceServiceTest}) since that's the more precise place to pin those
 * specific behaviors; the raw {@code executeJs("window.location.href = '/';")} browser
 * redirect itself still isn't observable from Browserless Testing (no real browser navigates).
 */
class UISessionDataServiceTest {

    // UC-006 step 2: clear() must null out every session-held field (surveyId, respondent,
    // navResponse) regardless of what was previously populated, so a subsequent visit starts
    // fresh per UC-006's postcondition ("session no longer references any respondent or survey").
    @Test
    void given_populatedSession_when_clear_then_allFieldsNulled() {
        UISessionDataService service = new UISessionDataService();
        service.sessionPersistenceService = new SessionPersistenceService();

        Respondent respondent = new Respondent();
        respondent.id = 42;
        respondent.accessCode = "tok-123";
        NavigationItem navItem = new NavigationItem("Step 1", false, "0-0-0-0-0-0-0", null, null);
        NavResponse navResponse = new NavResponse(null, navItem, null, null);

        service.setSurveyId(7);
        service.setRespondent(respondent);
        service.setNavResponse(navResponse);

        service.clear();

        assertNull(service.getSurveyId());
        assertNull(service.getRespondent());
        assertNull(service.getNavResponse());
    }

    // UC-006 step 2 (safety): clear() delegates to SessionPersistenceService.clearSessionData(),
    // which itself early-returns when there is no active VaadinSession (as in this plain unit
    // test). clear() must not throw even though it is called outside a real UI/session context.
    @Test
    void given_noVaadinSession_when_clear_then_noExceptionThrown() {
        UISessionDataService service = new UISessionDataService();
        service.sessionPersistenceService = new SessionPersistenceService();

        assertDoesNotThrow(service::clear);
    }

    // UC-006 (supporting behavior): restoreFromSession() must return false, not throw, when
    // there is no persisted/restorable session data (e.g. right after logout cleared it, or
    // simply because no VaadinSession is bound to the current thread).
    @Test
    void given_noRestorableSessionData_when_restoreFromSession_then_returnsFalse() {
        UISessionDataService service = new UISessionDataService();
        service.sessionPersistenceService = new SessionPersistenceService();

        boolean restored = assertDoesNotThrow(service::restoreFromSession);

        assertFalse(restored);
    }

    // UC-006 (supporting behavior): when SessionPersistenceService does have restorable data,
    // restoreFromSession() must copy every field out of it and return true. A small local
    // subclass overriding restoreSessionData() stands in for a real VaadinSession here -- this
    // project has no Mockito dependency, and plain subclassing is enough for this one method.
    @Test
    void given_sessionPersistenceServiceHasRestorableData_when_restoreFromSession_then_returnsTrueAndCopiesFields() {
        Respondent respondent = new Respondent();
        respondent.id = 5;
        NavResponse navResponse = new NavResponse(null, null, null, null);
        SessionPersistenceService.SessionData data =
                new SessionPersistenceService.SessionData(3, respondent, navResponse);

        UISessionDataService service = new UISessionDataService();
        service.sessionPersistenceService = new SessionPersistenceService() {
            @Override
            public SessionData restoreSessionData() {
                return data;
            }
        };

        boolean restored = service.restoreFromSession();

        assertTrue(restored);
        assertEquals(3, service.getSurveyId());
        assertSame(respondent, service.getRespondent());
        assertSame(navResponse, service.getNavResponse());
    }

    // toString() is used for debug logging; it must include the survey id, respondent id, and
    // current step id when all three are populated.
    @Test
    void toString_withRespondentAndNavResponse_includesKeyFieldsInOutput() {
        UISessionDataService service = new UISessionDataService();
        service.sessionPersistenceService = new SessionPersistenceService();

        Respondent respondent = new Respondent();
        respondent.id = 42;
        Step step = new Step();
        step.id = 7;
        NavigationItem navItem = new NavigationItem("Step 1", false, "path", null, null);
        NavResponse navResponse = new NavResponse(step, navItem, null, null);

        service.setSurveyId(1);
        service.setRespondent(respondent);
        service.setNavResponse(navResponse);

        String result = service.toString();

        assertTrue(result.contains("Survey Id: 1"));
        assertTrue(result.contains("42"));
        assertTrue(result.contains("7"));
    }
}
