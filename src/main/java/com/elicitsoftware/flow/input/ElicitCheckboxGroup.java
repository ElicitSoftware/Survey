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

import com.vaadin.flow.component.checkbox.CheckboxGroup;
import com.vaadin.flow.component.checkbox.CheckboxGroupVariant;
import com.elicitsoftware.model.Answer;
import com.elicitsoftware.model.Question;
import com.elicitsoftware.model.SelectItem;

import java.util.HashSet;

/**
 * The ElicitCheckboxGroup class is a concrete implementation of ElicitComponent that encapsulates
 * the functionality of a Vaadin CheckboxGroup component tailored for use in surveys and forms.
 * This component allows the selection of multiple items from a predefined list of options.
 * <p>
 * Responsibilities of this class include:
 * - Setting up the CheckboxGroup with provided select items and labels.
 * - Populating the CheckboxGroup with values derived from the Answer object.
 * - Applying constraints like required fields and various visual styling variants.
 * - Providing validation for user input against specified rules.
 * <p>
 * The class extends ElicitComponent, inheriting methods for general configuration,
 * while overriding specific methods to provide CheckboxGroup-specific behavior.
 * <p>
 * Constructor:
 * - ElicitCheckboxGroup(Answer answer): Instantiates the component, initializes its state from
 * the Answer object, and configures its properties and select items.
 * <p>
 * Overridden Methods:
 * - setValue(Answer answer): Populates the CheckboxGroup with selected values based on the
 * Answer's textValue property.
 * - setRequired(Boolean required): Marks the CheckboxGroup as required or optional, and manages
 * the visibility of the required indicator.
 * - addVariants(Question question): Adds styling variants to the CheckboxGroup based on the
 * variants specified in the Question object.
 * - validate(): Ensures the validity of user input, setting the component as invalid if required
 * fields are left empty.
 */
public class ElicitCheckboxGroup extends ElicitComponent<CheckboxGroup<SelectItem>> {

    /**
     * Represents a custom checkbox group component for the Elicit survey framework.
     * This class extends a generic CheckboxGroup and is initialized with an Answer object.
     * It sets up the checkbox group with the provided answer's display text, items, and value.
     *
     * <p>Key functionalities include:</p>
     * <ul>
     *   <li>Setting the items for the checkbox group based on the answer's select items.</li>
     *   <li>Configuring the item label generator to display the item's display text.</li>
     *   <li>Initializing the value of the checkbox group if the answer has a non-empty text value.</li>
     * </ul>
     *
     * @param answer The {@link Answer} object containing the data to initialize the checkbox group.
     */
    public ElicitCheckboxGroup(Answer answer) {
        super(new CheckboxGroup<SelectItem>(answer.displayText), answer);
        component.setItems(answer.question.selectGroup.selectItems);
        component.setItemLabelGenerator(item -> item.displayText);
        if (answer.getTextValue() != null && !answer.getTextValue().isEmpty()) {
            setValue(answer);
        }
    }

    /**
     * Configures the data bindings for the checkbox group component based on the provided answer.
     * If the question associated with the answer is marked as required, the component is set to
     * reflect this requirement and a validation rule is applied to ensure the user provides a
     * response. The binding connects the selected items in the component to the corresponding
     * properties in the {@link Answer} object.
     *
     * @param answer The {@link Answer} object containing the question and its associated data
     *               used to configure the bindings for the checkbox group component.
     */
    @Override
    void setBindings(Answer answer) {
        if (answer.question.required) {
            component.setRequired(answer.question.required);
            component.setRequiredIndicatorVisible(answer.question.required);
            this.binder.forField(component)
                    .asRequired(answer.question.validationText)
                    .bind(Answer::getSelectedItems, Answer::setSelectedItems);
        }
        // There is no definition for min max on a checkbox
    }

    /**
     * Sets the value of the component based on the provided answer.
     * This method processes the text value of the given answer to determine
     * which items from the associated question's select group should be selected.
     *
     * @param answer The answer object containing the text value and associated question.
     *               If the text value is not null, it is used to match and select
     *               items from the question's select group.
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
     * /**
     * Adds theme variants to the checkbox group component based on the specified
     * variants in the given question. If the question contains specific variant
     * names, the corresponding theme variants are applied to the component.
     *
     * @param question the question object containing variant information
     */
    @Override
    void addVariants(Question question) {
        if (question.variant != null) {
            if (question.variant.contains(CheckboxGroupVariant.LUMO_VERTICAL.getVariantName())) {
                component.addThemeVariants(CheckboxGroupVariant.LUMO_VERTICAL);
            }
            if (question.variant.contains(CheckboxGroupVariant.LUMO_HELPER_ABOVE_FIELD.getVariantName())) {
                component.addThemeVariants(CheckboxGroupVariant.LUMO_HELPER_ABOVE_FIELD);
            }
        }
    }
}
