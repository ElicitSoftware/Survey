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

import com.elicitsoftware.etl.ETLRespondentService;
import com.elicitsoftware.i18n.ContentTranslator;
import com.elicitsoftware.model.Answer;
import com.elicitsoftware.model.Respondent;
import com.elicitsoftware.response.NavResponse;
import com.elicitsoftware.response.ReviewItem;
import com.elicitsoftware.response.ReviewResponse;
import com.elicitsoftware.response.ReviewSection;
import com.elicitsoftware.util.DatabaseRetryUtil;
import com.vaadin.quarkus.annotation.NormalUIScoped;
import io.micrometer.core.annotation.Timed;
import io.quarkus.logging.Log;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.transaction.Transactional;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * The QuestionService class provides functionality for managing survey questions, answers,
 * and their navigation. It includes methods to initialize the survey process,
 * review submitted answers, save answers, and finalize the submission for a specific respondent.
 * This class interacts with the database to perform CRUD operations on survey questions and answers.
 * It also orchestrates navigation and downstream dependency operations for survey questions.
 * <p>
 * The service is application-scoped and leverages transactional demarcations to ensure consistency
 * when interacting with the database. It uses an injected EntityManager for database queries,
 * and collaborates with QuestionManager and ETLService for additional processing.
 * <p>
 * Main features include:
 * - Initializing a survey session based on respondent ID and display key.
 * - Retrieving review responses for all sections and questions for a respondent.
 * - Saving and managing answers, including handling downstream questions.
 * - Finalizing the respondent's survey and managing data population for reporting.
 * <p>
 * This service relies on VaadinSession for maintaining session-specific attributes
 * like the current respondent.
 */
@NormalUIScoped
public class QuestionService {


    /**
     * SQL query used to retrieve and consolidate survey answers for a specific respondent.
     * <p>
     * The query fetches data from the `survey.answers` table and optionally joins
     * with the `survey.questions` table to enrich the results with question metadata.
     * It performs the following operations:
     * <ul>
     *   <li>Retrieves answers linked to specific questions and sections, ensuring only non-deleted and valid entries are included.</li>
     *   <li>Handles missing {@code short_text} fields by falling back to {@code display_text}.</li>
     *   <li>Provides a default value of "not reported" for empty {@code text_value} fields.</li>
     *   <li>Merges results from different sections and unlinked entries using a union operation.</li>
     * </ul>
     * <p>
     * Results are ordered by `display_key` to ensure a predictable output sequence.
     * <p>
     * The query uses the parameter `:respondentId` to filter answers specific to a given respondent.
     */
    private static final String reviewSQL = """ 
            select c.*
             from (SELECT a.id,
             a.respondent_id,
                a.display_key,
                CASE WHEN a.display_language = :language THEN a.display_text_local ELSE a.display_text END AS display_text,
                COALESCE(ts.value, q.short_text) AS short_text,
                COALESCE(NULLIF(COALESCE(ts.value, q.short_text)::text, ''::text),
                         CASE WHEN a.display_language = :language THEN a.display_text_local ELSE a.display_text END::text) AS short_display_text,
                COALESCE(ti.value, i.display_text, a.text_value) AS display_value,
                t.name AS question_type
             FROM survey.answers a
             JOIN survey.questions q ON a.question_id = q.id
             LEFT JOIN survey.question_types t ON q.type_id = t.id
             LEFT JOIN survey.select_groups g ON q.select_group_id = g.select_group_id
             LEFT JOIN survey.select_items i ON g.select_group_id = i.select_group_id AND a.text_value::text = i.coded_value::text
             LEFT JOIN survey.translations ts ON ts.element_key = q.question_key AND ts.field = 'short_text'
                AND ts.language = :language AND ts.effective_from <= :asOf AND ts.effective_to > :asOf
                AND ts.source_hash = encode(sha256(convert_to(q.short_text, 'UTF8')), 'hex')
             LEFT JOIN survey.translations ti ON ti.element_key = i.select_item_key AND ti.field = 'display_text'
                AND ti.language = :language AND ti.effective_from <= :asOf AND ti.effective_to > :asOf
                AND ti.source_hash = encode(sha256(convert_to(i.display_text, 'UTF8')), 'hex')
             WHERE a.respondent_id = :respondentId
                AND a.deleted = false
                AND a.text_value IS NOT NULL
             UNION\s
             SELECT a1.id,
                a1.respondent_id,
                a1.display_key,
                CASE WHEN a1.display_language = :language THEN a1.display_text_local ELSE a1.display_text END AS display_text,
                COALESCE(ts1.value, q1.short_text) AS short_text,
                COALESCE(NULLIF(COALESCE(ts1.value, q1.short_text)::text, ''::text),
                         CASE WHEN a1.display_language = :language THEN a1.display_text_local ELSE a1.display_text END::text) AS short_display_text,
                null AS display_value,
                null AS question_type
             FROM survey.answers a1
             LEFT JOIN survey.questions q1 ON a1.question_id = q1.id
             LEFT JOIN survey.translations ts1 ON ts1.element_key = q1.question_key AND ts1.field = 'short_text'
                AND ts1.language = :language AND ts1.effective_from <= :asOf AND ts1.effective_to > :asOf
                AND ts1.source_hash = encode(sha256(convert_to(q1.short_text, 'UTF8')), 'hex')
             WHERE a1.respondent_id = :respondentId
                AND a1.deleted = false
                AND a1.section_question_id IS NULL) c
             order by c.display_key;
            """;

    @Inject
    com.elicitsoftware.psa.PostSurveyActionDelivery postSurveyActionDelivery;

    @Inject
    EntityManager entityManager;

    @Inject
    QuestionManager questionManager;

    @Inject
    ETLRespondentService etlRespondentService;

    @Inject
    UISessionDataService sessionDataService;

    @Inject
    QuestionService self;

    @Inject
    ContentTranslator translator;

    /**
     * Initializes the respondent's survey by generating initial answers for all sections
     * and navigating to the step associated with the specified display key.
     *
     * @param respondentId the unique identifier of the respondent
     * @param displaykey   the display key representing the specific survey section to initialize
     * @return an instance of {@code NavResponse}, which includes navigation details, answers,
     * and step information for the specified display key
     */
    @Timed(value = "survey.init", description = "Time to initialize survey for respondent", histogram = true)
    public NavResponse init(int respondentId, String displaykey) {
        // Two transactions on purpose (UC-012 BR-004): the activity stamp joins the caller's
        // transaction when there is one (session restore has just logged the respondent in and
        // holds their row), while the page render keeps its own, as it always has. Stamping the
        // row from inside the nested transaction would wait on the outer one until it timed out.
        self.recordActivity(respondentId);
        return self.renderPage(respondentId, displaykey);
    }

    /** Records the respondent's activity in the caller's transaction, or one of its own. */
    @Transactional
    public void recordActivity(int respondentId) {
        questionManager.recordActivity(respondentId);
    }

    /** Builds the respondent's missing rows and renders the page, in a transaction of its own. */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public NavResponse renderPage(int respondentId, String displaykey) {
        questionManager.init(respondentId, displaykey);
        return questionManager.navigate(respondentId, displaykey);
    }

    /**
     * Initializes the respondent's survey session based on the current UI-scoped session service and
     * navigates to the step associated with the specified display key.
     *
     * @param displaykey the display key representing the specific survey section to initialize
     * @return an instance of {@code NavResponse}, which includes navigation details, answers,
     * and step information for the specified display key
     */
    public NavResponse init(String displaykey) {
        Respondent respondent = sessionDataService.getRespondent();
        return init(respondent.id, displaykey);
    }
    /**
     * Generates a review response containing sections and items based on the respondent's data.
     * Each section represents a logical grouping of items, and items include display labels and values.
     *
     * @param respondent_id the unique identifier of the respondent for whom the review response is generated
     * @return an instance of {@code ReviewResponse} containing a list of sections with their associated items
     */
    /**
     * Re-renders a respondent's in-progress answer labels in the language now in force
     * (UC-009 BR-005). Called from {@code LocaleSelection.apply} when a respondent switches
     * language mid-survey.
     *
     * @param respondentId the respondent whose labels should be rebuilt
     */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void relocalize(int respondentId) {
        questionManager.relocalize(respondentId);
    }

    @Timed(value = "survey.review", description = "Time to generate review response", histogram = true)
    @Transactional
    public ReviewResponse review(int respondent_id) {

        List<ReviewItem> items = new ArrayList<>();
        List<ReviewSection> sections = new ArrayList<>();

        Respondent respondent = Respondent.findById(respondent_id);
        String language = respondent == null ? null : translator.language(respondent.survey);

        Query query = entityManager.createNativeQuery(reviewSQL);
        query.setParameter("respondentId", respondent_id);
        // Empty rather than null: a null :language would make every comparison null, not false,
        // and the CASE would fall through for a respondent who has no content language at all.
        query.setParameter("language", language == null ? "" : language);
        // The same instant the structural finders are pinned to, so the review page shows the
        // wording the respondent answered under rather than the newest one (UC-009 BR-010).
        query.setParameter("asOf", Respondent.snapshotAnchor(respondent_id));
        @SuppressWarnings("unchecked")
        List<Object[]> results = query.getResultList();
        String sectionTitle = "";
        String sectionDisplayKey = "";

        String displayKey;
        String displayText;
        String shortDisplayText;
        String displayValue;

        for (Object[] result : results) {
            displayKey = (String) result[2];
            displayText = (String) result[3];
            shortDisplayText = (String) result[5];
            displayValue = (String) result[6];

            if (displayValue == null) {
                if (!sectionTitle.equals(displayText)) {
                    if (!items.isEmpty()) {
                        // Save the section
                        sections.add(new ReviewSection(sectionTitle, sectionDisplayKey, items));
                    }
                    items = new ArrayList<>();
                    sectionTitle = displayText;
                    sectionDisplayKey = displayKey;
                }
            } else {
                if (shortDisplayText != null) {
                    items.add(new ReviewItem(shortDisplayText, displayValue));
                } else {
                    items.add(new ReviewItem(displayText, displayValue));
                }
            }
        }
        // Save the section
        sections.add(new ReviewSection(sectionTitle, sectionDisplayKey, items));

        return new ReviewResponse(sections);
    }

    /**
     * Finalizes the respondent's survey process by performing several operations, including
     * marking the respondent as inactive, cleaning up deleted entries, populating fact and
     * dimension tables, and logging the ETL execution time.
     *
     * @param respondentId the unique identifier of the respondent to be finalized
     */
    public void finalize(int respondentId) {
        Date startDt = new Date();
        setActiveFalse(respondentId);
        questionManager.removeDeleted(respondentId);
        String etl = etlRespondentService.populateFactSectionTable(respondentId);
        Date endDt = new Date();
        Duration duration = Duration.between(startDt.toInstant(), endDt.toInstant());
        Log.debug(System.lineSeparator() + etl + " etl took " + duration.getSeconds() + " seconds" + System.lineSeparator());
        Log.debug("Post survey actions:");
        PostSurveyActions(respondentId);
    }


    /**
     * Marks the respondent as inactive by updating the `active` column to false and setting
     * the `finalized_dt` column to the current timestamp in the database.
     *
     * @param respondentId the unique identifier for the respondent to be marked as inactive
     */
    @Transactional
    public void setActiveFalse(int respondentId) {
        DatabaseRetryUtil.executeWithRetry(() -> {
            Query activeQuery = entityManager.createNativeQuery("UPDATE survey.respondents set active = false, finalized_dt = CURRENT_TIMESTAMP where id = :respondentId");
            activeQuery.setParameter("respondentId", respondentId);
            activeQuery.executeUpdate();
        }, "setting respondent " + respondentId + " to inactive");
    }

    /**
     * Saves or updates the given answer and processes downstream effects such as navigation
     * and building dependent questions. If the answer has not changed, the method will
     * not perform any updates or downstream operations.
     *
     * @param answer the {@code Answer} object containing the respondent's input to be saved or updated
     * @return an instance of {@code NavResponse} containing updated navigation information,
     * answers, and related step details
     */
    @Timed(value = "survey.save.answer", description = "Time to save answer and process dependencies", histogram = true)
    @Transactional
    public NavResponse saveAnswer(Answer answer) {
        // Load the answer
        Answer a = Answer.findById(answer.id);
        // See if the answer has changed
        if (a.getTextValue() != null && a.getTextValue().equals(answer.getTextValue())) {
            // the answer is the same -- don't do anything.
        } else {
            a.setTextValue(answer.getTextValue());
            a.savedDt = (new Date());

            if (a.question != null
                    && "CHECKBOX".equals(a.question.questionType.name)
                    && !Boolean.parseBoolean(a.getTextValue())) {
                // There really isn't a false only true and null
                a.setTextValue(null);
            }

            // The answer has changed. This can effect downstream questions.

            // For Relationships that are Repeating this could be a decrease in
            // the value like the change from 3 to 2 or from 3 to 0.
            // In that case we do not want to delete all the downstream answers
            // but only some of them.
            questionManager.deleteDownstreamAnswers(answer.respondentId, a, a.id);

            // Build Downstream questions and sections
            questionManager.buildDownstreamQuestions(a);
        }
        questionManager.recordActivity(a.respondentId);

        // Now build a new set of Answers and return it.
        return questionManager.navigate(a.respondentId, a.getDisplayKey());
    }

    /**
     * UC-004 step 4: delivers the survey's post-survey actions for the respondent (UC-015). Each
     * write of the delivery commits on its own and no call runs inside a transaction, so this
     * method opens none.
     */
    @Timed(value = "survey.post.actions", description = "Time to execute post-survey actions", histogram = true)
    public void PostSurveyActions(int respondentId) {
        postSurveyActionDelivery.deliverAll(respondentId);
    }
}
