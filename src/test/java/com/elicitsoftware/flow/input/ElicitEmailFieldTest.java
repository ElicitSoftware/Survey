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

import com.elicitsoftware.i18n.ContentTexts;
import com.elicitsoftware.model.Question;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.TextFieldVariant;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static com.elicitsoftware.flow.input.ElicitAnswerFixtures.question;
import static com.elicitsoftware.flow.input.InputFieldSupport.answer;
import static com.elicitsoftware.flow.input.InputFieldSupport.errors;
import static com.elicitsoftware.flow.input.InputFieldSupport.themes;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UC-002: Answer Survey Questions — {@link ElicitEmailField} restores the stored address and
 * enforces the per-question required and format rules (BR-004).
 */
class ElicitEmailFieldTest {

    @Test
    void given_storedAddress_when_constructed_then_valueIsRestored() {
        EmailField field = new ElicitEmailField(answer(InputFieldSupport.plain(), "ada@example.org"),
                ContentTexts.base()).component;

        assertEquals("ada@example.org", field.getValue());
        assertEquals("Label", field.getLabel());
        assertFalse(field.isRequired());
    }

    @Test
    void given_emptyStoredAddress_when_constructed_then_fieldIsEmpty() {
        assertEquals("", new ElicitEmailField(answer(InputFieldSupport.plain(), ""), ContentTexts.base())
                .component.getValue());
    }

    @Test
    void given_requiredQuestion_when_validated_then_emptyFailsAndFilledPasses() {
        ElicitEmailField wrapper = new ElicitEmailField(answer(question(true, null, null, null, null), null),
                ContentTexts.base());

        assertTrue(wrapper.component.isRequired());
        assertTrue(wrapper.component.isRequiredIndicatorVisible());
        assertEquals(List.of(InputFieldSupport.requiredFallback()), errors(wrapper.getBinder()));

        wrapper.component.setValue("ada@example.org");
        assertTrue(wrapper.getBinder().validate().isOk());
    }

    @Test
    void given_boundedQuestion_when_wellFormedAddress_then_validationPasses() {
        ElicitEmailField wrapper = new ElicitEmailField(answer(question(false, 1, 50, null, null), null),
                ContentTexts.base());

        wrapper.component.setValue("ada.lovelace@mail.example.org");
        assertTrue(wrapper.getBinder().validate().isOk());

        wrapper.component.setValue("a_b-c@ex-ample.co");
        assertTrue(wrapper.getBinder().validate().isOk());
    }

    @Test
    void given_onlyOneBound_when_plusAddressEntered_then_noElicitFormatRuleApplies() {
        // A plus-address passes the field's own pattern; only the Elicit format rule, which is
        // added when both bounds are authored, would reject it.
        ElicitEmailField maxOnly = new ElicitEmailField(answer(question(false, null, 50, null, null), null),
                ContentTexts.base());
        maxOnly.component.setValue("ada+survey@example.org");
        assertTrue(maxOnly.getBinder().validate().isOk());

        ElicitEmailField minOnly = new ElicitEmailField(answer(question(false, 1, null, null, null), null),
                ContentTexts.base());
        minOnly.component.setValue("ada+survey@example.org");
        assertTrue(minOnly.getBinder().validate().isOk());
    }

    @Test
    void given_allVariants_when_constructed_then_allThemesApplied() {
        String all = String.join(",", TextFieldVariant.LUMO_ALIGN_CENTER.getVariantName(),
                TextFieldVariant.LUMO_SMALL.getVariantName(), TextFieldVariant.LUMO_HELPER_ABOVE_FIELD.getVariantName());

        EmailField field = new ElicitEmailField(answer(question(false, null, null, all, null), null),
                ContentTexts.base()).component;

        assertEquals(Set.of(TextFieldVariant.LUMO_ALIGN_CENTER.getVariantName(),
                TextFieldVariant.LUMO_SMALL.getVariantName(),
                TextFieldVariant.LUMO_HELPER_ABOVE_FIELD.getVariantName()), themes(field));
    }

    @Test
    void given_unknownOrNullVariant_when_addVariants_then_noThemeApplied() {
        ElicitEmailField wrapper = new ElicitEmailField(answer(question(false, null, null, "none", null), null),
                ContentTexts.base());
        assertTrue(themes(wrapper.component).isEmpty());

        Question noVariant = InputFieldSupport.plain();
        assertDoesNotThrow(() -> wrapper.addVariants(noVariant));
        assertTrue(themes(wrapper.component).isEmpty());
    }
}
