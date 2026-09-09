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
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextFieldVariant;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static com.elicitsoftware.flow.input.ElicitAnswerFixtures.answer;
import static com.elicitsoftware.flow.input.ElicitAnswerFixtures.question;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UC-002 (Answer Survey Questions), BR-004: coverage of ElicitIntegerField and
 * ElicitDoubleField - both parse their initial value from Answer.getTextValue(), enforce
 * required/range rules via the shared Binder, and fall back to a null component value
 * when the saved text can't be parsed.
 */
@QuarkusTest
class ElicitNumericFieldsTest {

    @Test
    void integerField_construction_parsesInitialValueFromAnswer() {
        Question question = question(false, null, null, null, null);
        Answer answer = answer("1.1.1.1.2.1.1", "Age", question, "42");

        IntegerField field = new ElicitIntegerField(answer).component;

        assertEquals(42, field.getValue());
        assertTrue(field.isStepButtonsVisible());
    }

    @Test
    void integerField_missingOrUnparseableTextValue_setsNullRatherThanThrowing() {
        Question question = question(false, null, null, null, null);
        Answer blank = answer("1.1.1.1.2.1.2", "Age", question, null);
        Answer literalNull = answer("1.1.1.1.2.1.3", "Age", question, "null");

        assertNull(new ElicitIntegerField(blank).component.getValue());
        assertNull(new ElicitIntegerField(literalNull).component.getValue());
    }

    @Test
    void integerField_required_blocksEmptyValue_allowsFilledValue() {
        Question question = question(true, null, null, null, "Age is required");
        Answer answer = answer("1.1.1.1.2.1.4", "Age", question, null);

        ElicitIntegerField wrapper = new ElicitIntegerField(answer);
        IntegerField field = wrapper.component;

        assertTrue(field.isRequiredIndicatorVisible());
        assertFalse(wrapper.getBinder().validate().isOk());

        field.setValue(30);
        assertTrue(wrapper.getBinder().validate().isOk());
    }

    @Test
    void integerField_rangeValidator_rejectsOutOfRangeAndNullValues() {
        Question question = question(false, 1, 120, null, "Must be between 1 and 120");
        Answer answer = answer("1.1.1.1.2.1.5", "Age", question, null);

        ElicitIntegerField wrapper = new ElicitIntegerField(answer);
        IntegerField field = wrapper.component;

        assertFalse(wrapper.getBinder().validate().isOk(), "no value at all must fail the range validator");

        field.setValue(0);
        assertFalse(wrapper.getBinder().validate().isOk(), "below the minimum must fail");

        field.setValue(200);
        assertFalse(wrapper.getBinder().validate().isOk(), "above the maximum must fail");

        field.setValue(30);
        assertTrue(wrapper.getBinder().validate().isOk(), "within range must pass");
    }

    @Test
    void integerField_variants_appliedFromQuestion() {
        String variants = TextFieldVariant.LUMO_ALIGN_CENTER.getVariantName();
        Question question = question(false, null, null, variants, null);
        Answer answer = answer("1.1.1.1.2.1.6", "Age", question, null);

        IntegerField field = new ElicitIntegerField(answer).component;

        assertTrue(field.getThemeNames().contains(TextFieldVariant.LUMO_ALIGN_CENTER.getVariantName()));
    }

    @Test
    void doubleField_construction_parsesInitialValueFromAnswer() {
        Question question = question(false, null, null, null, null);
        Answer answer = answer("1.1.1.1.2.2.1", "Height", question, "5.75");

        NumberField field = new ElicitDoubleField(answer).component;

        assertEquals(5.75, field.getValue());
        assertTrue(field.isStepButtonsVisible());
    }

    @Test
    void doubleField_missingOrUnparseableTextValue_setsNullRatherThanThrowing() {
        Question question = question(false, null, null, null, null);
        Answer blank = answer("1.1.1.1.2.2.2", "Height", question, null);
        Answer literalNull = answer("1.1.1.1.2.2.6", "Height", question, "null");

        assertNull(new ElicitDoubleField(blank).component.getValue());
        assertNull(new ElicitDoubleField(literalNull).component.getValue());
    }

    @Test
    void doubleField_required_blocksEmptyValue_allowsFilledValue() {
        Question question = question(true, null, null, null, "Height is required");
        Answer answer = answer("1.1.1.1.2.2.3", "Height", question, null);

        ElicitDoubleField wrapper = new ElicitDoubleField(answer);
        NumberField field = wrapper.component;

        assertFalse(wrapper.getBinder().validate().isOk());

        field.setValue(5.5);
        assertTrue(wrapper.getBinder().validate().isOk());
    }

    @Test
    void doubleField_rangeValidator_setsComponentLimitsAndValidates() {
        Question question = question(false, 1, 10, null, "Must be between 1 and 10");
        Answer answer = answer("1.1.1.1.2.2.4", "Height", question, null);

        ElicitDoubleField wrapper = new ElicitDoubleField(answer);
        NumberField field = wrapper.component;

        assertEquals(1.0, field.getMin());
        assertEquals(10.0, field.getMax());

        field.setValue(0.5);
        assertFalse(wrapper.getBinder().validate().isOk(), "below the minimum must fail");

        field.setValue(20.0);
        assertFalse(wrapper.getBinder().validate().isOk(), "above the maximum must fail");

        field.setValue(5.0);
        assertTrue(wrapper.getBinder().validate().isOk(), "within range must pass");
    }

    @Test
    void doubleField_variants_appliedFromQuestion() {
        String variants = TextFieldVariant.LUMO_HELPER_ABOVE_FIELD.getVariantName();
        Question question = question(false, null, null, variants, null);
        Answer answer = answer("1.1.1.1.2.2.5", "Height", question, null);

        NumberField field = new ElicitDoubleField(answer).component;

        assertTrue(field.getThemeNames().contains(TextFieldVariant.LUMO_HELPER_ABOVE_FIELD.getVariantName()));
    }
}
