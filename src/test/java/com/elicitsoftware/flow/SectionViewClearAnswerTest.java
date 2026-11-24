package com.elicitsoftware.flow;

/*-
 * ***LICENSE_START***
 * Elicit Survey
 * %%
 * Copyright (C) 2025 - 2026 Elicit Software LLC
 * %%
 * PolyForm Noncommercial License 1.0.0
 * <https://polyformproject.org/licenses/noncommercial/1.0.0>
 * ***LICENSE_END***
 */

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UC-002 BR-017: what a field's value is saved as, and when it is saved. A cleared field's value
 * is {@code null}; the listeners in {@link SectionView} turn it into the stored text through
 * these helpers instead of calling {@code toString()} on it.
 */
class SectionViewClearAnswerTest {

    @Test
    void clearedFieldOfAnyTypeGivesNoText() {
        // UC-002 BR-017: date, date-time, time, number and integer fields hold null once cleared.
        assertNull(SectionView.answerText(null));
    }

    @Test
    void filledFieldsGiveTheirValuesText() {
        assertEquals("2026-11-20", SectionView.answerText(LocalDate.of(2026, 11, 20)));
        assertEquals("2026-11-20T08:30", SectionView.answerText(LocalDateTime.of(2026, 11, 20, 8, 30)));
        assertEquals("08:30", SectionView.answerText(LocalTime.of(8, 30)));
        assertEquals("2.5", SectionView.answerText(2.5d));
        assertEquals("3", SectionView.answerText(3));
    }

    @Test
    void clearedIntegerIsNeverStoredAsTheTextNull() {
        // UC-002 BR-017: String.valueOf(null) used to store "null".
        Integer cleared = null;
        assertNull(SectionView.storedValue(SectionView.answerText(cleared)));
    }

    @Test
    void emptyTextIsStoredAsUnanswered() {
        // UC-002 BR-017: an emptied text, email or password field, or an emptied multi-select
        // (whose coded values join to ""), is unanswered, not an empty string.
        assertNull(SectionView.storedValue(""));
        assertNull(SectionView.storedValue(null));
        assertEquals("x", SectionView.storedValue("x"));
        assertEquals(" ", SectionView.storedValue(" "), "what the respondent typed is kept as typed");
    }

    @Test
    void clearingAnAnsweredQuestionIsAChange() {
        // UC-002 BR-017: the clear is saved, so the old answer leaves the review page and reports.
        assertTrue(SectionView.changesAnswer("2026-11-20", null));
        assertTrue(SectionView.changesAnswer("Smith", ""));
    }

    @Test
    void clearingAnUnansweredQuestionSavesNothing() {
        // UC-002 BR-017: no save, no rule recalculation for a value already stored.
        assertFalse(SectionView.changesAnswer(null, null));
        assertFalse(SectionView.changesAnswer(null, ""));
        assertFalse(SectionView.changesAnswer("3", "3"));
    }

    @Test
    void anAnswerGivenOrChangedIsStillAChange() {
        assertTrue(SectionView.changesAnswer(null, "3"));
        assertTrue(SectionView.changesAnswer("3", "4"));
    }
}
