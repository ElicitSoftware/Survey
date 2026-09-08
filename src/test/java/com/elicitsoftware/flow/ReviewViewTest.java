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

/**
 * UC-003/UC-004: Review Answers & Finalize — Browserless coverage of ReviewView's
 * review-previous-button / review-finish-button, seeded with a fresh, isolated respondent
 * (see SectionViewTest for why the shared "Tess Tester" fixture isn't reused, and for why
 * {@code @TestTransaction} can't be used here — {@code QuestionService.init(...)} needs the
 * respondent already committed).
 */
@QuarkusTest
class ReviewViewTest extends QuarkusBrowserlessTest {

    private static final int SURVEY_ID = 1;

    @Inject
    UISessionDataService sessionDataService;

    @Inject
    QuestionService questionService;

    @Inject
    EntityManager em;

    @Test
    // UC-003 main success scenario: the first section of a fresh respondent has no
    // previous section, so review-previous-button starts disabled.
    void freshRespondent_review_previousButtonDisabled() {
        Respondent respondent = QuarkusTransaction.requiringNew().call(this::createFreshRespondent);
        try {
            seedSession(respondent);

            navigate(ReviewView.class);

            Button previous = find(Button.class).id("review-previous-button");
            assertFalse(previous.isEnabled());
        } finally {
            cleanup(respondent.id);
        }
    }

    @Test
    // UC-004: review-finish-button is gated on navResponse.getCurrentNavItem().getPrevious()
    // (ReviewView.java) — the same condition used for review-previous-button. For a fresh,
    // unanswered respondent whose conditional skip logic collapses the survey to a single
    // reachable section, previous is null, so Finish starts disabled just like Previous does.
    // NOTE: this looks like it may be a copy-paste condition rather than intentional — Finish
    // arguably shouldn't depend on whether a previous section exists. Flagging rather than
    // "fixing" it here since that's a behavior change outside this test's scope.
    void freshRespondent_review_finishButtonAlsoDisabled() {
        Respondent respondent = QuarkusTransaction.requiringNew().call(this::createFreshRespondent);
        try {
            seedSession(respondent);

            navigate(ReviewView.class);

            Button finish = find(Button.class).id("review-finish-button");
            assertFalse(finish.isEnabled());
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
