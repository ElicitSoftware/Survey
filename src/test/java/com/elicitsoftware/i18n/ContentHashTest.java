package com.elicitsoftware.i18n;

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

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

import com.elicitsoftware.PostgresTestResource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The shared hash vector (docs/research/i18n_survey.md section 3.2).
 * <p>
 * Three implementations have to agree on this number -- Java here, Java in Author when it writes a
 * translation, and PostgreSQL in the review query's join -- or a translation is either served when
 * it should not be or hidden when it should not be. The SQL case is checked against the database
 * rather than asserted from memory, because that is the one that is easy to get subtly wrong
 * ({@code convert_to} versus the server encoding, {@code encode} versus {@code digest}).
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class ContentHashTest {

    /** The vector: a real question text, with a token and an apostrophe in it. */
    private static final String VECTOR_TEXT = "Has {S1's mother|your mother} ever been diagnosed with cancer?";

    @Inject
    EntityManager em;

    @Test
    void javaAndPostgresAgreeOnTheSameString() {
        String java = ContentHash.of(VECTOR_TEXT);
        String sql = (String) em.createNativeQuery(
                        "SELECT encode(sha256(convert_to(?1, 'UTF8')), 'hex')")
                .setParameter(1, VECTOR_TEXT).getSingleResult();
        assertEquals(sql, java, "the SQL form in the review query must match ContentHash.of");
        assertEquals(64, java.length());
        assertEquals(java.toLowerCase(), java, "lower-case hex, as the column stores it");
    }

    @Test
    void agreementHoldsForNonAsciiAndForWhitespace() {
        for (String text : new String[]{"¿Ha tenido cáncer?", "نص عربي", "  leading and trailing  ",
                "line\nbreak", "", "emoji 🧬 in prose"}) {
            String sql = (String) em.createNativeQuery(
                            "SELECT encode(sha256(convert_to(?1, 'UTF8')), 'hex')")
                    .setParameter(1, text).getSingleResult();
            assertEquals(sql, ContentHash.of(text), "disagreement on: " + text);
        }
    }

    @Test
    void theHashIsOfTheExactStoredString() {
        // No trimming and no normalisation: a base text that gained a trailing space is a different
        // base text, and its translation is stale until someone looks at it.
        assertFalse(ContentHash.of("Question?").equals(ContentHash.of("Question? ")));
        assertFalse(ContentHash.of("Question?").equals(ContentHash.of("question?")));
    }

    @Test
    void matchesComparesAgainstTheCurrentBaseText() {
        String hash = ContentHash.of("Original wording");
        assertTrue(ContentHash.matches(hash, "Original wording"));
        assertFalse(ContentHash.matches(hash, "Reworded"), "an edited base text makes it stale");
        assertFalse(ContentHash.matches(null, "Original wording"), "a row with no hash is never fresh");
    }

    @Test
    void nullTextHasNoHash() {
        assertNull(ContentHash.of(null));
    }
}
