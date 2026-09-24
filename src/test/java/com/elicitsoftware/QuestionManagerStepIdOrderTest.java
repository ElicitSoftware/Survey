package com.elicitsoftware;

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

import com.elicitsoftware.model.Answer;
import com.elicitsoftware.model.Respondent;
import com.elicitsoftware.model.Survey;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * UC-002 A3 (a question repeats on a numeric answer) and the step-show branch of UC-002 (a rule
 * reveals a whole step, BR-008), for a survey whose step ids DIFFER from their display orders.
 * <p>
 * Every seeded survey numbers its steps so that {@code steps.id}, the durable {@code step_id} and
 * the display order all coincide, which hid a whole family of defects: an answer's display key
 * carries display orders, a relationship row carries durable ids, and several lookups compared
 * the two directly. For an Author-published survey (durable ids 16/17, display orders 1/2, say)
 * a rule that names its upstream step -- every rule whose target lies in another step -- was
 * never found, a shown step got no initial questions, and TEXT rules on a step never applied.
 * <p>
 * The fixture is built inside the test transaction with surrogate ids 91xx, durable ids 81xx
 * and display orders 1/2, so all three spaces differ and any comparison across them fails
 * loudly. Steps: "First step" (a count question, the name question it repeats, and a household
 * name) and "Household member of {HOUSE|the household}" holding the section
 * "About {NAME|this person}". Rules: a question-only REPEAT of the name question, a SHOW from
 * the name question (upstream step named) to the second step carrying token NAME, and a TEXT
 * rule from the household name (upstream step named) to the second step carrying token HOUSE.
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class QuestionManagerStepIdOrderTest {

    @Inject
    QuestionManager questionManager;

    @Inject
    EntityManager em;

    // Surrogate ids (steps.id and friends), durable ids (step_id and friends): deliberately
    // distinct from each other and from the display orders 1 and 2.
    static final int STEP_ONE = 9161, STEP_ONE_DURABLE = 8161;
    static final int STEP_TWO = 9162, STEP_TWO_DURABLE = 8162;
    static final int SECTION_NAMES = 9171, SECTION_NAMES_DURABLE = 8171;
    static final int SECTION_ABOUT = 9172, SECTION_ABOUT_DURABLE = 8172;
    static final int SS_ONE = 9181, SS_ONE_DURABLE = 8181;
    static final int SS_TWO = 9182, SS_TWO_DURABLE = 8182;
    static final int Q_COUNT = 9191, Q_COUNT_DURABLE = 8191;
    static final int Q_NAME = 9192, Q_NAME_DURABLE = 8192;
    static final int Q_ABOUT = 9193, Q_ABOUT_DURABLE = 8193;
    static final int Q_HOUSE = 9194, Q_HOUSE_DURABLE = 8194;
    static final int SQ_COUNT = 9201, SQ_COUNT_DURABLE = 8201;
    static final int SQ_NAME = 9202, SQ_NAME_DURABLE = 8202;
    static final int SQ_ABOUT = 9203, SQ_ABOUT_DURABLE = 8203;
    static final int SQ_HOUSE = 9204, SQ_HOUSE_DURABLE = 8204;

    static final int TYPE_INTEGER = 5, TYPE_TEXT = 8;
    static final int OP_GREATER_THAN = 2, OP_FIELD_EXIST = 5;
    static final int ACTION_SHOW = 1, ACTION_REPEAT = 2, ACTION_TEXT = 3;

    private int exec(String sql, Object... params) {
        var q = em.createNativeQuery(sql);
        for (int i = 0; i < params.length; i++) {
            q.setParameter(i + 1, params[i]);
        }
        return q.executeUpdate();
    }

    private String key(int surveyId, String rest) {
        return String.format("%04d-%s", surveyId, rest);
    }

    /** Builds the survey and returns its id. */
    private int buildSurvey() {
        int surveyId = ((Number) em.createNativeQuery(
                "INSERT INTO survey.surveys(id, name, display_order, title, description, initial_display_key, post_survey_url, survey_key) "
                        + "VALUES (NEXTVAL('survey.surveys_seq'), 'IdOrderFixture', 903, 'Id/order fixture', "
                        + "'Steps whose ids differ from their display orders', NULL, NULL, gen_random_uuid()) RETURNING id")
                .getSingleResult()).intValue();

        String steps = "INSERT INTO survey.steps(id, step_id, survey_id, display_order, name, dimension_name, description, step_key) "
                + "VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, gen_random_uuid())";
        exec(steps, STEP_ONE, STEP_ONE_DURABLE, surveyId, 1, "First step", "IdOrderFirst", "Holds the count, the names and the household name");
        exec(steps, STEP_TWO, STEP_TWO_DURABLE, surveyId, 2, "Household member of {HOUSE|the household}", "IdOrderMember", "Shown once per name");

        String sections = "INSERT INTO survey.sections(id, section_id, survey_id, display_order, name, dimension_name, description, section_key) "
                + "VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, gen_random_uuid())";
        exec(sections, SECTION_NAMES, SECTION_NAMES_DURABLE, surveyId, 1, "Names", "IdOrderNames", "First step's section");
        exec(sections, SECTION_ABOUT, SECTION_ABOUT_DURABLE, surveyId, 2, "About {NAME|this person}", "IdOrderAbout", "Second step's section");

        String ss = "INSERT INTO survey.steps_sections(id, steps_sections_id, survey_id, step_id, step_display_order, section_id, section_display_order, display_key, steps_sections_key) "
                + "VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, gen_random_uuid())";
        exec(ss, SS_ONE, SS_ONE_DURABLE, surveyId, STEP_ONE_DURABLE, 1, SECTION_NAMES_DURABLE, 1, key(surveyId, "0001-0000-0001-0000-0000-0000"));
        exec(ss, SS_TWO, SS_TWO_DURABLE, surveyId, STEP_TWO_DURABLE, 2, SECTION_ABOUT_DURABLE, 1, key(surveyId, "0002-0000-0001-0000-0000-0000"));

        exec("UPDATE survey.surveys SET initial_display_key = ?1 WHERE id = ?2", key(surveyId, "0001-0000-0001-0000-0000-0000"), surveyId);

        String questions = "INSERT INTO survey.questions(id, question_id, survey_id, type_id, text, short_text, tool_tip, required, "
                + "min_value, max_value, validation_text, select_group_id, mask, placeholder, default_value, question_key) "
                + "VALUES (?1, ?2, ?3, ?4, ?5, ?6, '', false, NULL, NULL, NULL, NULL, NULL, NULL, NULL, gen_random_uuid())";
        exec(questions, Q_COUNT, Q_COUNT_DURABLE, surveyId, TYPE_INTEGER, "How many people live with you?", "Q_COUNT");
        exec(questions, Q_NAME, Q_NAME_DURABLE, surveyId, TYPE_TEXT, "Name of person", "Q_NAME");
        exec(questions, Q_ABOUT, Q_ABOUT_DURABLE, surveyId, TYPE_TEXT, "Anything about this person?", "Q_ABOUT");
        exec(questions, Q_HOUSE, Q_HOUSE_DURABLE, surveyId, TYPE_TEXT, "Household name", "Q_HOUSE");

        String sq = "INSERT INTO survey.sections_questions(id, sections_question_id, survey_id, question_id, section_id, display_order, sections_question_key) "
                + "VALUES (?1, ?2, ?3, ?4, ?5, ?6, gen_random_uuid())";
        exec(sq, SQ_COUNT, SQ_COUNT_DURABLE, surveyId, Q_COUNT_DURABLE, SECTION_NAMES_DURABLE, 1);
        exec(sq, SQ_NAME, SQ_NAME_DURABLE, surveyId, Q_NAME_DURABLE, SECTION_NAMES_DURABLE, 2);
        exec(sq, SQ_HOUSE, SQ_HOUSE_DURABLE, surveyId, Q_HOUSE_DURABLE, SECTION_NAMES_DURABLE, 3);
        exec(sq, SQ_ABOUT, SQ_ABOUT_DURABLE, surveyId, Q_ABOUT_DURABLE, SECTION_ABOUT_DURABLE, 1);

        String rel = "INSERT INTO survey.relationships(id, survey_id, upstream_step_id, upstream_sq_id, "
                + "downstream_step_id, downstream_ss_id, downstream_sq_id, operator_id, action_id, description, token, "
                + "reference_value, default_upstream_value, relationship_key) "
                + "VALUES (NEXTVAL('survey.relationships_seq'), ?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11, '', gen_random_uuid())";
        // Question-only REPEAT: count > 0 repeats the name question in its own section (UC-002 A3).
        exec(rel, surveyId, STEP_ONE_DURABLE, SQ_COUNT_DURABLE, null, null, SQ_NAME_DURABLE, OP_GREATER_THAN, ACTION_REPEAT,
                "Repeat the name question count times", null, "0");
        // SHOW: every name reveals one instance of the second step, carrying the name as NAME.
        exec(rel, surveyId, STEP_ONE_DURABLE, SQ_NAME_DURABLE, STEP_TWO_DURABLE, null, null, OP_FIELD_EXIST, ACTION_SHOW,
                "Show a household-member step per name", "NAME", "");
        // TEXT across steps: the household name fills HOUSE in the second step's name.
        exec(rel, surveyId, STEP_ONE_DURABLE, SQ_HOUSE_DURABLE, STEP_TWO_DURABLE, null, null, OP_FIELD_EXIST, ACTION_TEXT,
                "Household name into the member step's title", "HOUSE", "");
        return surveyId;
    }

    private Respondent createFreshRespondent(int surveyId) {
        Respondent r = new Respondent();
        r.survey = Survey.findById(surveyId);
        r.accessCode = "idorder_test_" + System.nanoTime();
        r.active = true;
        r.logins = 0;
        r.persist();
        return r;
    }

    /** Replicates QuestionService.saveAnswer() without the UIScoped dependency. */
    private void saveAnswer(Answer answer, String newValue) {
        Answer a = Answer.findById(answer.id);
        a.setTextValue(newValue);
        em.flush();
        questionManager.deleteDownstreamAnswers(answer.respondentId, a, a.id);
        questionManager.buildDownstreamQuestions(a);
    }

    private Answer active(int respondentId, String displayKey) {
        return Answer.findByDisplayKeyActive(respondentId, displayKey);
    }

    @Test
    @TestTransaction
    void stepNamedByDurableId_isShownOncePerRepeatedName_withTokensFilled() {
        int surveyId = buildSurvey();
        Respondent r = createFreshRespondent(surveyId);
        int rid = r.id.intValue();
        questionManager.init(rid, key(surveyId, "0001-0000-0001-0000-0000-0000"));

        String namesPrefix = key(surveyId, "0001-0000-0001-0000");
        Answer count = active(rid, namesPrefix + "-0001-0000");
        Answer house = active(rid, namesPrefix + "-0003-0000");
        assertNotNull(count, "the count question is initial and must be seeded");
        assertNotNull(house, "the household name is initial and must be seeded");
        assertEquals(0, Answer.count("respondentId = ?1 and displayKey like ?2 and deleted = false", r.id, key(surveyId, "0002-%")),
                "the second step is a rule's downstream and must not be seeded at login");

        saveAnswer(house, "Smiths");

        // UC-002 A3: the question-only REPEAT creates two name instances in the first step.
        assertDoesNotThrow(() -> saveAnswer(count, "2"));
        Answer name1 = active(rid, namesPrefix + "-0002-0001");
        Answer name2 = active(rid, namesPrefix + "-0002-0002");
        assertNotNull(name1, "name instance 1 must be repeated into the count question's own section");
        assertNotNull(name2, "name instance 2 must be repeated into the count question's own section");

        // Step-show: a rule whose upstream step is named by durable id must fire on a survey
        // whose durable ids differ from its display orders.
        assertDoesNotThrow(() -> saveAnswer(name1, "Alice"));
        assertDoesNotThrow(() -> saveAnswer(name2, "Bob"));

        Answer step1 = active(rid, key(surveyId, "0002-0001-0000-0000-0000-0000"));
        Answer step2 = active(rid, key(surveyId, "0002-0002-0000-0000-0000-0000"));
        assertNotNull(step1, "step instance 1 must be shown for Alice");
        assertNotNull(step2, "step instance 2 must be shown for Bob");
        assertEquals("Household member of Smiths", step1.displayText,
                "the TEXT rule across steps must fill HOUSE in the step name");
        assertEquals("Household member of Smiths", step2.displayText);

        Answer about1 = active(rid, key(surveyId, "0002-0001-0001-0000-0000-0000"));
        Answer about2 = active(rid, key(surveyId, "0002-0002-0001-0000-0000-0000"));
        assertNotNull(about1, "the shown step's section must get its section answer (instance 1)");
        assertNotNull(about2, "the shown step's section must get its section answer (instance 2)");
        assertEquals("About Alice", about1.displayText, "NAME must be filled from the name that showed instance 1");
        assertEquals("About Bob", about2.displayText, "NAME must be filled from the name that showed instance 2");

        assertNotNull(active(rid, key(surveyId, "0002-0001-0001-0000-0001-0000")),
                "the shown step's initial question must be seeded for instance 1");
        assertNotNull(active(rid, key(surveyId, "0002-0002-0001-0000-0001-0000")),
                "the shown step's initial question must be seeded for instance 2");
    }
}
