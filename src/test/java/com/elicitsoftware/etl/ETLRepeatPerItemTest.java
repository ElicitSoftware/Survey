package com.elicitsoftware.etl;

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

import com.elicitsoftware.PostgresTestResource;
import com.elicitsoftware.QuestionManager;
import com.elicitsoftware.RepeatPerItemFixture;
import com.elicitsoftware.model.Answer;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * UC-008 BR-012: a section repeated once per selected item reports which question and which
 * item each of its fact rows is about, by keys that survive a reorder of the list.
 * <p>
 * The survey is {@link RepeatPerItemFixture} (a "Michigan vs. {game}" section per game
 * attended), committed rather than built in a test transaction because the ETL writes through
 * the {@code owner} datasource, which cannot join the test's transaction. Respondents are walked
 * with the real {@link QuestionManager}, so the instance numbers the ETL reads are the ones the
 * runtime wrote (UC-002 BR-012), and the ETL's position rule is held to the runtime's by the
 * item text the runtime put in each instance's title.
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class ETLRepeatPerItemTest {

    static final String SCHEMA = "report_repeatperitemfixture";

    @Inject
    QuestionManager questionManager;

    @Inject
    ETLService etlService;

    @Inject
    EntityManager em;

    @PersistenceContext(unitName = "owner")
    EntityManager ownerEm;

    private record Fixture(int surveyId, UUID key) {
    }

    private Fixture install() {
        return QuarkusTransaction.requiringNew().call(() -> {
            int surveyId = RepeatPerItemFixture.build(em);
            UUID key = (UUID) em.createNativeQuery("SELECT survey_key FROM survey.surveys WHERE id = ?1")
                    .setParameter(1, surveyId).getSingleResult();
            return new Fixture(surveyId, key);
        });
    }

    private void remove(Fixture fixture) {
        QuarkusTransaction.requiringNew().run(() -> {
            int id = fixture.surveyId();
            for (String table : List.of("dependents", "answers")) {
                em.createNativeQuery("DELETE FROM survey." + table
                                + " WHERE respondent_id IN (SELECT id FROM survey.respondents WHERE survey_id = ?1)")
                        .setParameter(1, id).executeUpdate();
            }
            for (String table : List.of("respondents", "relationships", "sections_questions", "steps_sections",
                    "questions", "select_items", "select_groups", "sections", "steps")) {
                em.createNativeQuery("DELETE FROM survey." + table + " WHERE survey_id = ?1").setParameter(1, id).executeUpdate();
            }
            em.createNativeQuery("DELETE FROM survey.surveys WHERE id = ?1").setParameter(1, id).executeUpdate();
        });
        QuarkusTransaction.requiringNew().run(() ->
                ownerEm.createNativeQuery("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE").executeUpdate());
    }

    /**
     * A respondent whose anchor is the database's clock now, so it falls after every version
     * row committed so far (the fixture's, or a reorder's).
     */
    private int newRespondent(int surveyId) {
        return QuarkusTransaction.requiringNew().call(() -> ((Number) em.createNativeQuery(
                "INSERT INTO survey.respondents(id, survey_id, access_code, active, logins, created_dt, first_access_dt) "
                        + "VALUES (NEXTVAL('survey.respondents_seq'), ?1, ?2, true, 1, NOW(), NOW()) RETURNING id")
                .setParameter(1, surveyId).setParameter(2, "etl_peritem_" + System.nanoTime()).getSingleResult()).intValue());
    }

    private String key(int surveyId, String rest) {
        return RepeatPerItemFixture.key(surveyId, rest);
    }

    /** The key of the game section's marker (question 0) or one of its questions, for an instance. */
    private String game(int surveyId, int instance, int question) {
        return key(surveyId, String.format("0002-0000-0001-%04d-%04d-0000", instance, question));
    }

    /** Replicates QuestionService.saveAnswer(), saved_dt included: the ETL loads only saved answers. */
    private void saveAnswer(Answer answer, String newValue) {
        Answer a = Answer.findById(answer.id);
        a.setTextValue(newValue);
        a.savedDt = new java.util.Date();
        em.flush();
        questionManager.deleteDownstreamAnswers(answer.respondentId, a, a.id);
        questionManager.buildDownstreamQuestions(a);
    }

    /**
     * Logs the respondent in, selects the games (as the UI stores them, in the order picked),
     * answers the concession question in every instance built, and finishes.
     *
     * @return the title the runtime gave each instance, keyed by instance number
     */
    private java.util.Map<Integer, String> walkAndFinish(Fixture fixture, int rid, String selection, int... instances) {
        return QuarkusTransaction.requiringNew().call(() -> {
            questionManager.init(rid, key(fixture.surveyId(), "0001-0000-0001-0000-0000-0000"));
            Answer games = Answer.findByDisplayKeyActive(rid, key(fixture.surveyId(), "0001-0000-0001-0000-0001-0000"));
            assertNotNull(games, "the multi-select is initial and must be seeded");
            saveAnswer(games, selection);
            java.util.Map<Integer, String> titles = new java.util.TreeMap<>();
            for (int instance : instances) {
                Answer marker = Answer.findByDisplayKeyActive(rid, game(fixture.surveyId(), instance, 0));
                assertNotNull(marker, "instance " + instance + " must have been built");
                titles.put(instance, marker.displayText);
                Answer bought = Answer.findByDisplayKeyActive(rid, game(fixture.surveyId(), instance, 1));
                saveAnswer(bought, "no");
            }
            em.createNativeQuery("UPDATE survey.respondents SET active = false, finalized_dt = NOW() WHERE id = ?1")
                    .setParameter(1, rid).executeUpdate();
            return titles;
        });
    }

    private void build(Fixture fixture) {
        ETLService.RebuildResult result = etlService.rebuildReportingSchema(Optional.of(fixture.key()));
        assertEquals(ETLService.RebuildStatus.OK, result.status(), result.message());
    }

    /** (section_instance, question, item value, item text, item key) of the respondent's game-section rows. */
    @SuppressWarnings("unchecked")
    private List<Object[]> gameRows(int rid) {
        return ownerEm.createNativeQuery(
                        "SELECT f.section_instance, q.value, i.value, i.display_text, i.select_item_key "
                                + "FROM " + SCHEMA + ".fact_sections f "
                                + "JOIN " + SCHEMA + ".dim_question q ON q.id = f.question_key "
                                + "JOIN " + SCHEMA + ".dim_item i ON i.id = f.item_key "
                                + "WHERE f.respondent_id = ?1 AND f.name = 'Game' ORDER BY f.section_instance")
                .setParameter(1, rid).getResultList();
    }

    @Test
    // Section 8 of the research plan: "a respondent selects the second and fourth options and
    // finishes: two fact rows, section_instance 2 and 4, each with the question and the option
    // whose text titled that instance at runtime". Then the reorder, then the regeneration.
    void perItemSectionRows_nameTheQuestionAndTheItem_acrossAReorderAndARegeneration() {
        Fixture fixture = install();
        try {
            // ── respondent A: Iowa then Oklahoma, instances 4 and 2 (UC-002 BR-012) ──
            int a = newRespondent(fixture.surveyId());
            java.util.Map<Integer, String> titlesA = walkAndFinish(fixture, a, "G04,G02", 2, 4);
            build(fixture);

            List<Object[]> rowsA = gameRows(a);
            assertEquals(2, rowsA.size(), "one fact row per instance");
            assertEquals(2, ((Number) rowsA.get(0)[0]).intValue());
            assertEquals("Q_GAMES", rowsA.get(0)[1], "the question the Repeat rule reads");
            assertEquals("g02", rowsA.get(0)[2], "the item's coded value, lower-cased like every tag value");
            assertEquals("Michigan vs. " + rowsA.get(0)[3], titlesA.get(2),
                    "the ETL's position rule names the item the runtime titled instance 2 with");
            assertEquals(4, ((Number) rowsA.get(1)[0]).intValue());
            assertEquals("g04", rowsA.get(1)[2]);
            assertEquals("Michigan vs. " + rowsA.get(1)[3], titlesA.get(4));
            assertEquals(1, ((Number) ownerEm.createNativeQuery(
                            "SELECT COUNT(*) FROM " + SCHEMA + ".fact_sections WHERE respondent_id = ?1 "
                                    + "AND name = 'Games attended' AND question_key = -1 AND item_key = -1")
                    .setParameter(1, a).getSingleResult()).intValue(),
                    "a row that is not a per-item instance keeps -1 in both columns");

            // ── the list is reordered and applied again: Oklahoma becomes the first item ──
            QuarkusTransaction.requiringNew().run(() -> em.createNativeQuery(
                            "INSERT INTO survey.select_items(id, select_item_id, survey_id, select_group_id, display_text, display_order, coded_value, select_item_key, version) "
                                    + "SELECT NEXTVAL('survey.select_items_seq'), select_item_id, survey_id, select_group_id, display_text, "
                                    + "CASE coded_value WHEN 'G02' THEN 0 ELSE display_order END, coded_value, select_item_key, version + 1 "
                                    + "FROM survey.select_items WHERE survey_id = ?1 AND select_group_id = ?2 AND effective_to = '9999-12-31 23:59:59+00'")
                    .setParameter(1, fixture.surveyId()).setParameter(2, RepeatPerItemFixture.GROUP_GAMES_DURABLE).executeUpdate());

            // ── respondent B, after the reorder: Oklahoma is now instance 1 ──
            int b = newRespondent(fixture.surveyId());
            java.util.Map<Integer, String> titlesB = walkAndFinish(fixture, b, "G02", 1);
            assertEquals("Michigan vs. Oklahoma", titlesB.get(1), "the runtime built Oklahoma at its new position");
            build(fixture);

            List<Object[]> rowsB = gameRows(b);
            assertEquals(1, rowsB.size());
            assertEquals(1, ((Number) rowsB.get(0)[0]).intValue(), "a different instance number");
            assertEquals("g02", rowsB.get(0)[2]);
            assertEquals(rowsA.get(0)[4], rowsB.get(0)[4], "the same item key as A's Oklahoma row");
            assertNotEquals(rowsA.get(0)[0], rowsB.get(0)[0]);
            assertEquals(1, ((Number) ownerEm.createNativeQuery(
                            "SELECT COUNT(*) FROM " + SCHEMA + ".dim_item WHERE value = 'g02'").getSingleResult()).intValue(),
                    "one dim_item row per item, whatever its position");

            // ── regenerate: drop the schema and rebuild from survey.answers ──
            ETLService.DropResult dropped = etlService.dropReportingSchema(fixture.key());
            assertEquals(ETLService.DropStatus.OK, dropped.status(), dropped.message());
            build(fixture);
            List<Object[]> againA = gameRows(a);
            List<Object[]> againB = gameRows(b);
            assertEquals(2, againA.size());
            assertEquals(rowsA.get(0)[4], againA.get(0)[4], "A's Oklahoma row comes back with the same item");
            assertEquals(rowsA.get(1)[4], againA.get(1)[4]);
            assertEquals(rowsB.get(0)[4], againB.get(0)[4]);
            assertEquals("Q_GAMES", againB.get(0)[1]);
        } finally {
            remove(fixture);
        }
    }
}
