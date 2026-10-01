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
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    // ---- UC-002 BR-011: an authored value may be a placeholder in its own right ---------------

    private static java.util.Set<String> authored(String... tokens) {
        return java.util.Set.of(tokens);
    }

    /**
     * The Family History Survey's own shape: S1's value is the phrase "{@code {<G1>'s|your} mother}"
     * and G1's value is the patient's name, so the sentence needs two rounds.
     */
    @Test
    void anAuthoredValueThatIsItselfAPlaceholderIsResolved() {
        assertEquals("Please indicate if Dennis' mother is still living.",
                QuestionManager.replaceTokens("Please indicate if {<S1>|you} is still living.",
                        values("S1", "{<G1>'s|your} mother", "G1", "Dennis"), authored("S1")));
    }

    @Test
    void anAuthoredValuesOwnDefaultAppliesWhenItsTokenHasNoValue() {
        assertEquals("Please indicate if your mother is still living.",
                QuestionManager.replaceTokens("Please indicate if {<S1>|you} is still living.",
                        values("S1", "{<G1>'s|your} mother"), authored("S1")));
    }

    @Test
    void nestingIsBoundedRatherThanEndless() {
        // A value that refers to itself must terminate, not hang the page draw.
        String out = QuestionManager.replaceTokens("Ask {<A>|someone}.",
                values("A", "{<A>'s|their} friend"), authored("A"));
        assertTrue(out.startsWith("Ask ") && out.endsWith("."), out);
    }

    /**
     * Respondent text is spliced in once and never re-read, so free text that happens to hold braces
     * is shown as typed rather than treated as a placeholder.
     */
    @Test
    void respondentTextIsNeverRescannedForPlaceholders() {
        assertEquals("You said {foo|bar}.",
                QuestionManager.replaceTokens("You said {<ANSWER>|nothing}.",
                        values("ANSWER", "{foo|bar}"), authored()));
    }

    @Test
    void respondentTextIsStillEscapedButAnAuthoredValueIsNot() {
        assertEquals("Hello &lt;b&gt;Bob&lt;/b&gt;",
                QuestionManager.replaceTokens("Hello {<NAME>|friend}", values("NAME", "<b>Bob</b>"), authored()));
        // An authored value has already had its own substitutions escaped; escaping it again would
        // double-escape them, and would destroy the <TOKEN> brackets before they could be read.
        assertEquals("Hello <b>your mother</b>",
                QuestionManager.replaceTokens("Hello {<S1>|friend}", values("S1", "<b>your mother</b>"), authored("S1")));
    }

    @Test
    void respondentTextInsideAnAuthoredValueIsStillEscaped() {
        // The escaping runs before the possessive tidy, so "s's" is not collapsed here: the character
        // in front of the apostrophe is the ';' of an entity, not an 's'. Escaped either way, which is
        // what this is checking.
        assertEquals("Ask &lt;b&gt;Dennis&lt;/b&gt;'s mother.",
                QuestionManager.replaceTokens("Ask {<S1>|them}.",
                        values("S1", "{<G1>'s|your} mother", "G1", "<b>Dennis</b>"), authored("S1")));
        assertEquals("Ask Dennis' mother.",
                QuestionManager.replaceTokens("Ask {<S1>|them}.",
                        values("S1", "{<G1>'s|your} mother", "G1", "Dennis"), authored("S1")));
    }

    /** An undelimited token names nothing the runtime can fill, so the default stands. */
    @Test
    void aBareTokenNameIsNotFilled() {
        assertEquals("Do you currently have cancer?",
                QuestionManager.replaceTokens("{Does S1|Do you} currently have cancer?",
                        values("S1", "Dennis"), authored()));
    }

}
