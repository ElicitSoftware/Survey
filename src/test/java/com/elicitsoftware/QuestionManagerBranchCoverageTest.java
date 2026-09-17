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
import com.elicitsoftware.model.Relationship;
import com.elicitsoftware.model.Respondent;
import com.elicitsoftware.model.Section;
import com.elicitsoftware.model.SelectItem;
import com.elicitsoftware.model.Step;
import com.elicitsoftware.model.Survey;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.common.QuarkusTestResource;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Characterization tests for {@link QuestionManager} and its model entities against
 * TODAY's schema (surrogate ids, INTEGER display_order, DOWNSTREAM_S_ID column) — see
 * research/Kimball_type_2.md. These lock in current behavior for code paths the doc
 * says it will rename, retype, or rewire, so that an unmodified re-run of this suite
 * proves nothing regressed once that migration lands.
 * <p>
 * Uses its own isolated, generic (non-FHHS) fixture — V9012__QuestionManager_Branch_Fixture.sql
 * — because every relationship in the existing Library/Tess fixture that sets
 * downstream_step_id also sets downstream_s_id, so {@link QuestionManager}'s
 * downstream-step-*only* SHOW branch and its (currently stubbed) REPEAT branch are
 * never reached by any other test. That fixture also sets each step/section's
 * display_order equal to its own surrogate id (see the fixture's header comment), so
 * this class always derives display keys from the real ids rather than assuming any
 * particular small number.
 * <p>
 * Writing this suite surfaced a real, previously-unknown limitation, not just a coverage
 * gap: the SHOW downstreamStep-only branch used to throw for any survey, because
 * {@code buildDisplayKey} sets the section key segment to 0 when there is no downstream
 * section, and {@code survey.answers.section} is FK'd to {@code survey.sections(id)}, which
 * never contains a row with id 0. Fixed in {@code Answer.setDisplayKeyValues()} by routing
 * the section segment through {@code valueOrNull()} (already applied to
 * {@code section_question_id} in that same method) so it becomes {@code null} instead of a
 * literal {@code 0} -- see {@code showStepOnlyBranch_succeedsAndCreatesStepOnlyAnswer} below.
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class QuestionManagerBranchCoverageTest {

    @Inject
    QuestionManager questionManager;

    @Inject
    EntityManager em;

    private Integer surveyId() {
        return (Integer) em.createNativeQuery(
                "SELECT id FROM survey.surveys WHERE name = 'BranchFixture'").getSingleResult();
    }

    private Integer stepOneId(Integer surveyId) {
        return (Integer) em.createNativeQuery(
                "SELECT id FROM survey.steps WHERE survey_id = ?1 AND name = 'BranchStepOne'")
                .setParameter(1, surveyId).getSingleResult();
    }

    private Integer stepTwoId(Integer surveyId) {
        return (Integer) em.createNativeQuery(
                "SELECT id FROM survey.steps WHERE survey_id = ?1 AND name = 'BranchStepTwo'")
                .setParameter(1, surveyId).getSingleResult();
    }

    private Integer sectionOneId(Integer surveyId) {
        return (Integer) em.createNativeQuery(
                "SELECT id FROM survey.sections WHERE survey_id = ?1 AND name = 'BranchSectionOne'")
                .setParameter(1, surveyId).getSingleResult();
    }

    private Integer stepThreeId(Integer surveyId) {
        return (Integer) em.createNativeQuery(
                "SELECT id FROM survey.steps WHERE survey_id = ?1 AND name = 'BranchStepThree'")
                .setParameter(1, surveyId).getSingleResult();
    }

    private Integer sectionTwoId(Integer surveyId) {
        return (Integer) em.createNativeQuery(
                "SELECT id FROM survey.sections WHERE survey_id = ?1 AND name = 'BranchSectionTwo'")
                .setParameter(1, surveyId).getSingleResult();
    }

    private Integer sectionThreeId(Integer surveyId) {
        return (Integer) em.createNativeQuery(
                "SELECT id FROM survey.sections WHERE survey_id = ?1 AND name = 'BranchSectionThree'")
                .setParameter(1, surveyId).getSingleResult();
    }

    private String initialDisplayKey(Integer surveyId) {
        return (String) em.createNativeQuery(
                "SELECT initial_display_key FROM survey.surveys WHERE id = ?1")
                .setParameter(1, surveyId).getSingleResult();
    }

    /** Section-level display key for BranchStepTwo/BranchSectionTwo. */
    private String stepTwoSectionKey(Integer surveyId) {
        return String.format("%04d-%04d-0000-%04d", surveyId, stepTwoId(surveyId), sectionTwoId(surveyId));
    }

    /** Section-level display key for BranchStepThree/BranchSectionThree. */
    private String stepThreeSectionKey(Integer surveyId) {
        return String.format("%04d-%04d-0000-%04d", surveyId, stepThreeId(surveyId), sectionThreeId(surveyId));
    }

    private String qAKey(Integer surveyId) {
        return String.format("%04d-%04d-0000-%04d-0000-0001-0000", surveyId, stepOneId(surveyId), sectionOneId(surveyId));
    }

    private String qBKey(Integer surveyId) {
        return String.format("%04d-%04d-0000-%04d-0000-0002-0000", surveyId, stepOneId(surveyId), sectionOneId(surveyId));
    }

    private Respondent createFreshRespondent(Integer surveyId) {
        Respondent r = new Respondent();
        r.survey = Survey.findById(surveyId);
        r.accessCode = "branch_test_" + System.nanoTime();
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

    // ── SHOW, downstreamStep-only branch (QuestionManager.java ~line 581) ──────

    @Test
    @TestTransaction
    void showStepOnlyBranch_succeedsAndCreatesStepOnlyAnswer() {
        // QuestionManager.buildDisplayKey sets key.setSection(0) whenever downstreamSection ==
        // null (QuestionManager.java ~line 776), and buildStepAnswer then persists an Answer
        // with that key. Answer.setDisplayKeyValues() now routes the section segment through
        // valueOrNull() (matching section_question_id in that same method), so a step-only
        // Answer gets sectionId = null rather than a literal 0 -- satisfying the nullable
        // answers_section_fk instead of violating it. This test used to pin a throw here;
        // every existing fixture relationship that sets downstream_step_id also sets
        // downstream_s_id specifically to avoid this path (see the Library fixture's R19/R20
        // comments), so this branch was previously untested in every other survey too.
        Integer surveyId = surveyId();
        Respondent r = createFreshRespondent(surveyId);
        questionManager.init(r.id.intValue(), initialDisplayKey(surveyId));

        long beforeStepTwo = Answer.count(
                "respondentId = ?1 and displayKey like ?2 and deleted = false",
                r.id, stepTwoSectionKey(surveyId) + "%");
        assertEquals(0, beforeStepTwo, "StepTwo must be invisible before Q_A fires the SHOW relationship");

        Answer qA = Answer.findByDisplayKeyActive(r.id.intValue(), qAKey(surveyId));
        assertNotNull(qA, "Q_A must be seeded by init()");

        assertDoesNotThrow(() -> saveAnswer(qA, "true"),
                "Firing the step-only SHOW relationship must now succeed");

        // buildInitialStepAnswers now also runs (it never used to be reached, since the save
        // above always threw first) and populates BranchSectionTwo's own initial answers,
        // since that section lives inside StepTwo.
        long afterStepTwo = Answer.count(
                "respondentId = ?1 and displayKey like ?2 and deleted = false",
                r.id, stepTwoSectionKey(surveyId) + "%");
        assertTrue(afterStepTwo > 0, "BranchSectionTwo's initial answers must now be created inside StepTwo");

        // The step-only marker answer itself (buildStepAnswer's own save) is a distinct row:
        // no question, no section_question_id, and now sectionId = null rather than 0.
        Answer stepOnlyAnswer = Answer.find(
                "respondentId = ?1 and stepId = ?2 and question is null and deleted = false",
                r.id, stepTwoId(surveyId)).firstResult();
        assertNotNull(stepOnlyAnswer, "The step-only marker answer for StepTwo must exist");
        assertNull(stepOnlyAnswer.sectionId, "A step-only answer must have a null sectionId, not a literal 0");
    }

    // ── REPEAT, downstreamStep-only branch — buildRepeatedStep is currently a stub ──

    @Test
    @TestTransaction
    void repeatStepOnlyBranch_isCurrentlyANoOp() {
        // Pins buildRepeatedStep's current "// TODO ... not yet implemented" behavior
        // deliberately: implementing it later is an intentional, visible test change,
        // not silent drift.
        Integer surveyId = surveyId();
        Respondent r = createFreshRespondent(surveyId);
        questionManager.init(r.id.intValue(), initialDisplayKey(surveyId));

        Answer qB = Answer.findByDisplayKeyActive(r.id.intValue(), qBKey(surveyId));
        assertNotNull(qB, "Q_B must be seeded by init()");
        saveAnswer(qB, "2");

        long stepThreeAnswers = Answer.count(
                "respondentId = ?1 and displayKey like ?2 and deleted = false",
                r.id, stepThreeSectionKey(surveyId) + "%");
        assertEquals(0, stepThreeAnswers,
                "buildRepeatedStep is currently an unimplemented stub — no StepThree answers must be created");
    }

    // ── Entity field round-trips (fields research/Kimball_type_2.md renames/retypes) ──

    @Test
    void relationshipDownstreamSection_roundTripsAsStepsSectionsReference() {
        Integer surveyId = surveyId();
        Relationship r = Relationship.find("description = ?1",
                "Show StepTwo (step-only SHOW branch) when Q_A is checked").firstResult();
        assertNotNull(r, "R_show_step must exist in the fixture");
        assertNull(r.downstreamSection,
                "R_show_step deliberately leaves downstream_s_id NULL (step-only SHOW)");
        assertNotNull(r.downstreamStep, "R_show_step.downstreamStep must be populated");
        assertEquals(BigDecimal.valueOf(stepTwoId(surveyId)), r.downstreamStep.displayOrder,
                "downstreamStep must resolve to BranchStepTwo (display_order == its own id, by fixture construction)");
    }

    @Test
    void stepAndSectionDisplayOrder_roundTripAsBigDecimal() {
        // display_order is NUMERIC, not INTEGER (Kimball Type 2 SCD, research/Kimball_type_2.md's
        // "Adding a new question" section) -- supports decimal-midpoint insertion.
        Integer surveyId = surveyId();
        Step stepOne = Step.find("surveyId = ?1 and name = ?2", surveyId, "BranchStepOne").firstResult();
        Step stepTwo = Step.find("surveyId = ?1 and name = ?2", surveyId, "BranchStepTwo").firstResult();
        Section sectionOne = Section.find("surveyId = ?1 and name = ?2", surveyId, "BranchSectionOne").firstResult();
        Section sectionTwo = Section.find("surveyId = ?1 and name = ?2", surveyId, "BranchSectionTwo").firstResult();

        assertNotNull(stepOne);
        assertNotNull(stepTwo);
        assertNotNull(sectionOne);
        assertNotNull(sectionTwo);

        assertEquals(BigDecimal.valueOf(stepOneId(surveyId)), stepOne.displayOrder, "Step.displayOrder must round-trip as BigDecimal");
        assertEquals(BigDecimal.valueOf(stepTwoId(surveyId)), stepTwo.displayOrder, "Step.displayOrder must round-trip as BigDecimal");
        assertEquals(BigDecimal.valueOf(sectionOneId(surveyId)), sectionOne.displayOrder, "Section.displayOrder must round-trip as BigDecimal");
        assertEquals(BigDecimal.valueOf(sectionTwoId(surveyId)), sectionTwo.displayOrder, "Section.displayOrder must round-trip as BigDecimal");
    }

    @Test
    void selectItem_selectGroupId_roundTrips() {
        Integer surveyId = surveyId();
        Integer expectedGroupId = (Integer) em.createNativeQuery(
                "SELECT id FROM survey.select_groups WHERE survey_id = ?1 AND name = 'BranchChoice'")
                .setParameter(1, surveyId).getSingleResult();

        SelectItem alpha = SelectItem.find("surveyId = ?1 and displayText = ?2", surveyId, "Alpha").firstResult();
        assertNotNull(alpha, "Alpha select item must exist in the fixture");
        assertEquals(expectedGroupId, alpha.selectGroupId,
                "SelectItem.selectGroupId must round-trip before the group_id -> select_group_id rename");
    }
}
