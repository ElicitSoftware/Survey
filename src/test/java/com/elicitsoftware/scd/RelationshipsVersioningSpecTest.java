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
 * Executable spec for Type 2 versioning of {@code survey.relationships} —
 * research/Kimball_type_2.md, "Schema Changes Per Table &rarr; relationships" and
 * "Versioning Workflows &rarr; Changing conditional logic". Covers the
 * {@code downstream_s_id -> downstream_ss_id} rename and all 5 durable-key FK columns.
 */
@QuarkusTest
@Disabled("Enable once the relationships Type 2 migration lands — see research/Kimball_type_2.md 'Schema Changes Per Table -> relationships'")
class RelationshipsVersioningSpecTest {

    @Inject
    EntityManager em;

    static final OffsetDateTime MAX_SENTINEL = OffsetDateTime.parse("9999-12-31T23:59:59+00:00");

    @Test
    void migration_renamesDownstreamSIdAndAddsAllFiveVersionCompanions() {
        long renamedColumnGone = ((Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='survey' "
                        + "AND table_name='relationships' AND column_name='downstream_s_id'").getSingleResult()).longValue();
        assertEquals(0, renamedColumnGone, "downstream_s_id must be renamed to downstream_ss_id");

        long cols = ((Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='survey' AND table_name='relationships' "
                        + "AND column_name IN ('relationship_id','version','effective_from','effective_to','is_draft',"
                        + "'downstream_ss_id','upstream_step_version','upstream_sq_version','downstream_step_version',"
                        + "'downstream_ss_version','downstream_sq_version')").getSingleResult()).longValue();
        assertEquals(11, cols);
    }

    @Test
    @TestTransaction
    void changingConditionalLogic_isInvisibleToARespondentPinnedBeforeThePublish() {
        Integer relationshipSurrogateId = ScdFixtureIds.relationshipId(em);
        Integer durableRelationshipId = (Integer) em.createNativeQuery(
                "SELECT relationship_id FROM survey.relationships WHERE id = ?1")
                .setParameter(1, relationshipSurrogateId).getSingleResult();
        int currentVersion = ((Number) em.createNativeQuery("SELECT version FROM survey.relationships WHERE id = ?1")
                .setParameter(1, relationshipSurrogateId).getSingleResult()).intValue();
        OffsetDateTime firstAccessDt = OffsetDateTime.now();
        OffsetDateTime publishInstant = OffsetDateTime.now().plusSeconds(5);

        em.createNativeQuery("UPDATE survey.relationships SET effective_to = ?2 WHERE relationship_id = ?1 AND effective_to = ?3")
                .setParameter(1, durableRelationshipId).setParameter(2, publishInstant).setParameter(3, MAX_SENTINEL).executeUpdate();
        em.createNativeQuery(
                "INSERT INTO survey.relationships (id, relationship_id, version, survey_id, upstream_step_id, upstream_sq_id, "
                        + "downstream_step_id, downstream_ss_id, downstream_sq_id, operator_id, action_id, reference_value, "
                        + "effective_from, effective_to, is_draft) "
                        + "SELECT nextval('survey.relationships_seq'), relationship_id, ?2, survey_id, upstream_step_id, upstream_sq_id, "
                        + "downstream_step_id, downstream_ss_id, downstream_sq_id, operator_id, action_id, 'FALSE', "
                        + "?3, ?4, false FROM survey.relationships WHERE relationship_id = ?1 AND version = ?5")
                .setParameter(1, durableRelationshipId).setParameter(2, currentVersion + 1).setParameter(3, publishInstant)
                .setParameter(4, MAX_SENTINEL).setParameter(5, currentVersion).executeUpdate();
        em.flush();

        String referenceValueAtFirstAccess = (String) em.createNativeQuery(
                "SELECT reference_value FROM survey.relationships WHERE relationship_id = ?1 "
                        + "AND effective_from <= ?2 AND effective_to > ?2")
                .setParameter(1, durableRelationshipId).setParameter(2, firstAccessDt).getSingleResult();
        String referenceValueNow = (String) em.createNativeQuery(
                "SELECT reference_value FROM survey.relationships WHERE relationship_id = ?1 "
                        + "AND effective_from <= ?2 AND effective_to > ?2")
                .setParameter(1, durableRelationshipId).setParameter(2, OffsetDateTime.now().plusSeconds(10)).getSingleResult();

        assertEquals("TRUE", referenceValueAtFirstAccess,
                "A respondent whose firstAccessDt predates the logic change must continue to evaluate the old operator/reference_value");
        assertEquals("FALSE", referenceValueNow,
                "A NOW() query must resolve the new operator/reference_value");
    }
}
