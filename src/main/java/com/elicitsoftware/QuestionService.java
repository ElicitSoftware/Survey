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
import com.elicitsoftware.model.Answer;
import com.elicitsoftware.model.Respondent;
import com.elicitsoftware.response.NavResponse;
import com.elicitsoftware.response.ReviewItem;
import com.elicitsoftware.response.ReviewResponse;
import com.elicitsoftware.response.ReviewSection;
import com.vaadin.quarkus.annotation.NormalUIScoped;
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
     *   - Retrieves answers linked to specific questions and sections, ensuring only non-deleted and valid entries are included.
     *   - Handles missing `short_text` fields by falling back to `display_text`.
     *   - Provides a default value of "not reported" for empty `text_value` fields.
     *   - Merges results from different sections and unlinked entries using a union operation.
     * </ul>
     * <p>
     * Results are ordered by `display_key` to ensure a predictable output sequence.
     * <p>
     * The query uses the parameter `:respondentId` to filter answers specific to a given respondent.
     */
    private static final String reviewSQL = """
            SELECT a.id,
                a.respondent_id,
                a.display_key,
                a.display_text,
                q.short_text,
                COALESCE(NULLIF(q.short_text::text, ''::text), a.display_text::text) AS short_display_text,
                COALESCE(i.display_text, a.text_value) AS display_value,
                t.name AS question_type
               FROM survey.answers a
                 LEFT JOIN survey.questions q ON a.question_id = q.id
                 LEFT JOIN survey.question_types t ON q.type_id = t.id
                 LEFT JOIN survey.select_groups g ON q.select_group_id = g.id
                 LEFT JOIN survey.select_items i ON g.id = i.group_id AND a.text_value::text = i.coded_value::text
              WHERE a.deleted = false AND a.section_question_id IS NULL AND a.question_id IS NULL OR a.text_value IS NOT NULL
              AND a.respondent_id = :respondentId
              order by a.display_key;
            """;

    @Inject
    EntityManager entityManager;

    @Inject
    QuestionManager questionManager;

    @Inject
    ETLRespondentService etlRespondentService;

    @Inject
    UISessionDataService sessionDataService;

    /**
     * Initializes the respondent's survey by generating initial answers for all sections
     * and navigating to the step associated with the specified display key.
     *
     * @param respondentId the unique identifier of the respondent
     * @param displaykey   the display key representing the specific survey section to initialize
     * @return an instance of {@code NavResponse}, which includes navigation details, answers,
     * and step information for the specified display key
     */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public NavResponse init(int respondentId, String displaykey) {
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
    @Transactional(Transactional.TxType.REQUIRES_NEW)
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
    @Transactional
    public ReviewResponse review(int respondent_id) {

        List<ReviewItem> items = new ArrayList<>();
        List<ReviewSection> sections = new ArrayList<>();

        Query query = entityManager.createNativeQuery(reviewSQL);
        query.setParameter("respondentId", respondent_id);
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
    }


    /**
     * Marks the respondent as inactive by updating the `active` column to false and setting
     * the `finalized_dt` column to the current timestamp in the database.
     *
     * @param respondentId the unique identifier for the respondent to be marked as inactive
     */
    @Transactional
    public void setActiveFalse(int respondentId) {
        Query activeQuery = entityManager.createNativeQuery("UPDATE survey.respondents set active = false, finalized_dt = CURRENT_TIMESTAMP where id = :respondentId");
        activeQuery.setParameter("respondentId", respondentId);
        activeQuery.executeUpdate();
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

        // Now build a new set of Answers and return it.
        return questionManager.navigate(a.respondentId, a.getDisplayKey());
    }
}
