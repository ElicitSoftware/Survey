package com.elicitsoftware.scd;

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
import io.quarkus.test.TestTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Executable spec for V019 and docs/research/i18n_survey.md sections 3.2 and 3.3: {@code
 * survey.translations} is Type 2 in the same shape as the eight structural tables, but keyed to
 * what it translates by that element's {@code *_key} rather than by a durable-id companion column.
 * <p>
 * Covers what the schema alone must guarantee, before any service exists: the trigger closes the
 * predecessor, the durable id is stable across versions, one current row per durable id and per
 * target string, the element-type check, and the two rules that follow from the key choice --
 * versioning a question leaves its translation attached, and the translation row survives it.
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class TranslationsVersioningSpecTest {

    @Inject
    EntityManager em;

    static final OffsetDateTime MAX_SENTINEL = OffsetDateTime.parse("9999-12-31T23:59:59+00:00");
    private static final String HASH = "0".repeat(64);

    @Test
    void migration_createsTheTableWithItsTypeTwoColumns() {
        long cols = ((Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='survey' AND table_name='translations' "
                        + "AND column_name IN ('element_type','element_key','field','language','value','source_hash',"
                        + "'source_text','translation_id','translation_key','version','effective_from','effective_to')")
                .getSingleResult()).longValue();
        assertEquals(12, cols);
    }

    @Test
    void migration_addsTheLanguageColumnsToSurveysAndAnswers() {
        long surveyCols = ((Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='survey' AND table_name='surveys' "
                        + "AND column_name IN ('base_language','content_languages')").getSingleResult()).longValue();
        assertEquals(2, surveyCols);

        long answerCols = ((Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='survey' AND table_name='answers' "
                        + "AND column_name IN ('display_text_local','display_language')").getSingleResult()).longValue();
        assertEquals(2, answerCols);

        String baseLanguage = (String) em.createNativeQuery(
                "SELECT base_language FROM survey.surveys WHERE id = ?1")
                .setParameter(1, ScdFixtureIds.surveyId(em)).getSingleResult();
        assertEquals("en", baseLanguage, "an existing survey's content is in the default base language");
    }

    @Test
    @TestTransaction
    void insertingASecondVersion_closesThePredecessorAndKeepsTheDurableId() {
        UUID target = questionKey();
        Integer first = insert(target, "text", "es-419", "Redacción original", HASH);

        Integer durableId = (Integer) em.createNativeQuery(
                "SELECT translation_id FROM survey.translations WHERE id = ?1").setParameter(1, first).getSingleResult();
        UUID key = (UUID) em.createNativeQuery(
                "SELECT translation_key FROM survey.translations WHERE id = ?1").setParameter(1, first).getSingleResult();

        Integer second = insertVersion(target, "text", "es-419", "Redacción corregida", HASH, durableId, key, 1);

        OffsetDateTime closed = toOffsetDateTime(em.createNativeQuery(
                "SELECT effective_to FROM survey.translations WHERE id = ?1").setParameter(1, first).getSingleResult());
        assertNotEquals(MAX_SENTINEL, closed, "the trigger must close the predecessor");

        OffsetDateTime current = toOffsetDateTime(em.createNativeQuery(
                "SELECT effective_to FROM survey.translations WHERE id = ?1").setParameter(1, second).getSingleResult());
        assertEquals(MAX_SENTINEL, current);

        long versions = ((Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM survey.translations WHERE translation_id = ?1")
                .setParameter(1, durableId).getSingleResult()).longValue();
        assertEquals(2, versions, "both versions live under one durable id");
    }

    @Test
    @TestTransaction
    void twoCurrentRowsForOneTargetString_areRejected() {
        UUID target = questionKey();
        insert(target, "short_text", "ar", "نص قصير", HASH);
        assertThrows(PersistenceException.class,
                () -> insert(target, "short_text", "ar", "نص آخر", HASH),
                "translations_target_current_un must allow one current row per (survey, language, element, field)");
    }

    @Test
    @TestTransaction
    void theSameStringInTwoLanguages_isTwoCurrentRows() {
        UUID target = questionKey();
        insert(target, "tool_tip", "es-419", "Ayuda", HASH);
        insert(target, "tool_tip", "ar", "تلميح", HASH);

        long current = ((Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM survey.translations WHERE element_key = ?1 AND field = 'tool_tip' "
                        + "AND effective_to = ?2").setParameter(1, target).setParameter(2, MAX_SENTINEL)
                .getSingleResult()).longValue();
        assertEquals(2, current);
    }

    @Test
    @TestTransaction
    void anUnknownElementType_isRejected() {
        assertThrows(PersistenceException.class,
                () -> insert(questionKey(), "name", "es-419", "x", HASH, "select_groups"),
                "translations_element_type_ck must reject a table that carries no translatable text");
    }

    @Test
    @TestTransaction
    void versioningTheQuestion_leavesItsTranslationCurrentAndAttached() {
        // The point of keying on question_key: a new question version is a new surrogate id under
        // the same key, so the translation still attaches -- and is untouched, which is what makes
        // it stale by hash rather than retired (UC-009 BR-008).
        UUID target = questionKey();
        Integer translation = insert(target, "text", "es-419", "¿Pregunta?", HASH);
        Integer questionId = ScdFixtureIds.questionId(em);

        Integer durable = (Integer) em.createNativeQuery(
                "SELECT question_id FROM survey.questions WHERE id = ?1").setParameter(1, questionId).getSingleResult();
        em.createNativeQuery(
                "INSERT INTO survey.questions (id, survey_id, type_id, text, short_text, required, question_id, "
                        + "question_key, version, effective_from, effective_to) "
                        + "SELECT NEXTVAL('survey.questions_seq'), survey_id, type_id, 'Reworded', short_text, required, "
                        + "question_id, question_key, version + 1, now(), ?2 FROM survey.questions WHERE id = ?1")
                .setParameter(1, questionId).setParameter(2, MAX_SENTINEL).executeUpdate();
        em.flush();

        long currentQuestions = ((Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM survey.questions WHERE question_id = ?1 AND effective_to = ?2")
                .setParameter(1, durable).setParameter(2, MAX_SENTINEL).getSingleResult()).longValue();
        assertEquals(1, currentQuestions, "the trigger closed the previous question version");

        OffsetDateTime stillCurrent = toOffsetDateTime(em.createNativeQuery(
                "SELECT effective_to FROM survey.translations WHERE id = ?1")
                .setParameter(1, translation).getSingleResult());
        assertEquals(MAX_SENTINEL, stillCurrent, "versioning a question must not close its translations");

        UUID stillAttached = (UUID) em.createNativeQuery(
                "SELECT t.element_key FROM survey.translations t JOIN survey.questions q ON q.question_key = t.element_key "
                        + "WHERE t.id = ?1 AND q.effective_to = ?2").setParameter(1, translation)
                .setParameter(2, MAX_SENTINEL).getSingleResult();
        assertEquals(target, stillAttached, "the translation joins the new version by question_key");
    }

    @Test
    @TestTransaction
    void aRetiredTranslation_leavesTheTargetFreeForANewCurrentRow() {
        UUID target = questionKey();
        Integer first = insert(target, "placeholder", "es-419", "Escriba aquí", HASH);
        em.createNativeQuery("UPDATE survey.translations SET effective_to = now() WHERE id = ?1")
                .setParameter(1, first).executeUpdate();
        em.flush();

        Integer replacement = insert(target, "placeholder", "es-419", "Escriba su respuesta", HASH);
        assertTrue(replacement > 0, "the partial unique index constrains current rows only");
    }

    /** The driver may hand back either temporal type for a timestamptz; normalize as the sibling specs do. */
    private static OffsetDateTime toOffsetDateTime(Object value) {
        if (value instanceof OffsetDateTime odt) {
            return odt;
        }
        if (value instanceof Instant instant) {
            return instant.atOffset(ZoneOffset.UTC);
        }
        throw new IllegalArgumentException("Unexpected temporal type: " + (value == null ? "null" : value.getClass()));
    }

    private UUID questionKey() {
        return (UUID) em.createNativeQuery("SELECT question_key FROM survey.questions WHERE id = ?1")
                .setParameter(1, ScdFixtureIds.questionId(em)).getSingleResult();
    }

    private Integer insert(UUID target, String field, String language, String value, String hash) {
        return insert(target, field, language, value, hash, "questions");
    }

    private Integer insert(UUID target, String field, String language, String value, String hash, String elementType) {
        Integer id = ((Number) em.createNativeQuery("SELECT NEXTVAL('survey.translations_seq')")
                .getSingleResult()).intValue();
        em.createNativeQuery(
                "INSERT INTO survey.translations (id, survey_id, element_type, element_key, field, language, value, "
                        + "source_hash, translation_key) VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, gen_random_uuid())")
                .setParameter(1, id).setParameter(2, ScdFixtureIds.surveyId(em)).setParameter(3, elementType)
                .setParameter(4, target).setParameter(5, field).setParameter(6, language).setParameter(7, value)
                .setParameter(8, hash).executeUpdate();
        em.flush();
        return id;
    }

    private Integer insertVersion(UUID target, String field, String language, String value, String hash,
                                  Integer durableId, UUID key, int version) {
        Integer id = ((Number) em.createNativeQuery("SELECT NEXTVAL('survey.translations_seq')")
                .getSingleResult()).intValue();
        em.createNativeQuery(
                "INSERT INTO survey.translations (id, survey_id, element_type, element_key, field, language, value, "
                        + "source_hash, translation_id, translation_key, version, effective_from, effective_to) "
                        + "VALUES (?1, ?2, 'questions', ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, now(), ?11)")
                .setParameter(1, id).setParameter(2, ScdFixtureIds.surveyId(em)).setParameter(3, target)
                .setParameter(4, field).setParameter(5, language).setParameter(6, value).setParameter(7, hash)
                .setParameter(8, durableId).setParameter(9, key).setParameter(10, version)
                .setParameter(11, MAX_SENTINEL).executeUpdate();
        em.flush();
        return id;
    }
}
