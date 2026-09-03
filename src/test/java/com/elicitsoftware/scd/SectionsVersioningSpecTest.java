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
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Executable spec for Type 2 versioning of {@code survey.sections} —
 * research/Kimball_type_2.md, "Schema Changes Per Table &rarr; sections". Same
 * versioning/constraint/display_order-NUMERIC pattern as {@link StepsVersioningSpecTest}
 * (see that class for the decimal-midpoint-insertion round-trip test); this class covers
 * the sections-specific constraint checks only.
 */
@QuarkusTest
@Disabled("Enable once the sections Type 2 migration lands — see research/Kimball_type_2.md 'Schema Changes Per Table -> sections'")
class SectionsVersioningSpecTest {

    @Inject
    EntityManager em;

    static final OffsetDateTime MAX_SENTINEL = OffsetDateTime.parse("9999-12-31T23:59:59+00:00");

    @Test
    void migration_addsExpectedColumnsAndNumericDisplayOrder() {
        long columns = ((Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = 'survey' AND table_name = 'sections' "
                        + "AND column_name IN ('section_id','version','effective_from','effective_to','is_draft','published_by','published_comment')")
                .getSingleResult()).longValue();
        assertEquals(7, columns);

        String dataType = (String) em.createNativeQuery(
                "SELECT data_type FROM information_schema.columns WHERE table_schema='survey' "
                        + "AND table_name='sections' AND column_name='display_order'").getSingleResult();
        assertEquals("numeric", dataType);
    }

    @Test
    @TestTransaction
    void onlyOneCurrentRowPerDurableId_isEnforced() {
        Integer sectionId = ScdFixtureIds.sectionId(em);
        assertThrows(PersistenceException.class, () -> {
            em.createNativeQuery(
                    "INSERT INTO survey.sections (id, section_id, version, survey_id, display_order, name, dimension_name, "
                            + "effective_from, effective_to, is_draft) "
                            + "SELECT nextval('survey.sections_seq'), section_id, version + 1, survey_id, display_order, name, dimension_name, "
                            + "?2, ?3, false FROM survey.sections WHERE id = ?1")
                    .setParameter(1, sectionId).setParameter(2, OffsetDateTime.now()).setParameter(3, MAX_SENTINEL)
                    .executeUpdate();
            em.flush();
        }, "sections_one_current_un must reject a second current row for the same section_id");
    }

    @Test
    @TestTransaction
    void renamingSection_doesNotDisturbTheStepsSectionsJoinRow() {
        Integer surrogateId = ScdFixtureIds.sectionId(em);
        Integer stepsSectionsId = ScdFixtureIds.stepsSectionsId(em);
        Integer durableSectionId = (Integer) em.createNativeQuery(
                "SELECT section_id FROM survey.sections WHERE id = ?1").setParameter(1, surrogateId).getSingleResult();
        int currentVersion = ((Number) em.createNativeQuery("SELECT version FROM survey.sections WHERE id = ?1")
                .setParameter(1, surrogateId).getSingleResult()).intValue();
        OffsetDateTime publishInstant = OffsetDateTime.now();

        em.createNativeQuery("UPDATE survey.sections SET effective_to = ?2 WHERE section_id = ?1 AND effective_to = ?3")
                .setParameter(1, durableSectionId).setParameter(2, publishInstant).setParameter(3, MAX_SENTINEL).executeUpdate();
        em.createNativeQuery(
                "INSERT INTO survey.sections (id, section_id, version, survey_id, display_order, name, dimension_name, "
                        + "effective_from, effective_to, is_draft) "
                        + "SELECT nextval('survey.sections_seq'), section_id, ?2, survey_id, display_order, 'ScdSectionRenamed', dimension_name, "
                        + "?3, ?4, false FROM survey.sections WHERE section_id = ?1 AND version = ?5")
                .setParameter(1, durableSectionId).setParameter(2, currentVersion + 1).setParameter(3, publishInstant)
                .setParameter(4, MAX_SENTINEL).setParameter(5, currentVersion).executeUpdate();
        em.flush();

        Integer sectionIdOnJoinRow = (Integer) em.createNativeQuery(
                "SELECT section_id FROM survey.steps_sections WHERE id = ?1")
                .setParameter(1, stepsSectionsId).getSingleResult();
        assertEquals(durableSectionId, sectionIdOnJoinRow, "steps_sections.section_id (durable) must be unchanged");
    }
}
