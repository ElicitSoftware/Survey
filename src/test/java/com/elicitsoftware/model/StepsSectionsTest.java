package com.elicitsoftware.model;

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

import com.elicitsoftware.DisplayKey;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * UC-002: Answer Survey Questions - StepsSections wraps its raw displaykey string in a
 * lazily-built, cached DisplayKey; these instance methods never touch the database.
 */
class StepsSectionsTest {

    private static final String KEY = "1-2-0-3-0-0-0";
    // getDisplaykey()/getValue() always round-trips through zero-padded 4-digit segments.
    private static final String PADDED_KEY = "0001-0002-0000-0003-0000-0000-0000";

    @Test
    void setDisplaykey_thenGetKey_parsesIntoDisplayKey() {
        StepsSections ss = new StepsSections();
        ss.setDisplaykey(KEY);

        DisplayKey key = ss.getKey();
        assertEquals(1, key.getSurvey());
        assertEquals(2, key.getStep());
        assertEquals(3, key.getSection());
    }

    @Test
    void getDisplaykey_lazilyParsesRawFieldWhenKeyNotYetCached() {
        StepsSections ss = new StepsSections();
        ss.displaykey = KEY;

        assertEquals(PADDED_KEY, ss.getDisplaykey());
    }

    @Test
    void getKey_calledTwice_returnsSameCachedInstance() {
        StepsSections ss = new StepsSections();
        ss.setDisplaykey(KEY);

        DisplayKey first = ss.getKey();
        DisplayKey second = ss.getKey();
        assertSame(first, second);
    }
}
