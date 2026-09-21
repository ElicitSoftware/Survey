package com.elicitsoftware.i18n;

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

import com.elicitsoftware.PostgresTestResource;
import com.elicitsoftware.QuestionService;
import com.elicitsoftware.UISessionDataService;
import com.elicitsoftware.flow.AboutView;
import com.elicitsoftware.flow.MainView;
import com.elicitsoftware.flow.ReportView;
import com.elicitsoftware.flow.ReviewView;
import com.elicitsoftware.flow.SectionView;
import com.elicitsoftware.flow.VersionView;
import com.elicitsoftware.model.Respondent;
import com.elicitsoftware.model.Survey;
import com.vaadin.browserless.quarkus.QuarkusBrowserlessTest;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.dom.Element;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UC-008 / NFR-010 backstop: with the pseudo-locale every translated text renders as
 * {@code ⟦key⟧}, so any prose-like text on a rendered route that lacks the marker bypassed the
 * provider. Subtrees flagged {@code data-i18n-content} carry survey content and are skipped.
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class DisplayedStringsSweepTest extends QuarkusBrowserlessTest {

    private static final int SURVEY_ID = 1;
    private static final Pattern PROSE_LIKE = Pattern.compile("^(?=.*\\p{L})(.*\\s.*|\\p{Lu}.*|.*[.:!?])$", Pattern.DOTALL);
    private static final List<String> TEXT_ATTRIBUTES = List.of(
            "label", "placeholder", "helper-text", "aria-label", "title", "alt", "error-message");
    private static final List<String> TEXT_PROPERTIES = List.of(
            "label", "placeholder", "helperText", "errorMessage", "innerHTML", "textContent");

    @Inject
    UISessionDataService sessionDataService;

    @Inject
    QuestionService questionService;

    @Inject
    EntityManager em;

    @Test
    void loginAboutVersion_haveNoUntranslatedText() {
        UI.getCurrent().setLocale(ElicitI18NProvider.PSEUDO_LOCALE);
        List<String> problems = new ArrayList<>();
        navigate(MainView.class);
        sweep("login", problems);
        navigate(AboutView.class);
        sweep("about", problems);
        navigate(VersionView.class);
        sweep("version", problems);
        assertTrue(problems.isEmpty(), report(problems));
    }

    @Test
    void sectionReviewReport_haveNoUntranslatedText() {
        UI.getCurrent().setLocale(ElicitI18NProvider.PSEUDO_LOCALE);
        Respondent active = QuarkusTransaction.requiringNew().call(() -> createRespondent(true));
        Respondent finished = QuarkusTransaction.requiringNew().call(() -> createRespondent(false));
        List<String> problems = new ArrayList<>();
        try {
            sessionDataService.setSurveyId(SURVEY_ID);
            sessionDataService.setRespondent(active);
            sessionDataService.setNavResponse(
                    questionService.init(active.id.intValue(), active.survey.initialDisplayKey));
            navigate(SectionView.class);
            sweep("section", problems, false); // page title is the authored section title
            navigate(ReviewView.class);
            sweep("review", problems);

            sessionDataService.setRespondent(finished);
            navigate(ReportView.class);
            sweep("report", problems);
        } finally {
            cleanup(active.id);
            cleanup(finished.id);
        }
        assertTrue(problems.isEmpty(), report(problems));
    }

    private void sweep(String route, List<String> problems) {
        sweep(route, problems, true);
    }

    private void sweep(String route, List<String> problems, boolean checkTitle) {
        UI ui = UI.getCurrent();
        if (checkTitle) {
            check(route, "<page title>", ui.getInternals().getTitle(), problems);
        }
        walk(route, ui.getElement(), problems);
        for (Grid<?> grid : find(Grid.class).all().stream().map(g -> (Grid<?>) g).toList()) {
            grid.getColumns().forEach(c -> check(route, "grid header", c.getHeaderText(), problems));
        }
    }

    private void walk(String route, Element element, List<String> problems) {
        if (element.isTextNode()) {
            check(route, "text", element.getText(), problems);
            return;
        }
        if (element.hasAttribute("data-i18n-content")) {
            return;
        }
        String where = element.getTag() + (element.getAttribute("id") != null ? "#" + element.getAttribute("id") : "");
        for (String attr : TEXT_ATTRIBUTES) {
            if (element.hasAttribute(attr)) {
                check(route, where + "[" + attr + "]", element.getAttribute(attr), problems);
            }
        }
        for (String prop : TEXT_PROPERTIES) {
            if (element.hasProperty(prop)) {
                check(route, where + "." + prop, element.getProperty(prop), problems);
            }
        }
        for (int i = 0; i < element.getChildCount(); i++) {
            walk(route, element.getChild(i), problems);
        }
    }

    private static void check(String route, String where, String value, List<String> problems) {
        if (value == null || value.isBlank()) {
            return;
        }
        String stripped = value.replaceAll("<[^>]+>", " ").strip();
        if (stripped.contains(ElicitI18NProvider.PSEUDO_OPEN) || !PROSE_LIKE.matcher(stripped).matches()) {
            return;
        }
        problems.add(route + " " + where + ": \"" + stripped + "\"");
    }

    private static String report(List<String> problems) {
        return problems.size() + " untranslated text(s) rendered (UC-008 / NFR-010):\n  " + String.join("\n  ", problems);
    }

    private Respondent createRespondent(boolean active) {
        Survey survey = Survey.find("FROM Survey s LEFT JOIN FETCH s.reports WHERE s.id = ?1", SURVEY_ID).firstResult();
        Respondent r = new Respondent();
        r.survey = survey;
        r.accessCode = "i18n_" + System.nanoTime();
        r.active = active;
        r.logins = active ? 0 : 1;
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
