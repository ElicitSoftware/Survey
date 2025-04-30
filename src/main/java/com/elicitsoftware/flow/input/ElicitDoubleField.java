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

import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextFieldVariant;
import com.elicitsoftware.model.Answer;
import com.elicitsoftware.model.Question;

/**
 * The ElicitDoubleField class represents a user interface component designed for numerical
 * input, specifically handling double values. It extends the ElicitComponent class and wraps
 * a NumberField component, providing additional functionality for managing input values,
 * constraints, and validation. This class is typically initialized with an Answer object,
 * which carries metadata and initial values for the component.
 * <p>
 * The main purpose of this class is to provide:
 * - Parsing and setting of double values from a given Answer.
 * - Setting minimum and maximum allowed values for the input field.
 * - Controlling the required state of the component.
 * - Adding specific visual variants to the component based on the Question configuration.
 * - Performing validation to ensure constraints are met, such as non-empty input for required fields.
 * <p>
 * This class relies on Answer and Question objects to configure and initialize the component
 * appropriately, including display text, constraints, and appearance.
 * <p>
 * Overrides:
 * - setValue: Sets the numeric value of the component as a double.
 * - setMinMax: Configures minimum and maximum constraints for the input.
 * - setRequired: Marks the component as required and adjusts visibility indicators.
 * - addVariants: Adds style variants to the component based on Question metadata.
 * - validate: Ensures required fields are filled and marks invalid states.
 */
public class ElicitDoubleField extends ElicitComponent<NumberField> {

    /**
     * Represents a custom input field for handling double values in the Elicit survey system.
     * This class extends a base input field and provides additional functionality for
     * managing numeric input with step buttons and pre-filled values.
     *
     * @param answer The {@link Answer} object containing the display text and initial value
     *               for the double field. If the text value of the answer is not null or empty,
     *               it will be used to set the initial value of the field.
     */
    public ElicitDoubleField(Answer answer) {
        super(new NumberField(answer.displayText), answer);
        component.setStepButtonsVisible(true);
        if (answer.getTextValue() != null && !answer.getTextValue().isEmpty()) {
            setValue(answer);
        }
    }

    /**
     * Configures the bindings and validation rules for the input component
     * based on the provided {@link Answer} object.
     *
     * <p>This method sets up the following:
     * <ul>
     *   <li>If the question is required, the component is marked as required,
     *       and a required indicator is displayed. A validation rule is added
     *       to ensure the field is not left empty.</li>
     *   <li>If both minimum and maximum values are specified for the question,
     *       a validation rule is added to ensure the input value falls within
     *       the specified range.</li>
     *   <li>If only a minimum value is specified, it is set as the component's
     *       minimum value.</li>
     *   <li>If only a maximum value is specified, it is set as the component's
     *       maximum value.</li>
     * </ul>
     *
     * @param answer The {@link Answer} object containing the question's
     *               requirements, validation rules, and other metadata.
     */
    @Override
    void setBindings(Answer answer) {
        if (answer.question.required) {
            component.setRequired(answer.question.required);
            component.setRequiredIndicatorVisible(answer.question.required);
            this.binder.forField(component)
                    .asRequired(answer.question.validationText)
                    .bind(Answer::getDouble, Answer::setDouble);
        }

        if (answer.question.minValue != null && answer.question.maxValue != null) {
            this.binder.forField(component)
                    .withValidator(
                            value -> value < answer.question.minValue || value > answer.question.maxValue,
                            answer.question.validationText
                    )
                    .bind(Answer::getDouble, Answer::setDouble);
        }

        if (answer.question.minValue != null) {
            component.setMin(answer.question.minValue);
        }
        if (answer.question.maxValue != null) {
            component.setMax(answer.question.maxValue);
        }
    }

    /**
     * Sets the value of the component using the provided Answer object.
     * The method parses the text value of the Answer as a double and assigns it to the component.
     *
     * @param answer The Answer object containing the text value to be parsed and set.
     * @throws NumberFormatException if the text value of the Answer cannot be parsed as a double.
     */
    @Override
    void setValue(Answer answer) {
        component.setValue(Double.parseDouble(answer.getTextValue()));
    }


    /**
     * Adds theme variants to the component based on the specified question's variants.
     *
     * @param question the {@link Question} object containing the variant information.
     *                 If the question's variant is not null, this method checks for specific
     *                 theme variant names and applies them to the component.
     *                 <ul>
     *                     <li>If the variant contains {@code LUMO_ALIGN_CENTER}, the corresponding
     *                     theme variant is added to the component.</li>
     *                     <li>If the variant contains {@code LUMO_SMALL}, the corresponding
     *                     theme variant is added to the component.</li>
     *                     <li>If the variant contains {@code LUMO_HELPER_ABOVE_FIELD}, the corresponding
     *                     theme variant is added to the component.</li>
     *                 </ul>
     */
    @Override
    void addVariants(Question question) {
        if (question.variant != null) {
            if (question.variant.contains(TextFieldVariant.LUMO_ALIGN_CENTER.getVariantName())) {
                component.addThemeVariants(TextFieldVariant.LUMO_ALIGN_CENTER);
            }
            if (question.variant.contains(TextFieldVariant.LUMO_SMALL.getVariantName())) {
                component.addThemeVariants(TextFieldVariant.LUMO_SMALL);
            }
            if (question.variant.contains(TextFieldVariant.LUMO_HELPER_ABOVE_FIELD.getVariantName())) {
                component.addThemeVariants(TextFieldVariant.LUMO_HELPER_ABOVE_FIELD);
            }
        }
    }
}
