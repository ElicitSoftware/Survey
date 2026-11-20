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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UC-002: Answer Survey Questions — {@link ElicitDateTimePicker} restores the stored ISO
 * date-time, carries the date overlay's words in the respondent's language (NFR-011), reads
 * min/max as epoch days on the answer's date, and checks them on the server whether or not the
 * question is required (BR-016).
 */
class ElicitDateTimePickerTest {

    private static DateTimePicker bounded(Integer min, Integer max) {
        return new ElicitDateTimePicker(answer(question(false, min, max, null, null), null),
                ContentTexts.base()).component;
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
    void given_epochDayBounds_when_constructed_then_theyAreWholeDays() {
        DateTimePicker picker = bounded(0, 365);

        // BR-016: from the start of the minimum day to the end of the maximum day, whatever
        // day the question is answered on.
        assertEquals(LocalDateTime.of(1970, 1, 1, 0, 0), picker.getMin());
        assertEquals(LocalDateTime.of(1971, 1, 1, 23, 59, 59), picker.getMax());
    }

    @Test
    void given_oneBound_when_constructed_then_theOtherIsUnset() {
        assertNull(bounded(10, null).getMax());
        assertNull(bounded(null, 10).getMin());
    }

    @Test
    void given_optionalBoundedQuestion_when_validated_then_onlyAnAnswerOutsideTheDaysFails() {
        LocalDate first = LocalDate.ofEpochDay(20_000);
        LocalDate last = LocalDate.ofEpochDay(20_010);
        ElicitDateTimePicker wrapper = new ElicitDateTimePicker(
                answer(question(false, 20_000, 20_010, null, null), null), ContentTexts.base());

        // BR-016: an empty optional answer passes; a bounded one is checked on the server.
        assertTrue(wrapper.getBinder().validate().isOk());

        wrapper.component.setValue(last.atTime(23, 30));
        assertTrue(wrapper.getBinder().validate().isOk(), "late on the maximum day is inside");

        wrapper.component.setValue(first.atStartOfDay());
        assertTrue(wrapper.getBinder().validate().isOk(), "the start of the minimum day is inside");

        wrapper.component.setValue(last.plusDays(1).atStartOfDay());
        assertEquals(List.of(Translations.get("validation.dateMax", PickerTexts.formatDate(last))),
                errors(wrapper.getBinder()));

        wrapper.component.setValue(first.minusDays(1).atTime(23, 59));
        assertEquals(List.of(Translations.get("validation.dateMin", PickerTexts.formatDate(first))),
                errors(wrapper.getBinder()));
    }

    @Test
    void given_authoredValidationText_when_outOfBounds_then_itIsTheMessage() {
        ElicitDateTimePicker wrapper = new ElicitDateTimePicker(
                answer(question(false, 20_000, 20_010, null, "Pick a day in the study window."), null),
                ContentTexts.base());

        wrapper.component.setValue(LocalDate.ofEpochDay(20_011).atTime(9, 0));

        assertEquals(List.of("Pick a day in the study window."), errors(wrapper.getBinder()));
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
