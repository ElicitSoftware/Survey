package com.elicitsoftware.flow.input;

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

import com.elicitsoftware.i18n.Translations;
import com.vaadin.flow.component.datepicker.DatePicker;

import java.text.DateFormatSymbols;
import java.time.temporal.WeekFields;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * The calendar overlay's own words in the respondent's language (NFR-011).
 *
 * <p>A date picker's locale only decides how a date is formatted; the month names, the weekday
 * names and the Today and Cancel buttons of its overlay stay in English unless they are given.
 * The names come from the JDK's locale data, so every language the image carries has them without
 * a translator; the two button words come from the language files.</p>
 */
final class PickerTexts {

    private PickerTexts() {
    }

    static DatePicker.DatePickerI18n datePicker() {
        Locale locale = Translations.currentLocale();
        DateFormatSymbols symbols = DateFormatSymbols.getInstance(locale);
        // Vaadin counts the week from Sunday (0); java.time counts it from Monday (1) to Sunday (7).
        int firstDayOfWeek = WeekFields.of(locale).getFirstDayOfWeek().getValue() % 7;
        return new DatePicker.DatePickerI18n()
                .setMonthNames(List.copyOf(Arrays.asList(symbols.getMonths()).subList(0, 12)))
                .setWeekdays(List.copyOf(Arrays.asList(symbols.getWeekdays()).subList(1, 8)))
                .setWeekdaysShort(List.copyOf(Arrays.asList(symbols.getShortWeekdays()).subList(1, 8)))
                .setFirstDayOfWeek(firstDayOfWeek)
                .setToday(Translations.get("datePicker.today"))
                .setCancel(Translations.get("datePicker.cancel"));
    }
}
