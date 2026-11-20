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

import com.elicitsoftware.i18n.ContentTexts;
import com.elicitsoftware.model.Answer;
import com.elicitsoftware.model.Question;
import com.vaadin.flow.component.datetimepicker.DateTimePicker;
import com.vaadin.flow.component.datetimepicker.DateTimePickerVariant;
import com.vaadin.flow.data.binder.Binder;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * ElicitDateTimePicker is a custom component built on top of the DateTimePicker
 * to integrate with the Elicit framework, providing enhanced features for
 * handling answers and questions that involve date and time inputs.
 * <p>
 * This class extends the ElicitComponent with a DateTimePicker type and
 * customizes its behavior for setting values, validating input, setting
 * requirements, and applying style variants.
 */
public class ElicitDateTimePicker extends ElicitComponent<DateTimePicker> {

    /** The last moment the picker can show on a day: a maximum day ends here (BR-016). */
    private static final LocalTime END_OF_DAY = LocalTime.of(23, 59, 59);

    /**
     * Constructs a new ElicitDateTimePicker for the given answer.
     *
     * @param answer the answer object to bind to this date-time picker
     * @param texts  the content language of this page draw; null means the survey's base text
     */
    public ElicitDateTimePicker(Answer answer, ContentTexts texts) {
        super(new DateTimePicker(answer.label()), answer, texts);
        component.setDatePickerI18n(PickerTexts.datePicker());
        // The bounds' messages name days, as the bounds are days (BR-016); the picker's own check,
        // which the binder runs, reads them.
        DateTimePicker.DateTimePickerI18n i18n = new DateTimePicker.DateTimePickerI18n();
        if (component.getMin() != null) {
            i18n.setMinErrorMessage(dateMinMessage(answer, component.getMin().toLocalDate()));
        }
        if (component.getMax() != null) {
            i18n.setMaxErrorMessage(dateMaxMessage(answer, component.getMax().toLocalDate()));
        }
        component.setI18n(i18n);
        if (answer.getTextValue() != null && !answer.getTextValue().isEmpty()) {
            setValue(answer);
        }
    }

    /**
     * Binds the picker and sets its bounds (UC-002 BR-016).
     *
     * <p>The picker is always bound, so the binder runs the picker's own check -- its minimum,
     * its maximum and incomplete or unreadable input -- on the server whether or not the question
     * is required; an empty optional answer passes. A question's minimum and maximum are
     * integers, for a date-time the days since 1970-01-01, as for a date: any time of day is
     * accepted from the start of the minimum day to the end of the maximum day, whatever day the
     * question is answered on. Either may be set alone.</p>
     *
     * @param answer the answer whose question supplies the required flag and the bounds
     */
    @Override
    void setBindings(Answer answer) {
        // One binding carries every rule: Binder keeps one binding per field (UC-002).
        Binder.BindingBuilder<Answer, LocalDateTime> binding = binder.forField(component);
        if (answer.question.required) {
            component.setRequiredIndicatorVisible(answer.question.required);
            binding = binding.asRequired(requiredMessage(answer));
        }
        binding.bind(Answer::getLocalDateTime, Answer::setLocalDateTime);

        if (answer.question.minValue != null) {
            component.setMin(LocalDate.ofEpochDay(answer.question.minValue).atStartOfDay());
        }
        if (answer.question.maxValue != null) {
            component.setMax(LocalDate.ofEpochDay(answer.question.maxValue).atTime(END_OF_DAY));
        }
    }

    /**
     * Sets the value of the component using the provided answer.
     * The method parses the text value of the answer into a LocalDateTime
     * and assigns it to the component.
     *
     * @param answer The Answer object containing the text value to be parsed
     *               and set as the component's value.
     * @throws DateTimeParseException if the text value cannot be parsed into a LocalDateTime.
     */
    @Override
    void setValue(Answer answer) {
        component.setValue(LocalDateTime.parse(answer.getTextValue()));
    }

    /**
     * Adds theme variants to the DateTimePicker component based on the variants
     * specified in the given {@link Question} object.
     *
     * @param question the {@link Question} object containing the variant information.
     *                 If the {@code variant} field is not null, the method checks for
     *                 specific variant names and applies the corresponding theme
     *                 variants to the component.
     *                 <p>
     *                 Supported variants include:
     *                 <ul>
     *                     <li>{@code LUMO_ALIGN_CENTER} - Aligns the content to the center.</li>
     *                     <li>{@code LUMO_ALIGN_LEFT} - Aligns the content to the left.</li>
     *                     <li>{@code LUMO_ALIGN_RIGHT} - Aligns the content to the right.</li>
     *                     <li>{@code LUMO_SMALL} - Applies a smaller size to the component.</li>
     *                     <li>{@code LUMO_HELPER_ABOVE_FIELD} - Positions the helper text above the field.</li>
     *                 </ul>
     */
    @Override
    void addVariants(Question question) {
        if (question.variant != null) {
            if (question.variant.contains(DateTimePickerVariant.LUMO_ALIGN_CENTER.getVariantName())) {
                component.addThemeVariants(DateTimePickerVariant.LUMO_ALIGN_CENTER);
            }
            if (question.variant.contains(DateTimePickerVariant.LUMO_ALIGN_LEFT.getVariantName())) {
                component.addThemeVariants(DateTimePickerVariant.LUMO_ALIGN_LEFT);
            }
            if (question.variant.contains(DateTimePickerVariant.LUMO_ALIGN_RIGHT.getVariantName())) {
                component.addThemeVariants(DateTimePickerVariant.LUMO_ALIGN_RIGHT);
            }
            if (question.variant.contains(DateTimePickerVariant.LUMO_SMALL.getVariantName())) {
                component.addThemeVariants(DateTimePickerVariant.LUMO_SMALL);
            }
            if (question.variant.contains(DateTimePickerVariant.LUMO_HELPER_ABOVE_FIELD.getVariantName())) {
                component.addThemeVariants(DateTimePickerVariant.LUMO_HELPER_ABOVE_FIELD);
            }
        }
    }

    /**
     * Reads a time stored as HHMM, the form {@code questions.min_value} and {@code max_value} take
     * for time questions (830 is 08:30; UC-002 BR-016).
     *
     * @param hhmm the time as HHMM, 0 to 2359
     * @return the time of day
     * @throws IllegalArgumentException if the value is not a time of day
     */
    static LocalTime timeFromHHMM(int hhmm) {
        if (hhmm < 0 || hhmm > 2359) {
            throw new IllegalArgumentException("The time must be between 0000 and 2359");
        }

        int hours = hhmm / 100;
        int minutes = hhmm % 100;

        // hhmm is 0..2359 by the check above, so hours is 0..23 and minutes is 0..99: the only
        // value left to reject is a minute field past 59, e.g. 1170.
        if (minutes > 59) {
            throw new IllegalArgumentException("Invalid time specified");
        }

        return LocalTime.of(hours, minutes);
    }
}
