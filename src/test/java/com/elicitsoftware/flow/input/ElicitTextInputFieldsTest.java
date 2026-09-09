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
import com.vaadin.flow.component.HasValidation;
import com.vaadin.flow.component.shared.HasTooltip;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextAreaVariant;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.textfield.TextFieldVariant;
import com.vaadin.flow.data.binder.BinderValidationStatus;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static com.elicitsoftware.flow.input.ElicitAnswerFixtures.answer;
import static com.elicitsoftware.flow.input.ElicitAnswerFixtures.question;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UC-002 (Answer Survey Questions), BR-004: coverage of the text-shaped Elicit* wrappers
 * (ElicitTextField, ElicitTextArea, ElicitEmailField, ElicitPasswordField) - the base
 * ElicitComponent wiring (id, css class, tooltip, placeholder, validation message) is
 * asserted once via ElicitTextField since every wrapper shares that constructor logic.
 */
@QuarkusTest
class ElicitTextInputFieldsTest {

    @Test
    void textField_construction_wiresBaseAttributesWidthAndInitialValue() {
        Question question = question(false, null, null, null, "Enter your full name",
                "e.g. Ada Lovelace", "Invalid name");
        Answer answer = answer("1.1.1.1.1.1.1", "Full Name", question, "Ada Lovelace");

        TextField field = new ElicitTextField(answer).component;

        assertEquals("1.1.1.1.1.1.1", field.getId().orElse(null));
        assertTrue(field.hasClassName("elicit-input-field"));
        assertEquals("Ada Lovelace", field.getValue());
        assertEquals(answer.displayText.length() + ".0ch", field.getWidth());
        assertEquals("Enter your full name", ((HasTooltip) field).getTooltip().getText());
        assertEquals("e.g. Ada Lovelace", field.getPlaceholder());
        assertEquals("Invalid name", ((HasValidation) field).getErrorMessage());
    }

    @Test
    void textField_required_blocksEmptyValue_allowsFilledValue() {
        Question question = question(true, null, null, null, "Name is required");
        Answer answer = answer("1.1.1.1.1.1.2", "Full Name", question, null);

        ElicitTextField wrapper = new ElicitTextField(answer);
        TextField field = wrapper.component;

        assertTrue(field.isRequiredIndicatorVisible());
        assertFalse(wrapper.getBinder().validate().isOk(), "empty required field must fail validation");

        field.setValue("Ada");
        assertTrue(wrapper.getBinder().validate().isOk(), "filled required field must pass validation");
    }

    @Test
    void textField_lengthRange_validatesMinAndMax() {
        Question question = question(false, 3, 5, null, "Must be 3-5 characters");
        Answer answer = answer("1.1.1.1.1.1.3", "Code", question, null);

        ElicitTextField wrapper = new ElicitTextField(answer);
        TextField field = wrapper.component;

        field.setValue("ab");
        assertFalse(wrapper.getBinder().validate().isOk(), "below minimum length must fail");

        field.setValue("abcd");
        assertTrue(wrapper.getBinder().validate().isOk(), "value within range must pass");

        field.setValue("abcdef");
        assertFalse(wrapper.getBinder().validate().isOk(), "above maximum length must fail");
    }

    @Test
    void textField_variants_appliedFromQuestion() {
        String variants = String.join(",", TextFieldVariant.LUMO_ALIGN_CENTER.getVariantName(),
                TextFieldVariant.LUMO_SMALL.getVariantName(),
                TextFieldVariant.LUMO_HELPER_ABOVE_FIELD.getVariantName());
        Question question = question(false, null, null, variants, null);
        Answer answer = answer("1.1.1.1.1.1.4", "Field", question, null);

        TextField field = new ElicitTextField(answer).component;

        assertTrue(field.getThemeNames().contains(TextFieldVariant.LUMO_ALIGN_CENTER.getVariantName()));
        assertTrue(field.getThemeNames().contains(TextFieldVariant.LUMO_SMALL.getVariantName()));
        assertTrue(field.getThemeNames().contains(TextFieldVariant.LUMO_HELPER_ABOVE_FIELD.getVariantName()));
    }

    @Test
    void textArea_lengthRange_setsComponentLimitsAndValidates() {
        Question question = question(false, 2, 4, null, "Must be 2-4 characters");
        Answer answer = answer("1.1.1.1.1.2.1", "Notes", question, null);

        ElicitTextArea wrapper = new ElicitTextArea(answer);
        TextArea area = wrapper.component;

        assertEquals(2, area.getMinLength());
        assertEquals(4, area.getMaxLength());

        area.setValue("a");
        assertFalse(wrapper.getBinder().validate().isOk());

        area.setValue("abc");
        assertTrue(wrapper.getBinder().validate().isOk());
    }

    @Test
    void textArea_variants_appliedFromQuestion() {
        String variants = TextAreaVariant.LUMO_SMALL.getVariantName();
        Question question = question(false, null, null, variants, null);
        Answer answer = answer("1.1.1.1.1.2.2", "Notes", question, null);

        TextArea area = new ElicitTextArea(answer).component;

        assertTrue(area.getThemeNames().contains(TextAreaVariant.LUMO_SMALL.getVariantName()));
    }

    @Test
    void emailField_required_blocksEmptyValue_allowsFilledValue() {
        Question question = question(true, null, null, null, "Email is required");
        Answer answer = answer("1.1.1.1.1.3.1", "Email", question, null);

        ElicitEmailField wrapper = new ElicitEmailField(answer);
        EmailField field = wrapper.component;

        assertFalse(wrapper.getBinder().validate().isOk());

        field.setValue("ada@example.com");
        assertTrue(wrapper.getBinder().validate().isOk());
    }

    @Test
    void emailField_minAndMaxPresent_validatesEmailFormat() {
        // Both minValue and maxValue being non-null is what the wrapper uses to trigger
        // its email-format validator - the two are otherwise unused for an EmailField.
        Question question = question(false, 0, 255, null, "Not a valid email address");
        Answer answer = answer("1.1.1.1.1.3.2", "Email", question, null);

        ElicitEmailField wrapper = new ElicitEmailField(answer);
        EmailField field = wrapper.component;

        field.setValue("not-an-email");
        assertFalse(wrapper.getBinder().validate().isOk(), "malformed address must fail the pattern validator");

        field.setValue("ada@example.com");
        assertTrue(wrapper.getBinder().validate().isOk(), "well-formed address must pass");
    }

    @Test
    void emailField_variants_appliedFromQuestion() {
        String variants = TextFieldVariant.LUMO_ALIGN_CENTER.getVariantName();
        Question question = question(false, null, null, variants, null);
        Answer answer = answer("1.1.1.1.1.3.3", "Email", question, null);

        EmailField field = new ElicitEmailField(answer).component;

        assertTrue(field.getThemeNames().contains(TextFieldVariant.LUMO_ALIGN_CENTER.getVariantName()));
    }

    @Test
    void passwordField_neverPrefillsValueFromAnswer() {
        // Unlike the other text-shaped wrappers, ElicitPasswordField's constructor
        // deliberately never calls setValue(answer) even when the answer already has a
        // saved textValue, so a previously-saved password is never echoed back into the UI.
        Question question = question(false, null, null, null, null);
        Answer answer = answer("1.1.1.1.1.4.1", "Password", question, "super-secret");

        PasswordField field = new ElicitPasswordField(answer).component;

        assertEquals("", field.getValue(), "the saved password must not be pre-filled into the field");
    }

    @Test
    void passwordField_lengthRange_setsComponentLimitsAndValidates() {
        Question question = question(false, 6, 10, null, "Must be 6-10 characters");
        Answer answer = answer("1.1.1.1.1.4.2", "Password", question, null);

        ElicitPasswordField wrapper = new ElicitPasswordField(answer);
        PasswordField field = wrapper.component;

        assertEquals(6, field.getMinLength());
        assertEquals(10, field.getMaxLength());

        field.setValue("short");
        assertFalse(wrapper.getBinder().validate().isOk());

        field.setValue("longenough");
        assertTrue(wrapper.getBinder().validate().isOk());
    }

    @Test
    void passwordField_variants_appliedFromQuestion() {
        String variants = TextFieldVariant.LUMO_SMALL.getVariantName();
        Question question = question(false, null, null, variants, null);
        Answer answer = answer("1.1.1.1.1.4.3", "Password", question, null);

        PasswordField field = new ElicitPasswordField(answer).component;

        assertTrue(field.getThemeNames().contains(TextFieldVariant.LUMO_SMALL.getVariantName()));
    }
}
