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

import com.elicitsoftware.i18n.Translations;
import com.vaadin.flow.component.datepicker.DatePicker;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * UC-009: Complete the Survey in My Language — {@link PickerTexts} gives a date picker's
 * overlay its month and weekday names and its Today/Cancel words in the respondent's language
 * (NFR-011). Off the UI thread the current locale is the English fallback (BR-001).
 */
class PickerTextsTest {

    @Test
    void given_englishFallback_when_datePicker_then_monthsAndWeekdaysAreEnglishFromSunday() {
        DatePicker.DatePickerI18n i18n = PickerTexts.datePicker();

        assertEquals(List.of("January", "February", "March", "April", "May", "June", "July", "August",
                "September", "October", "November", "December"), i18n.getMonthNames());
        assertEquals(List.of("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"),
                i18n.getWeekdays());
        assertEquals(List.of("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"), i18n.getWeekdaysShort());
    }

    @Test
    void given_englishFallback_when_datePicker_then_weekStartsOnSunday() {
        // java.time puts Sunday at 7; Vaadin counts Sunday as 0.
        assertEquals(0, PickerTexts.datePicker().getFirstDayOfWeek());
    }

    @Test
    void given_languageFiles_when_datePicker_then_buttonWordsComeFromThem() {
        DatePicker.DatePickerI18n i18n = PickerTexts.datePicker();

        assertEquals(Translations.get("datePicker.today"), i18n.getToday());
        assertEquals(Translations.get("datePicker.cancel"), i18n.getCancel());
        assertEquals("Today", i18n.getToday());
        assertEquals("Cancel", i18n.getCancel());
    }
}
