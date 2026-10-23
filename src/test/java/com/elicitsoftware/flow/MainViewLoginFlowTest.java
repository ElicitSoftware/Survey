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

import com.elicitsoftware.flow.PlainViewTestSupport.StubAccessCodeService;
import com.elicitsoftware.flow.PlainViewTestSupport.StubQuestionService;
import com.elicitsoftware.flow.PlainViewTestSupport.StubSession;
import com.elicitsoftware.model.Respondent;
import com.elicitsoftware.model.Survey;
import com.elicitsoftware.response.NavResponse;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.Location;
import com.vaadin.flow.router.NavigationEvent;
import com.vaadin.flow.router.NavigationTrigger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.elicitsoftware.flow.PlainViewTestSupport.activePath;
import static com.elicitsoftware.flow.PlainViewTestSupport.byId;
import static com.elicitsoftware.flow.PlainViewTestSupport.respondent;
import static com.elicitsoftware.flow.PlainViewTestSupport.survey;
import static com.elicitsoftware.flow.PlainViewTestSupport.t;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UC-001 (enter a survey via access code): the sign-in page built by {@link MainView#init()} in a
 * plain browserless UI, with hand-written stand-ins for the injected services. Covers the
 * access-code length rule (BR-001), the auto-registration notices (BR-002), the routing of an
 * active respondent to the section page and a finalized one to the report page (BR-003), the
 * survey picker and the session-restore shortcut.
 */
class MainViewLoginFlowTest extends PlainViewTestSupport {

    private StubSession session;
    private StubAccessCodeService accessCodes;
    private StubQuestionService questions;
    private Survey survey;

    @BeforeEach
    void stubs() {
        session = new StubSession();
        accessCodes = new StubAccessCodeService();
        questions = new StubQuestionService();
        survey = survey(7, "Family");
        accessCodes.surveys = List.of(survey);
    }

    private MainView build() {
        MainView view = new MainView();
        view.sessionDataService = session;
        view.accessCodeService = accessCodes;
        view.questionService = questions;
        view.init();
        UI.getCurrent().add(view);
        return view;
    }

    private TextField accessCodeField(MainView view) {
        return byId(view, "login-access-code-field", TextField.class);
    }

    private Button loginButton(MainView view) {
        return byId(view, "login-button", Button.class);
    }

    private String onlyNotification() {
        List<Notification> notifications = $(Notification.class).all();
        assertEquals(1, notifications.size(), "exactly one notification expected");
        return test(notifications.get(0)).getText();
    }

    private static NavResponse nav() {
        return new NavResponse(null, null, List.of(), null);
    }

    private StubSession completeSession(boolean active) {
        StubSession s = new StubSession();
        s.setSurveyId(7);
        s.setRespondent(respondent(3, survey, active));
        s.setNavResponse(nav());
        return s;
    }

    @Test
    void given_noSessionAndNothingToRestore_when_init_then_loginFormIsBuilt() {
        MainView view = build();

        assertEquals(1, session.restoreCalls, "an empty session must try the HTTP session first");
        assertEquals(FlexComponent.Alignment.CENTER, view.getAlignItems());
        TextField field = accessCodeField(view);
        assertNotNull(field);
        assertEquals(t("mainView.txtAccessCode"), field.getLabel());
        assertEquals(t("mainView.txtAccessCode.tooltip"), field.getTooltip().getText());
        assertTrue(field.getThemeNames().contains("bordered"));
        assertTrue(field.getThemeNames().contains("small"));
        assertTrue(field.isAutofocus());
        Button login = loginButton(view);
        assertEquals(t("mainView.btnLogin"), login.getText());
        assertTrue(login.getThemeNames().contains(ButtonVariant.LUMO_PRIMARY.getVariantName()));
        assertSame(field.getParent().orElseThrow(), login.getParent().orElseThrow(),
                "the field and the button share the login layout");
        VerticalLayout loginLayout = (VerticalLayout) field.getParent().orElseThrow();
        assertEquals(FlexComponent.Alignment.CENTER, loginLayout.getAlignItems());
        assertEquals(t("mainView.pageTitle"), view.getPageTitle());
        assertEquals(List.of(), questions.initCalls);
    }

    @Test
    void given_restorableActiveRespondent_when_init_then_navigatesToSectionWithoutBuildingForm() {
        StubSession stored = completeSession(true);
        session.restorable = stored;

        MainView view = build();

        assertEquals("section", activePath());
        assertEquals(0, view.getComponentCount(), "a restored sign-in builds no form");
    }

    @Test
    void given_restorableFinalizedRespondent_when_init_then_navigatesToReport() {
        session.restorable = completeSession(false);

        MainView view = build();

        assertEquals("report", activePath());
        assertEquals(0, view.getComponentCount());
    }

    @Test
    void given_restoredSessionWithoutRespondent_when_init_then_loginFormIsBuilt() {
        StubSession stored = new StubSession();
        stored.setSurveyId(7);
        session.restorable = stored;

        MainView view = build();

        assertEquals(1, session.restoreCalls);
        assertNotNull(accessCodeField(view));
        assertEquals("", activePath());
    }

    @Test
    void given_completeSessionWithActiveRespondent_when_init_then_navigatesToSectionWithoutRestoring() {
        session = completeSession(true);

        MainView view = build();

        assertEquals(0, session.restoreCalls, "complete session data needs no restore");
        assertEquals("section", activePath());
        assertEquals(0, view.getComponentCount());
    }

    @Test
    void given_completeSessionWithFinalizedRespondent_when_init_then_navigatesToReport() {
        session = completeSession(false);

        MainView view = build();

        assertEquals("report", activePath());
        assertEquals(0, view.getComponentCount());
    }

    @Test
    void given_partialSession_when_init_then_neitherRestoresNorNavigates() {
        session.setSurveyId(7);
        session.setRespondent(respondent(3, survey, true));

        MainView view = build();

        assertEquals(0, session.restoreCalls, "partial session data is not overwritten by a restore");
        assertEquals("", activePath());
        assertNotNull(accessCodeField(view));
    }

    @Test
    void given_partialSessionWithOnlyNavResponse_when_init_then_buildsForm() {
        session.setNavResponse(nav());

        MainView view = build();

        assertEquals(0, session.restoreCalls);
        assertNotNull(accessCodeField(view));
    }

    @Test
    void given_partialSessionWithOnlyRespondent_when_init_then_buildsForm() {
        session.setRespondent(respondent(3, survey, true));

        MainView view = build();

        assertEquals(0, session.restoreCalls);
        assertNotNull(accessCodeField(view));
    }

    @Test
    void given_autoRegisterDisabled_when_init_then_noAutoRegisterNotices() {
        MainView view = build();

        assertEquals(1, view.getComponentCount(), "only the login layout");
        assertTrue(view.getComponentAt(0) instanceof VerticalLayout);
    }

    @Test
    void given_autoRegisterEnabled_when_init_then_instructionsPrecedeAndWarningFollowsLoginLayout() {
        accessCodes.autoRegisterFlag = true;

        MainView view = build();

        assertEquals(3, view.getComponentCount());
        Component first = view.getComponentAt(0);
        Component last = view.getComponentAt(2);
        assertTrue(first instanceof Div);
        assertTrue(view.getComponentAt(1) instanceof VerticalLayout);
        assertTrue(last instanceof Div);
        assertEquals(t("mainView.autoRegister.instructions"), first.getElement().getProperty("innerHTML"));
        assertEquals(t("mainView.autoRegister.warning"), last.getElement().getProperty("innerHTML"));
    }

    @Test
    void given_noSurveys_when_init_then_emptyMessageShownAndNoSurveySelected() {
        accessCodes.surveys = List.of();

        MainView view = build();

        VerticalLayout loginLayout = (VerticalLayout) view.getComponentAt(0);
        assertTrue(loginLayout.getComponentAt(0) instanceof Paragraph);
        assertEquals(t("mainView.surveys.isEmpty"), ((Paragraph) loginLayout.getComponentAt(0)).getText());
        assertNull(session.getSurveyId());
        assertNull(byId(view, "login-survey-select", ComboBox.class));
    }

    @Test
    void given_oneSurvey_when_init_then_itIsSelectedWithoutAPicker() {
        MainView view = build();

        assertEquals(7, session.getSurveyId());
        assertNull(byId(view, "login-survey-select", ComboBox.class));
        VerticalLayout loginLayout = (VerticalLayout) view.getComponentAt(0);
        assertEquals(2, loginLayout.getComponentCount(), "access code field and button only");
    }

    @Test
    @SuppressWarnings("unchecked")
    void given_severalSurveys_when_oneIsPicked_then_itsIdIsStoredInTheSession() {
        Survey other = survey(9, "Other");
        accessCodes.surveys = List.of(survey, other);

        MainView view = build();

        assertNull(session.getSurveyId(), "nothing is chosen for the respondent");
        ComboBox<Survey> picker = byId(view, "login-survey-select", ComboBox.class);
        assertNotNull(picker);
        assertEquals(t("mainView.comboBox"), picker.getLabel());
        assertEquals("Other", picker.getItemLabelGenerator().apply(other));
        assertEquals(List.of(survey, other), test(picker).getSuggestionItems());
        assertTrue(picker.getThemeNames().contains("small"));

        picker.setValue(other);

        assertEquals(9, session.getSurveyId());
    }

    @Test
    void given_blankAccessCode_when_entered_then_emptyErrorShown() {
        MainView view = build();
        TextField field = accessCodeField(view);
        field.setValue("abcde");

        field.setValue("   ");

        assertTrue(field.isInvalid());
        assertEquals(t("mainView.accessCode.empty"), field.getErrorMessage());
    }

    @Test
    void given_fourCharacterAccessCode_when_entered_then_tooShortErrorShown() {
        MainView view = build();
        TextField field = accessCodeField(view);

        field.setValue("abcd");

        assertTrue(field.isInvalid());
        assertEquals(t("mainView.accessCode.tooShort"), field.getErrorMessage());
    }

    @Test
    void given_fiveCharacterAccessCode_when_entered_then_accepted() {
        MainView view = build();
        TextField field = accessCodeField(view);
        field.setValue("abcd");

        field.setValue("abcde");

        assertFalse(field.isInvalid());
        assertTrue(field.getErrorMessage() == null || field.getErrorMessage().isEmpty(),
                "the earlier error is cleared");
    }

    @Test
    void given_twelveCharacterAccessCode_when_entered_then_accepted() {
        MainView view = build();
        TextField field = accessCodeField(view);
        field.setValue("abcd");

        field.setValue("abcdefghijkl");

        assertFalse(field.isInvalid());
    }

    @Test
    void given_thirteenCharacterAccessCode_when_entered_then_tooLongErrorShown() {
        MainView view = build();
        TextField field = accessCodeField(view);

        field.setValue("abcdefghijklm");

        assertTrue(field.isInvalid());
        assertEquals(t("mainView.accessCode.tooLong"), field.getErrorMessage());
    }

    @Test
    void given_noAccessCode_when_loginClicked_then_fixErrorsShownAndNoLoginAttempted() {
        MainView view = build();

        test(loginButton(view)).click();

        assertEquals(t("mainView.error.fixErrors"), onlyNotification());
        assertEquals(List.of(), accessCodes.logins);
        assertEquals(0, session.clearCalls);
    }

    @Test
    void given_tooShortAccessCode_when_loginClicked_then_noLoginAttempted() {
        MainView view = build();
        accessCodeField(view).setValue("abc");

        test(loginButton(view)).click();

        assertEquals(t("mainView.error.fixErrors"), onlyNotification());
        assertEquals(List.of(), accessCodes.logins);
    }

    @Test
    void given_validButUnknownAccessCode_when_loginClicked_then_invalidAccessCodeShown() {
        MainView view = build();
        accessCodeField(view).setValue("abcdef");

        test(loginButton(view)).click();

        assertEquals(List.of("7:abcdef"), accessCodes.logins);
        assertEquals(t("mainView.error.invalidAccessCode"), onlyNotification());
        assertEquals(1, session.clearCalls, "the session is cleared before a fresh sign-in");
        assertEquals(7, session.getSurveyId(), "the chosen survey survives the clear");
        assertNull(session.getRespondent());
        assertEquals("", activePath());
    }

    @Test
    void given_activeRespondentsAccessCode_when_loginClicked_then_sessionSeededAndSectionShown() {
        Respondent respondent = respondent(3, survey, true);
        accessCodes.loginAnswer = (s, c) -> respondent;
        NavResponse nav = nav();
        questions.initAnswers.add(nav);
        session.setRespondent(respondent(99, survey, true));
        MainView view = build();
        accessCodeField(view).setValue("abcdef");

        test(loginButton(view)).click();

        assertEquals(1, session.clearCalls);
        assertSame(respondent, session.getRespondent());
        assertSame(nav, session.getNavResponse());
        assertEquals(List.of("3:0001-0001"), questions.initCalls);
        assertEquals("section", activePath());
        assertEquals(0, $(Notification.class).all().size());
    }

    @Test
    void given_finalizedRespondentsAccessCode_when_enterPressed_then_reportShown() {
        accessCodes.loginAnswer = (s, c) -> respondent(4, survey, false);
        questions.initAnswers.add(nav());
        MainView view = build();
        accessCodeField(view).setValue("abcdef");

        fireShortcut(Key.ENTER);

        assertEquals(List.of("7:abcdef"), accessCodes.logins);
        assertEquals("report", activePath());
    }

    @Test
    void given_loginPathWithAccessCode_when_parameterSet_then_fieldIsFilled() {
        MainView view = build();

        view.setParameter(event("login/abcdef"), "abcdef");

        assertEquals("abcdef", accessCodeField(view).getValue());
    }

    @Test
    void given_rootPathWithAccessCode_when_parameterSet_then_fieldIsLeftEmpty() {
        MainView view = build();

        view.setParameter(event("other/abcdef"), "abcdef");

        assertEquals("", accessCodeField(view).getValue());
    }

    @Test
    void given_loginPathWithBlankAccessCode_when_parameterSet_then_fieldIsLeftEmpty() {
        MainView view = build();

        view.setParameter(event("login"), "  ");

        assertEquals("", accessCodeField(view).getValue());
    }

    @Test
    void given_loginPathWithoutAccessCode_when_parameterSet_then_fieldIsLeftEmpty() {
        MainView view = build();

        view.setParameter(event("login"), null);

        assertEquals("", accessCodeField(view).getValue());
    }

    private static BeforeEnterEvent event(String path) {
        UI ui = UI.getCurrent();
        NavigationEvent navigation = new NavigationEvent(ui.getInternals().getRouter(), new Location(path), ui,
                NavigationTrigger.PROGRAMMATIC);
        return new BeforeEnterEvent(navigation, MainView.class, List.of());
    }
}
