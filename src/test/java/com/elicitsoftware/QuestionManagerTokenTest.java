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

/**
 * UC-002: Answer Survey Questions — replaceTokens() backs the TEXT action's
 * display-text token substitution (QuestionManager.buildDownstreamQuestions/replaceText).
 */
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

    // An apostrophe in a respondent's answer must read as typed in a plain-text label:
    // escaping it there displayed "Leon&#39;s mother".
    @Test
    void given_apostropheInValue_when_replaceTokensForPlainText_then_notEscaped() {
        TreeMap<String, String> values = new TreeMap<>();
        values.put("NAME", "Leon's");
        String result = QuestionManager.replaceTokens("Please indicate if {NAME|your} mother is still living.", values);
        assertEquals("Please indicate if Leon's mother is still living.", result);
    }

    @Test
    void given_markupInValue_when_replaceTokensForPlainText_then_notEscaped() {
        TreeMap<String, String> values = new TreeMap<>();
        values.put("NAME", "A & B <c>");
        String result = QuestionManager.replaceTokens("Hello {NAME|friend}", values, false);
        assertEquals("Hello A & B <c>", result);
    }

    // An HTML question's text goes into the page as markup, so the answer is still escaped there.
    @Test
    void given_markupInValue_when_replaceTokensForHtml_then_escaped() {
        TreeMap<String, String> values = new TreeMap<>();
        values.put("NAME", "<img src=x onerror=alert(1)>Leon's");
        String result = QuestionManager.replaceTokens("<b>Hello {NAME|friend}</b>", values, true);
        assertEquals("<b>Hello &lt;img src=x onerror=alert(1)&gt;Leon&#39;s</b>", result);
    }

    // The FHHS shape: a rule's default value is itself a template holding the apostrophe
    // ("{G1's|your} mother"), spliced into a plain-text label through another token.
    @Test
    void given_apostropheInNestedDefault_when_replaceTokensForPlainText_then_notEscaped() {
        TreeMap<String, String> values = new TreeMap<>();
        values.put("G1", "Bob");
        values.put("S1", "{G1's|your} mother");
        String result = QuestionManager.replaceTokens("Please indicate if {S1} is still living.", values);
        assertEquals("Please indicate if Bob's mother is still living.", result);
    }
}
