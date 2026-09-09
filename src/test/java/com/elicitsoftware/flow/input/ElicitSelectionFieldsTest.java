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
import com.elicitsoftware.model.SelectItem;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.checkbox.CheckboxGroup;
import com.vaadin.flow.component.checkbox.CheckboxGroupVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.combobox.ComboBoxVariant;
import com.vaadin.flow.component.combobox.MultiSelectComboBox;
import com.vaadin.flow.component.combobox.MultiSelectComboBoxVariant;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.radiobutton.RadioGroupVariant;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static com.elicitsoftware.flow.input.ElicitAnswerFixtures.answer;
import static com.elicitsoftware.flow.input.ElicitAnswerFixtures.item;
import static com.elicitsoftware.flow.input.ElicitAnswerFixtures.question;
import static com.elicitsoftware.flow.input.ElicitAnswerFixtures.selectQuestion;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UC-002 (Answer Survey Questions), BR-004: coverage of the selection-shaped Elicit*
 * wrappers - ElicitCheckbox, ElicitCheckboxGroup, ElicitRadioButtonGroup, ElicitComboBox,
 * and ElicitMultiSelectComboBox. The group/combo wrappers all populate their items from
 * Question.selectGroup.selectItems and label them with SelectItem.displayText.
 */
@QuarkusTest
class ElicitSelectionFieldsTest {

    @Test
    void checkbox_construction_parsesInitialBooleanFromAnswer() {
        Question question = question(false, null, null, null, null);
        Answer checked = answer("1.1.1.1.3.1.1", "I agree to the terms", question, "true");
        Answer unchecked = answer("1.1.1.1.3.1.2", "I agree to the terms", question, "false");

        Checkbox checkedBox = new ElicitCheckbox(checked).component;
        Checkbox uncheckedBox = new ElicitCheckbox(unchecked).component;

        assertTrue(checkedBox.getValue());
        assertFalse(uncheckedBox.getValue());
        assertEquals("I agree to the terms", checkedBox.getLabel());
    }

    @Test
    void checkboxGroup_construction_populatesItemsAndInitialSelection() {
        SelectItem red = item("R", "Red");
        SelectItem blue = item("B", "Blue");
        Question question = selectQuestion(false, null, List.of(red, blue));
        Answer answer = answer("1.1.1.1.3.2.1", "Favorite colors", question, "R,B");

        CheckboxGroup<SelectItem> group = new ElicitCheckboxGroup(answer).component;

        assertEquals(Set.of(red, blue), group.getValue());
        assertEquals("Red", group.getItemLabelGenerator().apply(red));
    }

    @Test
    void checkboxGroup_required_blocksEmptySelection_allowsSelection() {
        SelectItem red = item("R", "Red");
        Question question = selectQuestion(true, "Pick at least one color", List.of(red));
        Answer answer = answer("1.1.1.1.3.2.2", "Favorite colors", question, null);

        ElicitCheckboxGroup wrapper = new ElicitCheckboxGroup(answer);
        CheckboxGroup<SelectItem> group = wrapper.component;

        assertTrue(group.isRequiredIndicatorVisible());
        assertFalse(wrapper.getBinder().validate().isOk());

        group.setValue(Set.of(red));
        assertTrue(wrapper.getBinder().validate().isOk());
    }

    @Test
    void checkboxGroup_variants_appliedFromQuestion() {
        SelectItem red = item("R", "Red");
        Question question = selectQuestion(false, null, List.of(red));
        question.variant = String.join(",", CheckboxGroupVariant.LUMO_VERTICAL.getVariantName(),
                CheckboxGroupVariant.LUMO_HELPER_ABOVE_FIELD.getVariantName());
        Answer answer = answer("1.1.1.1.3.2.3", "Favorite colors", question, null);

        CheckboxGroup<SelectItem> group = new ElicitCheckboxGroup(answer).component;

        assertTrue(group.getThemeNames().contains(CheckboxGroupVariant.LUMO_VERTICAL.getVariantName()));
        assertTrue(group.getThemeNames().contains(CheckboxGroupVariant.LUMO_HELPER_ABOVE_FIELD.getVariantName()));
    }

    @Test
    void radioButtonGroup_construction_populatesItemsAndInitialSelection() {
        SelectItem yes = item("Y", "Yes");
        SelectItem no = item("N", "No");
        Question question = selectQuestion(false, null, List.of(yes, no));
        Answer answer = answer("1.1.1.1.3.3.1", "Do you smoke?", question, "y");

        RadioButtonGroup<SelectItem> group = new ElicitRadioButtonGroup(answer).component;

        assertEquals(yes, group.getValue(), "matching must be case-insensitive on the coded value");
    }

    @Test
    void radioButtonGroup_required_blocksEmptySelection_allowsSelection() {
        SelectItem yes = item("Y", "Yes");
        Question question = selectQuestion(true, "An answer is required", List.of(yes));
        Answer answer = answer("1.1.1.1.3.3.2", "Do you smoke?", question, null);

        ElicitRadioButtonGroup wrapper = new ElicitRadioButtonGroup(answer);
        RadioButtonGroup<SelectItem> group = wrapper.component;

        assertTrue(group.isRequiredIndicatorVisible());
        assertFalse(wrapper.getBinder().validate().isOk());

        group.setValue(yes);
        assertTrue(wrapper.getBinder().validate().isOk());
    }

    @Test
    void radioButtonGroup_variants_appliedFromQuestion() {
        SelectItem yes = item("Y", "Yes");
        Question question = selectQuestion(false, null, List.of(yes));
        question.variant = RadioGroupVariant.LUMO_VERTICAL.getVariantName();
        Answer answer = answer("1.1.1.1.3.3.3", "Do you smoke?", question, null);

        RadioButtonGroup<SelectItem> group = new ElicitRadioButtonGroup(answer).component;

        assertTrue(group.getThemeNames().contains(RadioGroupVariant.LUMO_VERTICAL.getVariantName()));
    }

    @Test
    void comboBox_construction_populatesItemsAndLabelGenerator() {
        SelectItem us = item("US", "United States");
        Question question = selectQuestion(false, null, List.of(us));
        Answer answer = answer("1.1.1.1.3.4.1", "Country", question, null);

        ComboBox<SelectItem> combo = new ElicitComboBox(answer).component;

        assertEquals("United States", combo.getItemLabelGenerator().apply(us));
    }

    @Test
    void comboBox_construction_doesNotPrefillValueFromAnswer() {
        // Unlike every other selection-based wrapper here (RadioButtonGroup, CheckboxGroup,
        // MultiSelectComboBox all call setValue(answer) in their constructor when a saved
        // textValue exists), ElicitComboBox's constructor never does - a previously-answered
        // combo box question currently renders blank. Documenting this as the actual current
        // behavior (looks like an unintentional gap, not a deliberate design choice) rather
        // than silently changing it, since fixing it wasn't in scope for this test pass.
        SelectItem us = item("US", "United States");
        Question question = selectQuestion(false, null, List.of(us));
        Answer answer = answer("1.1.1.1.3.4.2", "Country", question, "US");

        ComboBox<SelectItem> combo = new ElicitComboBox(answer).component;

        assertNull(combo.getValue(), "ElicitComboBox construction currently leaves a saved answer unselected");
    }

    @Test
    void comboBox_setValue_matchesByCodedValue() {
        SelectItem us = item("US", "United States");
        SelectItem ca = item("CA", "Canada");
        Question question = selectQuestion(false, null, List.of(us, ca));
        Answer answer = answer("1.1.1.1.3.4.5", "Country", question, "CA");

        // Package-private setValue(Answer) itself does match by coded value correctly -
        // it's simply never invoked from the constructor (see the test above).
        ElicitComboBox wrapper = new ElicitComboBox(answer);
        wrapper.setValue(answer);

        assertEquals(ca, wrapper.component.getValue());
    }

    @Test
    void comboBox_required_blocksEmptySelection_allowsSelection() {
        SelectItem us = item("US", "United States");
        Question question = selectQuestion(true, "A country is required", List.of(us));
        Answer answer = answer("1.1.1.1.3.4.3", "Country", question, null);

        ElicitComboBox wrapper = new ElicitComboBox(answer);
        ComboBox<SelectItem> combo = wrapper.component;

        assertTrue(combo.isRequiredIndicatorVisible());
        assertFalse(wrapper.getBinder().validate().isOk());

        combo.setValue(us);
        assertTrue(wrapper.getBinder().validate().isOk());
    }

    @Test
    void comboBox_variants_appliedFromQuestion() {
        SelectItem us = item("US", "United States");
        Question question = selectQuestion(false, null, List.of(us));
        question.variant = String.join(",", ComboBoxVariant.LUMO_ALIGN_RIGHT.getVariantName(),
                ComboBoxVariant.LUMO_SMALL.getVariantName());
        Answer answer = answer("1.1.1.1.3.4.4", "Country", question, null);

        ComboBox<SelectItem> combo = new ElicitComboBox(answer).component;

        assertTrue(combo.getThemeNames().contains(ComboBoxVariant.LUMO_ALIGN_RIGHT.getVariantName()));
        assertTrue(combo.getThemeNames().contains(ComboBoxVariant.LUMO_SMALL.getVariantName()));
    }

    @Test
    void multiSelectComboBox_construction_populatesItemsAndInitialSelection() {
        SelectItem red = item("R", "Red");
        SelectItem blue = item("B", "Blue");
        Question question = selectQuestion(false, null, List.of(red, blue));
        Answer answer = answer("1.1.1.1.3.5.1", "Favorite colors", question, "R,B");

        MultiSelectComboBox<SelectItem> combo = new ElicitMultiSelectComboBox(answer).component;

        assertEquals(Set.of(red, blue), combo.getValue());
    }

    @Test
    void multiSelectComboBox_required_blocksEmptySelection_allowsSelection() {
        SelectItem red = item("R", "Red");
        Question question = selectQuestion(true, "Pick at least one color", List.of(red));
        Answer answer = answer("1.1.1.1.3.5.2", "Favorite colors", question, null);

        ElicitMultiSelectComboBox wrapper = new ElicitMultiSelectComboBox(answer);
        MultiSelectComboBox<SelectItem> combo = wrapper.component;

        assertTrue(combo.isRequiredIndicatorVisible());
        assertFalse(wrapper.getBinder().validate().isOk());

        combo.setValue(Set.of(red));
        assertTrue(wrapper.getBinder().validate().isOk());
    }

    @Test
    void multiSelectComboBox_variants_appliedFromQuestion() {
        SelectItem red = item("R", "Red");
        Question question = selectQuestion(false, null, List.of(red));
        question.variant = MultiSelectComboBoxVariant.LUMO_SMALL.getVariantName();
        Answer answer = answer("1.1.1.1.3.5.3", "Favorite colors", question, null);

        MultiSelectComboBox<SelectItem> combo = new ElicitMultiSelectComboBox(answer).component;

        assertTrue(combo.getThemeNames().contains(MultiSelectComboBoxVariant.LUMO_SMALL.getVariantName()));
    }
}
