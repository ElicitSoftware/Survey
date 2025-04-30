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

import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextAreaVariant;
import com.elicitsoftware.model.Answer;
import com.elicitsoftware.model.Question;

/**
 * ElicitTextArea is a UI component that represents a text area input field.
 * It extends the generic ElicitComponent class, providing specific
 * implementations for handling textual input within a text area.
 * <p>
 * This class configures a TextArea component based on the properties
 * of an Answer and Question model. It supports features like setting
 * required fields, setting minimum and maximum lengths, applying
 * theme variants, and validating user input.
 * <p>
 * Key features include:
 * - Setting and updating the value of the TextArea component.
 * - Configuring minimum and maximum character lengths.
 * - Marking the field as required and showing an indicator if necessary.
 * - Adding visual or behavior-related theme variants.
 * - Event listener support for handling changes in the TextArea component.
 * - Validation to ensure the field adheres to required input constraints.
 */
public class ElicitTextArea extends ElicitComponent<TextArea> {

    /**
     * Constructs an instance of ElicitTextArea.
     *
     * @param answer The Answer object containing the display text to be shown in the text area.
     *               This is used to initialize the TextArea component with the provided display text.
     */
    public ElicitTextArea(Answer answer) {
        super(new TextArea(answer.displayText), answer);
    }

    /**
     * Configures the bindings and validation rules for the text area component
     * based on the properties of the provided {@link Answer} object.
     *
     * <p>This method sets up the following:
     * <ul>
     *   <li>If the question is marked as required, the component is set as required,
     *       and a required indicator is made visible. A validation rule is added to
     *       ensure the field is not empty, using the provided validation text.</li>
     *   <li>If both minimum and maximum value constraints are specified, a validator
     *       is added to ensure the text length falls within the specified range.</li>
     *   <li>If only a minimum value is specified, the minimum length of the component
     *       is set accordingly.</li>
     *   <li>If only a maximum value is specified, the maximum length of the component
     *       is set accordingly.</li>
     * </ul>
     *
     * @param answer The {@link Answer} object containing the question's constraints
     *               and validation rules to be applied to the text area component.
     */
    @Override
    void setBindings(Answer answer) {
        if (answer.question.required) {
            component.setRequired(answer.question.required);
            component.setRequiredIndicatorVisible(answer.question.required);
            binder.forField(component)
                    .asRequired(answer.question.validationText)
                    .bind(Answer::getTextValue, Answer::setTextValue);
        }

        if (answer.question.minValue != null && answer.question.maxValue != null) {
            binder.forField(component)
                    .withValidator(textValue -> textValue.length() >= answer.question.minValue || textValue.length() <= answer.question.maxValue, answer.question.validationText)
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
     * Sets the value of the component based on the provided {@link Answer}.
     * If the text value of the answer is not null or empty, it updates the value.
     *
     * @param answer the {@link Answer} object containing the text value to set
     */
    @Override
    void setValue(Answer answer) {
        component.setValue(answer.getTextValue());
        if (answer.getTextValue() != null && !answer.getTextValue().isEmpty()) {
            setValue(answer);
        }
    }

    /**
     * Adds theme variants to the component based on the specified question's variants.
     *
     * @param question the {@link Question} object containing the variants to be applied.
     *                 If the question's variant is not null, this method checks for specific
     *                 variant names and applies the corresponding theme variants to the component.
     *                 Supported variants include:
     *                 <ul>
     *                     <li>{@code TextAreaVariant.LUMO_ALIGN_CENTER}</li>
     *                     <li>{@code TextAreaVariant.LUMO_SMALL}</li>
     *                     <li>{@code TextAreaVariant.LUMO_HELPER_ABOVE_FIELD}</li>
     *                 </ul>
     */
    @Override
    void addVariants(Question question) {
        if (question.variant != null) {
            if (question.variant.contains(TextAreaVariant.LUMO_ALIGN_CENTER.getVariantName())) {
                component.addThemeVariants(TextAreaVariant.LUMO_ALIGN_CENTER);
            }
            if (question.variant.contains(TextAreaVariant.LUMO_SMALL.getVariantName())) {
                component.addThemeVariants(TextAreaVariant.LUMO_SMALL);
            }
            if (question.variant.contains(TextAreaVariant.LUMO_HELPER_ABOVE_FIELD.getVariantName())) {
                component.addThemeVariants(TextAreaVariant.LUMO_HELPER_ABOVE_FIELD);
            }
        }
    }
}
