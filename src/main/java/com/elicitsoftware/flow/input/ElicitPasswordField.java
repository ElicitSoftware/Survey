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
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextFieldVariant;

/**
 * The ElicitPasswordField class is a specialized implementation of the ElicitComponent
 * for handling password fields within the Elicit framework. It provides extensions
 * for setting attributes, validation, and customization specific to password input fields.
 */
public class ElicitPasswordField extends ElicitComponent<PasswordField> {

    /**
     * Constructs an instance of ElicitPasswordField.
     *
     * @param answer The {@link Answer} object containing the display text
     *               to be used for the password field.
     */
    public ElicitPasswordField(Answer answer) {
        super(new PasswordField(answer.displayText), answer);
    }

    /**
     * Sets the value of the password field component based on the provided answer.
     * If the answer's text value is not null or empty, it recursively sets the value.
     *
     * @param answer The {@link Answer} object containing the text value to set.
     */
    @Override
    void setValue(Answer answer) {
        component.setValue(answer.getTextValue());
        if (answer.getTextValue() != null && !answer.getTextValue().isEmpty()) {
            setValue(answer);
        }
    }

    /**
     * Configures the bindings and validation rules for the password field component
     * based on the provided {@link Answer} object.
     *
     * @param answer The {@link Answer} object containing the question metadata and
     *               validation requirements for the password field.
     *
     *               <p>This method performs the following actions:</p>
     *               <ul>
     *                 <li>If the question is marked as required, sets the component as required,
     *                     makes the required indicator visible, and binds the component to the
     *                     {@link Answer#getTextValue()} and {@link Answer#setTextValue(String)} methods
     *                     with a validation message.</li>
     *                 <li>If both minimum and maximum value constraints are specified, adds a validator
     *                     to ensure the text value length falls within the specified range.</li>
     *                 <li>If a minimum value constraint is specified, sets the minimum length for the
     *                     component.</li>
     *                 <li>If a maximum value constraint is specified, sets the maximum length for the
     *                     component.</li>
     *               </ul>
     *
     *               <p>Note: The {@code minValue} and {@code maxValue} constraints are interpreted as
     *               the minimum and maximum lengths of the text value, respectively.</p>
     */
    @Override
    void setBindings(Answer answer) {
        if (answer.question.required) {
            component.setRequired(answer.question.required);
            component.setRequiredIndicatorVisible(answer.question.required);
            this.binder.forField(component)
                    .asRequired(answer.question.validationText)
                    .bind(Answer::getTextValue, Answer::setTextValue);
        }

        if (answer.question.minValue != null && answer.question.maxValue != null) {
            binder.forField(component)
                    .withValidator(textValue -> textValue.length() >= answer.question.minValue
                                    || textValue.length() <= answer.question.maxValue,
                            answer.question.validationText)
                    .bind(Answer::getTextValue, Answer::setTextValue);
        }

        if (answer.question.minValue != null) {
            component.setMinLength(answer.question.minValue);
        }
        if (answer.question.maxValue != null) {
            component.setMaxLength(answer.question.maxValue);
        }
    }

    /**
     * Adds theme variants to the component based on the specified question's variants.
     *
     * @param question the {@link Question} object containing the variants to be applied.
     *                 If the question's variant is not null, specific theme variants
     *                 such as LUMO_ALIGN_CENTER, LUMO_SMALL, and LUMO_HELPER_ABOVE_FIELD
     *                 are added to the component if they are present in the variant list.
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
