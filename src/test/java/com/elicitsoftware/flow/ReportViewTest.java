package com.elicitsoftware.flow;

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

import com.elicitsoftware.UISessionDataService;
import com.elicitsoftware.model.Respondent;
import com.elicitsoftware.model.Survey;
import com.vaadin.browserless.quarkus.QuarkusBrowserlessTest;
import com.vaadin.flow.component.button.Button;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UC-005: View Reports & Download PDF — Browserless coverage of ReportView's
 * report-generate-pdf-button / report-next-button. The seeded LibraryCardReg (survey id 1)
 * report points at a local URL nothing is listening to during tests, so
 * ReportView.callReport()'s existing exception handling produces an error ReportCard rather
 * than crashing — the same graceful-failure path ReportServiceCallTest already covers at the
 * service layer.
 * <p>
 * No {@code @TestTransaction}: see SectionViewTest for why respondent creation/cleanup goes
 * through {@code QuarkusTransaction.requiringNew()} instead.
 */
@QuarkusTest
class ReportViewTest extends QuarkusBrowserlessTest {

    private static final int SURVEY_ID = 1;

    @Inject
    UISessionDataService sessionDataService;

    @Inject
    EntityManager em;

    @Test
    // UC-005 main success scenario: an inactive (post-review) respondent lands on
    // ReportView and sees the PDF download button.
    void inactiveRespondent_reportView_showsGeneratePdfButton() {
        Respondent respondent = QuarkusTransaction.requiringNew().call(this::createFinishedRespondent);
        try {
            sessionDataService.setSurveyId(SURVEY_ID);
            sessionDataService.setRespondent(respondent);

            navigate(ReportView.class);

            assertTrue(find(Button.class).id("report-generate-pdf-button").isEnabled());
        } finally {
            cleanup(respondent.id);
        }
    }

    @Test
    // UC-005: LibraryCardReg (survey id 1) has no post-survey URL configured, so the
    // "Next" button must not be rendered.
    void surveyWithoutPostSurveyUrl_reportView_hidesNextButton() {
        Respondent respondent = QuarkusTransaction.requiringNew().call(this::createFinishedRespondent);
        try {
            sessionDataService.setSurveyId(SURVEY_ID);
            sessionDataService.setRespondent(respondent);

            navigate(ReportView.class);

            assertFalse(find(Button.class).withId("report-next-button").exists());
        } finally {
            cleanup(respondent.id);
        }
    }

    private Respondent createFinishedRespondent() {
        // ReportView.init() iterates respondent.survey.reports outside of any transaction
        // (the view is rendered after this @Transactional-boundary method returns), so the
        // collection must be eagerly loaded here rather than left as a lazy proxy that would
        // throw LazyInitializationException once the session closes.
        Survey survey = Survey.find("FROM Survey s LEFT JOIN FETCH s.reports WHERE s.id = ?1", SURVEY_ID)
                .firstResult();
        Respondent r = new Respondent();
        r.survey = survey;
        r.token = "test_" + System.nanoTime();
        r.active = false;
        r.logins = 1;
        r.persist();
        return r;
    }

    private void cleanup(Integer respondentId) {
        QuarkusTransaction.requiringNew().run(() ->
                em.createNativeQuery("DELETE FROM survey.respondents WHERE id = ?1")
                        .setParameter(1, respondentId).executeUpdate());
    }
}
