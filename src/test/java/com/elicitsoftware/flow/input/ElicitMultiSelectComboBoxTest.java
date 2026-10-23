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
import com.vaadin.flow.component.combobox.MultiSelectComboBox;
import com.vaadin.flow.component.combobox.MultiSelectComboBoxVariant;
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
 * UC-002: Answer Survey Questions — {@link ElicitMultiSelectComboBox} lists the answer's
 * options with their labels in the respondent's content language (UC-009 BR-005), restores the
 * stored selection and enforces the per-question required rule (BR-004).
 */
class ElicitMultiSelectComboBoxTest {

    private final SelectItem chess = item("CHESS", "Chess");
    private final SelectItem go = item("GO", "Go");
    private final SelectItem poker = item("POKER", "Poker");

    private Question options(boolean required) {
        return selectQuestion(required, null, List.of(chess, go, poker));
    }

    @Test
    void given_storedCodes_when_constructed_then_matchingItemsAreSelected() {
        MultiSelectComboBox<SelectItem> box = new ElicitMultiSelectComboBox(answer(options(false), "POKER,CHESS"),
                ContentTexts.base()).component;

        assertEquals(Set.of(chess, poker), box.getValue());
        assertEquals("Label", box.getLabel());
        assertFalse(box.isRequired());
    }

    @Test
    void given_translatedTexts_when_constructed_then_optionLabelsComeFromTheTexts() {
        MultiSelectComboBox<SelectItem> box = new ElicitMultiSelectComboBox(answer(options(false), null),
                InputFieldSupport.translated()).component;

        assertEquals("T:Go", box.getItemLabelGenerator().apply(go));
        assertEquals(List.of(chess, go, poker), box.getListDataView().getItems().toList());
    }

    @Test
    void given_noOrEmptyStoredValue_when_constructed_then_nothingSelected() {
        assertTrue(new ElicitMultiSelectComboBox(answer(options(false), null), ContentTexts.base()).component.getValue().isEmpty());
        assertTrue(new ElicitMultiSelectComboBox(answer(options(false), ""), ContentTexts.base()).component.getValue().isEmpty());
    }

    @Test
    void given_storedValueCleared_when_setValue_then_selectionIsEmptied() {
        Answer answer = answer(options(false), "GO");
        ElicitMultiSelectComboBox wrapper = new ElicitMultiSelectComboBox(answer, ContentTexts.base());
        assertEquals(Set.of(go), wrapper.component.getValue());

        answer.setTextValue(null);
        wrapper.setValue(answer);

        assertTrue(wrapper.component.getValue().isEmpty());
    }

    @Test
    void given_requiredQuestion_when_validated_then_emptyFailsAndSelectionPasses() {
        ElicitMultiSelectComboBox wrapper = new ElicitMultiSelectComboBox(answer(options(true), null),
                ContentTexts.base());

        assertTrue(wrapper.component.isRequired());
        assertTrue(wrapper.component.isRequiredIndicatorVisible());
        assertEquals(List.of(InputFieldSupport.requiredFallback()), errors(wrapper.getBinder()));

        wrapper.component.setValue(Set.of(go));
        assertTrue(wrapper.getBinder().validate().isOk());
    }

    @Test
    void given_allVariants_when_constructed_then_allThemesApplied() {
        Question question = options(false);
        question.variant = String.join(",", MultiSelectComboBoxVariant.LUMO_ALIGN_LEFT.getVariantName(),
                MultiSelectComboBoxVariant.LUMO_ALIGN_CENTER.getVariantName(),
                MultiSelectComboBoxVariant.LUMO_ALIGN_RIGHT.getVariantName(),
                MultiSelectComboBoxVariant.LUMO_SMALL.getVariantName(),
                MultiSelectComboBoxVariant.LUMO_HELPER_ABOVE_FIELD.getVariantName());

        MultiSelectComboBox<SelectItem> box = new ElicitMultiSelectComboBox(answer(question, null),
                ContentTexts.base()).component;

        assertEquals(Set.of(MultiSelectComboBoxVariant.LUMO_ALIGN_LEFT.getVariantName(),
                MultiSelectComboBoxVariant.LUMO_ALIGN_CENTER.getVariantName(),
                MultiSelectComboBoxVariant.LUMO_ALIGN_RIGHT.getVariantName(),
                MultiSelectComboBoxVariant.LUMO_SMALL.getVariantName(),
                MultiSelectComboBoxVariant.LUMO_HELPER_ABOVE_FIELD.getVariantName()), themes(box));
    }

    @Test
    void given_unknownOrNullVariant_when_addVariants_then_noThemeApplied() {
        Question question = options(false);
        question.variant = "none";
        ElicitMultiSelectComboBox wrapper = new ElicitMultiSelectComboBox(answer(question, null), ContentTexts.base());
        assertTrue(themes(wrapper.component).isEmpty());

        Question noVariant = InputFieldSupport.plain();
        assertDoesNotThrow(() -> wrapper.addVariants(noVariant));
        assertTrue(themes(wrapper.component).isEmpty());
    }
}
