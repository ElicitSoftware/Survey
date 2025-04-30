package com.elicitsoftware.flow;

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

/**
 * A utility class that holds constant keys used for session management throughout the application.
 * These keys are used to set and retrieve session attributes in {@link com.vaadin.flow.server.VaadinSession}.
 * <p>
 * SURVEY_ID: Represents the key for storing the survey's unique identifier in the session.
 * RESPONDENT: Represents the key for storing the respondent's information in the session.
 * NAV_RESPONSE: Represents the key for storing navigation response details from the survey in the session.
 */
public class SessionKeys {

    public static final String SURVEY_ID = "SURVEY_ID";
    public static final String RESPONDENT = "RESPONDENT";
    public static final String NAV_RESPONSE = "NAV_RESPONSE";

}
