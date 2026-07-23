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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * UC-006: Log Out — shallow unit-test-only coverage of SessionPersistenceService's
 * null-VaadinSession guard clauses. These guards are the reachable non-UI logic behind
 * "System clears all session-held survey and respondent state" (UC-006 step 2): every
 * public method on this class starts with a {@code VaadinSession.getCurrent() == null}
 * check and safely no-ops/returns null when there is no active Vaadin session — exactly
 * the state of a plain JUnit test thread, so no mocking or fake session is needed to
 * exercise this branch honestly.
 * <p>
 * This test intentionally does NOT attempt to exercise the "session present" branches
 * (which read/write real VaadinSession attributes) since doing so meaningfully would
 * require a running Vaadin UI/session, which in turn would require a UI-testing
 * framework such as Karibu-Testing — not currently a dependency of this project and
 * explicitly out of scope for UC-006's coverage. See {@link UISessionDataServiceTest}
 * for the accompanying note on LogoutView's UI-only redirect trigger being unreachable
 * to plain unit tests for the same reason.
 */
class SessionPersistenceServiceTest {

    // UC-006 step 2: clearSessionData() is the method LogoutView -> UISessionDataService.clear()
    // ultimately delegates to. With no VaadinSession bound to the current thread (as in this
    // plain unit test), it must be a safe no-op rather than throwing.
    @Test
    void given_noVaadinSession_when_clearSessionData_then_noExceptionThrown() {
        SessionPersistenceService service = new SessionPersistenceService();

        assertDoesNotThrow(service::clearSessionData);
    }

    // UC-006 (supporting behavior, shared with UC-001/UC-002 session persistence): with no
    // VaadinSession available, persisting must not throw even though respondent data is supplied.
    @Test
    void given_noVaadinSessionAndRespondentData_when_persistSessionData_then_noExceptionThrown() {
        SessionPersistenceService service = new SessionPersistenceService();

        assertDoesNotThrow(() -> service.persistSessionData(1, null, null));
    }

    // UC-006 (supporting behavior): restoreSessionData()'s null-session guard must return null
    // rather than throwing, since it is called on every new UI creation, including after logout
    // when the session has been cleared/invalidated.
    @Test
    void given_noVaadinSession_when_restoreSessionData_then_returnsNullSafely() {
        SessionPersistenceService service = new SessionPersistenceService();

        assertNull(assertDoesNotThrow(service::restoreSessionData));
    }
}
