package com.elicitsoftware.model;

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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UC-002: Answer Survey Questions, BR-007 — a Dependent records that one answer exists because
 * a rule held on another. QuestionManager copies a rule's pending dependent onto each answer it
 * builds ({@code shallowCopy}) and saves it only once both ends and the rule are known
 * ({@code isComplete}); both are plain field logic.
 */
class DependentTest {

    private static Dependent complete() {
        return new Dependent(7, new Answer(), new Answer(), new Relationship());
    }

    @Test
    void given_allFourParts_when_isComplete_then_true() {
        assertTrue(complete().isComplete());
    }

    @Test
    void given_anyPartMissing_when_isComplete_then_false() {
        Dependent noRespondent = complete();
        noRespondent.respondentId = null;
        Dependent noUpstream = complete();
        noUpstream.upstream = null;
        Dependent noDownstream = complete();
        noDownstream.downstream = null;
        Dependent noRule = complete();
        noRule.relationship = null;

        assertFalse(noRespondent.isComplete());
        assertFalse(noUpstream.isComplete());
        assertFalse(noDownstream.isComplete());
        assertFalse(noRule.isComplete());
        assertFalse(new Dependent().isComplete());
    }

    @Test
    void given_aDependent_when_shallowCopy_then_everythingButTheDownstreamIsShared() {
        Dependent original = complete();
        original.id = 99;
        original.deleted = true;

        Dependent copy = original.shallowCopy();

        assertNotSame(original, copy);
        assertSame(original.respondentId, copy.respondentId);
        assertSame(original.upstream, copy.upstream);
        assertSame(original.relationship, copy.relationship);
        assertNull(copy.downstream);
        // A copy is a new row: no id, and not deleted.
        assertNull(copy.id);
        assertFalse(copy.deleted);
        assertFalse(copy.isComplete());
    }
}
