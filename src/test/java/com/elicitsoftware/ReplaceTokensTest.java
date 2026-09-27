package com.elicitsoftware;

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

import org.junit.jupiter.api.Test;

import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Token substitution, now that a token is written {@code <NAME>} inside a placeholder's phrase.
 *
 * <p>The brackets are what make this tractable. Substitution used to be
 * {@code string.contains(key)} over a whole segment of the text, which could not tell the token
 * from an ordinary word, so prose that happened to contain the token's letters was rewritten
 * instead of the placeholder.</p>
 */
class ReplaceTokensTest {

    private static TreeMap<String, String> values(String... pairs) {
        TreeMap<String, String> values = new TreeMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            values.put(pairs[i], pairs[i + 1]);
        }
        return values;
    }

    @Test
    void aPhraseIsFilledWhereItsTokenHasAValue() {
        assertEquals("How old is Alice?",
                QuestionManager.replaceTokens("How old is {<NAME>|this person}?", values("NAME", "Alice")));
    }

    @Test
    void theAuthoredPhraseShapeIsKept() {
        assertEquals("What is Alice's gender?",
                QuestionManager.replaceTokens("What is {<NAME>'s|this person's} gender?", values("NAME", "Alice")));
        assertEquals("Has Alice been diagnosed",
                QuestionManager.replaceTokens("{Has <CS2> been|Were you} diagnosed", values("CS2", "Alice")));
    }

    @Test
    void aMissingValueFallsBackToTheDefault() {
        assertEquals("How old is this person?",
                QuestionManager.replaceTokens("How old is {<NAME>|this person}?", values()));
        assertEquals("What is this person's gender?",
                QuestionManager.replaceTokens("What is {<NAME>'s|this person's} gender?", values("OTHER", "x")));
    }

    /** The defect the brackets exist to stop: prose is prose, even when it reads like a token. */
    @Test
    void proseIsNeverMistakenForAToken() {
        assertEquals("What is the name of person {Q#}?",
                QuestionManager.replaceTokens("What is the name of person {Q#}?", values("NAME", "Alice")));
        assertEquals("Please write your name below. Alice",
                QuestionManager.replaceTokens("Please write your name below. {<NAME>|this person}",
                        values("NAME", "Alice")));
    }

    /** A token is a slot, not a prefix: NAME must not be filled by a rule carrying SURNAME. */
    @Test
    void oneTokenIsNotAnotherTokensPrefix() {
        assertEquals("Ms Smith and this person",
                QuestionManager.replaceTokens("Ms {<SURNAME>|Doe} and {<NAME>|this person}",
                        values("SURNAME", "Smith")));
        // S1 is a prefix of S10; only the rule that carries S10 may fill <S10>.
        assertEquals("step 1 of this",
                QuestionManager.replaceTokens("step {<S1>|this} of {<S10>|this}", values("S1", "1")));
    }

    /** The runtime counters are filled later, against the answer's own key, so they survive. */
    @Test
    void builtInCountersAreLeftAlone() {
        assertEquals("Person {Q#} in step {S#}",
                QuestionManager.replaceTokens("Person {Q#} in step {S#}", values("NAME", "Alice")));
    }

    @Test
    void aSubstitutedValueIsHtmlEscaped() {
        assertEquals("Hello &lt;script&gt;",
                QuestionManager.replaceTokens("Hello {<NAME>|friend}", values("NAME", "<script>")));
    }
}
