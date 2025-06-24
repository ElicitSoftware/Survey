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
import com.vaadin.flow.component.checkbox.Checkbox;

/**
 * The ElicitCheckbox class represents a specific implementation of the ElicitComponent
 * abstract class for handling Checkbox components. It is designed to provide functionality
 * for managing and configuring checkboxes in a UI driven by Question and Answer data objects.
 * <p>
 * This class encapsulates the logic for creating and interacting with a Checkbox,
 * including setting its value, enforcing requirements, and validating the data entered
 * by users. The class does not support variants since checkboxes typically do not require
 * this customization.
 * <p>
 * Functionality includes:
 * - Instantiating a Checkbox component with its initial value and configuration based on
 * Answer data.
 * - Configuring whether the checkbox is marked as required.
 * - Defining validation logic to ensure that required checkboxes are appropriately checked.
 * <p>
 * It overrides key methods from ElicitComponent to provide component-specific behavior,
 * including:
 * - setValue(Answer answer): Logic for setting the Checkbox value, with implementation
 * described in the constructor.
 * - setRequired(Boolean required): Configures whether the checkbox is a required field
 * by setting the required indicator.
 * - addVariants(Question question): Intentionally left empty, as variants are not applicable
 * to checkboxes.
 * - validate(): Ensures that the required validation logic, such as marking the Checkbox
 * invalid when necessary, is implemented.
 */
public class ElicitCheckbox extends ElicitComponent<Checkbox> {

    /**
     * Constructs an ElicitCheckbox component for the given answer.
     * The checkbox is initialized with the display text and checked state
     * based on the boolean value parsed from the answer's text value.
     *
     * @param answer the answer object containing the data for this checkbox
     */
    public ElicitCheckbox(Answer answer) {
        super(new Checkbox(answer.displayText, Boolean.parseBoolean(answer.getTextValue())), answer);
    }

    @Override
    void setBindings(Answer answer) {
        Checkbox checkbox = component;
        //A single checkbox shouldn't have a required, max or min values.
    }

    @Override
    void setValue(Answer answer) {
        // see constructor
    }

    @Override
    void addVariants(Question question) {
        // no variants
    }
}
