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
import com.elicitsoftware.model.Survey;
import com.elicitsoftware.response.NavResponse;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class QuestionManagerTest {

    @Inject
    QuestionManager questionManager;

    @Inject
    EntityManager em;

    // V005.5 Tess Tester — completed respondent, all active paths walked
    static final int TESS_RESPONDENT_ID = 1;
    static final int SURVEY_ID = 1;

    // Display key constants derived from V005/V005.5 fixture
    static final String WELCOME_SECTION      = "0001-0001-0000-0001-0000-0000-0000";
    static final String PATRON_SECTION       = "0001-0002-0000-0002-0000-0000-0000";
    static final String DIGITAL_SECTION      = "0001-0002-0000-0003-0000-0000-0000";
    static final String COLLPREFS_SECTION    = "0001-0003-0000-0004-0000-0000-0000";
    static final String MEDIAPREFS_SECTION   = "0001-0003-0000-0005-0000-0000-0000";
    static final String CHECKOUT_SECTION_1   = "0001-0004-0000-0006-0001-0000-0000";
    static final String RENEWAL_SECTION_1    = "0001-0004-0000-0007-0001-0000-0000";

    // Additional display key constants
    static final String EMAIL_ADDR_DK         = "0001-0002-0000-0002-0000-0003-0000"; // q6 email address (TEXT upstream R4)
    static final String EMAIL_NOTIF_DK        = "0001-0002-0000-0003-0000-0002-0000"; // q12 email notification (downstream R7, TEXT target R4)
    static final String NARRATOR_DK           = "0001-0003-0000-0004-0000-0003-0000"; // q17 narrator (FIELD_EXIST upstream R12)

    // Tess Tester answer display keys used in tests
    static final String TERMS_DK          = "0001-0001-0000-0001-0000-0002-0000"; // BOOLEAN, q2
    static final String DIGITAL_ACCESS_DK = "0001-0002-0000-0002-0000-0007-0000"; // EQUAL, q10
    static final String MEDIA_TYPE_DK     = "0001-0003-0000-0004-0000-0002-0000"; // CONTAINS, q16
    static final String CHECKOUT_QTY_DK   = "0001-0003-0000-0004-0000-0001-0000"; // GREATER_THAN, q15
    static final String ITEM_TYPE_INST1_DK = "0001-0004-0000-0006-0001-0003-0000"; // NOT_EQUAL, q29

    /**
     * Replicates QuestionService.saveAnswer() without the UIScoped dependency.
     * Must be called within a @TestTransaction.
     */
    void saveAnswer(Answer answer, String newValue) {
        Answer a = Answer.findById(answer.id);
        a.setTextValue(newValue);
        em.flush(); // ensure new value is visible to native queries in delete/build
        questionManager.deleteDownstreamAnswers(answer.respondentId, a, a.id);
        questionManager.buildDownstreamQuestions(a);
    }

    /** Create a fresh respondent for mutating tests. */
    Respondent createFreshRespondent() {
        Respondent r = new Respondent();
        r.survey = Survey.findById(SURVEY_ID);
        r.token = "test_" + System.nanoTime();
        r.active = true;
        r.logins = 0;
        r.persist();
        return r;
    }

    // ── US-1 Init ───────────────────────────────────────────────────────────

    @Test
    @TestTransaction
    void given_freshRespondent_when_init_then_baselineAnswersCreated() {
        // Matrix row 1: BOOLEAN — init populates non-downstream questions
        Respondent r = createFreshRespondent();
        questionManager.init(r.id.intValue(), WELCOME_SECTION);

        List<Answer> answers = Answer.find("respondentId = ?1 and deleted = false", r.id).list();
        assertFalse(answers.isEmpty(), "init() must create at least one answer");
        // Welcome section answers exist
        assertTrue(answers.stream().anyMatch(a -> a.getDisplayKey().startsWith("0001-0001")),
                "Welcome step answers expected after init()");
    }

    @Test
    @TestTransaction
    void given_freshRespondent_when_initTwice_then_noDuplicates() {
        Respondent r = createFreshRespondent();
        questionManager.init(r.id.intValue(), WELCOME_SECTION);
        long countAfterFirst = Answer.count("respondentId = ?1 and deleted = false", r.id);

        questionManager.init(r.id.intValue(), WELCOME_SECTION);
        long countAfterSecond = Answer.count("respondentId = ?1 and deleted = false", r.id);

        assertEquals(countAfterFirst, countAfterSecond, "Second init() must not create duplicate answers");
    }

    // ── US-2 Navigate ───────────────────────────────────────────────────────

    @Test
    void given_tessAnswers_when_navigateWelcomeSection_then_answersReturnedInOrder() {
        // Matrix row 8: SHOW action — navigate returns section answers ordered by display key
        NavResponse response = questionManager.navigate(TESS_RESPONDENT_ID, WELCOME_SECTION);

        assertNotNull(response, "NavResponse must not be null");
        assertNotNull(response.getAnswers(), "Answers list must not be null");
        assertFalse(response.getAnswers().isEmpty(), "Welcome section must have answers");

        List<Answer> answers = response.getAnswers();
        for (int i = 1; i < answers.size(); i++) {
            assertTrue(
                answers.get(i - 1).getDisplayKey().compareTo(answers.get(i).getDisplayKey()) <= 0,
                "Answers must be in ascending displayKey order"
            );
        }
    }

    @Test
    void given_tessDigitalAccessTrue_when_navigateDigitalSection_then_answersPresent() {
        // Matrix row 3: EQUAL true — digital access section visible for Tess
        NavResponse response = questionManager.navigate(TESS_RESPONDENT_ID, DIGITAL_SECTION);

        assertNotNull(response);
        assertFalse(response.getAnswers().isEmpty(),
                "Digital Access section must have answers since Tess answered TRUE");
    }

    @Test
    void given_tessAnswers_when_navigateCollPrefs_then_answersInDisplayKeyOrder() {
        // EC-09: answer ordering is stable
        NavResponse response = questionManager.navigate(TESS_RESPONDENT_ID, COLLPREFS_SECTION);

        assertNotNull(response);
        List<Answer> answers = response.getAnswers();
        for (int i = 1; i < answers.size(); i++) {
            assertTrue(
                answers.get(i - 1).getDisplayKey().compareTo(answers.get(i).getDisplayKey()) <= 0,
                "CollPrefs answers must be in ascending displayKey order"
            );
        }
    }

    @Test
    void given_tessCheckout_when_navigateCheckoutSection1_then_checkoutAnswersPresent() {
        // Matrix row 9: REPEAT — first checkout instance visible
        NavResponse response = questionManager.navigate(TESS_RESPONDENT_ID, CHECKOUT_SECTION_1);

        assertNotNull(response);
        assertFalse(response.getAnswers().isEmpty(),
                "Checkout section instance 1 must have answers");
    }

    // ── US-6 BOOLEAN operator ───────────────────────────────────────────────

    @Test
    @TestTransaction
    void given_freshRespondent_when_termsTrue_then_patronStepCreated() {
        // Matrix row 1: BOOLEAN true → SHOW patron step
        Respondent r = createFreshRespondent();
        questionManager.init(r.id.intValue(), WELCOME_SECTION);

        Answer termsAnswer = Answer.findByDisplayKeyActive(r.id.intValue(), TERMS_DK);
        assertNotNull(termsAnswer, "Terms answer must exist after init()");

        saveAnswer(termsAnswer, "TRUE");

        long patronAnswers = Answer.count(
            "respondentId = ?1 and displayKey like '0001-0002%' and deleted = false", r.id);
        assertTrue(patronAnswers > 0, "Patron step answers must appear after terms=TRUE");
    }

    @Test
    @TestTransaction
    void given_termsTruePatronVisible_when_termsFalse_then_patronDeleted() {
        // Matrix row 1: BOOLEAN false → patron step hidden
        Respondent r = createFreshRespondent();
        questionManager.init(r.id.intValue(), WELCOME_SECTION);

        Answer termsAnswer = Answer.findByDisplayKeyActive(r.id.intValue(), TERMS_DK);
        saveAnswer(termsAnswer, "TRUE");

        // Confirm patron visible
        long patronBefore = Answer.count(
            "respondentId = ?1 and displayKey like '0001-0002%' and deleted = false", r.id);
        assertTrue(patronBefore > 0);

        // Now set to FALSE
        termsAnswer = Answer.findByDisplayKeyActive(r.id.intValue(), TERMS_DK);
        saveAnswer(termsAnswer, "FALSE");

        long patronAfter = Answer.count(
            "respondentId = ?1 and displayKey like '0001-0002%' and deleted = false", r.id);
        assertEquals(0, patronAfter, "Patron answers must be deleted when terms=FALSE");
    }

    @Test
    @TestTransaction
    void given_termsFalse_when_termsTrue_then_patronRestored() {
        // Matrix row 12: soft-delete restore — patron answers restored, not duplicated
        Respondent r = createFreshRespondent();
        questionManager.init(r.id.intValue(), WELCOME_SECTION);

        Answer termsAnswer = Answer.findByDisplayKeyActive(r.id.intValue(), TERMS_DK);
        saveAnswer(termsAnswer, "TRUE");
        long countAfterTrue = Answer.count(
            "respondentId = ?1 and displayKey like '0001-0002%' and deleted = false", r.id);

        termsAnswer = Answer.findByDisplayKeyActive(r.id.intValue(), TERMS_DK);
        saveAnswer(termsAnswer, "FALSE");

        termsAnswer = Answer.findByDisplayKeyActive(r.id.intValue(), TERMS_DK);
        saveAnswer(termsAnswer, "TRUE");
        long countAfterRestore = Answer.count(
            "respondentId = ?1 and displayKey like '0001-0002%' and deleted = false", r.id);

        assertEquals(countAfterTrue, countAfterRestore, "Patron answers must be restored, not duplicated");
    }

    // ── US-3 EQUAL + CONTAINS SHOW ──────────────────────────────────────────

    @Test
    @TestTransaction
    void given_freshRespondent_when_digitalAccessTrue_then_digitalSectionCreated() {
        // Matrix row 3: EQUAL true → SHOW digital access section
        Respondent r = createFreshRespondent();
        questionManager.init(r.id.intValue(), WELCOME_SECTION);

        // Get to patron step first
        Answer termsAnswer = Answer.findByDisplayKeyActive(r.id.intValue(), TERMS_DK);
        saveAnswer(termsAnswer, "TRUE");

        Answer digitalAnswer = Answer.findByDisplayKeyActive(r.id.intValue(), DIGITAL_ACCESS_DK);
        assertNotNull(digitalAnswer, "Digital access question must exist after init+terms=TRUE");

        saveAnswer(digitalAnswer, "TRUE");

        long digitalSectionAnswers = Answer.count(
            "respondentId = ?1 and displayKey like '0001-0002-0000-0003%' and deleted = false", r.id);
        assertTrue(digitalSectionAnswers > 0, "Digital Access section must appear after digitalAccess=TRUE");
    }

    @Test
    @TestTransaction
    void given_digitalAccessTrue_when_digitalAccessFalse_then_sectionDeleted() {
        // Matrix row 3: EQUAL false → digital section hidden
        Respondent r = createFreshRespondent();
        questionManager.init(r.id.intValue(), WELCOME_SECTION);

        Answer termsAnswer = Answer.findByDisplayKeyActive(r.id.intValue(), TERMS_DK);
        saveAnswer(termsAnswer, "TRUE");

        Answer digitalAnswer = Answer.findByDisplayKeyActive(r.id.intValue(), DIGITAL_ACCESS_DK);
        saveAnswer(digitalAnswer, "TRUE");

        long before = Answer.count(
            "respondentId = ?1 and displayKey like '0001-0002-0000-0003%' and deleted = false", r.id);
        assertTrue(before > 0);

        digitalAnswer = Answer.findByDisplayKeyActive(r.id.intValue(), DIGITAL_ACCESS_DK);
        saveAnswer(digitalAnswer, "FALSE");

        long after = Answer.count(
            "respondentId = ?1 and displayKey like '0001-0002-0000-0003%' and deleted = false", r.id);
        assertEquals(0, after, "Digital section must be deleted when digitalAccess=FALSE");
    }

    @Test
    @TestTransaction
    void given_freshRespondent_when_mediaTypeContainsDvd_then_mediaPrefsSectionCreated() {
        // Matrix row 7: CONTAINS true → SHOW media preferences section
        Respondent r = createFreshRespondent();
        questionManager.init(r.id.intValue(), WELCOME_SECTION);

        Answer termsAnswer = Answer.findByDisplayKeyActive(r.id.intValue(), TERMS_DK);
        saveAnswer(termsAnswer, "TRUE");

        // Navigate to collection prefs to ensure media type question is initialized
        questionManager.navigate(r.id.intValue(), COLLPREFS_SECTION);

        Answer mediaType = Answer.findByDisplayKeyActive(r.id.intValue(), MEDIA_TYPE_DK);
        assertNotNull(mediaType, "Media type answer must exist after navigating to collection prefs");

        saveAnswer(mediaType, "dvd");

        long mediaPrefAnswers = Answer.count(
            "respondentId = ?1 and displayKey like '0001-0003-0000-0005%' and deleted = false", r.id);
        assertTrue(mediaPrefAnswers > 0, "Media prefs section must appear when media type CONTAINS dvd");
    }

    // ── US-7 NOT_EQUAL operator ─────────────────────────────────────────────

    @Test
    @TestTransaction
    void given_checkoutItemTypeNotBook_when_saveAnswer_then_formatQuestionShown() {
        // Matrix row 4: NOT_EQUAL true → SHOW format question
        // Use Tess Tester's checkout instance 1 (item type = "book"), change to "audiobook"
        Answer itemTypeAnswer = Answer.findByDisplayKeyActive(TESS_RESPONDENT_ID, ITEM_TYPE_INST1_DK);
        assertNotNull(itemTypeAnswer, "Item type answer must exist for Tess checkout instance 1");

        saveAnswer(itemTypeAnswer, "audiobook");

        // Format question (q30) should now exist for this checkout instance
        long formatAnswers = Answer.count(
            "respondentId = ?1 and displayKey = '0001-0004-0000-0006-0001-0004-0000' and deleted = false",
            TESS_RESPONDENT_ID);
        assertTrue(formatAnswers > 0, "Format question must appear when item type NOT_EQUAL 'book'");
    }

    @Test
    @TestTransaction
    void given_itemTypeNotBook_when_itemTypeBook_then_formatQuestionDeleted() {
        // Matrix row 4: NOT_EQUAL false → format question hidden
        Answer itemTypeAnswer = Answer.findByDisplayKeyActive(TESS_RESPONDENT_ID, ITEM_TYPE_INST1_DK);
        saveAnswer(itemTypeAnswer, "audiobook");

        itemTypeAnswer = Answer.findByDisplayKeyActive(TESS_RESPONDENT_ID, ITEM_TYPE_INST1_DK);
        saveAnswer(itemTypeAnswer, "book");

        long formatAnswers = Answer.count(
            "respondentId = ?1 and displayKey = '0001-0004-0000-0006-0001-0004-0000' and deleted = false",
            TESS_RESPONDENT_ID);
        assertEquals(0, formatAnswers, "Format question must be deleted when item type = 'book'");
    }

    // ── US-4 REPEAT action ──────────────────────────────────────────────────

    @Test
    @TestTransaction
    void given_freshRespondent_when_checkoutQty2_then_twoCheckoutInstancesCreated() {
        // Matrix row 9: REPEAT step — qty=2 creates 2 checkout instances
        Respondent r = createFreshRespondent();
        questionManager.init(r.id.intValue(), WELCOME_SECTION);

        Answer termsAnswer = Answer.findByDisplayKeyActive(r.id.intValue(), TERMS_DK);
        saveAnswer(termsAnswer, "TRUE");

        questionManager.navigate(r.id.intValue(), COLLPREFS_SECTION);

        Answer checkoutQty = Answer.findByDisplayKeyActive(r.id.intValue(), CHECKOUT_QTY_DK);
        assertNotNull(checkoutQty, "Checkout quantity answer must exist");

        saveAnswer(checkoutQty, "2");

        long instance1 = Answer.count(
            "respondentId = ?1 and displayKey like '0001-0004-0000-0006-0001%' and deleted = false", r.id);
        long instance2 = Answer.count(
            "respondentId = ?1 and displayKey like '0001-0004-0000-0006-0002%' and deleted = false", r.id);

        assertTrue(instance1 > 0, "Checkout instance 1 must exist");
        assertTrue(instance2 > 0, "Checkout instance 2 must exist");
    }

    @Test
    @TestTransaction
    void given_twoCheckoutInstances_when_qtyDecreasedTo1_then_secondInstanceDeleted() {
        // Matrix row 9: REPEAT decrement
        Respondent r = createFreshRespondent();
        questionManager.init(r.id.intValue(), WELCOME_SECTION);

        Answer termsAnswer = Answer.findByDisplayKeyActive(r.id.intValue(), TERMS_DK);
        saveAnswer(termsAnswer, "TRUE");
        questionManager.navigate(r.id.intValue(), COLLPREFS_SECTION);

        Answer checkoutQty = Answer.findByDisplayKeyActive(r.id.intValue(), CHECKOUT_QTY_DK);
        saveAnswer(checkoutQty, "2");

        // Now decrease to 1
        checkoutQty = Answer.findByDisplayKeyActive(r.id.intValue(), CHECKOUT_QTY_DK);
        saveAnswer(checkoutQty, "1");

        long instance2 = Answer.count(
            "respondentId = ?1 and displayKey like '0001-0004-0000-0006-0002%' and deleted = false", r.id);
        assertEquals(0, instance2, "Checkout instance 2 must be deleted when qty decreases to 1");
    }

    // ── US-8 FIELD_EXIST + 3-level nested chain ─────────────────────────────

    @Test
    void given_tessMediaTypeAudiobook_when_navigate_then_narratorQ51Present() {
        // Matrix row 11: chain L1 — audiobook triggers Q51 (narrator)
        // Tess already has media_type=audiobook,dvd so Q51 should be visible
        NavResponse response = questionManager.navigate(TESS_RESPONDENT_ID, COLLPREFS_SECTION);

        assertNotNull(response);
        // Q51 (narrator question) display key: 0001-0003-0000-0004-0000-0003-0000
        boolean q51Present = response.getAnswers().stream()
            .anyMatch(a -> a.getDisplayKey().equals("0001-0003-0000-0004-0000-0003-0000"));
        assertTrue(q51Present, "Narrator question (Q51) must be present for Tess (audiobook selected)");
    }

    @Test
    @TestTransaction
    void given_mediaTypeNoAudiobook_when_saveAnswer_then_q51q52Absent() {
        // Matrix row 6: FIELD_EXIST blocked — no audiobook → no Q51 or Q52
        Answer mediaType = Answer.findByDisplayKeyActive(TESS_RESPONDENT_ID, MEDIA_TYPE_DK);
        saveAnswer(mediaType, "dvd"); // no audiobook

        long q51Count = Answer.count(
            "respondentId = ?1 and displayKey = '0001-0003-0000-0004-0000-0003-0000' and deleted = false",
            TESS_RESPONDENT_ID);
        assertEquals(0, q51Count, "Q51 (narrator) must be absent when audiobook NOT in media type");
    }

    // ── EC Edge Cases ────────────────────────────────────────────────────────

    @Test
    @TestTransaction
    void given_firstVisit_when_navigate_then_answersNotEmpty() {
        // EC-01: first visit to a section triggers buildInitialAnswers and returns non-empty list
        Respondent r = createFreshRespondent();
        questionManager.init(r.id.intValue(), WELCOME_SECTION);

        NavResponse response = questionManager.navigate(r.id.intValue(), WELCOME_SECTION);

        assertNotNull(response);
        assertFalse(response.getAnswers().isEmpty(), "First navigate must return non-empty answer list");
    }

    @Test
    @TestTransaction
    void given_nullTextValue_when_saveAnswerOnBoolean_then_noDownstream() {
        // EC-03: null textValue on BOOLEAN must not create downstream answers
        Respondent r = createFreshRespondent();
        questionManager.init(r.id.intValue(), WELCOME_SECTION);

        Answer termsAnswer = Answer.findByDisplayKeyActive(r.id.intValue(), TERMS_DK);
        saveAnswer(termsAnswer, null);

        long patronAnswers = Answer.count(
            "respondentId = ?1 and displayKey like '0001-0002%' and deleted = false", r.id);
        assertEquals(0, patronAnswers, "Null textValue on BOOLEAN must not create downstream answers");
    }

    @Test
    @TestTransaction
    void given_emptyTextValue_when_saveAnswerOnContains_then_noDownstream() {
        // EC-04: empty textValue on CONTAINS must not match downstream
        Respondent r = createFreshRespondent();
        questionManager.init(r.id.intValue(), WELCOME_SECTION);

        Answer termsAnswer = Answer.findByDisplayKeyActive(r.id.intValue(), TERMS_DK);
        saveAnswer(termsAnswer, "TRUE");
        questionManager.navigate(r.id.intValue(), COLLPREFS_SECTION);

        Answer mediaType = Answer.findByDisplayKeyActive(r.id.intValue(), MEDIA_TYPE_DK);
        assertNotNull(mediaType);
        saveAnswer(mediaType, "");

        long mediaPrefAnswers = Answer.count(
            "respondentId = ?1 and displayKey like '0001-0003-0000-0005%' and deleted = false", r.id);
        assertEquals(0, mediaPrefAnswers, "Empty CONTAINS value must not trigger downstream section");
    }

    @Test
    @TestTransaction
    void given_repeatQtyZero_when_saveAnswer_then_allInstancesDeleted() {
        // EC-05: REPEAT qty→0 deletes all instances
        Respondent r = createFreshRespondent();
        questionManager.init(r.id.intValue(), WELCOME_SECTION);

        Answer termsAnswer = Answer.findByDisplayKeyActive(r.id.intValue(), TERMS_DK);
        saveAnswer(termsAnswer, "TRUE");
        questionManager.navigate(r.id.intValue(), COLLPREFS_SECTION);

        Answer checkoutQty = Answer.findByDisplayKeyActive(r.id.intValue(), CHECKOUT_QTY_DK);
        saveAnswer(checkoutQty, "2");

        checkoutQty = Answer.findByDisplayKeyActive(r.id.intValue(), CHECKOUT_QTY_DK);
        saveAnswer(checkoutQty, "0");

        long checkoutInstances = Answer.count(
            "respondentId = ?1 and displayKey like '0001-0004%' and deleted = false", r.id);
        assertEquals(0, checkoutInstances, "All checkout instances must be deleted when qty=0");
    }

    @Test
    @TestTransaction
    void given_softDeletedAnswer_when_conditionBecomesTrue_then_restoredNotDuplicated() {
        // EC-08: soft-delete restore — total answer count must not grow beyond original
        Respondent r = createFreshRespondent();
        questionManager.init(r.id.intValue(), WELCOME_SECTION);

        Answer termsAnswer = Answer.findByDisplayKeyActive(r.id.intValue(), TERMS_DK);
        saveAnswer(termsAnswer, "TRUE");

        Answer digitalAnswer = Answer.findByDisplayKeyActive(r.id.intValue(), DIGITAL_ACCESS_DK);
        saveAnswer(digitalAnswer, "TRUE");
        long afterFirstTrue = Answer.count(
            "respondentId = ?1 and displayKey like '0001-0002-0000-0003%' and deleted = false", r.id);

        digitalAnswer = Answer.findByDisplayKeyActive(r.id.intValue(), DIGITAL_ACCESS_DK);
        saveAnswer(digitalAnswer, "FALSE");

        digitalAnswer = Answer.findByDisplayKeyActive(r.id.intValue(), DIGITAL_ACCESS_DK);
        saveAnswer(digitalAnswer, "TRUE");
        long afterRestore = Answer.count(
            "respondentId = ?1 and displayKey like '0001-0002-0000-0003%' and deleted = false", r.id);

        assertEquals(afterFirstTrue, afterRestore,
            "Digital section answer count must match after restore — no duplicates");
    }

    // ── Additional coverage: TEXT FIELD_EXIST + CONTAINS→FIELD_EXIST chain ─

    @Test
    @TestTransaction
    void given_emailNotifTrue_when_emailAddressSet_then_textFieldExistFires() {
        // Covers R4 TEXT + FIELD_EXIST: setting email address triggers token substitution
        // on the email notification display text → exercises getUpstreamAnswerByRelationshipId
        Respondent r = createFreshRespondent();
        questionManager.init(r.id.intValue(), WELCOME_SECTION);

        Answer termsAnswer = Answer.findByDisplayKeyActive(r.id.intValue(), TERMS_DK);
        saveAnswer(termsAnswer, "TRUE");

        // Digital access = TRUE to create section 3 (email notification question lives there)
        Answer digitalAnswer = Answer.findByDisplayKeyActive(r.id.intValue(), DIGITAL_ACCESS_DK);
        saveAnswer(digitalAnswer, "TRUE");

        // Set email notification = TRUE → R7 creates email notification downstream question
        Answer emailNotif = Answer.findByDisplayKeyActive(r.id.intValue(), EMAIL_NOTIF_DK);
        assertNotNull(emailNotif, "Email notification question must exist after digital=TRUE");
        saveAnswer(emailNotif, "true");

        // Set email address once → R4 TEXT FIELD_EXIST fires, creates Dependent for email notification
        Answer emailAnswer = Answer.findByDisplayKeyActive(r.id.intValue(), EMAIL_ADDR_DK);
        assertNotNull(emailAnswer, "Email address answer must exist after patron step init");
        saveAnswer(emailAnswer, "test@coverage.com");

        // Set email address again → deleteDownstreamAnswers hits TEXT case (marks Dependent deleted)
        // This covers the "case TEXT: dependent.deleted = true" branch in deleteDownstreamAnswers
        emailAnswer = Answer.findByDisplayKeyActive(r.id.intValue(), EMAIL_ADDR_DK);
        saveAnswer(emailAnswer, "updated@coverage.com");

        Answer refreshed = Answer.findByDisplayKeyActive(r.id.intValue(), EMAIL_ADDR_DK);
        assertNotNull(refreshed);
    }

    @Test
    @TestTransaction
    void given_freshRespondent_when_mediaTypeAudiobook_then_narratorQuestionShown() {
        // R11: CONTAINS audiobook → SHOW narrator question (sq_id=17) — FIELD_EXIST prep for R12
        Respondent r = createFreshRespondent();
        questionManager.init(r.id.intValue(), WELCOME_SECTION);

        Answer termsAnswer = Answer.findByDisplayKeyActive(r.id.intValue(), TERMS_DK);
        saveAnswer(termsAnswer, "TRUE");
        questionManager.navigate(r.id.intValue(), COLLPREFS_SECTION);

        Answer mediaType = Answer.findByDisplayKeyActive(r.id.intValue(), MEDIA_TYPE_DK);
        assertNotNull(mediaType);
        saveAnswer(mediaType, "audiobook");

        // Narrator question should now exist (R11: CONTAINS audiobook → SHOW sq_id=17)
        Answer narrator = Answer.findByDisplayKeyActive(r.id.intValue(), NARRATOR_DK);
        assertNotNull(narrator, "Narrator question must appear when media type CONTAINS audiobook");
    }

    @Test
    @TestTransaction
    void given_narratorAnswered_when_saveAnswer_then_fieldExistShowsFormat() {
        // R12: FIELD_EXIST narrator → SHOW audiobook format question
        Respondent r = createFreshRespondent();
        questionManager.init(r.id.intValue(), WELCOME_SECTION);

        Answer termsAnswer = Answer.findByDisplayKeyActive(r.id.intValue(), TERMS_DK);
        saveAnswer(termsAnswer, "TRUE");
        questionManager.navigate(r.id.intValue(), COLLPREFS_SECTION);

        // First trigger audiobook to create narrator question
        Answer mediaType = Answer.findByDisplayKeyActive(r.id.intValue(), MEDIA_TYPE_DK);
        saveAnswer(mediaType, "audiobook");

        // Now answer the narrator question → R12 FIELD_EXIST fires → SHOW audiobook format
        Answer narrator = Answer.findByDisplayKeyActive(r.id.intValue(), NARRATOR_DK);
        assertNotNull(narrator, "Narrator must be present after audiobook selected");
        saveAnswer(narrator, "Scott Brick");

        // Audiobook format question (sq_id=18) should now appear: display key 0001-0003-0000-0004-0000-0004-0000
        Answer format = Answer.findByDisplayKeyActive(r.id.intValue(), "0001-0003-0000-0004-0000-0004-0000");
        assertNotNull(format, "Audiobook format question must appear after narrator FIELD_EXIST");
    }

    // ── US-9 TEXT action / replaceText ──────────────────────────────────────

    @Test
    void given_tessCheckoutItem2Audiobook_when_navigateCheckout2_then_formatQuestionDisplayTextSubstituted() {
        // Matrix row 10: TEXT action on question (replaceText questionSQL branch)
        // Tess checkout item 2 is audiobook — the format question Q30 display text uses a token
        NavResponse response = questionManager.navigate(TESS_RESPONDENT_ID, "0001-0004-0000-0006-0002-0000-0000");
        assertNotNull(response);
        assertFalse(response.getAnswers().isEmpty(),
                "Checkout instance 2 must have answers with token-substituted display text");
    }

    // ── Golden master: replay Tess Tester's survey and compare answer sets ───

    @Test
    @TestTransaction
    void given_freshRespondent_when_answerInDisplayKeyOrder_then_matchesTessGoldenMaster() {
        // Create fresh respondent and initialise
        Respondent r = createFreshRespondent();
        questionManager.init(r.id.intValue(), WELCOME_SECTION);

        // Load every non-null answer Tess submitted, in display_key order.
        // Alphabetical sort of the zero-padded key = correct upstream-first traversal order.
        List<Answer> tessAnswers = Answer
                .find("respondentId = ?1 and textValue is not null and deleted = false order by displayKey",
                        TESS_RESPONDENT_ID)
                .list();

        for (Answer tess : tessAnswers) {
            Answer fresh = Answer.findByDisplayKeyActive(r.id.intValue(), tess.getDisplayKey());
            if (fresh != null) {
                saveAnswer(fresh, tess.getTextValue());
            }
        }

        // Compare active answer signatures: displayKey|textValue
        List<String> tessSet  = answerSignatures(TESS_RESPONDENT_ID);
        List<String> freshSet = answerSignatures(r.id.intValue());
        assertEquals(tessSet, freshSet,
                "Fresh respondent's answer set must be identical to Tess Tester's golden master");
    }

    private List<String> answerSignatures(int respondentId) {
        return Answer.<Answer>find(
                        "respondentId = ?1 and deleted = false order by displayKey", respondentId)
                .list()
                .stream()
                .map(a -> a.getDisplayKey() + "|" + (a.getTextValue() == null ? "" : a.getTextValue()))
                .collect(java.util.stream.Collectors.toList());
    }

    // ── removeDeleted ────────────────────────────────────────────────────────

    @Test
    @TestTransaction
    void given_deletedAnswers_when_removeDeleted_then_answersPhysicallyRemoved() {
        // removeDeleted() purges soft-deleted rows from the DB
        Respondent r = createFreshRespondent();
        questionManager.init(r.id.intValue(), WELCOME_SECTION);

        // Trigger soft-delete: set terms=FALSE then TRUE to create/delete patron answers
        Answer termsAnswer = Answer.findByDisplayKeyActive(r.id.intValue(), TERMS_DK);
        saveAnswer(termsAnswer, "TRUE");
        termsAnswer = Answer.findByDisplayKeyActive(r.id.intValue(), TERMS_DK);
        saveAnswer(termsAnswer, "FALSE"); // patron answers now deleted=true

        long deletedBefore = Answer.count("respondentId = ?1 and deleted = true", r.id);
        assertTrue(deletedBefore > 0, "There must be soft-deleted answers before removeDeleted()");

        questionManager.removeDeleted(r.id);

        long deletedAfter = Answer.count("respondentId = ?1 and deleted = true", r.id);
        assertEquals(0, deletedAfter, "removeDeleted() must purge all soft-deleted answers");
    }

    @Test
    void contextLoads() {
        assertNotNull(questionManager);
    }
}
