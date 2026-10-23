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
import com.elicitsoftware.i18n.Translations;
import com.elicitsoftware.model.Question;
import com.vaadin.flow.component.datetimepicker.DateTimePicker;
import com.vaadin.flow.component.datetimepicker.DateTimePickerVariant;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UC-002: Answer Survey Questions — {@link ElicitDateTimePicker} restores the stored ISO
 * date-time, carries the date overlay's words in the respondent's language (NFR-011), and reads
 * min/max as HHMM times of day (BR-004), rejecting a value that is not a time of day.
 */
class ElicitDateTimePickerTest {

    private static DateTimePicker bounded(Integer min, Integer max) {
        return new ElicitDateTimePicker(answer(question(false, min, max, null, null), null),
                ContentTexts.base()).component;
    }

    private static void assertTimeToday(LocalTime expected, LocalDate before, LocalDateTime actual) {
        assertEquals(expected, actual.toLocalTime());
        LocalDate after = LocalDate.now();
        assertTrue(!actual.toLocalDate().isBefore(before) && !actual.toLocalDate().isAfter(after),
                "the bound is anchored on the current date");
    }

    @Test
    void given_storedIsoDateTime_when_constructed_then_valueIsParsed() {
        DateTimePicker picker = new ElicitDateTimePicker(answer(InputFieldSupport.plain(), "2024-02-29T13:45"),
                ContentTexts.base()).component;

        assertEquals(LocalDateTime.of(2024, 2, 29, 13, 45), picker.getValue());
        assertEquals("Label", picker.getLabel());
        assertFalse(picker.isRequiredIndicatorVisible());
        assertEquals(Translations.get("datePicker.today"), picker.getDatePickerI18n().getToday());
    }

    @Test
    void given_emptyStoredText_when_constructed_then_pickerIsEmpty() {
        assertNull(new ElicitDateTimePicker(answer(InputFieldSupport.plain(), ""), ContentTexts.base()).component.getValue());
        assertNull(new ElicitDateTimePicker(answer(InputFieldSupport.plain(), null), ContentTexts.base()).component.getValue());
    }

    @Test
    void given_requiredQuestion_when_validated_then_emptyFailsAndFilledPasses() {
        ElicitDateTimePicker wrapper = new ElicitDateTimePicker(answer(question(true, null, null, null, "When?"), null),
                ContentTexts.base());

        assertTrue(wrapper.component.isRequiredIndicatorVisible());
        assertEquals(List.of("When?"), errors(wrapper.getBinder()));

        wrapper.component.setValue(LocalDateTime.of(2020, 5, 6, 7, 8));
        assertTrue(wrapper.getBinder().validate().isOk());
    }

    @Test
    void given_hhmmBounds_when_constructed_then_minAndMaxAreThoseTimesToday() {
        LocalDate before = LocalDate.now();

        DateTimePicker picker = bounded(830, 1745);

        assertTimeToday(LocalTime.of(8, 30), before, picker.getMin());
        assertTimeToday(LocalTime.of(17, 45), before, picker.getMax());
    }

    @Test
    void given_edgeTimesOfDay_when_constructed_then_theyAreAccepted() {
        LocalDate before = LocalDate.now();

        DateTimePicker picker = bounded(0, 2359);
        assertTimeToday(LocalTime.MIDNIGHT, before, picker.getMin());
        assertTimeToday(LocalTime.of(23, 59), before, picker.getMax());

        assertTimeToday(LocalTime.of(11, 59), before, bounded(1159, null).getMin());
        assertNull(bounded(1159, null).getMax());
        assertNull(bounded(null, 1159).getMin());
    }

    @Test
    void given_valueOutsideTheDay_when_constructed_then_rejected() {
        assertThrows(IllegalArgumentException.class, () -> bounded(-1, null));
        assertThrows(IllegalArgumentException.class, () -> bounded(null, 2360));
        assertThrows(IllegalArgumentException.class, () -> bounded(2400, null));
        assertThrows(IllegalArgumentException.class, () -> bounded(1160, null));
        assertThrows(IllegalArgumentException.class, () -> bounded(null, 1199));
    }

    @Test
    void given_allVariants_when_constructed_then_allThemesApplied() {
        String all = String.join(",", DateTimePickerVariant.LUMO_ALIGN_CENTER.getVariantName(),
                DateTimePickerVariant.LUMO_ALIGN_LEFT.getVariantName(),
                DateTimePickerVariant.LUMO_ALIGN_RIGHT.getVariantName(),
                DateTimePickerVariant.LUMO_SMALL.getVariantName(),
                DateTimePickerVariant.LUMO_HELPER_ABOVE_FIELD.getVariantName());

        DateTimePicker picker = new ElicitDateTimePicker(answer(question(false, null, null, all, null), null),
                ContentTexts.base()).component;

        assertEquals(Set.of(DateTimePickerVariant.LUMO_ALIGN_CENTER.getVariantName(),
                DateTimePickerVariant.LUMO_ALIGN_LEFT.getVariantName(),
                DateTimePickerVariant.LUMO_ALIGN_RIGHT.getVariantName(),
                DateTimePickerVariant.LUMO_SMALL.getVariantName(),
                DateTimePickerVariant.LUMO_HELPER_ABOVE_FIELD.getVariantName()), themes(picker));
    }

    @Test
    void given_unknownOrNullVariant_when_addVariants_then_noThemeApplied() {
        ElicitDateTimePicker wrapper = new ElicitDateTimePicker(answer(question(false, null, null, "none", null), null),
                ContentTexts.base());
        assertTrue(themes(wrapper.component).isEmpty());

        Question noVariant = InputFieldSupport.plain();
        assertDoesNotThrow(() -> wrapper.addVariants(noVariant));
        assertTrue(themes(wrapper.component).isEmpty());
    }
}
