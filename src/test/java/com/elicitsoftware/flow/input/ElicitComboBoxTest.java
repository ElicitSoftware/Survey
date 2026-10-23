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
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.combobox.ComboBoxVariant;
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
 * UC-002: Answer Survey Questions — {@link ElicitComboBox} lists the answer's options with their
 * labels in the respondent's content language (UC-009 BR-005), selects the option whose coded
 * value is stored, and enforces the per-question required rule (BR-004).
 */
class ElicitComboBoxTest {

    private final SelectItem one = item("1", "One");
    private final SelectItem two = item("2", "Two");

    private Question options(boolean required) {
        return selectQuestion(required, null, List.of(one, two));
    }

    @Test
    void given_answer_when_constructed_then_itemsAndTranslatedLabelsAreSet() {
        ComboBox<SelectItem> box = new ElicitComboBox(answer(options(false), null),
                InputFieldSupport.translated()).component;

        assertEquals(List.of(one, two), box.getListDataView().getItems().toList());
        assertEquals("T:Two", box.getItemLabelGenerator().apply(two));
        assertEquals("Label", box.getLabel());
        assertFalse(box.isRequired());
    }

    @Test
    void given_storedCode_when_setValue_then_exactlyMatchingItemIsSelected() {
        Answer answer = answer(options(false), "2");
        ElicitComboBox wrapper = new ElicitComboBox(answer, ContentTexts.base());

        wrapper.setValue(answer);
        assertEquals(two, wrapper.component.getValue());

        answer.setTextValue("1");
        wrapper.setValue(answer);
        assertEquals(one, wrapper.component.getValue());
    }

    @Test
    void given_unknownOrNullCode_when_setValue_then_selectionIsUnchanged() {
        Answer answer = answer(options(false), "9");
        ElicitComboBox wrapper = new ElicitComboBox(answer, ContentTexts.base());

        wrapper.setValue(answer);
        assertNull(wrapper.component.getValue());

        wrapper.component.setValue(one);
        answer.setTextValue(null);
        wrapper.setValue(answer);
        assertEquals(one, wrapper.component.getValue());
    }

    @Test
    void given_requiredQuestion_when_validated_then_emptyFailsAndSelectionPasses() {
        ElicitComboBox wrapper = new ElicitComboBox(answer(options(true), null), ContentTexts.base());

        assertTrue(wrapper.component.isRequired());
        assertTrue(wrapper.component.isRequiredIndicatorVisible());
        assertEquals(List.of(InputFieldSupport.requiredFallback()), errors(wrapper.getBinder()));

        wrapper.component.setValue(one);
        assertTrue(wrapper.getBinder().validate().isOk());
    }

    @Test
    void given_allVariants_when_constructed_then_allThemesApplied() {
        Question question = options(false);
        question.variant = String.join(",", ComboBoxVariant.LUMO_ALIGN_LEFT.getVariantName(),
                ComboBoxVariant.LUMO_ALIGN_CENTER.getVariantName(), ComboBoxVariant.LUMO_ALIGN_RIGHT.getVariantName(),
                ComboBoxVariant.LUMO_SMALL.getVariantName(), ComboBoxVariant.LUMO_HELPER_ABOVE_FIELD.getVariantName());

        ComboBox<SelectItem> box = new ElicitComboBox(answer(question, null), ContentTexts.base()).component;

        assertEquals(Set.of(ComboBoxVariant.LUMO_ALIGN_LEFT.getVariantName(),
                ComboBoxVariant.LUMO_ALIGN_CENTER.getVariantName(),
                ComboBoxVariant.LUMO_ALIGN_RIGHT.getVariantName(),
                ComboBoxVariant.LUMO_SMALL.getVariantName(),
                ComboBoxVariant.LUMO_HELPER_ABOVE_FIELD.getVariantName()), themes(box));
    }

    @Test
    void given_unknownOrNullVariant_when_addVariants_then_noThemeApplied() {
        Question question = options(false);
        question.variant = "none";
        ElicitComboBox wrapper = new ElicitComboBox(answer(question, null), ContentTexts.base());
        assertTrue(themes(wrapper.component).isEmpty());

        Question noVariant = InputFieldSupport.plain();
        assertDoesNotThrow(() -> wrapper.addVariants(noVariant));
        assertTrue(themes(wrapper.component).isEmpty());
    }
}
