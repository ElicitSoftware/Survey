package com.elicitsoftware.flyway;

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
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * UC-001: the respondent's login credential is stored as {@code survey.respondents.access_code}.
 * <p>
 * Every {@code @QuarkusTest} boots a fresh database, so this covers the greenfield
 * {@code db/migration} path through V014. The upgrade path is covered by
 * {@link ManualSchemaMigratorUpgradeTest}.
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class AccessCodeSchemaTest {

    @Inject
    EntityManager em;

    private long count(String sql, String... params) {
        var query = em.createNativeQuery(sql);
        for (int i = 0; i < params.length; i++) {
            query.setParameter(i + 1, params[i]);
        }
        return ((Number) query.getSingleResult()).longValue();
    }

    private long columnCount(String table, String column) {
        return count("SELECT COUNT(*) FROM information_schema.columns "
                + "WHERE table_schema = 'survey' AND table_name = ?1 AND column_name = ?2", table, column);
    }

    @Test
    void respondentsHasAccessCodeColumnAndNoTokenColumn() {
        assertEquals(1, columnCount("respondents", "access_code"));
        assertEquals(0, columnCount("respondents", "token"));
    }

    @Test
    void questionTextPlaceholderColumnIsNotRenamed() {
        // relationships.token is the {KEY|default} question-text placeholder, not the credential.
        assertEquals(1, columnCount("relationships", "token"));
    }

    @Test
    void accessCodeUniqueConstraintIsRenamed() {
        assertEquals(1, count("SELECT COUNT(*) FROM pg_constraint WHERE conname = ?1",
                "respondents_access_code_un"));
        assertEquals(0, count("SELECT COUNT(*) FROM pg_constraint WHERE conname = ?1",
                "respondents_token_un"));
    }

    @Test
    void accessCodeIndexesAreRenamed() {
        for (String index : new String[]{"idx_respondents_access_code", "idx_respondents_access_code_active",
                "idx_respondents_survey_access_code"}) {
            assertEquals(1, count("SELECT COUNT(*) FROM pg_indexes WHERE schemaname = 'survey' AND indexname = ?1",
                    index), "index " + index + " must exist");
        }
        assertEquals(0, count("SELECT COUNT(*) FROM pg_indexes WHERE schemaname = 'survey' "
                + "AND tablename = 'respondents' AND indexname LIKE '%token%'"));
    }

    @Test
    void fixtureRespondentIsFoundByAccessCode() {
        // V9005.5 inserts Tess Tester with access code 'test1' on survey 1.
        assertEquals(1, count("SELECT COUNT(*) FROM survey.respondents WHERE survey_id = 1 AND access_code = ?1",
                "test1"));
    }
}
