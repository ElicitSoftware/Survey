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

import com.vaadin.flow.component.timepicker.TimePicker;
import com.vaadin.flow.component.timepicker.TimePickerVariant;
import com.elicitsoftware.model.Answer;
import com.elicitsoftware.model.Question;

import java.time.LocalTime;

/**
 * The ElicitTimePicker class represents a time picker component designed to handle
 * user input of time values within the Elicit framework. It extends the generic
 * ElicitComponent class and is specifically configured for managing TimePicker components.
 * <p>
 * The ElicitTimePicker class provides customization options through the following methods:
 * <p>
 * 1. setValue(): Abstract method to set the value in the TimePicker using provided answer data.
 * 2. setMinMax(): Configures minimum and maximum time values for the TimePicker based on Question constraints.
 * 3. setRequired(): Sets whether the TimePicker is required and controls the visibility of the required indicator.
 * 4. addVariants(): Adds theme-based visual variants to the TimePicker component based on Question specifications.
 * 5. validate(): Validates the component ensuring required fields are properly filled and allows invalid state highlighting.
 * <p>
 * The constructor initializes the component and applies parameters like minimum/maximum values,
 * required status, placeholders, validation messages, and tooltip text from the supplied Answer and Question objects.
 */
public class ElicitTimePicker extends ElicitComponent<TimePicker> {

    /**
     * A custom time picker component for the Elicit application.
     * This class extends a base picker and initializes it with a TimePicker
     * component and an associated answer object.
     *
     * <p>If the provided answer has a non-empty text value, the time picker
     * is initialized with that value.</p>
     *
     * @param answer The {@link Answer} object containing the display text
     *               and initial value for the time picker.
     */
    public ElicitTimePicker(Answer answer) {
        super(new TimePicker(answer.displayText), answer);
        if (answer.getTextValue() != null && !answer.getTextValue().isEmpty()) {
            setValue(answer);
        }
    }

    /**
     * Configures the bindings for the time picker component based on the provided answer.
     *
     * <p>This method sets up validation and constraints for the time picker component
     * based on the properties of the associated question in the given answer. It ensures
     * that required fields are marked as such, and applies minimum and maximum time
     * constraints if specified.</p>
     *
     * @param answer The {@link Answer} object containing the question and its associated
     *               properties, such as whether the field is required, validation text,
     *               and minimum/maximum time values.
     */
    @Override
    void setBindings(Answer answer) {
        if (answer.question.required) {
            component.setRequired(answer.question.required);
            component.setRequiredIndicatorVisible(answer.question.required);
            this.binder.forField(component)
                    .asRequired(answer.question.validationText)
                    .bind(Answer::getLocalTime, Answer::setLocalTime);
        }
        if (answer.question.minValue != null) {
            component.setMin(LocalTime.ofNanoOfDay(answer.question.minValue));
        }
        if (answer.question.maxValue != null) {
            component.setMax(LocalTime.ofNanoOfDay(answer.question.maxValue));
        }

    }

    @Override
    void setValue(Answer answer) {
        //TODO
    }

    /**
     * Adds theme variants to the TimePicker component based on the specified
     * variants in the given question. This method checks if the question has
     * a non-null variant and applies the corresponding theme variants to the
     * component.
     *
     * @param question The question object containing the variant information.
     *                 The variant is expected to be a collection of strings
     *                 representing the names of the theme variants to apply.
     *                 Supported variants include:
     *                 <ul>
     *                     <li>LUMO_ALIGN_LEFT</li>
     *                     <li>LUMO_ALIGN_CENTER</li>
     *                     <li>LUMO_ALIGN_RIGHT</li>
     *                     <li>LUMO_SMALL</li>
     *                     <li>LUMO_HELPER_ABOVE_FIELD</li>
     *                 </ul>
     */
    @Override
    void addVariants(Question question) {
        if (question.variant != null) {
            if (question.variant.contains(TimePickerVariant.LUMO_ALIGN_LEFT.getVariantName())) {
                component.addThemeVariants(TimePickerVariant.LUMO_ALIGN_LEFT);
            }
            if (question.variant.contains(TimePickerVariant.LUMO_ALIGN_CENTER.getVariantName())) {
                component.addThemeVariants(TimePickerVariant.LUMO_ALIGN_CENTER);
            }
            if (question.variant.contains(TimePickerVariant.LUMO_ALIGN_RIGHT.getVariantName())) {
                component.addThemeVariants(TimePickerVariant.LUMO_ALIGN_RIGHT);
            }
            if (question.variant.contains(TimePickerVariant.LUMO_SMALL.getVariantName())) {
                component.addThemeVariants(TimePickerVariant.LUMO_SMALL);
            }
            if (question.variant.contains(TimePickerVariant.LUMO_HELPER_ABOVE_FIELD.getVariantName())) {
                component.addThemeVariants(TimePickerVariant.LUMO_HELPER_ABOVE_FIELD);
            }
        }
    }
}
