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
 * Executable spec for Type 2 versioning of {@code survey.select_groups} —
 * research/Kimball_type_2.md, "Schema Changes Per Table &rarr; select_groups". Same
 * pattern as {@link QuestionsVersioningSpecTest}; see that class for the fully
 * annotated reference implementation.
 */
@QuarkusTest
@Disabled("Enable once the select_groups Type 2 migration lands — see research/Kimball_type_2.md 'Schema Changes Per Table -> select_groups'")
class SelectGroupsVersioningSpecTest {

    @Inject
    EntityManager em;

    static final OffsetDateTime MAX_SENTINEL = OffsetDateTime.parse("9999-12-31T23:59:59+00:00");

    @Test
    void migration_addsExpectedColumnsAndSequence() {
        long columns = ((Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = 'survey' "
                        + "AND table_name = 'select_groups' AND column_name IN "
                        + "('select_group_id','version','effective_from','effective_to','is_draft','published_by','published_comment')")
                .getSingleResult()).longValue();
        assertEquals(7, columns);
        long seq = ((Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM information_schema.sequences WHERE sequence_schema = 'survey' "
                        + "AND sequence_name = 'select_groups_durable_seq'").getSingleResult()).longValue();
        assertEquals(1, seq);
    }

    @Test
    @TestTransaction
    void onlyOneCurrentRowPerDurableId_isEnforced() {
        Integer selectGroupId = ScdFixtureIds.selectGroupId(em);
        assertThrows(PersistenceException.class, () -> {
            em.createNativeQuery(
                    "INSERT INTO survey.select_groups (id, select_group_id, version, survey_id, name, data_type, "
                            + "effective_from, effective_to, is_draft) "
                            + "SELECT nextval('survey.select_groups_seq'), select_group_id, version + 1, survey_id, name, data_type, "
                            + "?2, ?3, false FROM survey.select_groups WHERE id = ?1")
                    .setParameter(1, selectGroupId).setParameter(2, OffsetDateTime.now()).setParameter(3, MAX_SENTINEL)
                    .executeUpdate();
            em.flush();
        }, "select_groups_one_current_un must reject a second current row for the same select_group_id");
    }

    @Test
    @TestTransaction
    void versioning_doesNotRequireTouchingSelectItemsJoinRow() {
        // Core "FK cascade problem" claim, applied to the select_groups/select_items pair:
        // renaming the group must not require any write to select_items.
        Integer surrogateId = ScdFixtureIds.selectGroupId(em);
        Integer selectItemId = ScdFixtureIds.selectItemId(em);
        Integer durableGroupId = (Integer) em.createNativeQuery(
                "SELECT select_group_id FROM survey.select_groups WHERE id = ?1")
                .setParameter(1, surrogateId).getSingleResult();
        int currentVersion = ((Number) em.createNativeQuery("SELECT version FROM survey.select_groups WHERE id = ?1")
                .setParameter(1, surrogateId).getSingleResult()).intValue();
        OffsetDateTime publishInstant = OffsetDateTime.now();

        em.createNativeQuery("UPDATE survey.select_groups SET effective_to = ?2 WHERE select_group_id = ?1 AND effective_to = ?3")
                .setParameter(1, durableGroupId).setParameter(2, publishInstant).setParameter(3, MAX_SENTINEL).executeUpdate();
        em.createNativeQuery(
                "INSERT INTO survey.select_groups (id, select_group_id, version, survey_id, name, data_type, "
                        + "effective_from, effective_to, is_draft) "
                        + "SELECT nextval('survey.select_groups_seq'), select_group_id, ?2, survey_id, 'ScdSelectGroupRenamed', data_type, "
                        + "?3, ?4, false FROM survey.select_groups WHERE select_group_id = ?1 AND version = ?5")
                .setParameter(1, durableGroupId).setParameter(2, currentVersion + 1).setParameter(3, publishInstant)
                .setParameter(4, MAX_SENTINEL).setParameter(5, currentVersion).executeUpdate();
        em.flush();

        Integer selectItemGroupIdAfter = (Integer) em.createNativeQuery(
                "SELECT select_group_id FROM survey.select_items WHERE id = ?1")
                .setParameter(1, selectItemId).getSingleResult();
        assertEquals(durableGroupId, selectItemGroupIdAfter,
                "select_items.select_group_id (durable) must be unchanged by versioning the referenced group");

        String currentGroupName = (String) em.createNativeQuery(
                "SELECT sg.name FROM survey.select_items si JOIN survey.select_groups sg ON sg.select_group_id = si.select_group_id "
                        + "WHERE si.id = ?1 AND sg.effective_from <= ?2 AND sg.effective_to > ?2")
                .setParameter(1, selectItemId).setParameter(2, OffsetDateTime.now().plusSeconds(1)).getSingleResult();
        assertEquals("ScdSelectGroupRenamed", currentGroupName,
                "The durable-key join from select_items must resolve to the new group version at NOW() with zero writes to select_items");
    }
}
