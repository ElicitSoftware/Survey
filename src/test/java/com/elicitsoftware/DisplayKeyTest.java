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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UC-002: Answer Survey Questions, BR-008 and BR-014 — a display key names an answer by survey,
 * step display order and instance, section display order and instance, and question display
 * order and instance. Its string forms are what answers are stored and looked up by: the full
 * key, the LIKE patterns for a step, a section or a question's instances, and the section key
 * with (navigation) and without (structure) its instance.
 */
class DisplayKeyTest {

    private static final String RAW = "12-3-4-5-6-7-8";

    @Test
    void given_aRawKey_when_parsed_then_everySegmentIsRead() {
        DisplayKey key = new DisplayKey(RAW);

        assertEquals(12, key.getSurvey());
        assertEquals(3, key.getStep());
        assertEquals(4, key.getStepInstance());
        assertEquals(5, key.getSection());
        assertEquals(6, key.getSectionInstance());
        assertEquals(7, key.getQuestion());
        assertEquals(8, key.getQuestionInstance());
    }

    @Test
    void given_aKey_when_formatted_then_eachFormPadsAndKeepsOnlyItsSegments() {
        DisplayKey key = new DisplayKey(RAW);

        assertEquals("0012-0003-0004-0005-0006-0007-0008", key.getValue());
        assertEquals("0012-0003-0004-0005-0006-0007-0008", key.toString());
        assertEquals("0012-0003-0004-0005-0006-0007.%", key.getAnswerQueryString());
        assertEquals("0012-0003-0004-0005-%", key.getSectionQueryString());
        assertEquals("0012-0003-0004-0005-0000-0000-0000", key.getSectionString());
        assertEquals("0012-0003-0004-0005-0006-0000-0000", key.getSectionInstanceString());
        assertEquals("0012-0003-0004-0000-0000-0000-0000", key.getStepString());
        assertEquals("0012-0003-%", key.getStepQueryString());
        assertEquals("0042", key.leftPad(42));
        assertEquals("12345", key.leftPad(12345));
    }

    @Test
    void given_aKey_when_everySegmentIsSet_then_theValueReflectsThem() {
        DisplayKey key = new DisplayKey("0-0-0-0-0-0-0");
        key.setSurvey(1);
        key.setStep(2);
        key.setStepInstance(3);
        key.setSection(4);
        key.setSectionInstance(5);
        key.setQuestion(6);
        key.setQuestionInstance(7);

        assertEquals("0001-0002-0003-0004-0005-0006-0007", key.getValue());
    }

    @Test
    void given_paddedAndUnpaddedSpellings_when_parsed_then_theyAreTheSameKey() {
        assertEquals(0, new DisplayKey("0001-0002-0000-0003-0000-0000-0000").compareTo(new DisplayKey("1-2-0-3-0-0-0")));
    }

    @Test
    void given_twoKeys_when_compared_then_theyOrderAsTheirPaddedValues() {
        DisplayKey earlier = new DisplayKey("1-2-0-3-0-0-0");
        DisplayKey later = new DisplayKey("1-10-0-1-0-0-0");

        // Padding is what makes step 10 sort after step 2.
        assertTrue(earlier.compareTo(later) < 0);
        assertTrue(later.compareTo(earlier) > 0);
    }
}
