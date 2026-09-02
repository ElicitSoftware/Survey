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
 * Executable spec for Type 2 versioning of {@code survey.select_items} —
 * research/Kimball_type_2.md, "Schema Changes Per Table &rarr; select_items". Covers
 * the {@code group_id -> select_group_id} rename/backfill and the
 * {@code select_group_version} FK-companion column, on top of the same
 * versioning/constraint pattern as {@link QuestionsVersioningSpecTest}.
 */
@QuarkusTest
@Disabled("Enable once the select_items Type 2 migration lands — see research/Kimball_type_2.md 'Schema Changes Per Table -> select_items'")
class SelectItemsVersioningSpecTest {

    @Inject
    EntityManager em;

    static final OffsetDateTime MAX_SENTINEL = OffsetDateTime.parse("9999-12-31T23:59:59+00:00");

    @Test
    void migration_renamesGroupIdAndAddsVersionCompanionColumn() {
        long groupIdColumnGone = ((Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='survey' "
                        + "AND table_name='select_items' AND column_name='group_id'").getSingleResult()).longValue();
        assertEquals(0, groupIdColumnGone, "group_id must be renamed to select_group_id");

        long durableAndVersionCols = ((Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='survey' AND table_name='select_items' "
                        + "AND column_name IN ('select_item_id','select_group_id','select_group_version','version',"
                        + "'effective_from','effective_to','is_draft')").getSingleResult()).longValue();
        assertEquals(7, durableAndVersionCols);
    }

    @Test
    void selectGroupVersionCompanionColumn_isPinnedToZeroByCheckConstraint() {
        Integer selectItemId = ScdFixtureIds.selectItemId(em);
        Integer version = (Integer) em.createNativeQuery(
                "SELECT select_group_version FROM survey.select_items WHERE id = ?1")
                .setParameter(1, selectItemId).getSingleResult();
        assertEquals(0, version, "select_group_version is a fixed entity-existence pin, always 0");
    }

    @Test
    @TestTransaction
    void selectGroupVersionCompanion_rejectsAnyNonZeroValue() {
        Integer selectItemId = ScdFixtureIds.selectItemId(em);
        assertThrows(PersistenceException.class, () ->
                em.createNativeQuery("UPDATE survey.select_items SET select_group_version = 1 WHERE id = ?1")
                        .setParameter(1, selectItemId).executeUpdate(),
                "select_items_select_group_version_ck must reject any non-zero value");
    }

    @Test
    @TestTransaction
    void fkToSelectGroups_targetsTheDurableIdVersionZeroComposite() {
        // (select_group_id, select_group_version) FK -> select_groups(select_group_id, version)
        // must reject an orphaned durable group id — the doc's entity-existence check.
        Integer selectItemId = ScdFixtureIds.selectItemId(em);
        assertThrows(PersistenceException.class, () ->
                em.createNativeQuery("UPDATE survey.select_items SET select_group_id = -999999 WHERE id = ?1")
                        .setParameter(1, selectItemId).executeUpdate(),
                "select_items_select_group_id_fkey must reject a select_group_id with no (id, version=0) row in select_groups");
    }
}
