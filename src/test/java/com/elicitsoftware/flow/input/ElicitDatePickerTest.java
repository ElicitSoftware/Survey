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
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.datepicker.DatePickerVariant;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
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
 * UC-002: Answer Survey Questions — {@link ElicitDatePicker} restores the stored ISO date,
 * carries the overlay's own words in the respondent's language (NFR-011), and applies the
 * per-question required rule and epoch-day min/max (BR-004).
 */
class ElicitDatePickerTest {

    @Test
    void given_storedIsoDate_when_constructed_then_valueIsParsed() {
        DatePicker picker = new ElicitDatePicker(answer(InputFieldSupport.plain(), "1965-04-30"),
                ContentTexts.base()).component;

        assertEquals(LocalDate.of(1965, 4, 30), picker.getValue());
        assertEquals("Label", picker.getLabel());
        assertFalse(picker.isRequiredIndicatorVisible());
    }

    @Test
    void given_emptyStoredText_when_constructed_then_pickerIsEmpty() {
        assertNull(new ElicitDatePicker(answer(InputFieldSupport.plain(), ""), ContentTexts.base()).component.getValue());
        assertNull(new ElicitDatePicker(answer(InputFieldSupport.plain(), null), ContentTexts.base()).component.getValue());
    }

    @Test
    void given_construction_when_inspected_then_overlayWordsAreTranslated() {
        DatePicker picker = new ElicitDatePicker(answer(InputFieldSupport.plain(), null), ContentTexts.base()).component;

        assertNotNull(picker.getI18n());
        assertEquals(Translations.get("datePicker.today"), picker.getI18n().getToday());
        assertEquals(Translations.get("datePicker.cancel"), picker.getI18n().getCancel());
    }

    @Test
    void given_requiredQuestion_when_validated_then_emptyFailsAndFilledPasses() {
        ElicitDatePicker wrapper = new ElicitDatePicker(answer(question(true, null, null, null, null), null),
                ContentTexts.base());

        assertTrue(wrapper.component.isRequiredIndicatorVisible());
        assertEquals(List.of(InputFieldSupport.requiredFallback()), errors(wrapper.getBinder()));

        wrapper.component.setValue(LocalDate.of(2000, 1, 1));
        assertTrue(wrapper.getBinder().validate().isOk());
    }

    @Test
    void given_epochDayBounds_when_constructed_then_minAndMaxAreSet() {
        DatePicker picker = new ElicitDatePicker(answer(question(false, 0, 365, null, null), null),
                ContentTexts.base()).component;

        assertEquals(LocalDate.of(1970, 1, 1), picker.getMin());
        assertEquals(LocalDate.of(1971, 1, 1), picker.getMax());
    }

    @Test
    void given_noBounds_when_constructed_then_minAndMaxAreUnset() {
        DatePicker picker = new ElicitDatePicker(answer(InputFieldSupport.plain(), null), ContentTexts.base()).component;

        assertNull(picker.getMin());
        assertNull(picker.getMax());
    }

    @Test
    void given_allVariants_when_constructed_then_allThemesApplied() {
        String all = String.join(",", DatePickerVariant.LUMO_ALIGN_CENTER.getVariantName(),
                DatePickerVariant.LUMO_ALIGN_LEFT.getVariantName(), DatePickerVariant.LUMO_ALIGN_RIGHT.getVariantName(),
                DatePickerVariant.LUMO_SMALL.getVariantName(), DatePickerVariant.LUMO_HELPER_ABOVE_FIELD.getVariantName());

        DatePicker picker = new ElicitDatePicker(answer(question(false, null, null, all, null), null),
                ContentTexts.base()).component;

        assertEquals(Set.of(DatePickerVariant.LUMO_ALIGN_CENTER.getVariantName(),
                DatePickerVariant.LUMO_ALIGN_LEFT.getVariantName(),
                DatePickerVariant.LUMO_ALIGN_RIGHT.getVariantName(),
                DatePickerVariant.LUMO_SMALL.getVariantName(),
                DatePickerVariant.LUMO_HELPER_ABOVE_FIELD.getVariantName()), themes(picker));
    }

    @Test
    void given_unknownOrNullVariant_when_addVariants_then_noThemeApplied() {
        ElicitDatePicker wrapper = new ElicitDatePicker(answer(question(false, null, null, "none", null), null),
                ContentTexts.base());
        assertTrue(themes(wrapper.component).isEmpty());

        Question noVariant = InputFieldSupport.plain();
        assertDoesNotThrow(() -> wrapper.addVariants(noVariant));
        assertTrue(themes(wrapper.component).isEmpty());
    }
}
