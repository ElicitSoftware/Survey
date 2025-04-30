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

import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.combobox.ComboBoxVariant;
import com.elicitsoftware.model.Answer;
import com.elicitsoftware.model.Question;
import com.elicitsoftware.model.SelectItem;

/**
 * ElicitComboBox is a specialized implementation of ElicitComponent designed to encapsulate
 * a ComboBox component for handling user interaction with selectable items. This class is
 * initialized with an Answer object and configures the ComboBox based on the provided
 * Answer and related Question data.
 * <p>
 * Key functionalities include:
 * - Initializing the ComboBox with a list of selectable items derived from the Answer's
 * associated Question's select group.
 * - Setting the label for each selectable item using their displayText.
 * - Applying a value to the ComboBox based on the Answer's textValue, if specified.
 * - Configuring the ComboBox to display validation indicators for required fields.
 * - Adding style variants to the ComboBox based on the Question's variant property.
 * - Validating the ComboBox's value depending on its required status.
 * <p>
 * The ComboBox component configuration includes:
 * - Items: Populated with the select items from the associated Question's select group.
 * - Required Indicator Visibility: Set to true or false based on the required property.
 * - Theme Variants: Applied dynamically if specified in the Question's variant attribute.
 * <p>
 * This class provides implementations for the following abstract methods from the
 * ElicitComponent base class:
 * - setValue: Maps the text value from the Answer to a corresponding ComboBox item.
 * - setRequired: Applies the required property to the ComboBox.
 * - addVariants: Adds ComboBox-specific style variants.
 * - validate: Enforces validation rules for ensuring the ComboBox's constraints are met.
 * <p>
 * This component is designed for scenarios where a dropdown menu of selectable options
 * is required, such as survey or form inputs.
 */
public class ElicitComboBox extends ElicitComponent<ComboBox<SelectItem>> {

    /**
     * A custom combo box component for displaying selectable items related to an answer.
     * This class extends a generic combo box and initializes it with the provided answer's
     * display text and selectable items.
     *
     * @param answer The {@link Answer} object containing the display text and selectable items
     *               for the combo box. The selectable items are retrieved from the associated
     *               question's select group.
     */
    public ElicitComboBox(Answer answer) {
        super(new ComboBox<SelectItem>(answer.displayText), answer);
        component.setItems(answer.question.selectGroup.selectItems);
        component.setItemLabelGenerator(item -> item.displayText);
    }

    /**
     * Binds the given {@link Answer} object to the ComboBox component.
     * If the question associated with the answer is marked as required,
     * the ComboBox is configured to enforce the required constraint and
     * display the required indicator. Additionally, a validation rule is
     * applied to ensure the field is filled, using the validation text
     * provided by the question.
     * <p>
     * Note: This method does not handle min/max constraints as they are
     * not applicable to a ComboBox.
     *
     * @param answer The {@link Answer} object containing the question
     *               and its associated data to bind to the ComboBox.
     */
    @Override
    void setBindings(Answer answer) {
        if (answer.question.required) {
            component.setRequired(answer.question.required);
            component.setRequiredIndicatorVisible(answer.question.required);
            this.binder.forField(component)
                    .asRequired(answer.question.validationText)
                    .bind(Answer::getSelectedItem, Answer::setSelectedItem);
        }
        // There is no definition for min max on a combobox
    }

    /**
     * Sets the value of the component based on the provided answer.
     * If the answer contains a non-null text value, this method iterates
     * through the select items of the associated question's select group
     * to find a matching item with the same coded value as the answer's text value.
     * Once a match is found, it sets the component's value to the matching item.
     *
     * @param answer The answer object containing the text value to match
     *               and the associated question's select group.
     */
    @Override
    void setValue(Answer answer) {
        if (answer.getTextValue() != null) {
            for (SelectItem item : answer.question.selectGroup.selectItems) {
                if (item.codedValue.equals(answer.getTextValue())) {
                    component.setValue(item);
                    break;
                }
            }
        }
    }

    /**
     * Adds theme variants to the ComboBox component based on the specified question's variants.
     *
     * <p>This method checks if the provided {@code question} has a non-null {@code variant} field.
     * If so, it iterates through the possible {@link ComboBoxVariant} values and adds the corresponding
     * theme variants to the ComboBox component if they are present in the question's variant list.</p>
     *
     * @param question the {@link Question} object containing the variant information.
     *                 If the {@code variant} field is null, no theme variants are added.
     */
    @Override
    void addVariants(Question question) {
        if (question.variant != null) {
            if (question.variant.contains(ComboBoxVariant.LUMO_ALIGN_LEFT.getVariantName())) {
                component.addThemeVariants(ComboBoxVariant.LUMO_ALIGN_LEFT);
            }
            if (question.variant.contains(ComboBoxVariant.LUMO_ALIGN_CENTER.getVariantName())) {
                component.addThemeVariants(ComboBoxVariant.LUMO_ALIGN_CENTER);
            }
            if (question.variant.contains(ComboBoxVariant.LUMO_ALIGN_RIGHT.getVariantName())) {
                component.addThemeVariants(ComboBoxVariant.LUMO_ALIGN_RIGHT);
            }
            if (question.variant.contains(ComboBoxVariant.LUMO_SMALL.getVariantName())) {
                component.addThemeVariants(ComboBoxVariant.LUMO_SMALL);
            }
            if (question.variant.contains(ComboBoxVariant.LUMO_HELPER_ABOVE_FIELD.getVariantName())) {
                component.addThemeVariants(ComboBoxVariant.LUMO_HELPER_ABOVE_FIELD);
            }
        }
    }
}
