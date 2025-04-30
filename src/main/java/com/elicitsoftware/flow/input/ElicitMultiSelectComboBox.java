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

import com.vaadin.flow.component.combobox.MultiSelectComboBox;
import com.vaadin.flow.component.combobox.MultiSelectComboBoxVariant;
import com.elicitsoftware.model.Answer;
import com.elicitsoftware.model.Question;
import com.elicitsoftware.model.SelectItem;

import java.util.HashSet;

/**
 * The ElicitMultiSelectComboBox class represents a UI component for selecting multiple
 * items from a predefined list. It extends the ElicitComponent class, utilizing the
 * MultiSelectComboBox from Vaadin as the underlying UI component.
 * <p>
 * The component supports multiple selectable options, item label customization,
 * validation, and theming through variants.
 */
public class ElicitMultiSelectComboBox extends ElicitComponent<MultiSelectComboBox<SelectItem>> {

    /**
     * A custom multi-select combo box component for the Elicit application.
     * This class extends a generic `MultiSelectComboBox` and is designed to handle
     * the selection of multiple items based on the provided `Answer` object.
     *
     * @param answer The `Answer` object containing the display text and selection
     *               items for the combo box. It also provides the initial value
     *               if available.
     *
     *               <p>Features:</p>
     *               <ul>
     *                 <li>Initializes the combo box with the display text from the `Answer` object.</li>
     *                 <li>Populates the combo box with selectable items from the `Answer`'s associated
     *                     `SelectGroup`.</li>
     *                 <li>Sets a label generator for the items to display their `displayText` property.</li>
     *                 <li>Pre-selects the value if the `Answer` object contains a non-empty text value.</li>
     *               </ul>
     */
    public ElicitMultiSelectComboBox(Answer answer) {
        super(new MultiSelectComboBox<SelectItem>(answer.displayText), answer);
        component.setItems(answer.question.selectGroup.selectItems);
        component.setItemLabelGenerator(item -> item.displayText);
        if (answer.getTextValue() != null && !answer.getTextValue().isEmpty()) {
            setValue(answer);
        }
    }

    /**
     * Configures the bindings for the given answer object to the component.
     * If the question associated with the answer is marked as required,
     * this method sets the component to be required and makes the required
     * indicator visible. It also binds the selected items of the answer
     * to the component with a validation rule using the provided validation text.
     * <p>
     * Note: This method does not enforce minimum or maximum selection limits
     * for the multi-select combo box.
     *
     * @param answer The answer object containing the question and selected items
     *               to bind to the component.
     */
    @Override
    void setBindings(Answer answer) {
        if (answer.question.required) {
            component.setRequired(answer.question.required);
            component.setRequiredIndicatorVisible(answer.question.required);
            this.binder.forField(component).asRequired(answer.question.validationText)
                    .bind(Answer::getSelectedItems, Answer::setSelectedItems);
            // No min or max for Combo Multi select
        }
    }

    /**
     * Sets the value of the component based on the provided answer.
     * <p>
     * This method processes the given {@link Answer} object to determine which
     * {@link SelectItem}s should be selected in the component. If the answer's
     * text value is not null, it iterates through the available select items
     * and adds those whose coded value is contained in the answer's text value
     * to the selected set. The selected set is then applied to the component.
     *
     * @param answer The {@link Answer} object containing the text value and
     *               associated question data used to determine the selected
     *               items.
     */
    @Override
    void setValue(Answer answer) {
        HashSet<SelectItem> selected = new HashSet<SelectItem>();
        if (answer.getTextValue() != null) {
            for (SelectItem item : answer.question.selectGroup.selectItems) {
                if (answer.getTextValue().contains(item.codedValue)) {
                    selected.add(item);
                }
            }
        }
        component.setValue(selected);
    }

    /**
     * Adds theme variants to the component based on the specified question's variants.
     *
     * <p>This method checks if the provided {@link Question} object has a non-null
     * {@code variant} field. If so, it iterates through the possible variants and
     * applies the corresponding theme variants to the {@code component}.
     *
     * <p>The supported variants include:
     * <ul>
     *   <li>{@code LUMO_ALIGN_LEFT} - Aligns the component's content to the left.</li>
     *   <li>{@code LUMO_ALIGN_CENTER} - Aligns the component's content to the center.</li>
     *   <li>{@code LUMO_ALIGN_RIGHT} - Aligns the component's content to the right.</li>
     *   <li>{@code LUMO_SMALL} - Applies a smaller size to the component.</li>
     *   <li>{@code LUMO_HELPER_ABOVE_FIELD} - Positions the helper text above the field.</li>
     * </ul>
     *
     * @param question The {@link Question} object containing the variants to be applied.
     *                 If {@code question.variant} is {@code null}, no action is taken.
     */
    @Override
    void addVariants(Question question) {
        if (question.variant != null) {
            if (question.variant.contains(MultiSelectComboBoxVariant.LUMO_ALIGN_LEFT.getVariantName())) {
                component.addThemeVariants(MultiSelectComboBoxVariant.LUMO_ALIGN_LEFT);
            }
            if (question.variant.contains(MultiSelectComboBoxVariant.LUMO_ALIGN_CENTER.getVariantName())) {
                component.addThemeVariants(MultiSelectComboBoxVariant.LUMO_ALIGN_CENTER);
            }
            if (question.variant.contains(MultiSelectComboBoxVariant.LUMO_ALIGN_RIGHT.getVariantName())) {
                component.addThemeVariants(MultiSelectComboBoxVariant.LUMO_ALIGN_RIGHT);
            }
            if (question.variant.contains(MultiSelectComboBoxVariant.LUMO_SMALL.getVariantName())) {
                component.addThemeVariants(MultiSelectComboBoxVariant.LUMO_SMALL);
            }
            if (question.variant.contains(MultiSelectComboBoxVariant.LUMO_HELPER_ABOVE_FIELD.getVariantName())) {
                component.addThemeVariants(MultiSelectComboBoxVariant.LUMO_HELPER_ABOVE_FIELD);
            }
        }
    }
}
