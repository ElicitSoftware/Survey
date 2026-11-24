package com.elicitsoftware.flow;

/*-
 * ***LICENSE_START***
 * Elicit Survey
 * %%
 * Copyright (C) 2025 - 2026 Elicit Software LLC
 * %%
 * PolyForm Noncommercial License 1.0.0
 * <https://polyformproject.org/licenses/noncommercial/1.0.0>
 * ***LICENSE_END***
 */

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.HasLabel;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.checkbox.CheckboxGroup;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.combobox.MultiSelectComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.datetimepicker.DateTimePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.timepicker.TimePicker;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UC-002 A2: a question already on the page whose text was rewritten by the answer just saved
 * shows the new text (#243). {@link SectionView} keeps such a question's component, so the text
 * has to reach it some other way than by building a new one.
 */
class SectionViewRelabelTest {

    private static final String BEFORE = "At what age was the Sarcoma diagnosed?";
    private static final String AFTER = "At what age was the Melanoma diagnosed?";

    @Test
    void everyInputTypeTakesItsRewrittenTextInPlace() {
        // One per input component SectionView builds (the Elicit* wrappers in flow/input).
        List<Component> inputs = List.of(new Checkbox(BEFORE), new CheckboxGroup<String>(BEFORE),
                new ComboBox<String>(BEFORE), new MultiSelectComboBox<String>(BEFORE), new DatePicker(BEFORE),
                new DateTimePicker(BEFORE), new TimePicker(BEFORE), new NumberField(BEFORE),
                new IntegerField(BEFORE), new EmailField(BEFORE), new PasswordField(BEFORE),
                new RadioButtonGroup<String>(BEFORE), new TextArea(BEFORE), new TextField(BEFORE));
        for (Component input : inputs) {
            String type = input.getClass().getSimpleName();
            assertTrue(SectionView.relabel(input, AFTER), type + " is relabelled, not replaced");
            assertEquals(AFTER, ((HasLabel) input).getLabel(), type);
        }
    }

    @Test
    void aTextFieldStaysAsWideAsItsQuestion() {
        TextField field = new TextField(BEFORE);
        SectionView.relabel(field, AFTER);
        assertEquals(AFTER.length() + ".0ch", field.getWidth());
    }

    @Test
    void aQuestionWhoseTextIsItsContentIsReplaced() {
        // HTML and MODAL questions (ElicitHtml, ElicitModal) carry the text as cleaned HTML, not
        // as a label, so the caller swaps in the newly built component.
        assertFalse(SectionView.relabel(new Div(), AFTER));
        assertFalse(SectionView.relabel(new Dialog(), AFTER));
    }
}
