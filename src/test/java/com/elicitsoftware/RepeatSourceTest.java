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

import com.elicitsoftware.model.Question;
import com.elicitsoftware.model.QuestionType;
import com.elicitsoftware.model.SelectItem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UC-002 BR-012: the instances a Repeat rule builds, from a count and from a selection.
 * No DB, no Quarkus.
 */
class RepeatSourceTest {

    private static Question question(String typeName, Integer selectGroupId) {
        QuestionType type = new QuestionType();
        type.name = typeName;
        Question q = new Question();
        q.questionType = type;
        q.selectGroupId = selectGroupId;
        return q;
    }

    /** A list of items in display order, coded as given. */
    private static List<SelectItem> items(String... codes) {
        List<SelectItem> items = new ArrayList<>();
        for (String code : codes) {
            SelectItem item = new SelectItem();
            item.codedValue = code;
            item.displayText = "Game " + code;
            items.add(item);
        }
        return items;
    }

    private static final List<SelectItem> GAMES = items("G01", "G02", "G03", "G04");

    @ParameterizedTest
    @CsvSource({"MULTI_SELECT, true", "CHECKBOX_GROUP, true", "COMBOBOX, false", "RADIO, false", "INTEGER, false", "TEXT, false"})
    void onlyAQuestionAnsweredBySelectingSeveralRepeatsPerItem(String type, boolean perItem) {
        assertEquals(perItem, RepeatSource.perItem(question(type, 7)));
    }

    @Test
    void aMultiSelectWithNoListCannotRepeatPerItem() {
        assertFalse(RepeatSource.perItem(question("MULTI_SELECT", null)));
        assertFalse(RepeatSource.perItem(null));
    }

    @Test
    void aCountGivesOneToN() {
        assertEquals(List.of(1, 2, 3), RepeatSource.instances(question("INTEGER", null), "3", List.of()));
        assertEquals(List.of(), RepeatSource.instances(question("INTEGER", null), "0", List.of()));
    }

    @Test
    void aCountThatIsNotANumberStillFails() {
        assertThrows(NumberFormatException.class,
                () -> RepeatSource.instances(question("TEXT", null), "many", List.of()));
    }

    @Test
    void aSelectionGivesThePositionOfEachSelectedItem() {
        assertEquals(List.of(2, 4), RepeatSource.instances(question("MULTI_SELECT", 7), "G02,G04", GAMES));
    }

    @Test
    void theOrderItemsWerePickedInDoesNotMatter() {
        assertEquals(List.of(1, 2, 4), RepeatSource.instances(question("CHECKBOX_GROUP", 7), "G04, G01,G02", GAMES));
    }

    @Test
    void aSelectedCodeTheListNoLongerHoldsGivesNoInstance() {
        assertEquals(List.of(3), RepeatSource.instances(question("MULTI_SELECT", 7), "G99,G03", GAMES));
    }

    @Test
    void nothingAnsweredGivesNoInstances() {
        assertTrue(RepeatSource.instances(question("MULTI_SELECT", 7), "", GAMES).isEmpty());
        assertTrue(RepeatSource.instances(question("MULTI_SELECT", 7), null, GAMES).isEmpty());
        assertTrue(RepeatSource.instances(question("INTEGER", null), " ", List.of()).isEmpty());
    }

    @Test
    void anInstanceNamesTheItemAtItsPosition() {
        assertEquals("G02", RepeatSource.item(GAMES, 2).codedValue);
        assertNull(RepeatSource.item(GAMES, 0), "instance 0 means not repeated");
        assertNull(RepeatSource.item(GAMES, 5), "past the end of the list");
    }
}
