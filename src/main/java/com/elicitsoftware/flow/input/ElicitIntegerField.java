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
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextFieldVariant;
import com.vaadin.flow.data.binder.ValidationResult;
import com.vaadin.flow.data.binder.Validator;

/**
 * The ElicitIntegerField class is a specific implementation of the ElicitComponent
 * that encapsulates the IntegerField component. This class provides functionality
 * to configure and manage integer-based input fields defined by the Answer
 * and Question objects.
 * <p>
 * It enables setting of minimum and maximum values, configuring required properties,
 * adding visual variants, managing tooltip, placeholder, and validation settings,
 * as well as interpreting and applying states based on Answer and Question data models.
 */
public class ElicitIntegerField extends ElicitComponent<IntegerField> {

    /**
     * Constructs an instance of ElicitIntegerField with the specified answer.
     *
     * @param answer The Answer object containing the display text and value for the integer field.
     *               If the text value of the answer is not null or empty, it initializes the field with that value.
     */
    public ElicitIntegerField(Answer answer) {
        super(new IntegerField(answer.displayText), answer);
        component.setStepButtonsVisible(true);
        if (answer.getTextValue() != null && !answer.getTextValue().isEmpty()) {
            setValue(answer);
        }
    }

    /**
     * Sets the bindings for the integer field component based on the provided answer.
     *
     * <p>This method configures the component's required status, validation text,
     * and range validation based on the properties of the associated question in the answer object.</p>
     *
     * @param answer The {@link Answer} object containing the question and its properties
     *               used to configure the bindings for the integer field.
     *
     *               <ul>
     *                 <li>If the question is marked as required, the component is set to required,
     *                     and a required indicator is made visible. A required validation rule is also added.</li>
     *                 <li>If the question specifies a minimum and maximum value, a range validator is added
     *                     to ensure the input falls within the specified range.</li>
     *               </ul>
     */
    @Override
    void setBindings(Answer answer) {
        if (answer.question.required) {
            component.setRequired(answer.question.required);
            component.setRequiredIndicatorVisible(answer.question.required);
            this.binder.forField(component)
                    .asRequired(answer.question.validationText)
                    .bind(Answer::getInteger, Answer::setInteger);
        }

        if (answer.question.minValue != null && answer.question.maxValue != null) {
            this.binder.forField(component)
                    .withValidator(createRangeValidator(answer.question.minValue, answer.question.maxValue, answer.question.validationText))
                    .bind(Answer::getInteger, Answer::setInteger);
        }
    }

    /**
     * Sets the value of the component using the provided Answer object.
     * The method parses the text value of the Answer as an integer and assigns it to the component.
     *
     * @param answer The Answer object containing the text value to be set.
     *               The text value must be a valid integer, otherwise a NumberFormatException will be thrown.
     * @throws NumberFormatException if the text value of the Answer cannot be parsed as an integer.
     */
    @Override
    void setValue(Answer answer) {
        component.setValue(Integer.parseInt(answer.getTextValue()));
    }

    /**
     * Adds theme variants to the component based on the variants specified in the given question.
     *
     * @param question the {@link Question} object containing the variant information.
     *                 If the question's variant is not null and contains specific variant names,
     *                 the corresponding theme variants are added to the component.
     *                 Supported variants include:
     *                 <ul>
     *                     <li>{@code TextFieldVariant.LUMO_ALIGN_CENTER}</li>
     *                     <li>{@code TextFieldVariant.LUMO_SMALL}</li>
     *                     <li>{@code TextFieldVariant.LUMO_HELPER_ABOVE_FIELD}</li>
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

    /**
     * Creates a range validator for integer values.
     *
     * @param min          The minimum allowable value (inclusive).
     * @param max          The maximum allowable value (inclusive).
     * @param errorMessage The error message to return if the validation fails.
     * @return A {@link Validator} that checks if an integer value is within the specified range.
     */
    private Validator<Integer> createRangeValidator(Integer min, Integer max, String errorMessage) {
        return (value, context) -> {
            if (value == null) {
                return ValidationResult.error(errorMessage);
            }
            if (value >= min && value <= max) {
                return ValidationResult.ok();
            } else {
                return ValidationResult.error(errorMessage);
            }
        };
    }
}
