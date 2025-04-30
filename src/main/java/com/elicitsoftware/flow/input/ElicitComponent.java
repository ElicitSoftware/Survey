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

import com.vaadin.flow.component.*;
import com.vaadin.flow.component.shared.HasAllowedCharPattern;
import com.vaadin.flow.component.shared.HasTooltip;
import com.vaadin.flow.data.binder.Binder;
import com.elicitsoftware.model.Answer;
import com.elicitsoftware.model.Question;

/**
 * The ElicitComponent class serves as an abstract base class for creating custom UI components
 * that encapsulate a specific Vaadin Component type. This class provides a framework for setting
 * up and configuring UI components based on data from the Answer and Question objects.
 * <p>
 * The class includes capabilities for:
 * - Setting UI component properties like placeholders, tooltips, validation error message, and helper texts.
 * - Configuring required fields and minimum/maximum constraints when applicable.
 * - Adding visual variants and customization options to the UI component.
 * - Validating user input based on the rules defined by the Answer and Question objects.
 * <p>
 * This class must be extended to implement specialized behavior for specific component types.
 * Implementing classes are required to provide concrete implementations for the following methods:
 * - setValue(Answer answer): Defines how the value retrieved from Answer is applied to the component.
 * - setRequired(Boolean required): Enforces the required constraint on the component.
 * - addVariants(Question question): Adds styling variants to the component.
 * - validate(): Performs validation logic to ensure the component adheres to defined constraints.
 *
 * @param <T> the type of Vaadin Component used by this abstract component class
 */
@Tag("ElicitComponent")
public abstract class ElicitComponent<T extends Component> extends Component {
    public T component;
    Answer answer;

    Binder<Answer> binder = null;


    /**
     * Constructs an instance of the ElicitComponent class.
     *
     * @param component The UI component of type {@code T} associated with this ElicitComponent.
     * @param answer    The {@link Answer} object containing data and metadata for the component.
     *
     *                  <p>This constructor initializes the component with the provided {@code Answer} object,
     *                  sets up data bindings, and applies various configurations such as ID, CSS class name,
     *                  tooltip, validation error message, and placeholder text based on the properties of the
     *                  associated {@code Answer} and its {@code Question}.</p>
     *
     *                  <p>Key operations performed in this constructor:</p>
     *                  <ul>
     *                    <li>Assigns the {@code Answer} object to the component and initializes a {@code Binder} for it.</li>
     *                    <li>Sets the component's ID and CSS class name.</li>
     *                    <li>Configures data bindings using {@code setBindings}.</li>
     *                    <li>Adds variants to the component if the associated question has variants.</li>
     *                    <li>Sets a tooltip if the question has a tooltip defined.</li>
     *                    <li>Sets a validation error message if the question has validation text defined.</li>
     *                    <li>Sets a placeholder text if the question has a placeholder defined.</li>
     *                  </ul>
     * @see Binder
     * @see Answer
     * @see edu.umich.elicit.flow.input.Question
     */
    public ElicitComponent(T component, Answer answer) {
        super();
        this.answer = answer;
        this.binder = new Binder<Answer>();
        this.component = component;
        this.component.setId(answer.getDisplayKey());
        this.component.setClassName("elicit-input-field");

        setBindings(answer);

        if (answer.question.variant != null) {
            addVariants(answer.question);
        }

        if (answer.question.toolTip != null && !answer.question.toolTip.isEmpty()) {
            setToolTip(answer.question.toolTip);
        }

        if (answer.question.validationText != null && !answer.question.validationText.isEmpty()) {
            setValidationErrorMessage(answer.question.validationText);
        }

        if (answer.question.placeholder != null && !answer.question.placeholder.isEmpty()) {
            setPlaceholderText(answer.question.placeholder);
        }
    }

    /**
     * Retrieves the Binder instance associated with the Answer class.
     * The Binder is used to bind data between the Answer model and the UI components.
     *
     * @return the Binder instance for Answer
     */
    public Binder<Answer> getBinder() {
        return binder;
    }

    /**
     * Sets the value of this component using the provided answer.
     *
     * @param answer the answer object containing the value to be set
     */
    abstract void setValue(Answer answer);

    /**
     * Abstract method to set the bindings for the given answer.
     * Implementing classes should define how the bindings are established
     * based on the provided {@link Answer} object.
     *
     * @param answer the {@link Answer} object containing the data to bind
     */
    abstract void setBindings(Answer answer);

    /**
     * Adds variants to the specified question. This method is abstract and must be
     * implemented by subclasses to define how variants are added to a question.
     *
     * @param question the question to which variants will be added
     */
    abstract void addVariants(Question question);

    /**
     * Sets the help text for the component if it implements the {@link HasHelper} interface.
     *
     * @param helpText the help text to be set for the component
     */
    private void setHelpText(String helpText) {
        if (component instanceof HasHelper) {
            ((HasHelper) component).setHelperText(helpText);
        }
    }

    /**
     * Sets the input mask for the component if it implements the
     * {@link HasAllowedCharPattern} interface. The input mask defines
     * a pattern of allowed characters for the component.
     *
     * @param inputMask the pattern of allowed characters to be set
     *                  for the component
     */
    private void setInputMask(String inputMask) {
        if (component instanceof HasAllowedCharPattern) {
            ((HasAllowedCharPattern) component).setAllowedCharPattern(inputMask);
        }
    }

    /**
     * Sets the placeholder text for the component if it implements the {@link HasPlaceholder} interface.
     *
     * @param placeholder the placeholder text to set
     */
    private void setPlaceholderText(String placeholder) {
        if (component instanceof HasPlaceholder) {
            ((HasPlaceholder) component).setPlaceholder(placeholder);
        }
    }

    /**
     * Sets the tooltip text for the component if it implements the {@code HasTooltip} interface.
     *
     * @param toolTip the text to be displayed as a tooltip
     */
    private void setToolTip(String toolTip) {
        if (component instanceof HasTooltip) {
            ((HasTooltip) component).setTooltipText(toolTip);
        }
    }

    /**
     * Sets a validation error message for the component if it implements the {@link HasValidation} interface.
     *
     * @param errorMessage the error message to be set for the component
     */
    private void setValidationErrorMessage(String errorMessage) {
        if (component instanceof HasValidation) {
            ((HasValidation) component).setErrorMessage(errorMessage);
        }
    }
}
