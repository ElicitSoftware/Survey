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

import com.elicitsoftware.flow.PlainViewTestSupport.StubQuestionService;
import com.elicitsoftware.flow.PlainViewTestSupport.StubSession;
import com.elicitsoftware.flow.PlainViewTestSupport.StubTranslator;
import com.elicitsoftware.model.Survey;
import com.elicitsoftware.response.NavResponse;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.notification.Notification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.elicitsoftware.flow.PlainViewTestSupport.activePath;
import static com.elicitsoftware.flow.PlainViewTestSupport.respondent;
import static com.elicitsoftware.flow.PlainViewTestSupport.survey;
import static com.elicitsoftware.flow.PlainViewTestSupport.t;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * UC-002 (answer section questions): what {@link SectionView#init()} does when the session cannot
 * supply a section to draw -- no respondent at all, even after trying the HTTP session, or a
 * respondent whose first section cannot be loaded. The respondent is told why and sent back to
 * sign in.
 * <p>
 * Only these guards are tested plainly: drawing a section resolves the respondent's content
 * snapshot through {@code Respondent.snapshotAnchor}, a Panache lookup that needs Quarkus, so the
 * question rendering and navigation stay with the booted {@code SectionViewTest}.
 */
class SectionViewSessionGuardTest extends PlainViewTestSupport {

    private StubSession session;
    private StubQuestionService questions;
    private Survey survey;

    @BeforeEach
    void stubs() {
        session = new StubSession();
        questions = new StubQuestionService();
        survey = survey(7, "Family");
        UI.getCurrent().navigate("section");
    }

    private SectionView build() {
        SectionView view = new SectionView();
        view.sessionDataService = session;
        view.service = questions;
        view.translator = new StubTranslator();
        view.init();
        return view;
    }

    private String onlyNotification() {
        List<Notification> notifications = $(Notification.class).all();
        assertEquals(1, notifications.size(), "exactly one notification expected");
        return test(notifications.get(0)).getText();
    }

    @Test
    void given_emptySessionAndNothingToRestore_when_init_then_sessionExpiredAndBackToLogin() {
        SectionView view = build();

        assertEquals(1, session.restoreCalls);
        assertEquals(t("common.error.sessionExpired"), onlyNotification());
        assertEquals("", activePath());
        assertEquals(0, view.getComponentCount());
        assertEquals("", view.getPageTitle());
        assertEquals(List.of(), questions.initCalls);
    }

    @Test
    void given_restoredSessionWithoutRespondent_when_init_then_sessionExpired() {
        StubSession stored = new StubSession();
        stored.setSurveyId(7);
        session.restorable = stored;

        build();

        assertEquals(1, session.restoreCalls);
        assertEquals(t("common.error.sessionExpired"), onlyNotification());
        assertEquals("", activePath());
    }

    @Test
    void given_respondentWithoutNavigation_when_sectionCannotBeLoaded_then_loadErrorAndBackToLogin() {
        session.setRespondent(respondent(3, survey, true));
        questions.initAnswers.add(new IllegalStateException("no such section"));

        SectionView view = build();

        assertEquals(1, session.restoreCalls, "a missing navigation response tries the HTTP session first");
        assertEquals(List.of("3:0001-0001"), questions.initCalls,
                "the respondent's survey is restarted at its initial display key");
        assertEquals(t("sectionView.error.loadSurvey"), onlyNotification());
        assertEquals("", activePath());
        assertEquals(0, view.getComponentCount());
    }

    @Test
    void given_navigationWithoutRespondent_when_init_then_restoreIsTriedAndSessionExpired() {
        session.setNavResponse(new NavResponse(null, null, List.of(), null));

        build();

        assertEquals(1, session.restoreCalls, "a missing respondent alone is enough to try the HTTP session");
        assertEquals(t("common.error.sessionExpired"), onlyNotification());
    }

    @Test
    void given_restoredRespondentWithoutNavigation_when_sectionCannotBeLoaded_then_restoredRespondentIsUsed() {
        StubSession stored = new StubSession();
        stored.setRespondent(respondent(5, survey, true));
        session.restorable = stored;
        questions.initAnswers.add(new IllegalStateException("no such section"));

        build();

        assertEquals(List.of("5:0001-0001"), questions.initCalls, "the restored respondent's survey is reloaded");
        assertEquals(t("sectionView.error.loadSurvey"), onlyNotification());
    }
}
