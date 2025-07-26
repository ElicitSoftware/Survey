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
import com.vaadin.flow.component.html.Div;

/**
 * Represents a custom HTML component that is derived from the Div class.
 * This class sets an initial innerHTML property and stores a display key
 * based on the provided Answer object.
 */
public class ElicitHtml extends Div {

    /**
     * Constructs an ElicitHtml component for displaying HTML content from an answer.
     * The component is initialized with the display text as HTML content and
     * uses the answer's display key as its HTML ID.
     *
     * @param answer the answer object containing the HTML content to display
     */
    public ElicitHtml(Answer answer) {
        super();
        this.setId(answer.getDisplayKey());
        getElement().setProperty("innerHTML", answer.displayText);
    }
}
