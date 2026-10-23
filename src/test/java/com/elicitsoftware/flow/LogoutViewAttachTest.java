package com.elicitsoftware.flow;

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

import com.elicitsoftware.flow.PlainViewTestSupport.StubSession;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.internal.PendingJavaScriptInvocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.elicitsoftware.flow.PlainViewTestSupport.respondent;
import static com.elicitsoftware.flow.PlainViewTestSupport.survey;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UC-006 (log out): attaching {@link LogoutView} clears the respondent's session and reloads the
 * browser at the landing page, in a plain browserless UI.
 */
class LogoutViewAttachTest extends PlainViewTestSupport {

    private static List<String> pendingJs() {
        return UI.getCurrent().getInternals().dumpPendingJavaScriptInvocations().stream()
                .map(PendingJavaScriptInvocation::getInvocation)
                .map(i -> i.getExpression())
                .toList();
    }

    @Test
    void given_signedInRespondent_when_logoutAttached_then_sessionClearedAndPageReloadedAtRoot() {
        StubSession session = new StubSession();
        session.setSurveyId(7);
        session.setRespondent(respondent(3, survey(7, "Family"), true));
        LogoutView view = new LogoutView();
        view.sessionDataService = session;
        pendingJs();

        UI.getCurrent().add(view);

        assertEquals(1, session.clearCalls);
        assertNull(session.getRespondent());
        assertNull(session.getSurveyId());
        assertTrue(pendingJs().stream().anyMatch(js -> js.contains("window.location.href = '/';")));
    }

    @Test
    void given_noSessionService_when_logoutAttached_then_pageIsStillReloadedAtRoot() {
        LogoutView view = new LogoutView();
        pendingJs();

        UI.getCurrent().add(view);

        assertTrue(pendingJs().stream().anyMatch(js -> js.contains("window.location.href = '/';")));
    }
}
