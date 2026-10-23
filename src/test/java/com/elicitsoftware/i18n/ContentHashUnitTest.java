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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UC-009: Complete the Survey in My Language (BR-005, BR-008) -- the source hash a translation
 * carries, checked against fixed SHA-256 vectors without a database. {@code ContentHashTest}
 * additionally holds the Java result against PostgreSQL's.
 */
class ContentHashUnitTest {

    @Test
    void given_knownStrings_when_hashing_then_lowerCaseHexSha256() {
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", ContentHash.of(""));
        assertEquals("185f8db32271fe25f561a6fc938b2e264306ec304eda518007d1764826381969", ContentHash.of("Hello"));
    }

    @Test
    void given_nullText_when_hashing_then_null() {
        assertNull(ContentHash.of(null));
    }

    @Test
    void given_hashOfSameText_when_matching_then_true() {
        assertTrue(ContentHash.matches(ContentHash.of("Hello"), "Hello"));
    }

    @Test
    void given_hashOfOtherText_when_matching_then_false() {
        assertFalse(ContentHash.matches(ContentHash.of("Hello"), "Hello "));
    }

    @Test
    void given_noStoredHash_when_matching_then_false() {
        assertFalse(ContentHash.matches(null, "Hello"));
        assertFalse(ContentHash.matches(null, null), "a missing hash never matches, even missing text");
    }
}
