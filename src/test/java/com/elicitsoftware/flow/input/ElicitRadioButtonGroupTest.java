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
import com.elicitsoftware.model.Answer;
import com.elicitsoftware.model.Question;
import com.elicitsoftware.model.SelectItem;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.radiobutton.RadioGroupVariant;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static com.elicitsoftware.flow.input.ElicitAnswerFixtures.item;
import static com.elicitsoftware.flow.input.ElicitAnswerFixtures.selectQuestion;
import static com.elicitsoftware.flow.input.InputFieldSupport.answer;
import static com.elicitsoftware.flow.input.InputFieldSupport.errors;
import static com.elicitsoftware.flow.input.InputFieldSupport.themes;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UC-002: Answer Survey Questions — {@link ElicitRadioButtonGroup} lists the answer's options
 * with their labels in the respondent's content language (UC-009 BR-005), restores the stored
 * choice (case-insensitively on the coded value) and enforces the required rule (BR-004).
 */
class ElicitRadioButtonGroupTest {

    private final SelectItem yes = item("Y", "Yes");
    private final SelectItem no = item("N", "No");

    private Question options(boolean required) {
        return selectQuestion(required, null, List.of(yes, no));
    }

    @Test
    void given_storedCode_when_constructed_then_matchingItemIsSelectedIgnoringCase() {
        RadioButtonGroup<SelectItem> group = new ElicitRadioButtonGroup(answer(options(false), "n"),
                ContentTexts.base()).component;

        assertEquals(no, group.getValue());
        assertEquals(List.of(yes, no), group.getListDataView().getItems().toList());
        assertEquals("Label", group.getLabel());
        assertFalse(group.isRequired());
    }

    @Test
    void given_translatedTexts_when_constructed_then_optionLabelsComeFromTheTexts() {
        RadioButtonGroup<SelectItem> group = new ElicitRadioButtonGroup(answer(options(false), null),
                InputFieldSupport.translated()).component;

        assertEquals("T:Yes", group.getItemLabelGenerator().apply(yes));
    }

    @Test
    void given_noEmptyOrUnknownCode_when_constructed_then_nothingSelected() {
        assertNull(new ElicitRadioButtonGroup(answer(options(false), null), ContentTexts.base()).component.getValue());
        assertNull(new ElicitRadioButtonGroup(answer(options(false), ""), ContentTexts.base()).component.getValue());
        assertNull(new ElicitRadioButtonGroup(answer(options(false), "X"), ContentTexts.base()).component.getValue());
    }

    @Test
    void given_nullCode_when_setValue_then_selectionIsUnchanged() {
        Answer answer = answer(options(false), "Y");
        ElicitRadioButtonGroup wrapper = new ElicitRadioButtonGroup(answer, ContentTexts.base());
        assertEquals(yes, wrapper.component.getValue());

        answer.setTextValue(null);
        wrapper.setValue(answer);

        assertEquals(yes, wrapper.component.getValue());
    }

    @Test
    void given_requiredQuestion_when_validated_then_emptyFailsAndSelectionPasses() {
        ElicitRadioButtonGroup wrapper = new ElicitRadioButtonGroup(answer(options(true), null), ContentTexts.base());

        assertTrue(wrapper.component.isRequired());
        assertTrue(wrapper.component.isRequiredIndicatorVisible());
        assertEquals(List.of(InputFieldSupport.requiredFallback()), errors(wrapper.getBinder()));

        wrapper.component.setValue(no);
        assertTrue(wrapper.getBinder().validate().isOk());
    }

    @Test
    void given_allVariants_when_constructed_then_allThemesApplied() {
        Question question = options(false);
        question.variant = String.join(",", RadioGroupVariant.LUMO_VERTICAL.getVariantName(),
                RadioGroupVariant.LUMO_HELPER_ABOVE_FIELD.getVariantName());

        RadioButtonGroup<SelectItem> group = new ElicitRadioButtonGroup(answer(question, null),
                ContentTexts.base()).component;

        assertEquals(Set.of(RadioGroupVariant.LUMO_VERTICAL.getVariantName(),
                RadioGroupVariant.LUMO_HELPER_ABOVE_FIELD.getVariantName()), themes(group));
    }

    @Test
    void given_unknownOrNullVariant_when_addVariants_then_noThemeApplied() {
        Question question = options(false);
        question.variant = "none";
        ElicitRadioButtonGroup wrapper = new ElicitRadioButtonGroup(answer(question, null), ContentTexts.base());
        assertTrue(themes(wrapper.component).isEmpty());

        Question noVariant = InputFieldSupport.plain();
        assertDoesNotThrow(() -> wrapper.addVariants(noVariant));
        assertTrue(themes(wrapper.component).isEmpty());
    }
}
