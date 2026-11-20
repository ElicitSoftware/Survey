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
import com.vaadin.flow.component.checkbox.CheckboxGroup;
import com.vaadin.flow.component.checkbox.CheckboxGroupVariant;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UC-002: Answer Survey Questions — {@link ElicitCheckboxGroup} lists the answer's options with
 * their labels in the respondent's content language (UC-009 BR-005), restores the stored
 * selection and enforces the per-question required rule (BR-004).
 */
class ElicitCheckboxGroupTest {

    private final SelectItem red = item("R", "Red");
    private final SelectItem blue = item("B", "Blue");
    private final SelectItem green = item("G", "Green");

    private Question options(boolean required) {
        return selectQuestion(required, null, List.of(red, blue, green));
    }

    @Test
    void given_storedCodes_when_constructed_then_matchingItemsAreSelected() {
        CheckboxGroup<SelectItem> group = new ElicitCheckboxGroup(answer(options(false), "R,G"),
                ContentTexts.base()).component;

        assertEquals(Set.of(red, green), group.getValue());
        assertEquals(List.of(red, blue, green), group.getListDataView().getItems().toList());
        assertEquals("Label", group.getLabel());
        assertFalse(group.isRequired());
    }

    @Test
    void given_translatedTexts_when_constructed_then_optionLabelsComeFromTheTexts() {
        CheckboxGroup<SelectItem> base = new ElicitCheckboxGroup(answer(options(false), null),
                ContentTexts.base()).component;
        CheckboxGroup<SelectItem> translated = new ElicitCheckboxGroup(answer(options(false), null),
                InputFieldSupport.translated()).component;

        assertEquals("Red", base.getItemLabelGenerator().apply(red));
        assertEquals("T:Red", translated.getItemLabelGenerator().apply(red));
    }

    @Test
    void given_noOrEmptyStoredValue_when_constructed_then_nothingSelected() {
        assertTrue(new ElicitCheckboxGroup(answer(options(false), null), ContentTexts.base()).component.getValue().isEmpty());
        assertTrue(new ElicitCheckboxGroup(answer(options(false), ""), ContentTexts.base()).component.getValue().isEmpty());
    }

    @Test
    void given_storedValueCleared_when_setValue_then_selectionIsEmptied() {
        Answer answer = answer(options(false), "B");
        ElicitCheckboxGroup wrapper = new ElicitCheckboxGroup(answer, ContentTexts.base());
        assertEquals(Set.of(blue), wrapper.component.getValue());

        answer.setTextValue(null);
        wrapper.setValue(answer);

        assertTrue(wrapper.component.getValue().isEmpty());
    }

    @Test
    void given_requiredQuestion_when_validated_then_emptyFailsAndSelectionPasses() {
        ElicitCheckboxGroup wrapper = new ElicitCheckboxGroup(answer(options(true), null), ContentTexts.base());

        assertTrue(wrapper.component.isRequired());
        assertTrue(wrapper.component.isRequiredIndicatorVisible());
        assertEquals(List.of(InputFieldSupport.requiredFallback()), errors(wrapper.getBinder()));

        wrapper.component.setValue(Set.of(red));
        assertTrue(wrapper.getBinder().validate().isOk());
    }

    @Test
    void given_allVariants_when_constructed_then_allThemesApplied() {
        Question question = options(false);
        question.variant = String.join(",", CheckboxGroupVariant.LUMO_VERTICAL.getVariantName(),
                CheckboxGroupVariant.LUMO_HELPER_ABOVE_FIELD.getVariantName());

        CheckboxGroup<SelectItem> group = new ElicitCheckboxGroup(answer(question, null), ContentTexts.base()).component;

        assertEquals(Set.of(CheckboxGroupVariant.LUMO_VERTICAL.getVariantName(),
                CheckboxGroupVariant.LUMO_HELPER_ABOVE_FIELD.getVariantName()), themes(group));
    }

    @Test
    void given_unknownOrNullVariant_when_addVariants_then_noThemeApplied() {
        Question question = options(false);
        question.variant = "none";
        ElicitCheckboxGroup wrapper = new ElicitCheckboxGroup(answer(question, null), ContentTexts.base());
        assertTrue(themes(wrapper.component).isEmpty());

        Question noVariant = InputFieldSupport.plain();
        assertDoesNotThrow(() -> wrapper.addVariants(noVariant));
        assertTrue(themes(wrapper.component).isEmpty());
    }
}
