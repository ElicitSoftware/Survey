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

import com.vaadin.flow.component.html.Div;
import com.elicitsoftware.model.Answer;

/**
 * Represents a custom HTML component that is derived from the Div class.
 * This class sets an initial innerHTML property and stores a display key
 * based on the provided Answer object.
 */
public class ElicitHtml extends Div {

    public ElicitHtml(Answer answer) {
        super();
        this.setId(answer.getDisplayKey());
        getElement().setProperty("innerHTML", answer.displayText);
    }
}
