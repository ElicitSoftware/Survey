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

import com.elicitsoftware.model.Answer;
import com.elicitsoftware.model.Question;
import com.vaadin.flow.component.datetimepicker.DateTimePicker;
import com.vaadin.flow.component.datetimepicker.DateTimePickerVariant;

import java.time.LocalDate;
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

    /**
     * A custom date-time picker component for the Elicit application.
     * This class extends a base picker and initializes it with a specific answer.
     * If the provided answer has a non-empty text value, it sets the initial value
     * of the picker accordingly.
     */
    public ElicitDateTimePicker(Answer answer) {
        super(new DateTimePicker(answer.displayText), answer);
        if (answer.getTextValue() != null && !answer.getTextValue().isEmpty()) {
            setValue(answer);
        }
    }

    /**
     * Sets up the bindings and validation rules for the date-time picker component
     * based on the provided answer's question properties.
     *
     * @param answer The {@link Answer} object containing the question and its properties.
     *               This is used to configure the component's behavior and validation.
     *
     *               <p>Behavior:</p>
     *               <ul>
     *                 <li>If the question is required, the component will display a required indicator
     *                     and enforce a validation rule to ensure a value is provided.</li>
     *                 <li>Validation rules are bound to the {@link Answer#getLocalDateTime()} and
     *                     {@link Answer#setLocalDateTime(LocalDateTime)} methods.</li>
     *                 <li>If the question specifies a minimum value, the component will enforce
     *                     that the selected date-time is not earlier than the specified minimum.</li>
     *                 <li>If the question specifies a maximum value, the component will enforce
     *                     that the selected date-time is not later than the specified maximum.</li>
     *               </ul>
     *
     *               <p>Notes:</p>
     *               <ul>
     *                 <li>The minimum and maximum values are expected to be integers representing
     *                     24-hour time in the format HHMM (e.g., 830 for 8:30 AM).</li>
     *                 <li>Additional validators need to be implemented for both required and non-required cases.</li>
     *               </ul>
     */
    @Override
    void setBindings(Answer answer) {
        if (answer.question.required) {
            component.setRequiredIndicatorVisible(answer.question.required);
            this.binder.forField(component)
                    .asRequired(answer.question.validationText)
                    .bind(Answer::getLocalDateTime, Answer::setLocalDateTime);
            //Need to add a validator.
        } else {
            //Need to add a validator.
        }

        //TODO Min and Max are integers.
        // we will assume these are 24 hour click in the form of hour minute.
        // e.g. 830 would be 8:30 AM

        if (answer.question.minValue != null) {
            component.setMin(createLocalDateTimeFromHHMM(answer.question.minValue));
        }

        if (answer.question.maxValue != null) {
            component.setMax(createLocalDateTimeFromHHMM(answer.question.maxValue));
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
     * Creates a {@link LocalDateTime} instance from a given time in HHMM format.
     *
     * @param hhmm the time in HHMM format (e.g., 0930 for 9:30 AM, 1530 for 3:30 PM).
     *             The value must be between 0000 and 2359.
     * @return a {@link LocalDateTime} instance representing the current date with the specified time.
     * @throws IllegalArgumentException if the provided time is not between 0000 and 2359,
     *                                  or if the hours or minutes are invalid.
     */
    private static LocalDateTime createLocalDateTimeFromHHMM(int hhmm) {
        if (hhmm < 0 || hhmm > 2359) {
            throw new IllegalArgumentException("The time must be between 0000 and 2359");
        }

        int hours = hhmm / 100;
        int minutes = hhmm % 100;

        if (hours < 0 || hours > 23 || minutes < 0 || minutes > 59) {
            throw new IllegalArgumentException("Invalid time specified");
        }

        LocalDate currentDate = LocalDate.now(); // Get the current date
        LocalTime time = LocalTime.of(hours, minutes);

        return LocalDateTime.of(currentDate, time);
    }
}
