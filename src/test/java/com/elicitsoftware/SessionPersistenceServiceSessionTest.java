package com.elicitsoftware;

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

import com.elicitsoftware.model.Respondent;
import com.elicitsoftware.model.Survey;
import com.elicitsoftware.response.NavResponse;
import com.elicitsoftware.response.NavigationItem;
import com.vaadin.flow.server.VaadinSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UC-001: Enter Survey via Access Code (a browser refresh resumes the session) and UC-006: Log
 * Out (step 2, the session state is cleared) — SessionPersistenceService with a Vaadin session
 * bound to the test thread. The session is a subclass that keeps its attributes in a map, so no
 * Vaadin service or lock is needed; the access-code check and the page render are subclasses of
 * their services that answer from the test. {@link SessionPersistenceServiceTest} covers the
 * same methods with no session at all.
 */
class SessionPersistenceServiceSessionTest {

    private static final String SURVEY = "elicit.survey.id";
    private static final String RESPONDENT = "elicit.respondent.id";
    private static final String ACCESS_CODE = "elicit.respondent.accessCode";
    private static final String NAV_PATH = "elicit.current.nav.path";

    /** A session whose attributes live in a map; optionally refuses every write. */
    private static final class MapSession extends VaadinSession {
        final Map<String, Object> attributes = new HashMap<>();
        boolean failWrites;

        MapSession() {
            super(null);
        }

        @Override
        public void setAttribute(String name, Object value) {
            if (failWrites) {
                throw new IllegalStateException("session is gone");
            }
            if (value == null) {
                attributes.remove(name);
            } else {
                attributes.put(name, value);
            }
        }

        @Override
        public Object getAttribute(String name) {
            return attributes.get(name);
        }
    }

    private final MapSession session = new MapSession();
    private final List<String> calls = new ArrayList<>();

    @AfterEach
    void unbindSession() {
        VaadinSession.setCurrent(null);
    }

    private static Respondent respondent(int id, String accessCode, String initialDisplayKey) {
        Respondent respondent = new Respondent();
        respondent.id = id;
        respondent.accessCode = accessCode;
        if (initialDisplayKey != null) {
            respondent.survey = new Survey();
            respondent.survey.initialDisplayKey = initialDisplayKey;
        }
        return respondent;
    }

    private static NavResponse page(String path) {
        NavigationItem current = path == null ? null : new NavigationItem("Section", false, path, null, null);
        return new NavResponse(null, current, List.of(), new NavigationItem[0]);
    }

    /**
     * A service whose login answers with the given respondent and whose page render answers from
     * the given table (a key missing from it throws, as a stale path would).
     */
    private SessionPersistenceService service(Respondent loggedIn, Map<String, NavResponse> pages) {
        SessionPersistenceService service = new SessionPersistenceService();
        service.accessCodeService = new AccessCodeService() {
            @Override
            public Respondent login(int surveyId, String accessCode) {
                calls.add("login " + surveyId + " " + accessCode);
                return loggedIn;
            }
        };
        service.questionService = new QuestionService() {
            @Override
            public NavResponse init(int respondentId, String displaykey) {
                calls.add("init " + respondentId + " " + displaykey);
                if (!pages.containsKey(displaykey)) {
                    throw new IllegalStateException("no such page " + displaykey);
                }
                return pages.get(displaykey);
            }
        };
        return service;
    }

    private void storeSession(Integer surveyId, Integer respondentId, String accessCode, String navPath) {
        session.attributes.clear();
        if (surveyId != null) session.attributes.put(SURVEY, surveyId);
        if (respondentId != null) session.attributes.put(RESPONDENT, respondentId);
        if (accessCode != null) session.attributes.put(ACCESS_CODE, accessCode);
        if (navPath != null) session.attributes.put(NAV_PATH, navPath);
        VaadinSession.setCurrent(session);
    }

    // ---- persistSessionData --------------------------------------------------------------------

    @Test
    void given_aSessionAndFullState_when_persistSessionData_then_allFourAttributesAreStored() {
        VaadinSession.setCurrent(session);

        try (LogCapture log = LogCapture.of(SessionPersistenceService.class)) {
            new SessionPersistenceService().persistSessionData(3, respondent(9, "ABC123", null),
                    page("0003-0001-0000-0002-0000-0000-0000"));

            assertTrue(log.contains(LogCapture.INFO, "Session data persisted - Survey: 3, Respondent: 9"));
        }
        assertEquals(Map.of(SURVEY, 3, RESPONDENT, 9, ACCESS_CODE, "ABC123",
                NAV_PATH, "0003-0001-0000-0002-0000-0000-0000"), session.attributes);
    }

    @Test
    void given_aSessionAndNoState_when_persistSessionData_then_nothingIsStored() {
        VaadinSession.setCurrent(session);

        try (LogCapture log = LogCapture.of(SessionPersistenceService.class)) {
            new SessionPersistenceService().persistSessionData(null, null, null);

            assertTrue(log.contains(LogCapture.INFO, "Survey: null, Respondent: null"));
        }
        assertTrue(session.attributes.isEmpty());
    }

    @Test
    void given_aPageWithNoUsablePath_when_persistSessionData_then_noPathIsStored() {
        VaadinSession.setCurrent(session);
        SessionPersistenceService service = new SessionPersistenceService();

        service.persistSessionData(3, null, page(null));
        service.persistSessionData(3, null, page(""));
        service.persistSessionData(3, null, new NavResponse(null,
                new NavigationItem("Section", false, null, null, null), List.of(), new NavigationItem[0]));

        assertEquals(Map.of(SURVEY, 3), session.attributes);
    }

    @Test
    void given_aSessionThatRefusesWrites_when_persistSessionData_then_theFailureIsLoggedNotThrown() {
        session.failWrites = true;
        VaadinSession.setCurrent(session);

        try (LogCapture log = LogCapture.of(SessionPersistenceService.class)) {
            new SessionPersistenceService().persistSessionData(3, null, null);

            assertTrue(log.contains(LogCapture.ERROR, "Failed to persist session data"));
        }
    }

    @Test
    void given_noSession_when_persistSessionData_then_aWarningIsLogged() {
        try (LogCapture log = LogCapture.of(SessionPersistenceService.class)) {
            new SessionPersistenceService().persistSessionData(3, null, null);

            assertTrue(log.contains(LogCapture.WARN, "No VaadinSession available for persistence"));
            assertEquals(List.of(), log.messages(LogCapture.ERROR));
        }
    }

    // ---- clearSessionData ----------------------------------------------------------------------

    @Test
    void given_aStoredSession_when_clearSessionData_then_allFourAttributesAreRemoved() {
        storeSession(3, 9, "ABC123", "path");
        session.attributes.put("unrelated", "kept");

        try (LogCapture log = LogCapture.of(SessionPersistenceService.class)) {
            new SessionPersistenceService().clearSessionData();

            assertTrue(log.contains(LogCapture.INFO, "Session data cleared"));
        }
        assertEquals(Map.of("unrelated", "kept"), session.attributes);
    }

    @Test
    void given_aSessionThatRefusesWrites_when_clearSessionData_then_theFailureIsLoggedNotThrown() {
        session.failWrites = true;
        VaadinSession.setCurrent(session);

        try (LogCapture log = LogCapture.of(SessionPersistenceService.class)) {
            new SessionPersistenceService().clearSessionData();

            assertTrue(log.contains(LogCapture.ERROR, "Failed to clear session data"));
        }
    }

    @Test
    void given_noSession_when_clearSessionData_then_nothingIsAttemptedOrLogged() {
        try (LogCapture log = LogCapture.of(SessionPersistenceService.class)) {
            new SessionPersistenceService().clearSessionData();

            assertEquals(List.of(), log.messages(LogCapture.ERROR));
            assertEquals(List.of(), log.messages(LogCapture.INFO));
        }
    }

    // ---- restoreSessionData --------------------------------------------------------------------

    @Test
    void given_noSession_when_restoreSessionData_then_aWarningIsLoggedAndNothingRestored() {
        try (LogCapture log = LogCapture.of(SessionPersistenceService.class)) {
            assertNull(service(null, Map.of()).restoreSessionData());

            assertTrue(log.contains(LogCapture.WARN, "No VaadinSession available for restoration"));
            assertEquals(List.of(), log.messages(LogCapture.ERROR));
        }
        assertTrue(calls.isEmpty());
    }

    @Test
    void given_aStoredPath_when_restoreSessionData_then_theRespondentResumesOnThatPage() {
        Respondent respondent = respondent(9, "ABC123", "3-1-0-1-0-0-0");
        NavResponse resumed = page("0003-0002-0000-0001-0000-0000-0000");
        storeSession(3, 9, "ABC123", "3-2-0-1-0-0-0");

        SessionPersistenceService.SessionData data;
        try (LogCapture log = LogCapture.of(SessionPersistenceService.class)) {
            data = service(respondent, Map.of("3-2-0-1-0-0-0", resumed)).restoreSessionData();

            assertTrue(log.contains(LogCapture.INFO, "Restored to navigation path: 3-2-0-1-0-0-0"));
            assertTrue(log.contains(LogCapture.INFO, "Restored to path: 0003-0002-0000-0001-0000-0000-0000"));
        }

        assertEquals(3, data.getSurveyId());
        assertSame(respondent, data.getRespondent());
        assertSame(resumed, data.getNavResponse());
        assertEquals(List.of("login 3 ABC123", "init 9 3-2-0-1-0-0-0"), calls);
        // A successful restore leaves the stored state in place.
        assertEquals(4, session.attributes.size());
    }

    @Test
    void given_aStalePath_when_restoreSessionData_then_theRespondentStartsAtTheSurveysFirstPage() {
        Respondent respondent = respondent(9, "ABC123", "3-1-0-1-0-0-0");
        NavResponse first = page(null);
        storeSession(3, 9, "ABC123", "3-9-0-9-0-0-0");

        SessionPersistenceService.SessionData data;
        try (LogCapture log = LogCapture.of(SessionPersistenceService.class)) {
            data = service(respondent, Map.of("3-1-0-1-0-0-0", first)).restoreSessionData();

            assertTrue(log.contains(LogCapture.WARN, "Failed to restore to navigation path: 3-9-0-9-0-0-0"));
            assertTrue(log.contains(LogCapture.INFO, "Started from initial display key: 3-1-0-1-0-0-0"));
            assertTrue(log.contains(LogCapture.INFO, "Restored to path: unknown"));
        }

        assertSame(first, data.getNavResponse());
        assertEquals(List.of("login 3 ABC123", "init 9 3-9-0-9-0-0-0", "init 9 3-1-0-1-0-0-0"), calls);
    }

    @Test
    void given_aPathThatRendersNothing_when_restoreSessionData_then_theFirstPageIsRendered() {
        Respondent respondent = respondent(9, "ABC123", "3-1-0-1-0-0-0");
        NavResponse first = page("first");
        HashMap<String, NavResponse> pages = new HashMap<>();
        pages.put("3-2-0-1-0-0-0", null);
        pages.put("3-1-0-1-0-0-0", first);
        storeSession(3, 9, "ABC123", "3-2-0-1-0-0-0");

        SessionPersistenceService.SessionData data = service(respondent, pages).restoreSessionData();

        assertSame(first, data.getNavResponse());
        assertEquals(List.of("login 3 ABC123", "init 9 3-2-0-1-0-0-0", "init 9 3-1-0-1-0-0-0"), calls);
    }

    @Test
    void given_noStoredPath_when_restoreSessionData_then_theSurveysFirstPageIsRendered() {
        Respondent respondent = respondent(9, "ABC123", "3-1-0-1-0-0-0");
        NavResponse first = page("first");
        storeSession(3, 9, "ABC123", null);

        SessionPersistenceService.SessionData data =
                service(respondent, Map.of("3-1-0-1-0-0-0", first)).restoreSessionData();

        assertSame(first, data.getNavResponse());
        assertEquals(List.of("login 3 ABC123", "init 9 3-1-0-1-0-0-0"), calls);
    }

    @Test
    void given_anEmptyStoredPath_when_restoreSessionData_then_theSurveysFirstPageIsRendered() {
        Respondent respondent = respondent(9, "ABC123", "3-1-0-1-0-0-0");
        storeSession(3, 9, "ABC123", "");

        SessionPersistenceService.SessionData data =
                service(respondent, Map.of("3-1-0-1-0-0-0", page("first"))).restoreSessionData();

        assertEquals("first", data.getNavResponse().getCurrentNavItem().getPath());
        assertEquals(List.of("login 3 ABC123", "init 9 3-1-0-1-0-0-0"), calls);
    }

    @Test
    void given_aSurveyWithNoInitialKey_when_restoreSessionData_then_theDefaultKeyIsUsed() {
        Respondent noSurvey = respondent(9, "ABC123", null);
        storeSession(3, 9, "ABC123", null);

        SessionPersistenceService.SessionData data =
                service(noSurvey, Map.of("0-0-0-0-0-0-0", page("default"))).restoreSessionData();
        assertEquals("default", data.getNavResponse().getCurrentNavItem().getPath());

        calls.clear();
        Respondent blankSurvey = respondent(9, "ABC123", "x");
        blankSurvey.survey.initialDisplayKey = null;
        data = service(blankSurvey, Map.of("0-0-0-0-0-0-0", page("default"))).restoreSessionData();
        assertEquals("default", data.getNavResponse().getCurrentNavItem().getPath());
        assertEquals(List.of("login 3 ABC123", "init 9 0-0-0-0-0-0-0"), calls);
    }

    @Test
    void given_incompleteStoredState_when_restoreSessionData_then_nothingIsRestoredOrCheckedOrCleared() {
        Respondent respondent = respondent(9, "ABC123", "3-1-0-1-0-0-0");
        Integer[][] ids = {{null, 9}, {3, null}, {3, 9}};
        String[] codes = {"ABC123", "ABC123", null};
        for (int i = 0; i < ids.length; i++) {
            storeSession(ids[i][0], ids[i][1], codes[i], "path");
            int stored = session.attributes.size();
            try (LogCapture log = LogCapture.of(SessionPersistenceService.class)) {
                assertNull(service(respondent, Map.of()).restoreSessionData());

                assertTrue(log.contains(LogCapture.INFO, "Incomplete session data found"));
                // The access code itself is never logged, only whether one was stored.
                assertTrue(log.contains(LogCapture.INFO, "Access code: " + (codes[i] == null ? "null" : "present")));
                assertFalse(log.contains(LogCapture.INFO, "ABC123"));
            }
            assertEquals(stored, session.attributes.size(), "case " + i + " must not clear the session");
        }
        assertTrue(calls.isEmpty(), calls.toString());
    }

    @Test
    void given_anAccessCodeThatNoLongerLogsIn_when_restoreSessionData_then_theSessionIsCleared() {
        storeSession(3, 9, "ABC123", "path");

        try (LogCapture log = LogCapture.of(SessionPersistenceService.class)) {
            assertNull(service(null, Map.of()).restoreSessionData());

            assertTrue(log.contains(LogCapture.WARN, "Invalid respondent found during restoration"));
        }
        assertTrue(session.attributes.isEmpty());
        assertEquals(List.of("login 3 ABC123"), calls);
    }

    @Test
    void given_anAccessCodeNowNamingAnotherRespondent_when_restoreSessionData_then_theSessionIsCleared() {
        storeSession(3, 9, "ABC123", "path");

        assertNull(service(respondent(10, "ABC123", "k"), Map.of()).restoreSessionData());

        assertTrue(session.attributes.isEmpty());
        assertEquals(List.of("login 3 ABC123"), calls);
    }

    @Test
    void given_anUnexpectedFailure_when_restoreSessionData_then_itIsLoggedAndTheSessionCleared() {
        storeSession(3, 9, "ABC123", null);
        SessionPersistenceService service = service(null, Map.of());
        service.accessCodeService = new AccessCodeService() {
            @Override
            public Respondent login(int surveyId, String accessCode) {
                throw new IllegalStateException("database down");
            }
        };

        try (LogCapture log = LogCapture.of(SessionPersistenceService.class)) {
            assertNull(service.restoreSessionData());

            assertTrue(log.contains(LogCapture.ERROR, "Failed to restore session data"));
        }
        assertTrue(session.attributes.isEmpty());
    }

    @Test
    void given_restoredValues_when_sessionDataIsBuilt_then_itHandsThemBack() {
        Respondent respondent = respondent(1, "A", null);
        NavResponse nav = page("p");

        SessionPersistenceService.SessionData data = new SessionPersistenceService.SessionData(5, respondent, nav);

        assertEquals(5, data.getSurveyId());
        assertSame(respondent, data.getRespondent());
        assertSame(nav, data.getNavResponse());
        assertFalse(data.getNavResponse().getCurrentNavItem().isComplete());
    }
}
