package com.elicitsoftware.survey;

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

import com.elicitsoftware.QuestionManager;
import com.elicitsoftware.model.Answer;
import com.elicitsoftware.model.Respondent;
import com.elicitsoftware.model.Survey;
import com.elicitsoftware.response.ReviewItem;
import com.elicitsoftware.response.ReviewResponse;
import com.elicitsoftware.response.ReviewSection;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * UC-003: Review Answers — exercises the review() query/grouping logic directly
 * against the EntityManager rather than through the @NormalUIScoped QuestionService
 * bean, which does not behave as a true UI-scoped instance under @QuarkusTest (see
 * the same workaround used in QuestionManagerTest).
 */
@QuarkusTest
public class QuestionServiceReviewTest {

    @Inject
    EntityManager em;

    @Inject
    QuestionManager questionManager;

    private static final int TESS_RESPONDENT_ID = 1;
    private static final int SURVEY_ID = 1;
    private static final String WELCOME_SECTION = "0001-0001-0000-0001-0000-0000-0000";

    // Mirrors QuestionService.reviewSQL — kept identical so this test tracks the real query.
    private static final String REVIEW_SQL = """
            select c.*
             from (SELECT a.id,
             a.respondent_id,
                a.display_key,
                a.display_text,
                q.short_text,
                COALESCE(NULLIF(q.short_text::text, ''::text), a.display_text::text) AS short_display_text,
                COALESCE(i.display_text, a.text_value) AS display_value,
                t.name AS question_type
             FROM survey.answers a
             JOIN survey.questions q ON a.question_id = q.id
             LEFT JOIN survey.question_types t ON q.type_id = t.id
             LEFT JOIN survey.select_groups g ON q.select_group_id = g.id
             LEFT JOIN survey.select_items i ON g.id = i.group_id AND a.text_value::text = i.coded_value::text
             WHERE a.respondent_id = :respondentId
                AND a.deleted = false
                AND a.text_value IS NOT NULL
             UNION\s
             SELECT a1.id,
                a1.respondent_id,
                a1.display_key,
                a1.display_text,
                q1.short_text,
                COALESCE(NULLIF(q1.short_text::text, ''::text), a1.display_text::text) AS short_display_text,
                null AS display_value,
                null AS question_type
             FROM survey.answers a1
             LEFT JOIN survey.questions q1 ON a1.question_id = q1.id
             WHERE a1.respondent_id = :respondentId
                AND a1.deleted = false
                AND a1.section_question_id IS NULL) c
             order by c.display_key;
            """;

    /** Replicates QuestionService.review() without the @NormalUIScoped dependency. */
    private ReviewResponse review(int respondentId) {
        List<ReviewItem> items = new ArrayList<>();
        List<ReviewSection> sections = new ArrayList<>();

        Query query = em.createNativeQuery(REVIEW_SQL);
        query.setParameter("respondentId", respondentId);
        @SuppressWarnings("unchecked")
        List<Object[]> results = query.getResultList();

        String sectionTitle = "";
        String sectionDisplayKey = "";

        for (Object[] result : results) {
            String displayKey = (String) result[2];
            String displayText = (String) result[3];
            String shortDisplayText = (String) result[5];
            String displayValue = (String) result[6];

            if (displayValue == null) {
                if (!sectionTitle.equals(displayText)) {
                    if (!items.isEmpty()) {
                        sections.add(new ReviewSection(sectionTitle, sectionDisplayKey, items));
                    }
                    items = new ArrayList<>();
                    sectionTitle = displayText;
                    sectionDisplayKey = displayKey;
                }
            } else if (shortDisplayText != null) {
                items.add(new ReviewItem(shortDisplayText, displayValue));
            } else {
                items.add(new ReviewItem(displayText, displayValue));
            }
        }
        sections.add(new ReviewSection(sectionTitle, sectionDisplayKey, items));

        return new ReviewResponse(sections);
    }

    @Test
    // UC-003 main success scenario: answers are grouped into one section per card, in display-key order
    void given_tessAnswers_when_review_then_groupedIntoSectionsWithItems() {
        ReviewResponse response = review(TESS_RESPONDENT_ID);

        assertNotNull(response);
        List<ReviewSection> sections = response.getSections();
        assertFalse(sections.isEmpty(), "Tess Tester must have at least one review section");

        // Welcome section has no answered (non-null) questions, so it is not emitted as its own
        // populated section; Patron Information is the first section with items.
        ReviewSection patronSection = sections.stream()
                .filter(s -> "Patron Information".equals(s.getTitle()))
                .findFirst()
                .orElse(null);
        assertNotNull(patronSection, "Patron Information section must be present in the review");
        assertFalse(patronSection.getItems().isEmpty(), "Patron Information section must have answered items");

        boolean firstNameFound = patronSection.getItems().stream()
                .anyMatch(i -> "Tess".equals(i.value()));
        assertTrue(firstNameFound, "Patron Information section must include the first-name answer 'Tess'");
    }

    @Test
    // UC-003 BR-008: soft-deleted answers must not appear in the review
    @TestTransaction
    void given_softDeletedAnswer_when_review_then_excludedFromSections() {
        Respondent r = createFreshRespondent();
        questionManager.init(r.id.intValue(), WELCOME_SECTION);

        Answer terms = Answer.findByDisplayKeyActive(r.id.intValue(), "0001-0001-0000-0001-0000-0002-0000");
        assertNotNull(terms, "Terms answer must exist after init()");
        terms.setTextValue("TRUE");
        terms.persist();
        em.flush();

        ReviewResponse before = review(r.id.intValue());
        long itemCountBefore = before.getSections().stream().mapToLong(s -> s.getItems().size()).sum();
        assertTrue(itemCountBefore > 0, "Fresh respondent must show the terms answer before soft-delete");

        // Soft-delete the answer directly, as buildDownstreamQuestions/deleteDownstreamAnswers would.
        terms.deleted = true;
        terms.persist();
        em.flush();

        ReviewResponse after = review(r.id.intValue());
        boolean stillPresent = after.getSections().stream()
                .flatMap(s -> s.getItems().stream())
                .anyMatch(i -> "TRUE".equals(i.value()));
        assertFalse(stillPresent, "Soft-deleted answers must be excluded from the review (BR-008)");
    }

    @Test
    // UC-003: an answer with no value (unanswered question) must not appear as a review item
    @TestTransaction
    void given_unansweredQuestion_when_review_then_notIncludedAsItem() {
        Respondent r = createFreshRespondent();
        questionManager.init(r.id.intValue(), WELCOME_SECTION);

        ReviewResponse response = review(r.id.intValue());
        boolean anyNullValueItem = response.getSections().stream()
                .flatMap(s -> s.getItems().stream())
                .anyMatch(i -> i.value() == null);
        assertFalse(anyNullValueItem, "Unanswered questions (null text_value) must not produce review items");
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
}
