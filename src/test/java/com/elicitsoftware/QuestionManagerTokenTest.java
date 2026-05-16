package com.elicitsoftware;

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

import com.elicitsoftware.QuestionManager;
import org.junit.jupiter.api.Test;

import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QuestionManagerTokenTest {

    // Matrix row 10: TEXT action — replaceTokens unit tests (no DB, no Quarkus)

    @Test
    void given_matchedToken_when_replaceTokens_then_valueSubstituted() {
        TreeMap<String, String> values = new TreeMap<>();
        values.put("NAME", "Alice");
        String result = QuestionManager.replaceTokens("Hello {NAME|friend}", values);
        assertEquals("Hello Alice", result);
    }

    @Test
    void given_unmatchedToken_when_replaceTokens_then_defaultKept() {
        TreeMap<String, String> values = new TreeMap<>();
        String result = QuestionManager.replaceTokens("Hello {NAME|friend}", values);
        assertEquals("Hello friend", result);
    }

    @Test
    void given_multipleTokens_when_replaceTokens_then_allSubstituted() {
        TreeMap<String, String> values = new TreeMap<>();
        values.put("EMAIL", "alice@example.com");
        values.put("PHONE", "555-1234");
        String result = QuestionManager.replaceTokens("Email: {EMAIL|none} Phone: {PHONE|none}", values);
        assertEquals("Email: alice@example.com Phone: 555-1234", result);
    }

    @Test
    void given_possessiveSName_when_replaceTokens_then_normalized() {
        TreeMap<String, String> values = new TreeMap<>();
        String result = QuestionManager.replaceTokens("Dennis's card", values);
        assertEquals("Dennis' card", result);
    }

    @Test
    void given_hersPossessive_when_replaceTokens_then_normalized() {
        TreeMap<String, String> values = new TreeMap<>();
        String result = QuestionManager.replaceTokens("This is her's book", values);
        assertEquals("This is her book", result);
    }

    @Test
    void given_hisPossessive_when_replaceTokens_then_normalized() {
        // Line 102: his's → his
        TreeMap<String, String> values = new TreeMap<>();
        String result = QuestionManager.replaceTokens("This is his's car", values);
        assertEquals("This is his car", result);
    }

    @Test
    void given_YoursPossessive_when_replaceTokens_then_normalized() {
        // Line 103: Your's → Your
        TreeMap<String, String> values = new TreeMap<>();
        String result = QuestionManager.replaceTokens("This is Your's to keep", values);
        assertEquals("This is Your to keep", result);
    }

    @Test
    void given_unmatchedKeyInValues_when_replaceTokens_then_defaultKept() {
        // Exercises the branch where token key is in values map but not in text segment
        TreeMap<String, String> values = new TreeMap<>();
        values.put("EMAIL", "alice@example.com");
        String result = QuestionManager.replaceTokens("Hello {NAME|friend}", values);
        assertEquals("Hello friend", result);
    }
}
