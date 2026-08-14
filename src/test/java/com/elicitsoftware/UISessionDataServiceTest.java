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
import com.elicitsoftware.response.NavResponse;
import com.elicitsoftware.response.NavigationItem;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * UC-006: Log Out — shallow unit-test-only coverage of {@code UISessionDataService.clear()},
 * the method {@code LogoutView.onAttach(...)} calls to satisfy UC-006 step 2 ("System clears
 * all session-held survey and respondent state").
 * <p>
 * Scope note (see UC-006's "Notes / Known Gaps" and the accompanying decision to keep this
 * use case's coverage shallow): {@code LogoutView} itself is a {@code @NormalUIScoped} Vaadin
 * component whose behavior only runs via {@code onAttach(AttachEvent)} and finishes with a raw
 * {@code executeJs("window.location.href = '/';")} browser redirect. Exercising that class
 * meaningfully — attaching it to a UI, verifying the redirect JS actually executes — requires a
 * running Vaadin UI/session, i.e. a UI-testing framework such as Karibu-Testing or Vaadin
 * TestBench. Neither is a dependency of this project, and adding one solely for UC-006 was
 * explicitly rejected as out of scope. That UI-only redirect/session-invalidation trigger is
 * therefore NOT covered by any test in this project; this class covers only the reachable
 * non-UI logic it delegates to (field-nulling on {@code UISessionDataService} and the
 * null-VaadinSession guards on {@link SessionPersistenceService}, see
 * {@link SessionPersistenceServiceTest}).
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
        respondent.token = "tok-123";
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
}
