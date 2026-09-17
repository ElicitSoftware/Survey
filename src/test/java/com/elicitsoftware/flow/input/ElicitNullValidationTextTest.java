package com.elicitsoftware.flow.input;

/*-
 * ***LICENSE_START***
 * Elicit Survey
 * %%
 * Copyright (C) 2025 - 2026 The Regents of the University of Michigan - Rogel Cancer Center
 * %%
 * PolyForm Noncommercial License 1.0.0
 * <https://polyformproject.org/licenses/noncommercial/1.0.0>
 * ***LICENSE_END***
 */

import com.elicitsoftware.model.Answer;
import com.elicitsoftware.model.Question;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.common.QuarkusTestResource;
import com.elicitsoftware.PostgresTestResource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.elicitsoftware.flow.input.ElicitAnswerFixtures.answer;
import static com.elicitsoftware.flow.input.ElicitAnswerFixtures.question;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UC-002 (Answer Survey Questions), BR-004: a required question whose
 * {@code questions.validation_text} is null must still validate normally.
 * <p>
 * The column is nullable and most authored surveys leave it unset -- the ElicitDesigner
 * sample has it null on 14 of its 20 required questions. Passing that null straight to
 * Vaadin's {@code asRequired}/{@code withValidator} made {@code ValidationResult.create}
 * throw a NullPointerException from inside the binder: the write was aborted, no field
 * error was rendered, and the Next button silently did nothing, leaving the respondent
 * stuck on the page with no way to find out why.
 *
 * @see ElicitComponent#validationMessage(Answer, String)
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class ElicitNullValidationTextTest {

    @Test
    @DisplayName("required + null validation text: validates instead of throwing, and blocks empty")
    void requiredWithNullValidationText_doesNotThrow() {
        Question question = question(true, null, null, null, null);
        Answer answer = answer("1.1.1.1.1.1.1", "Title respondents will see", question, null);

        ElicitTextField wrapper = new ElicitTextField(answer);
        TextField field = wrapper.component;

        assertTrue(field.isRequiredIndicatorVisible());
        // Before the fix this call threw NullPointerException out of the binder.
        assertFalse(wrapper.getBinder().validate().isOk(),
                "an empty required field must fail validation, not blow up");

        field.setValue("Public Library Card Registration");
        assertTrue(wrapper.getBinder().validate().isOk(),
                "a filled required field must pass so the respondent can move on");
    }

    @Test
    @DisplayName("null validation text falls back to a generic message the respondent can read")
    void nullValidationText_producesGenericMessage() {
        Question question = question(true, null, null, null, null);
        Answer answer = answer("1.1.1.1.1.1.1", "Title", question, null);

        ElicitTextField wrapper = new ElicitTextField(answer);
        assertEquals("This question requires an answer.",
                ElicitComponent.requiredMessage(answer),
                "a null message is what Vaadin rejects; the fallback must be non-null");
        assertFalse(wrapper.getBinder().validate().isOk());
    }

    @Test
    @DisplayName("blank validation text is treated the same as null")
    void blankValidationText_fallsBack() {
        Question question = question(true, null, null, null, "   ");
        Answer answer = answer("1.1.1.1.1.1.1", "Title", question, null);

        assertEquals("This question requires an answer.", ElicitComponent.requiredMessage(answer));
    }

    @Test
    @DisplayName("authored validation text still wins")
    void authoredValidationTextIsPreserved() {
        Question question = question(true, null, null, null, "Name is required");
        Answer answer = answer("1.1.1.1.1.1.1", "Name", question, null);

        assertEquals("Name is required", ElicitComponent.requiredMessage(answer));
    }

    @Test
    @DisplayName("min/max length validator with null validation text also falls back")
    void lengthValidatorWithNullValidationText_doesNotThrow() {
        Question question = question(true, 2, 10, null, null);
        Answer answer = answer("1.1.1.1.1.1.2", "Short name", question, null);

        ElicitTextArea wrapper = new ElicitTextArea(answer);
        TextArea field = wrapper.component;

        assertEquals("Enter between 2 and 10 characters.", ElicitComponent.lengthMessage(answer));

        field.setValue("x");
        assertFalse(wrapper.getBinder().validate().isOk(), "too-short value must fail, not throw");

        field.setValue("LibraryReg");
        assertTrue(wrapper.getBinder().validate().isOk(), "in-range value must pass");
    }
}
