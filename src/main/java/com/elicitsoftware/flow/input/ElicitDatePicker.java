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
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.datepicker.DatePickerVariant;
import com.vaadin.flow.data.binder.Binder;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * The ElicitDatePicker class is a custom implementation of the ElicitComponent class
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
public class ElicitDatePicker extends ElicitComponent<DatePicker> {

    /**
     * Constructs an ElicitDatePicker component for the given answer.
     * The date picker is initialized with the display text from the answer,
     * and if the answer has a text value, it's parsed and set as the initial value.
     *
     * @param answer the answer object containing the data for this date picker
     * @param texts  the content language of this page draw; null means the survey's base text
     */
    public ElicitDatePicker(Answer answer, ContentTexts texts) {
        super(new DatePicker(answer.label()), answer, texts);
        DatePicker.DatePickerI18n i18n = PickerTexts.datePicker();
        // The bounds' messages, read by the picker's own check, which the binder runs (BR-016).
        if (component.getMin() != null) {
            i18n.setMinErrorMessage(dateMinMessage(answer, component.getMin()));
        }
        if (component.getMax() != null) {
            i18n.setMaxErrorMessage(dateMaxMessage(answer, component.getMax()));
        }
        component.setI18n(i18n);
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
     * Binds the picker and sets its bounds (UC-002 BR-016).
     *
     * <p>The picker is always bound, so the binder runs the picker's own check -- its minimum,
     * its maximum and input it cannot read -- on the server whether or not the question is
     * required; an empty optional answer passes. A question's minimum and maximum are integers,
     * for a date the days since 1970-01-01, and either may be set alone.</p>
     *
     * @param answer the answer whose question supplies the required flag and the bounds
     */
    @Override
    void setBindings(Answer answer) {
        // One binding carries every rule: Binder keeps one binding per field (UC-002).
        Binder.BindingBuilder<Answer, LocalDate> binding = binder.forField(component);
        if (answer.question.required) {
            component.setRequired(answer.question.required);
            component.setRequiredIndicatorVisible(answer.question.required);
            binding = binding.asRequired(requiredMessage(answer));
        }
        binding.bind(Answer::getLocalDate, Answer::setLocalDate);

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
