package com.elicitsoftware.scd;

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

import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Executable spec for research/Kimball_type_2.md "Metadata, Ontology, and ETL Impact"
 * — {@code survey.metadata}'s migration to durable keys, "Rule 1: Versioning an
 * existing row — no ontology changes needed", and Gap ETL-1's *accepted* retroactive
 * reclassification behavior.
 * <p>
 * Written as direct native SQL against the doc's revised join shape
 * ({@code m.question_id = q.question_id}, a durable integer, not the surrogate
 * {@code questions.id}) rather than through {@code ETLService}/{@code Sql.java}: today's
 * {@code Sql.INSERT_MISSING_FACT_SECTION_SQL} hardcodes {@code a.survey_id = 1}, so it
 * cannot populate fact_sections for the isolated ScdSpecFixture survey either way — that
 * is a pre-existing limitation unrelated to Type 2 versioning, not something these tests
 * should depend on either surviving or being fixed.
 */
@QuarkusTest
@Disabled("Enable once the metadata Type 2 migration lands — see research/Kimball_type_2.md 'Metadata, Ontology, and ETL Impact'")
class MetadataDurableKeySpecTest {

    @Inject
    EntityManager em;

    static final OffsetDateTime MAX_SENTINEL = OffsetDateTime.parse("9999-12-31T23:59:59+00:00");

    @Test
    void migration_rekeysMetadataToDurableColumns() {
        long surrogateColumnsRenamedAway = ((Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='survey' AND table_name='metadata' "
                        + "AND column_name IN ('question_id_surrogate','section_question_id_surrogate','step_section_id_surrogate')")
                .getSingleResult()).longValue();
        assertEquals(3, surrogateColumnsRenamedAway, "The old surrogate columns must be preserved under _surrogate names during the transition");

        long durableColumns = ((Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='survey' AND table_name='metadata' "
                        + "AND column_name IN ('question_id','sections_question_id','steps_sections_id')")
                .getSingleResult()).longValue();
        assertEquals(3, durableColumns, "metadata must expose durable question_id/sections_question_id/steps_sections_id columns");
    }

    @Test
    @TestTransaction
    void rule1_versioningTheReferencedQuestion_requiresNoMetadataChange() {
        Integer questionSurrogateId = ScdFixtureIds.questionId(em);
        Integer durableQuestionId = (Integer) em.createNativeQuery(
                "SELECT question_id FROM survey.questions WHERE id = ?1").setParameter(1, questionSurrogateId).getSingleResult();
        Integer surveyId = ScdFixtureIds.surveyId(em);

        Integer dimensionId = (Integer) em.createNativeQuery(
                "INSERT INTO survey.dimensions(id, name) VALUES (NEXTVAL('survey.dimensions_seq'), 'ScdDim') RETURNING id")
                .getSingleResult();
        Integer ontologyId = (Integer) em.createNativeQuery(
                "INSERT INTO survey.ontology(id, survey_id, name, tag, dimension) "
                        + "VALUES (NEXTVAL('survey.ontology_seq'), ?1, 'Scd Tag', 'scd_tag', ?2) RETURNING id")
                .setParameter(1, surveyId).setParameter(2, dimensionId).getSingleResult();
        em.createNativeQuery(
                "INSERT INTO survey.metadata(id, survey_id, step_section_id, question_id, section_question_id, ontology_id, value) "
                        + "VALUES (NEXTVAL('survey.metadata_seq'), ?1, NULL, ?2, NULL, ?3, NULL)")
                .setParameter(1, surveyId).setParameter(2, durableQuestionId).setParameter(3, ontologyId).executeUpdate();

        int currentVersion = ((Number) em.createNativeQuery(
                "SELECT version FROM survey.questions WHERE question_id = ?1 AND effective_to = ?2")
                .setParameter(1, durableQuestionId).setParameter(2, MAX_SENTINEL).getSingleResult()).intValue();
        em.createNativeQuery("UPDATE survey.questions SET effective_to = ?2 WHERE question_id = ?1 AND effective_to = ?3")
                .setParameter(1, durableQuestionId).setParameter(2, OffsetDateTime.now()).setParameter(3, MAX_SENTINEL).executeUpdate();
        em.createNativeQuery(
                "INSERT INTO survey.questions (id, question_id, version, survey_id, type_id, text, required, "
                        + "effective_from, effective_to, is_draft) "
                        + "SELECT nextval('survey.questions_seq'), question_id, ?2, survey_id, type_id, 'Reworded', required, "
                        + "?3, ?4, false FROM survey.questions WHERE question_id = ?1 AND version = ?5")
                .setParameter(1, durableQuestionId).setParameter(2, currentVersion + 1).setParameter(3, OffsetDateTime.now())
                .setParameter(4, MAX_SENTINEL).setParameter(5, currentVersion).executeUpdate();
        em.flush();

        long metadataRowsForThisQuestion = ((Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM survey.metadata WHERE question_id = ?1")
                .setParameter(1, durableQuestionId).getSingleResult()).longValue();
        assertEquals(1, metadataRowsForThisQuestion,
                "Rule 1: the same single metadata row must still cover the question after it is reworded -- no new/updated metadata row needed");
    }

    @Test
    @TestTransaction
    void gapEtl1_retroactivelyReclassifyingTheOntologyMapping_isAcceptedBehaviorNotABug() {
        // Doc's Option A, explicitly accepted: changing metadata's ontology_id changes
        // how ALL historical answers to that question are classified, including ones
        // already recorded before the change. The join is `metadata m ON m.question_id
        // = q.question_id` (durable) -- it always resolves to metadata's CURRENT row.
        Integer questionSurrogateId = ScdFixtureIds.questionId(em);
        Integer durableQuestionId = (Integer) em.createNativeQuery(
                "SELECT question_id FROM survey.questions WHERE id = ?1").setParameter(1, questionSurrogateId).getSingleResult();
        Integer surveyId = ScdFixtureIds.surveyId(em);

        Integer depressionOntologyId = insertTagOnlyOntology(surveyId, "Depression Score", "depression_score");
        Integer metadataId = (Integer) em.createNativeQuery(
                "INSERT INTO survey.metadata(id, survey_id, step_section_id, question_id, section_question_id, ontology_id, value) "
                        + "VALUES (NEXTVAL('survey.metadata_seq'), ?1, NULL, ?2, NULL, ?3, NULL) RETURNING id")
                .setParameter(1, surveyId).setParameter(2, durableQuestionId).setParameter(3, depressionOntologyId)
                .getSingleResult();

        // A respondent finalized under the "depression_score" classification.
        String tagResolvedBeforeReclassification = (String) em.createNativeQuery(
                "SELECT o.tag FROM survey.metadata m JOIN survey.ontology o ON o.id = m.ontology_id "
                        + "WHERE m.question_id = ?1")
                .setParameter(1, durableQuestionId).getSingleResult();
        assertEquals("depression_score", tagResolvedBeforeReclassification);

        // Researcher reclassifies the SAME metadata row to a different ontology tag.
        Integer anxietyOntologyId = insertTagOnlyOntology(surveyId, "Anxiety Score", "anxiety_score");
        em.createNativeQuery("UPDATE survey.metadata SET ontology_id = ?2 WHERE id = ?1")
                .setParameter(1, metadataId).setParameter(2, anxietyOntologyId).executeUpdate();

        String tagResolvedAfterReclassification = (String) em.createNativeQuery(
                "SELECT o.tag FROM survey.metadata m JOIN survey.ontology o ON o.id = m.ontology_id "
                        + "WHERE m.question_id = ?1")
                .setParameter(1, durableQuestionId).getSingleResult();
        assertEquals("anxiety_score", tagResolvedAfterReclassification,
                "Gap ETL-1 / Option A: the ETL join via durable question_id must resolve the reclassified tag "
                        + "for every respondent who ever answered this question, including already-finalized ones");
    }

    private Integer insertTagOnlyOntology(Integer surveyId, String name, String tag) {
        return (Integer) em.createNativeQuery(
                "INSERT INTO survey.ontology(id, survey_id, name, tag, dimension) "
                        + "VALUES (NEXTVAL('survey.ontology_seq'), ?1, ?2, ?3, NULL) RETURNING id")
                .setParameter(1, surveyId).setParameter(2, name).setParameter(3, tag).getSingleResult();
    }
}
