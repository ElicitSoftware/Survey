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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * UC-002 A3b: a question or section repeats once per item selected in a multi-select question,
 * numbered by the item's position in its list (BR-012), with the item's text filling the tokens of
 * the rules that read that question (BR-013).
 * <p>
 * The fixture is the football-satisfaction survey in miniature. Step "Games attended" asks which
 * games the respondent went to ({@code MULTI_SELECT} over Western Michigan, Oklahoma, Texas A&amp;M
 * and Iowa) and who came along ({@code CHECKBOX_GROUP} over spouse, child and friend). Step "Game"
 * holds the section "Michigan vs. {<GAME>|this game}", repeated per game, whose first question
 * carries a second token filled by a Text rule from the same multi-select and reveals a follow-up
 * question when answered "yes". The companion question repeats a name question in its own section.
 * <p>
 * The survey itself is {@link RepeatPerItemFixture}, built inside the test transaction.
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class QuestionManagerRepeatPerItemTest {

    @Inject
    QuestionManager questionManager;

    @Inject
    EntityManager em;

    private String key(int surveyId, String rest) {
        return RepeatPerItemFixture.key(surveyId, rest);
    }

    /** Builds the survey and returns its id. */
    private int buildSurvey() {
        return RepeatPerItemFixture.build(em);
    }

    private Respondent createFreshRespondent(int surveyId) {
        Respondent r = new Respondent();
        r.survey = Survey.findById(surveyId);
        r.accessCode = "peritem_test_" + System.nanoTime();
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

    /** The key of the game section's marker (question 0) or one of its questions, for an instance. */
    private String game(int surveyId, int instance, int question) {
        return key(surveyId, String.format("0002-0000-0001-%04d-%04d-0000", instance, question));
    }

    private long activeInStepTwo(int surveyId, int respondentId) {
        return Answer.count("respondentId = ?1 and displayKey like ?2 and deleted = false", respondentId, key(surveyId, "0002-%"));
    }

    @Test
    @TestTransaction
    void sectionRepeatsOncePerSelectedItem_numberedByTheItemsPosition() {
        int surveyId = buildSurvey();
        int rid = createFreshRespondent(surveyId).id.intValue();
        questionManager.init(rid, key(surveyId, "0001-0000-0001-0000-0000-0000"));
        Answer games = active(rid, key(surveyId, "0001-0000-0001-0000-0001-0000"));
        assertNotNull(games, "the multi-select is initial and must be seeded");
        assertEquals(0, activeInStepTwo(surveyId, rid), "the repeated section is a rule's downstream and must not be seeded at login");

        // Stored in the order picked, as SectionView writes it: Iowa first, then Oklahoma.
        saveAnswer(games, "G04,G02");

        // BR-012: the instance is the item's position -- Oklahoma is second, Iowa fourth.
        Answer oklahoma = active(rid, game(surveyId, 2, 0));
        Answer iowa = active(rid, game(surveyId, 4, 0));
        assertNotNull(oklahoma, "Oklahoma is the list's second item, so its section is instance 2");
        assertNotNull(iowa, "Iowa is the list's fourth item, so its section is instance 4");
        assertNull(active(rid, game(surveyId, 1, 0)), "Western Michigan was not selected");
        assertNull(active(rid, game(surveyId, 3, 0)), "Texas A&M was not selected");

        // BR-013: the Repeat rule's own token is the item's text, per instance.
        assertEquals("Michigan vs. Oklahoma", oklahoma.displayText);
        assertEquals("Michigan vs. Iowa", iowa.displayText);
        // BR-013: so is the token of a Text rule reading the same question, over its constant.
        assertEquals("Did you buy anything at the Oklahoma game?", active(rid, game(surveyId, 2, 1)).displayText);
        assertEquals("Did you buy anything at the Iowa game?", active(rid, game(surveyId, 4, 1)).displayText);
    }

    @Test
    @TestTransaction
    void anItemsTextIsEscapedLikeAnAnswer() {
        int surveyId = buildSurvey();
        int rid = createFreshRespondent(surveyId).id.intValue();
        questionManager.init(rid, key(surveyId, "0001-0000-0001-0000-0000-0000"));

        saveAnswer(active(rid, key(surveyId, "0001-0000-0001-0000-0001-0000")), "G03");

        // BR-013: spliced in as an answer is, so the label renders the ampersand rather than reading it as markup.
        assertEquals("Michigan vs. Texas A&amp;M", active(rid, game(surveyId, 3, 0)).displayText);
    }

    @Test
    @TestTransaction
    void changingTheSelection_leavesEveryOtherItemsAnswersWhereTheyAre() {
        int surveyId = buildSurvey();
        int rid = createFreshRespondent(surveyId).id.intValue();
        questionManager.init(rid, key(surveyId, "0001-0000-0001-0000-0000-0000"));
        Answer games = active(rid, key(surveyId, "0001-0000-0001-0000-0001-0000"));

        saveAnswer(games, "G02,G04");
        saveAnswer(active(rid, game(surveyId, 2, 1)), "yes");
        Answer food = active(rid, game(surveyId, 2, 2));
        assertNotNull(food, "the follow-up is revealed inside Oklahoma's instance");
        assertEquals("How was the food at the Oklahoma game?", food.displayText,
                "a question revealed later inherits the item from its section instance");
        saveAnswer(food, "great");

        // A3b: adding the first item of the list adds an instance and renumbers nothing.
        saveAnswer(games, "G02,G04,G01");
        assertEquals("Michigan vs. Western Michigan", active(rid, game(surveyId, 1, 0)).displayText);
        assertEquals("Michigan vs. Oklahoma", active(rid, game(surveyId, 2, 0)).displayText);
        assertEquals("yes", active(rid, game(surveyId, 2, 1)).getTextValue(), "Oklahoma's answer stays under Oklahoma");
        assertEquals("great", active(rid, game(surveyId, 2, 2)).getTextValue(), "and so does what that answer revealed");
        assertNull(active(rid, game(surveyId, 1, 1)).getTextValue(), "the new game starts unanswered");

        // A3b: unselecting removes that item's instance and only that one.
        saveAnswer(games, "G04,G01");
        assertNull(active(rid, game(surveyId, 2, 0)), "Oklahoma's section is removed");
        assertNull(active(rid, game(surveyId, 2, 1)));
        assertNull(active(rid, game(surveyId, 2, 2)));
        assertNotNull(active(rid, game(surveyId, 1, 0)));
        assertNotNull(active(rid, game(surveyId, 4, 0)));

        // A3b: selecting it again brings its instance back with the answers it had.
        saveAnswer(games, "G04,G01,G02");
        assertEquals("yes", active(rid, game(surveyId, 2, 1)).getTextValue());
        assertEquals("great", active(rid, game(surveyId, 2, 2)).getTextValue());

        // A3b: clearing the selection removes every instance.
        saveAnswer(games, "");
        assertEquals(0, activeInStepTwo(surveyId, rid));
    }

    @Test
    @TestTransaction
    void questionRepeatsOncePerSelectedItem_inItsOwnSection() {
        int surveyId = buildSurvey();
        int rid = createFreshRespondent(surveyId).id.intValue();
        questionManager.init(rid, key(surveyId, "0001-0000-0001-0000-0000-0000"));
        Answer with = active(rid, key(surveyId, "0001-0000-0001-0000-0002-0000"));
        assertNotNull(with, "the checkbox group is initial and must be seeded");
        String name = key(surveyId, "0001-0000-0001-0000-0003-");

        saveAnswer(with, "FRIEND,SPOUSE");

        // BR-012: a repeated question writes the position in its question instance.
        Answer spouse = active(rid, name + "0001");
        Answer friend = active(rid, name + "0003");
        assertNotNull(spouse, "the spouse is the list's first item");
        assertNotNull(friend, "the friend is the list's third item");
        assertNull(active(rid, name + "0002"), "the child was not selected");
        assertEquals("What is the name of your spouse?", spouse.displayText);
        assertEquals("What is the name of your friend?", friend.displayText);

        saveAnswer(friend, "Sam");
        saveAnswer(with, "FRIEND");
        assertNull(active(rid, name + "0001"), "the spouse's question is removed");
        assertEquals("Sam", active(rid, name + "0003").getTextValue(), "the friend's answer is untouched");
    }
}
