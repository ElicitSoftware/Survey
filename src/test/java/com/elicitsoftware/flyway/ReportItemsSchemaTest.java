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
package com.elicitsoftware.flyway;

import com.elicitsoftware.PostgresTestResource;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * UC-013 (Survey V023): the report-marks table and its translation hook, as the database
 * enforces them.
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class ReportItemsSchemaTest {

    private static final String INSERT = """
            INSERT INTO survey.report_items (id, survey_id, report_type, steps_sections_id, question_id, sections_question_id, label, display_order)
            VALUES (nextval('survey.report_items_seq'), 1, ?1, ?2, ?3, ?4, ?5, 0)
            """;

    @Inject
    EntityManager em;

    private void insert(String type, Integer ss, Integer q, Integer sq, String label) {
        em.createNativeQuery(INSERT).setParameter(1, type).setParameter(2, ss).setParameter(3, q)
                .setParameter(4, sq).setParameter(5, label).executeUpdate();
    }

    @Test
    @TestTransaction
    void given_oneElementAndAKnownType_when_insert_then_accepted() {
        // UC-013 BR-001, BR-002: one element, either report, a key minted by default
        assertDoesNotThrow(() -> insert("RESPONDENT", null, 7, null, "Your height"));
        assertDoesNotThrow(() -> insert("ADMINISTRATOR", 3, null, null, null));
        assertDoesNotThrow(() -> insert("RESPONDENT", null, null, 12, null));
    }

    @Test
    @TestTransaction
    void given_noElementOrTwo_when_insert_then_refused() {
        // UC-013 BR-001: exactly one, strictly
        assertThrows(PersistenceException.class, () -> insert("RESPONDENT", null, null, null, null));
    }

    @Test
    @TestTransaction
    void given_twoElements_when_insert_then_refused() {
        assertThrows(PersistenceException.class, () -> insert("RESPONDENT", 3, 7, null, null));
    }

    @Test
    @TestTransaction
    void given_unknownReportType_when_insert_then_refused() {
        // UC-013 BR-002
        assertThrows(PersistenceException.class, () -> insert("COORDINATOR", null, 7, null, null));
    }

    @Test
    @TestTransaction
    void given_labelTranslation_when_insert_then_elementTypeAccepted() {
        // UC-013 BR-004: translations_element_type_ck now admits report_items
        assertDoesNotThrow(() -> em.createNativeQuery("""
                INSERT INTO survey.translations (id, survey_id, element_type, element_key, field, language, value, source_hash, translation_key)
                VALUES (nextval('survey.translations_seq'), 1, 'report_items', ?1, 'label', 'es-419', 'Su estatura', repeat('0', 64), ?2)
                """).setParameter(1, UUID.randomUUID()).setParameter(2, UUID.randomUUID()).executeUpdate());
        assertThrows(PersistenceException.class, () -> em.createNativeQuery("""
                INSERT INTO survey.translations (id, survey_id, element_type, element_key, field, language, value, source_hash, translation_key)
                VALUES (nextval('survey.translations_seq'), 1, 'report_marks', ?1, 'label', 'es-419', 'x', repeat('0', 64), ?2)
                """).setParameter(1, UUID.randomUUID()).setParameter(2, UUID.randomUUID()).executeUpdate());
    }
}
