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

import com.elicitsoftware.QuestionService;
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
 * UC-002: Answer Section Questions — Browserless coverage of SectionView's navigation
 * controls (section-previous-button / section-next-button), seeded with a fresh, isolated
 * respondent (not the shared "Tess Tester" fixture, which other tests mutate — see
 * TokenServiceTest.testDeactivate).
 * <p>
 * No {@code @TestTransaction}: {@code QuestionService.init(...)} runs in its own
 * {@code REQUIRES_NEW} transaction (see the class javadoc on {@code QuestionServiceReviewTest}
 * for the same caveat), so the seeded respondent must already be committed for it to be
 * visible. Respondent creation and cleanup therefore go through
 * {@code QuarkusTransaction.requiringNew()}, mirroring {@code ETLServiceTest}'s pattern for the
 * same constraint.
 */
@QuarkusTest
class SectionViewTest extends QuarkusBrowserlessTest {

    private static final int SURVEY_ID = 1;

    @Inject
    UISessionDataService sessionDataService;

    @Inject
    QuestionService questionService;

    @Inject
    EntityManager em;

    @Test
    // UC-002 main success scenario: the first section of a fresh respondent has no
    // previous section, so the Previous button starts disabled. The Next button reads
    // "Next" for a multi-section survey, or "Review" if conditional skip logic (e.g. no
    // terms accepted yet) collapses the fresh respondent straight to the last section.
    void freshRespondent_firstSection_previousDisabled() {
        Respondent respondent = QuarkusTransaction.requiringNew().call(this::createFreshRespondent);
        try {
            seedSession(respondent);

            navigate(SectionView.class);

            Button previous = find(Button.class).id("section-previous-button");
            Button next = find(Button.class).id("section-next-button");

            assertFalse(previous.isEnabled(), "First section must not allow navigating further back");
            assertTrue(next.getText().equals("Next") || next.getText().equals("Review"),
                    "Next button must read either 'Next' or 'Review', got: " + next.getText());
        } finally {
            cleanup(respondent.id);
        }
    }

    private Respondent createFreshRespondent() {
        Respondent r = new Respondent();
        r.survey = Survey.findById(SURVEY_ID);
        r.token = "test_" + System.nanoTime();
        r.active = true;
        r.logins = 0;
        r.persist();
        return r;
    }

    private void seedSession(Respondent respondent) {
        sessionDataService.setSurveyId(SURVEY_ID);
        sessionDataService.setRespondent(respondent);
        sessionDataService.setNavResponse(
                questionService.init(respondent.id.intValue(), respondent.survey.initialDisplayKey));
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
