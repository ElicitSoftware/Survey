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
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.datepicker.DatePickerVariant;

import java.time.LocalDate;

/**
 * The ElcitDatePicker class is a custom implementation of the ElicitComponent class
 * that wraps a Vaadin DatePicker component. This class allows the DatePicker
 * component to be configured and customized based on data from Answer and Question objects.
 * <p>
 * The class provides functionality for:
 * - Setting the initial selected date from an Answer object.
 * - Configuring minimum and maximum allowed dates based on Question constraints.
 * - Marking the DatePicker component as required.
 * - Adding visual variants and styles as specified in the Question object.
 * - Performing validation logic specific to the DatePicker component.
 * <p>
 * Key Methods:
 * - setValue(Answer answer): Parses the date value from the Answer object and sets it into the DatePicker component.
 * - setMinMax(Question question): Configures the minimum and maximum date constraints for the DatePicker component.
 * - setRequired(Boolean required): Applies the required indicator and flag to the DatePicker component.
 * - addVariants(Question question): Adds visual variants (like alignment, size, etc.) to the DatePicker component as specified.
 * - validate(): Placeholder method to allow for future validation logic.
 */
public class ElcitDatePicker extends ElicitComponent<DatePicker> {

    /**
     * Constructs an ElcitDatePicker component for the given answer.
     * The date picker is initialized with the display text from the answer,
     * and if the answer has a text value, it's parsed and set as the initial value.
     *
     * @param answer the answer object containing the data for this date picker
     */
    public ElcitDatePicker(Answer answer) {
        super(new DatePicker(answer.displayText), answer);
        if (answer.getTextValue() != null && !answer.getTextValue().isEmpty()) {
            setValue(answer);
        }
    }

    /**
     * Sets the value of the date picker component based on the provided answer.
     * The answer's text value is parsed into a {@link LocalDate} and assigned to the component.
     *
     * @param answer The {@link Answer} object containing the text value to be parsed
     *               and set as the date picker value.
     * @throws DateTimeParseException if the text value cannot be parsed into a valid {@link LocalDate}.
     */
    @Override
    void setValue(Answer answer) {
        component.setValue(LocalDate.parse(answer.getTextValue()));
    }

    /**
     * Sets the bindings for the date picker component based on the provided answer.
     * This method configures the component's required status, validation, and
     * minimum/maximum date constraints.
     *
     * @param answer The {@link Answer} object containing the question and its associated
     *               properties such as required status, validation text, and min/max values.
     *
     *               <p><b>Behavior:</b></p>
     *               <ul>
     *                 <li>If the question is marked as required, the component is set to required and
     *                     displays a required indicator. Validation is also applied using the provided
     *                     validation text.</li>
     *                 <li>If minimum and maximum values are specified, they are interpreted as epoch
     *                     days and converted to {@link LocalDate} to set the component's min and max
     *                     constraints.</li>
     *               </ul>
     *
     *               <p><b>Note:</b> The minValue and maxValue are currently treated as integers representing
     *               epoch days. This may need to be updated to support other formats like actual date values.</p>
     */
    @Override
    void setBindings(Answer answer) {
        if (answer.question.required) {
            component.setRequired(answer.question.required);
            component.setRequiredIndicatorVisible(answer.question.required);
            this.binder.forField(component)
                    .asRequired(answer.question.validationText)
                    .bind(Answer::getLocalDate, Answer::setLocalDate);
        }

        //TODO Min and Max are integers.
        // We need a way to put in other values like dates
        // for now we will use the epoch time.
        if (answer.question.minValue != null) {
            component.setMin(LocalDate.ofEpochDay(answer.question.minValue));
        }
        if (answer.question.maxValue != null) {
            component.setMax(LocalDate.ofEpochDay(answer.question.maxValue));
        }
    }


    /**
     * Adds theme variants to the DatePicker component based on the specified
     * variants in the given {@link Question} object.
     *
     * @param question the {@link Question} object containing the variants to be applied.
     *                 If the question's variant is not null, the method checks for
     *                 specific variant names and applies the corresponding
     *                 {@link DatePickerVariant} to the component.
     *                 <p>
     *                 Supported variants include:
     *                 <ul>
     *                     <li>{@link DatePickerVariant#LUMO_ALIGN_CENTER}</li>
     *                     <li>{@link DatePickerVariant#LUMO_ALIGN_LEFT}</li>
     *                     <li>{@link DatePickerVariant#LUMO_ALIGN_RIGHT}</li>
     *                     <li>{@link DatePickerVariant#LUMO_SMALL}</li>
     *                     <li>{@link DatePickerVariant#LUMO_HELPER_ABOVE_FIELD}</li>
     *                 </ul>
     */
    @Override
    void addVariants(Question question) {
        if (question.variant != null) {
            if (question.variant.contains(DatePickerVariant.LUMO_ALIGN_CENTER.getVariantName())) {
                component.addThemeVariants(DatePickerVariant.LUMO_ALIGN_CENTER);
            }
            if (question.variant.contains(DatePickerVariant.LUMO_ALIGN_LEFT.getVariantName())) {
                component.addThemeVariants(DatePickerVariant.LUMO_ALIGN_LEFT);
            }
            if (question.variant.contains(DatePickerVariant.LUMO_ALIGN_RIGHT.getVariantName())) {
                component.addThemeVariants(DatePickerVariant.LUMO_ALIGN_RIGHT);
            }
            if (question.variant.contains(DatePickerVariant.LUMO_SMALL.getVariantName())) {
                component.addThemeVariants(DatePickerVariant.LUMO_SMALL);
            }
            if (question.variant.contains(DatePickerVariant.LUMO_HELPER_ABOVE_FIELD.getVariantName())) {
                component.addThemeVariants(DatePickerVariant.LUMO_HELPER_ABOVE_FIELD);
            }
        }
    }
}
