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
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.radiobutton.RadioGroupVariant;

/**
 * The ElicitRadioButtonGroup class is a specific implementation of the ElicitComponent class
 * for handling radio button groups within a survey or form. It utilizes a RadioButtonGroup
 * component and binds it to the provided Answer object, defining behavior for selection,
 * validation, and customization.
 * <p>
 * Responsibilities:
 * - Initializes a RadioButtonGroup component using the provided Answer object, setting items
 * and display labels based on the associated SelectItems.
 * - Supports setting an initial value from the Answer's textValue, if applicable.
 * - Implements validation logic to ensure required fields are filled when necessary.
 * - Allows specifying visual and functional variants for the radio button group, such as
 * vertical layout or helper text placement.
 * - Handles the required flag, showing or hiding the required indicator.
 * <p>
 * Constructor:
 * - ElicitRadioButtonGroup(Answer answer): Constructs an instance of the radio button group by
 * configuring the provided Answer object and populating the component.
 * <p>
 * Methods:
 * - setValue(Answer answer): Sets the selected value of the radio button group based on the
 * Answer's textValue and matches it to a corresponding SelectItem.
 * - setRequired(Boolean required): Marks the radio button group as required or optional and
 * configures the required indicator visibility.
 * - addVariants(Question question): Applies UI variants based on the Question's variant
 * settings, such as enabling vertical layout or positioning helper text above the field.
 * - validate(): Validates the radio button group's state, marking it as invalid if it is
 * required but no selection is made.
 */
public class ElicitRadioButtonGroup extends ElicitComponent<RadioButtonGroup<SelectItem>> {

    /**
     * A custom radio button group component for handling user input in the form of selectable items.
     * This class extends a generic RadioButtonGroup and is tailored to work with the application's
     * Answer model.
     *
     * @param answer The {@link Answer} object containing the display text and selectable items
     *               for the radio button group. It also holds the current value of the selection.
     *
     *               <p>Key Features:</p>
     *               <ul>
     *                 <li>Initializes the radio button group with the display text from the provided {@link Answer}.</li>
     *                 <li>Sets the selectable items for the radio button group based on the {@link SelectItem} list
     *                     from the {@link Answer}'s associated question.</li>
     *                 <li>Configures the item label generator to display the text of each {@link SelectItem}.</li>
     *                 <li>Pre-selects a value if the {@link Answer} already has a non-empty text value.</li>
     *               </ul>
     */
    public ElicitRadioButtonGroup(Answer answer) {
        super(new RadioButtonGroup<SelectItem>(answer.displayText), answer);
        this.component.setItems(answer.question.selectGroup.selectItems);
        this.component.setItemLabelGenerator(item -> item.displayText);
        if (answer.getTextValue() != null && !answer.getTextValue().isEmpty()) {
            setValue(answer);
        }
    }

    /**
     * Binds the given {@link Answer} object to the radio button group component.
     * If the question associated with the answer is marked as required, the component
     * is configured to enforce this requirement and display a required indicator.
     * Additionally, a validation rule is applied to ensure a selection is made,
     * using the validation text provided in the question.
     *
     * @param answer The {@link Answer} object containing the question and its associated data.
     */
    @Override
    void setBindings(Answer answer) {
        if (answer.question.required) {
            component.setRequired(answer.question.required);
            component.setRequiredIndicatorVisible(answer.question.required);
            this.binder.forField(component)
                    .asRequired(answer.question.validationText)
                    .bind(Answer::getSelectedItem, Answer::setSelectedItem);
            //No min or max for Radio Buttions
        }
    }

    /**
     * Sets the value of the radio button group based on the provided answer.
     * If the answer contains a text value, this method iterates through the
     * select items of the associated question's select group to find a match.
     * When a match is found (case-insensitive comparison), the corresponding
     * select item is set as the value of the component.
     *
     * @param answer The answer object containing the text value to match
     *               against the select items.
     */
    @Override
    void setValue(Answer answer) {
        if (answer.getTextValue() != null) {
            for (SelectItem item : answer.question.selectGroup.selectItems) {
                if (answer.getTextValue().equalsIgnoreCase(item.codedValue)) {
                    this.component.setValue(item);
                    break;
                }
            }
        }
    }

    /**
     * Adds theme variants to the radio button group component based on the
     * specified question's variant configuration.
     *
     * @param question The question object containing variant information.
     *                 If the question's variant is not null and contains
     *                 specific variant names, the corresponding theme
     *                 variants are added to the component.
     */
    @Override
    void addVariants(Question question) {
        if (question.variant != null) {
            if (question.variant.contains(RadioGroupVariant.LUMO_VERTICAL.getVariantName())) {
                this.component.addThemeVariants(RadioGroupVariant.LUMO_VERTICAL);
            }
            if (question.variant.contains(RadioGroupVariant.LUMO_HELPER_ABOVE_FIELD.getVariantName())) {
                this.component.addThemeVariants(RadioGroupVariant.LUMO_HELPER_ABOVE_FIELD);
            }
        }
    }
}
