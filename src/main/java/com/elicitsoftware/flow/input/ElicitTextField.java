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

import com.vaadin.flow.component.Unit;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.textfield.TextFieldVariant;
import com.elicitsoftware.model.Answer;
import com.elicitsoftware.model.Question;


/**
 * ElicitTextField is a subclass of ElicitComponent and is designed to manage TextField components.
 * It provides functionalities specific to handling text input fields within the Elicit framework,
 * including setting initial values, managing attributes, and applying validation logic.
 */
public class ElicitTextField extends ElicitComponent<TextField> {

    /**
     * Constructs an instance of ElicitTextField with the specified Answer object.
     * This class is a specialized input field for handling text-based answers.
     *
     * @param answer The Answer object containing the display text and value for the text field.
     *               The display text is used to set the width of the component, and if the
     *               text value is not null or empty, it initializes the field with the given value.
     */
    public ElicitTextField(Answer answer) {
        super(new TextField(answer.displayText), answer);
        //These are specific to TextFields
        component.setWidth(answer.displayText.length(), Unit.CH);
        if (answer.getTextValue() != null && !answer.getTextValue().isEmpty()) {
            setValue(answer);
        }
    }

    /**
     * Configures the bindings for the text field component based on the provided answer's question properties.
     *
     * <p>This method sets up validation and binding rules for the text field component. It ensures that
     * the component adheres to the requirements and constraints specified in the associated question.
     *
     * <ul>
     *   <li>If the question is marked as required, the component is set to required, and a required
     *       indicator is made visible. Additionally, a validation rule is added to enforce the requirement.</li>
     *   <li>If the question specifies minimum and maximum value constraints, a validator is added to
     *       ensure the text length falls within the specified range.</li>
     * </ul>
     *
     * @param answer The {@link Answer} object containing the question and its associated properties.
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
                    .withValidator(text -> text.length() >= answer.question.minValue && text.length() <= answer.question.maxValue, answer.question.validationText)
                    .bind(Answer::getTextValue, Answer::setTextValue);
        }
    }

    /**
     * Sets the value of the text field component using the provided answer.
     *
     * @param answer The Answer object containing the text value to set.
     *               The text value is retrieved using {@code answer.getTextValue()}.
     */
    @Override
    void setValue(Answer answer) {
        component.setValue(answer.getTextValue());
    }

    /**
     * Adds theme variants to the component based on the specified question's variants.
     *
     * @param question The {@link Question} object containing the variants to be applied.
     *                 If the question's variant list is not empty, this method checks for
     *                 specific variants and applies the corresponding theme variants to
     *                 the component.
     *                 <p>
     *                 Supported variants:
     *                 <ul>
     *                     <li>{@code TextFieldVariant.LUMO_ALIGN_CENTER}</li>
     *                     <li>{@code TextFieldVariant.LUMO_SMALL}</li>
     *                     <li>{@code TextFieldVariant.LUMO_HELPER_ABOVE_FIELD}</li>
     *                 </ul>
     */
    @Override
    void addVariants(Question question) {
        if (!question.variant.isEmpty()) {
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
