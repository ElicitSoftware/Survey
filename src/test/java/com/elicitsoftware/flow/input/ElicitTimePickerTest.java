package com.elicitsoftware.flow.input;

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

import com.elicitsoftware.i18n.ContentTexts;
import com.elicitsoftware.model.Question;
import com.vaadin.flow.component.timepicker.TimePicker;
import com.vaadin.flow.component.timepicker.TimePickerVariant;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import static com.elicitsoftware.flow.input.ElicitAnswerFixtures.question;
import static com.elicitsoftware.flow.input.InputFieldSupport.answer;
import static com.elicitsoftware.flow.input.InputFieldSupport.errors;
import static com.elicitsoftware.flow.input.InputFieldSupport.themes;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UC-002: Answer Survey Questions — {@link ElicitTimePicker} restores the stored ISO time and
 * applies the per-question required rule and the presence of min/max bounds (BR-004).
 * <p>
 * The unit the bounds are read in is deliberately not asserted here: the picker reads
 * {@code min_value}/{@code max_value} as nanoseconds of the day, which looks like a defect
 * (the date-time picker reads the same columns as HHMM) and is reported rather than enshrined.
 */
class ElicitTimePickerTest {

    @Test
    void given_storedIsoTime_when_constructed_then_valueIsParsed() {
        TimePicker picker = new ElicitTimePicker(answer(InputFieldSupport.plain(), "07:15"),
                ContentTexts.base()).component;

        assertEquals(LocalTime.of(7, 15), picker.getValue());
        assertEquals("Label", picker.getLabel());
        assertFalse(picker.isRequiredIndicatorVisible());
        assertFalse(picker.isRequired());
    }

    @Test
    void given_emptyStoredText_when_constructed_then_pickerIsEmpty() {
        assertNull(new ElicitTimePicker(answer(InputFieldSupport.plain(), ""), ContentTexts.base()).component.getValue());
        assertNull(new ElicitTimePicker(answer(InputFieldSupport.plain(), null), ContentTexts.base()).component.getValue());
    }

    @Test
    void given_requiredQuestion_when_validated_then_emptyFailsAndFilledPasses() {
        ElicitTimePicker wrapper = new ElicitTimePicker(answer(question(true, null, null, null, null), null),
                ContentTexts.base());

        assertTrue(wrapper.component.isRequired());
        assertTrue(wrapper.component.isRequiredIndicatorVisible());
        assertEquals(List.of(InputFieldSupport.requiredFallback()), errors(wrapper.getBinder()));

        wrapper.component.setValue(LocalTime.NOON);
        assertTrue(wrapper.getBinder().validate().isOk());
    }

    @Test
    void given_bounds_when_constructed_then_eachBoundIsSetOnlyWhenAuthored() {
        TimePicker none = new ElicitTimePicker(answer(InputFieldSupport.plain(), null), ContentTexts.base()).component;
        assertNull(none.getMin());
        assertNull(none.getMax());

        TimePicker minOnly = new ElicitTimePicker(answer(question(false, 1, null, null, null), null),
                ContentTexts.base()).component;
        assertNotNull(minOnly.getMin());
        assertNull(minOnly.getMax());

        TimePicker maxOnly = new ElicitTimePicker(answer(question(false, null, 1, null, null), null),
                ContentTexts.base()).component;
        assertNull(maxOnly.getMin());
        assertNotNull(maxOnly.getMax());
    }

    @Test
    void given_allVariants_when_constructed_then_allThemesApplied() {
        String all = String.join(",", TimePickerVariant.LUMO_ALIGN_LEFT.getVariantName(),
                TimePickerVariant.LUMO_ALIGN_CENTER.getVariantName(),
                TimePickerVariant.LUMO_ALIGN_RIGHT.getVariantName(),
                TimePickerVariant.LUMO_SMALL.getVariantName(),
                TimePickerVariant.LUMO_HELPER_ABOVE_FIELD.getVariantName());

        TimePicker picker = new ElicitTimePicker(answer(question(false, null, null, all, null), null),
                ContentTexts.base()).component;

        assertEquals(Set.of(TimePickerVariant.LUMO_ALIGN_LEFT.getVariantName(),
                TimePickerVariant.LUMO_ALIGN_CENTER.getVariantName(),
                TimePickerVariant.LUMO_ALIGN_RIGHT.getVariantName(),
                TimePickerVariant.LUMO_SMALL.getVariantName(),
                TimePickerVariant.LUMO_HELPER_ABOVE_FIELD.getVariantName()), themes(picker));
    }

    @Test
    void given_unknownOrNullVariant_when_addVariants_then_noThemeApplied() {
        ElicitTimePicker wrapper = new ElicitTimePicker(answer(question(false, null, null, "none", null), null),
                ContentTexts.base());
        assertTrue(themes(wrapper.component).isEmpty());

        Question noVariant = InputFieldSupport.plain();
        assertDoesNotThrow(() -> wrapper.addVariants(noVariant));
        assertTrue(themes(wrapper.component).isEmpty());
    }
}
