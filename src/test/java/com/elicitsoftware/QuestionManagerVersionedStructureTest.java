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

import com.elicitsoftware.model.Answer;
import com.elicitsoftware.model.Respondent;
import com.elicitsoftware.model.SelectItem;
import com.elicitsoftware.model.Survey;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UC-002 (Answer Survey Questions), BR-009, against research/Kimball_type_2.md ("Snapshot
 * Anchor") and Author's scd-contract.md: once Admin applies a revision every structure table
 * holds two rows per durable id, and a respondent must be served the version whose effective
 * window covers their first access -- whether that is before or after the revision.
 * <p>
 * The defect this pins: the durable-key columns used to be JPA associations, and loading one
 * (a placement's section, a rule's downstream question, a question's select group) once a
 * second version existed made Hibernate throw "More than one row with the given identifier
 * was found". {@code QuestionManager} swallowed it, the transaction was silently rolled back,
 * and a respondent whose first access came after the first revision could not save anything.
 * <p>
 * Deliberately not {@code @TestTransaction}: every call runs in its own committed transaction
 * and the assertions read back afterwards, so a rollback-only transaction that "completes
 * normally" would fail the test rather than hide behind the test's own rollback. The fixture
 * is removed in {@link #cleanUp()}.
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class QuestionManagerVersionedStructureTest {

    @Inject
    QuestionManager questionManager;

    @Inject
    EntityManager em;

    // Version-0 surrogate ids (9xxx), durable ids (8xxx) and version-1 surrogate ids (7xxx):
    // all distinct from each other and from the display orders 1..3.
    static final int STEP_ONE = 9761, STEP_ONE_DURABLE = 8761, STEP_ONE_V1 = 7761;
    static final int STEP_TWO = 9762, STEP_TWO_DURABLE = 8762, STEP_TWO_V1 = 7762;
    static final int SECTION_NAMES = 9771, SECTION_NAMES_DURABLE = 8771, SECTION_NAMES_V1 = 7771;
    static final int SECTION_ABOUT = 9772, SECTION_ABOUT_DURABLE = 8772, SECTION_ABOUT_V1 = 7772;
    static final int SS_ONE = 9781, SS_ONE_DURABLE = 8781, SS_ONE_V1 = 7781;
    static final int SS_TWO = 9782, SS_TWO_DURABLE = 8782, SS_TWO_V1 = 7782;
    static final int Q_COUNT = 9791, Q_COUNT_DURABLE = 8791, Q_COUNT_V1 = 7791;
    static final int Q_NAME = 9792, Q_NAME_DURABLE = 8792, Q_NAME_V1 = 7792;
    static final int Q_ABOUT = 9793, Q_ABOUT_DURABLE = 8793, Q_ABOUT_V1 = 7793;
    static final int Q_HOUSE = 9794, Q_HOUSE_DURABLE = 8794, Q_HOUSE_V1 = 7794;
    static final int SQ_COUNT = 9801, SQ_COUNT_DURABLE = 8801, SQ_COUNT_V1 = 7801;
    static final int SQ_NAME = 9802, SQ_NAME_DURABLE = 8802, SQ_NAME_V1 = 7802;
    static final int SQ_ABOUT = 9803, SQ_ABOUT_DURABLE = 8803, SQ_ABOUT_V1 = 7803;
    static final int SQ_HOUSE = 9804, SQ_HOUSE_DURABLE = 8804, SQ_HOUSE_V1 = 7804;
    static final int SG_MOOD = 9811, SG_MOOD_DURABLE = 8811, SG_MOOD_V1 = 7811;
    static final int SI_HAPPY = 9821, SI_HAPPY_DURABLE = 8821, SI_HAPPY_V1 = 7821;
    static final int SI_SAD = 9822, SI_SAD_DURABLE = 8822, SI_SAD_V1 = 7822;

    static final int TYPE_INTEGER = 5, TYPE_RADIO = 7, TYPE_TEXT = 8;
    static final int OP_GREATER_THAN = 2, OP_FIELD_EXIST = 5;
    static final int ACTION_SHOW = 1, ACTION_REPEAT = 2, ACTION_TEXT = 3;

    static final String ABOUT_TEXT_V0 = "Anything about this person?";
    static final String ABOUT_TEXT_V1 = "Anything else about this person?";
    static final OffsetDateTime MAX_SENTINEL = OffsetDateTime.parse("9999-12-31T23:59:59+00:00");

    private Integer surveyId;

    // ── fixture ──────────────────────────────────────────────────────────────

    private int exec(String sql, Object... params) {
        var q = em.createNativeQuery(sql);
        for (int i = 0; i < params.length; i++) {
            q.setParameter(i + 1, params[i]);
        }
        return q.executeUpdate();
    }

    private String key(String rest) {
        return String.format("%04d-%s", surveyId, rest);
    }

    private <T> T tx(Supplier<T> work) {
        return QuarkusTransaction.requiringNew().call(work::get);
    }

    private void tx(Runnable work) {
        QuarkusTransaction.requiringNew().run(work::run);
    }

    /** The household survey (UC-002 A3): version 0 of every structure table. */
    private int buildSurvey() {
        int id = ((Number) em.createNativeQuery(
                "INSERT INTO survey.surveys(id, name, display_order, title, description, initial_display_key, post_survey_url, survey_key) "
                        + "VALUES (NEXTVAL('survey.surveys_seq'), 'VersionedFixture', 904, 'Versioned fixture', "
                        + "'Every structure table carries two versions', NULL, NULL, gen_random_uuid()) RETURNING id")
                .getSingleResult()).intValue();
        surveyId = id;

        String steps = "INSERT INTO survey.steps(id, step_id, survey_id, display_order, name, dimension_name, description, step_key) "
                + "VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, gen_random_uuid())";
        exec(steps, STEP_ONE, STEP_ONE_DURABLE, id, 1, "Household", "VersionedHousehold", "Count, names and the household name");
        exec(steps, STEP_TWO, STEP_TWO_DURABLE, id, 2, "Household member", "VersionedMember", "Shown once per name");

        String sections = "INSERT INTO survey.sections(id, section_id, survey_id, display_order, name, dimension_name, description, section_key) "
                + "VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, gen_random_uuid())";
        exec(sections, SECTION_NAMES, SECTION_NAMES_DURABLE, id, 1, "Names", "VersionedNames", "First step's section");
        exec(sections, SECTION_ABOUT, SECTION_ABOUT_DURABLE, id, 2, "About {NAME|this person}", "VersionedAbout", "Second step's section");

        String ss = "INSERT INTO survey.steps_sections(id, steps_sections_id, survey_id, step_id, step_display_order, section_id, section_display_order, display_key, steps_sections_key) "
                + "VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, gen_random_uuid())";
        exec(ss, SS_ONE, SS_ONE_DURABLE, id, STEP_ONE_DURABLE, 1, SECTION_NAMES_DURABLE, 1, key("0001-0000-0001-0000-0000-0000"));
        exec(ss, SS_TWO, SS_TWO_DURABLE, id, STEP_TWO_DURABLE, 2, SECTION_ABOUT_DURABLE, 1, key("0002-0000-0001-0000-0000-0000"));

        exec("UPDATE survey.surveys SET initial_display_key = ?1 WHERE id = ?2", key("0001-0000-0001-0000-0000-0000"), id);

        exec("INSERT INTO survey.select_groups(id, select_group_id, survey_id, name, description, data_type, select_group_key) "
                + "VALUES (?1, ?2, ?3, 'Mood', 'How the person is doing', 'Text', gen_random_uuid())", SG_MOOD, SG_MOOD_DURABLE, id);
        String items = "INSERT INTO survey.select_items(id, select_item_id, survey_id, select_group_id, display_text, display_order, coded_value, select_item_key) "
                + "VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, gen_random_uuid())";
        exec(items, SI_HAPPY, SI_HAPPY_DURABLE, id, SG_MOOD_DURABLE, "Happy", 1, "H");
        exec(items, SI_SAD, SI_SAD_DURABLE, id, SG_MOOD_DURABLE, "Sad", 2, "S");

        String questions = "INSERT INTO survey.questions(id, question_id, survey_id, type_id, text, short_text, tool_tip, required, "
                + "min_value, max_value, validation_text, select_group_id, mask, placeholder, default_value, question_key) "
                + "VALUES (?1, ?2, ?3, ?4, ?5, ?6, '', false, NULL, NULL, NULL, ?7, NULL, NULL, NULL, gen_random_uuid())";
        exec(questions, Q_COUNT, Q_COUNT_DURABLE, id, TYPE_INTEGER, "How many people live with you?", "Q_COUNT", null);
        exec(questions, Q_NAME, Q_NAME_DURABLE, id, TYPE_TEXT, "Name of person {Q#}", "Q_NAME", null);
        exec(questions, Q_ABOUT, Q_ABOUT_DURABLE, id, TYPE_RADIO, ABOUT_TEXT_V0, "Q_ABOUT", SG_MOOD_DURABLE);
        exec(questions, Q_HOUSE, Q_HOUSE_DURABLE, id, TYPE_TEXT, "Household name", "Q_HOUSE", null);

        String sq = "INSERT INTO survey.sections_questions(id, sections_question_id, survey_id, question_id, section_id, display_order, sections_question_key) "
                + "VALUES (?1, ?2, ?3, ?4, ?5, ?6, gen_random_uuid())";
        exec(sq, SQ_COUNT, SQ_COUNT_DURABLE, id, Q_COUNT_DURABLE, SECTION_NAMES_DURABLE, 1);
        exec(sq, SQ_NAME, SQ_NAME_DURABLE, id, Q_NAME_DURABLE, SECTION_NAMES_DURABLE, 2);
        exec(sq, SQ_HOUSE, SQ_HOUSE_DURABLE, id, Q_HOUSE_DURABLE, SECTION_NAMES_DURABLE, 3);
        exec(sq, SQ_ABOUT, SQ_ABOUT_DURABLE, id, Q_ABOUT_DURABLE, SECTION_ABOUT_DURABLE, 1);

        String rel = "INSERT INTO survey.relationships(id, survey_id, upstream_step_id, upstream_sq_id, "
                + "downstream_step_id, downstream_ss_id, downstream_sq_id, operator_id, action_id, description, token, "
                + "reference_value, default_upstream_value, relationship_key) "
                + "VALUES (NEXTVAL('survey.relationships_seq'), ?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11, '', gen_random_uuid())";
        // Question-only REPEAT: count > 0 repeats the name question in its own section (UC-002 A3).
        exec(rel, id, STEP_ONE_DURABLE, SQ_COUNT_DURABLE, null, null, SQ_NAME_DURABLE, OP_GREATER_THAN, ACTION_REPEAT,
                "Repeat the name question count times", null, "0");
        // SHOW: every name reveals one instance of the second step, carrying the name as NAME.
        exec(rel, id, STEP_ONE_DURABLE, SQ_NAME_DURABLE, STEP_TWO_DURABLE, null, null, OP_FIELD_EXIST, ACTION_SHOW,
                "Show a household-member step per name", "NAME", "");
        // TEXT across steps: the household name fills HOUSE in the second step's name.
        exec(rel, id, STEP_ONE_DURABLE, SQ_HOUSE_DURABLE, STEP_TWO_DURABLE, null, null, OP_FIELD_EXIST, ACTION_TEXT,
                "Household name into the member step's title", "HOUSE", "");
        return id;
    }

    /**
     * What Admin does when it applies a revision: for EVERY versioned table, insert version 1
     * of each row (same durable id, new surrogate id, effective from {@code publish}) and let
     * {@code survey.scd_close_predecessor()} close version 0 at that instant. The about
     * question's text and one select item's label change; everything else is copied.
     */
    private void applyRevision(OffsetDateTime publish) {
        String stepCopy = "INSERT INTO survey.steps(id, step_id, step_key, version, survey_id, display_order, name, dimension_name, description, effective_from, effective_to) "
                + "SELECT ?1, step_id, step_key, 1, survey_id, display_order, name, dimension_name, description, ?3, ?4 FROM survey.steps WHERE id = ?2";
        exec(stepCopy, STEP_ONE_V1, STEP_ONE, publish, MAX_SENTINEL);
        exec(stepCopy, STEP_TWO_V1, STEP_TWO, publish, MAX_SENTINEL);

        String sectionCopy = "INSERT INTO survey.sections(id, section_id, section_key, version, survey_id, display_order, name, dimension_name, description, effective_from, effective_to) "
                + "SELECT ?1, section_id, section_key, 1, survey_id, display_order, name, dimension_name, description, ?3, ?4 FROM survey.sections WHERE id = ?2";
        exec(sectionCopy, SECTION_NAMES_V1, SECTION_NAMES, publish, MAX_SENTINEL);
        exec(sectionCopy, SECTION_ABOUT_V1, SECTION_ABOUT, publish, MAX_SENTINEL);

        String ssCopy = "INSERT INTO survey.steps_sections(id, steps_sections_id, steps_sections_key, version, survey_id, step_id, step_display_order, section_id, section_display_order, display_key, effective_from, effective_to) "
                + "SELECT ?1, steps_sections_id, steps_sections_key, 1, survey_id, step_id, step_display_order, section_id, section_display_order, display_key, ?3, ?4 FROM survey.steps_sections WHERE id = ?2";
        exec(ssCopy, SS_ONE_V1, SS_ONE, publish, MAX_SENTINEL);
        exec(ssCopy, SS_TWO_V1, SS_TWO, publish, MAX_SENTINEL);

        exec("INSERT INTO survey.select_groups(id, select_group_id, select_group_key, version, survey_id, name, description, data_type, effective_from, effective_to) "
                + "SELECT ?1, select_group_id, select_group_key, 1, survey_id, name, description, data_type, ?3, ?4 FROM survey.select_groups WHERE id = ?2",
                SG_MOOD_V1, SG_MOOD, publish, MAX_SENTINEL);
        String itemCopy = "INSERT INTO survey.select_items(id, select_item_id, select_item_key, version, survey_id, select_group_id, display_text, display_order, coded_value, effective_from, effective_to) "
                + "SELECT ?1, select_item_id, select_item_key, 1, survey_id, select_group_id, ?5, display_order, coded_value, ?3, ?4 FROM survey.select_items WHERE id = ?2";
        exec(itemCopy, SI_HAPPY_V1, SI_HAPPY, publish, MAX_SENTINEL, "Cheerful");
        exec(itemCopy, SI_SAD_V1, SI_SAD, publish, MAX_SENTINEL, "Sad");

        String questionCopy = "INSERT INTO survey.questions(id, question_id, question_key, version, survey_id, type_id, text, short_text, tool_tip, required, select_group_id, effective_from, effective_to) "
                + "SELECT ?1, question_id, question_key, 1, survey_id, type_id, ?5, short_text, tool_tip, required, select_group_id, ?3, ?4 FROM survey.questions WHERE id = ?2";
        exec(questionCopy, Q_COUNT_V1, Q_COUNT, publish, MAX_SENTINEL, "How many people live with you?");
        exec(questionCopy, Q_NAME_V1, Q_NAME, publish, MAX_SENTINEL, "Name of person {Q#}");
        exec(questionCopy, Q_ABOUT_V1, Q_ABOUT, publish, MAX_SENTINEL, ABOUT_TEXT_V1);
        exec(questionCopy, Q_HOUSE_V1, Q_HOUSE, publish, MAX_SENTINEL, "Household name");

        String sqCopy = "INSERT INTO survey.sections_questions(id, sections_question_id, sections_question_key, version, survey_id, question_id, section_id, display_order, effective_from, effective_to) "
                + "SELECT ?1, sections_question_id, sections_question_key, 1, survey_id, question_id, section_id, display_order, ?3, ?4 FROM survey.sections_questions WHERE id = ?2";
        exec(sqCopy, SQ_COUNT_V1, SQ_COUNT, publish, MAX_SENTINEL);
        exec(sqCopy, SQ_NAME_V1, SQ_NAME, publish, MAX_SENTINEL);
        exec(sqCopy, SQ_ABOUT_V1, SQ_ABOUT, publish, MAX_SENTINEL);
        exec(sqCopy, SQ_HOUSE_V1, SQ_HOUSE, publish, MAX_SENTINEL);

        int rules = exec("INSERT INTO survey.relationships(id, relationship_id, relationship_key, version, survey_id, upstream_step_id, upstream_sq_id, "
                + "downstream_step_id, downstream_ss_id, downstream_sq_id, operator_id, action_id, description, token, reference_value, default_upstream_value, effective_from, effective_to) "
                + "SELECT NEXTVAL('survey.relationships_seq'), relationship_id, relationship_key, 1, survey_id, upstream_step_id, upstream_sq_id, "
                + "downstream_step_id, downstream_ss_id, downstream_sq_id, operator_id, action_id, description, token, reference_value, default_upstream_value, ?2, ?3 "
                + "FROM survey.relationships WHERE survey_id = ?1 AND version = 0", surveyId, publish, MAX_SENTINEL);
        assertEquals(3, rules, "every rule must get a version 1");
    }

    private int createRespondent(OffsetDateTime firstAccessDt) {
        Respondent r = new Respondent();
        r.survey = Survey.findById(surveyId);
        r.accessCode = "versioned_test_" + System.nanoTime();
        r.active = true;
        r.logins = 1;
        r.firstAccessDt = firstAccessDt;
        r.persist();
        em.flush();
        return r.id;
    }

    // ── driving the runtime, one committed transaction per call ──────────────

    private void saveAnswer(int answerId, String newValue) {
        tx(() -> {
            Answer a = Answer.findById(answerId);
            a.setTextValue(newValue);
            em.flush();
            questionManager.deleteDownstreamAnswers(a.respondentId, a, a.id);
            questionManager.buildDownstreamQuestions(a);
        });
    }

    private Answer active(int respondentId, String displayKey) {
        return tx(() -> Answer.findByDisplayKeyActive(respondentId, displayKey));
    }

    private long committedAnswers(int respondentId) {
        return tx(() -> Answer.count("respondentId = ?1 and deleted = false", respondentId));
    }

    /**
     * Logs the respondent in, answers count "2", names Alice and Bob, and returns the two
     * "About ..." question answers of the shown second step (instance 1 and 2).
     */
    private List<Answer> completeHousehold(int rid) {
        tx(() -> questionManager.init(rid, key("0001-0000-0001-0000-0000-0000")));
        assertTrue(committedAnswers(rid) > 0, "init must commit the initial answers");

        String namesPrefix = key("0001-0000-0001-0000");
        Answer count = active(rid, namesPrefix + "-0001-0000");
        Answer house = active(rid, namesPrefix + "-0003-0000");
        assertNotNull(count, "the count question is initial and must be seeded");
        assertNotNull(house, "the household name is initial and must be seeded");

        saveAnswer(house.id, "Smiths");
        saveAnswer(count.id, "2");
        Answer name1 = active(rid, namesPrefix + "-0002-0001");
        Answer name2 = active(rid, namesPrefix + "-0002-0002");
        assertNotNull(name1, "name instance 1 must be committed (the count save used to roll back silently)");
        assertNotNull(name2, "name instance 2 must be committed");
        assertEquals("Name of person 1", name1.displayText);
        assertEquals("Name of person 2", name2.displayText);

        saveAnswer(name1.id, "Alice");
        saveAnswer(name2.id, "Bob");

        Answer step1 = active(rid, key("0002-0001-0000-0000-0000-0000"));
        Answer step2 = active(rid, key("0002-0002-0000-0000-0000-0000"));
        assertNotNull(step1, "step instance 1 must be shown for Alice");
        assertNotNull(step2, "step instance 2 must be shown for Bob");
        Answer about1 = active(rid, key("0002-0001-0001-0000-0000-0000"));
        Answer about2 = active(rid, key("0002-0002-0001-0000-0000-0000"));
        assertNotNull(about1, "the shown step's section must get its section answer (instance 1)");
        assertNotNull(about2, "the shown step's section must get its section answer (instance 2)");
        assertEquals("About Alice", about1.displayText, "NAME must be filled from the name that showed instance 1");
        assertEquals("About Bob", about2.displayText, "NAME must be filled from the name that showed instance 2");

        Answer q1 = active(rid, key("0002-0001-0001-0000-0001-0000"));
        Answer q2 = active(rid, key("0002-0002-0001-0000-0001-0000"));
        assertNotNull(q1, "the shown step's question must be seeded for instance 1");
        assertNotNull(q2, "the shown step's question must be seeded for instance 2");
        return List.of(q1, q2);
    }

    private List<String> selectItemLabels(Answer answer) {
        return tx(() -> Answer.<Answer>findById(answer.id).getSelectItems().stream().map(i -> i.displayText).toList());
    }

    // ── the scenario ─────────────────────────────────────────────────────────

    @Test
    void respondentsAnchoredBeforeAndAfterARevision_eachCompleteTheSurveyWithTheirOwnVersion() {
        OffsetDateTime beforeRevision = OffsetDateTime.now();
        OffsetDateTime publish = beforeRevision.plusMinutes(1);
        OffsetDateTime afterRevision = beforeRevision.plusMinutes(2);

        tx(this::buildSurvey);
        int respondentA = tx(() -> createRespondent(beforeRevision));
        tx(() -> applyRevision(publish));
        int respondentB = tx(() -> createRespondent(afterRevision));

        long versions = tx(() -> ((Number) em.createNativeQuery(
                "SELECT count(*) FROM survey.questions WHERE question_id = ?1").setParameter(1, Q_ABOUT_DURABLE).getSingleResult()).longValue());
        assertEquals(2, versions, "the revision must leave two rows per durable id");

        // Respondent A first accessed the survey before the revision: version 0 throughout.
        List<Answer> aboutA = completeHousehold(respondentA);
        for (Answer about : aboutA) {
            assertEquals(ABOUT_TEXT_V0, about.displayText, "A stays on the wording in effect at first access");
            assertEquals(Q_ABOUT, about.question.id, "A's answer pins the version-0 question row");
            assertEquals(0, about.questionVersion);
            assertEquals(List.of("Happy", "Sad"), selectItemLabels(about), "A sees the version-0 select items");
        }

        // Respondent B first accessed it after the revision: version 1 throughout. This is the
        // respondent who could not save a single answer while durable keys were associations.
        List<Answer> aboutB = completeHousehold(respondentB);
        for (Answer about : aboutB) {
            assertEquals(ABOUT_TEXT_V1, about.displayText, "B gets the revised wording");
            assertEquals(Q_ABOUT_V1, about.question.id, "B's answer pins the version-1 question row");
            assertEquals(1, about.questionVersion);
            assertEquals(List.of("Cheerful", "Sad"), selectItemLabels(about), "B sees the version-1 select items");
        }

        // And a selection made against the version-1 items resolves through them (RADIO is
        // one of the two types Answer.getSelectedItem() serves).
        Answer aboutB1 = aboutB.get(0);
        saveAnswer(aboutB1.id, "H");
        SelectItem chosen = tx(() -> Answer.<Answer>findById(aboutB1.id).getSelectedItem());
        assertNotNull(chosen, "a RADIO answer resolves its item through the as-of item list");
        assertEquals(SI_HAPPY_V1, chosen.id, "the resolved item is the version-1 row");
    }

    @AfterEach
    void cleanUp() {
        if (surveyId == null) {
            return;
        }
        int id = surveyId;
        tx(() -> {
            exec("DELETE FROM survey.dependents WHERE respondent_id IN (SELECT id FROM survey.respondents WHERE survey_id = ?1)", id);
            exec("DELETE FROM survey.answers WHERE survey_id = ?1", id);
            exec("DELETE FROM survey.respondents WHERE survey_id = ?1", id);
            exec("DELETE FROM survey.relationships WHERE survey_id = ?1", id);
            exec("DELETE FROM survey.sections_questions WHERE survey_id = ?1", id);
            exec("DELETE FROM survey.questions WHERE survey_id = ?1", id);
            exec("DELETE FROM survey.select_items WHERE survey_id = ?1", id);
            exec("DELETE FROM survey.select_groups WHERE survey_id = ?1", id);
            exec("DELETE FROM survey.steps_sections WHERE survey_id = ?1", id);
            exec("DELETE FROM survey.sections WHERE survey_id = ?1", id);
            exec("DELETE FROM survey.steps WHERE survey_id = ?1", id);
            exec("DELETE FROM survey.surveys WHERE id = ?1", id);
        });
        surveyId = null;
    }
}
