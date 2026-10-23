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

import com.elicitsoftware.i18n.Translations;
import com.elicitsoftware.i18n.ContentTexts;
import com.elicitsoftware.model.Answer;
import com.elicitsoftware.model.Question;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.TextFieldVariant;

import java.util.regex.Pattern;

/**
 * The ElicitEmailField class is a concrete implementation of the ElicitComponent
 * class, specialized for handling email fields. It encapsulates logic for
 * rendering an email field component and provides functionality to handle attributes
 * such as minimum and maximum lengths, required fields, validation, and theme variants.
 * <p>
 * This class is designed to work with instances of the EmailField component,
 * applying configurations dynamically based on the provided Answer and Question objects.
 */
public class ElicitEmailField extends ElicitComponent<EmailField> {
    // The local part takes the characters RFC 5322 allows unquoted, "+" among them.
    Pattern emailPattern = Pattern.compile("^[\\w.!#$%&'*+/=?^`{|}~-]+@([\\w-]+\\.)+[\\w-]{2,}$");

    /**
     * Represents a specialized email input field for the Elicit survey system.
     * This class extends functionality to handle email-specific input fields
     * and initializes the field with a given answer.
     *
     * <p>If the provided {@link Answer} object contains a non-null and non-empty
     * text value, the field is pre-populated with that value.</p>
     *
     * @param answer The {@link Answer} object containing the display text and
     *               initial value for the email field.
     */
    public ElicitEmailField(Answer answer, ContentTexts texts) {
        super(new EmailField(answer.label()), answer, texts);
        if (answer.getTextValue() != null && !answer.getTextValue().isEmpty()) {
            setValue(answer);
        }
    }

    /**
     * Configures the bindings for the email field component based on the provided answer.
     *
     * <p>This method sets up validation and binding rules for the email field. If the question
     * associated with the answer is marked as required, the component is configured to enforce
     * this requirement and display a required indicator. Additionally, if the question specifies
     * a minimum and maximum value, the method applies an email format validator using a regex
     * pattern to ensure the input matches the expected format.</p>
     *
     * @param answer The {@link Answer} object containing the question and its associated
     *               validation rules and requirements.
     */
    @Override
    void setBindings(Answer answer) {
        // One binding carries every rule: Binder keeps one binding per field, so a second
        // forField for this component would replace the required rule (UC-002).
        Binder.BindingBuilder<Answer, String> binding = binder.forField(component);
        if (answer.question.required) {
            component.setRequired(answer.question.required);
            component.setRequiredIndicatorVisible(answer.question.required);
            binding = binding.asRequired(requiredMessage(answer));
        }
        // UC-002: every email answer is checked for its form, whatever the question's min and
        // max say; an empty one is left to the required rule.
        String emailMessage = validationMessage(answer, Translations.get("validation.email"));
        binding.withValidator(email -> email == null || email.isEmpty() || emailPattern.matcher(email).matches(),
                        emailMessage)
                .bind(Answer::getTextValue, Answer::setTextValue);
        // The field's own format check runs before the binder's; it shows the same message.
        component.setI18n(new EmailField.EmailFieldI18n().setPatternErrorMessage(emailMessage));
    }

    /**
     * Sets the value of the email field component using the provided answer.
     *
     * @param answer The Answer object containing the text value to set in the component.
     */
    @Override
    void setValue(Answer answer) {
        component.setValue(answer.getTextValue());
    }

    /**
     * Adds theme variants to the component based on the specified question's variants.
     *
     * <p>This method checks if the provided {@code question} has a non-null {@code variant}
     * property. If so, it evaluates the presence of specific variant names and applies
     * the corresponding theme variants to the component.</p>
     *
     * @param question The {@link Question} object containing the variant information.
     *                 If {@code question.variant} is null, no action is taken.
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
