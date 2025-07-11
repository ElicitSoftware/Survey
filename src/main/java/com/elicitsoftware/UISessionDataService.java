package com.elicitsoftware;

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

import com.elicitsoftware.model.Respondent;
import com.elicitsoftware.response.NavResponse;
import com.vaadin.quarkus.annotation.NormalUIScoped;

/**
 * Service class for managing UI session-specific data in a Vaadin application.
 * This service is scoped to the UI level, meaning each browser tab/window will have
 * its own instance of this service, preventing data leakage between different UI sessions.
 *
 * <p>The service maintains essential session state including:
 * <ul>
 *   <li>Survey ID - identifies the current survey being taken</li>
 *   <li>Respondent - the current user taking the survey</li>
 *   <li>Navigation Response - tracks the current navigation state</li>
 * </ul>
 *
 * <p>This service uses {@code @NormalUIScoped} annotation which ensures proper
 * CDI scoping for Vaadin UI contexts, providing isolation between different
 * browser tabs/windows.
 *
 * @see com.elicitsoftware.model.Respondent
 * @see com.elicitsoftware.response.NavResponse
 * @see com.vaadin.quarkus.annotation.NormalUIScoped
 */
@NormalUIScoped
public class UISessionDataService {
    /** The ID of the survey currently being taken by the respondent. */
    private Integer surveyId;

    /** The respondent (user) currently taking the survey. */
    private Respondent respondent;

    /** The current navigation response containing step and navigation state. */
    private NavResponse navResponse;

    /**
     * Gets the survey ID for the current session.
     *
     * @return the survey ID, or null if no survey is currently active
     */
    public Integer getSurveyId() {
        return surveyId;
    }

    /**
     * Sets the survey ID for the current session.
     *
     * @param surveyId the survey ID to set
     */
    public void setSurveyId(Integer surveyId) {
        this.surveyId = surveyId;
    }

    /**
     * Gets the respondent for the current session.
     *
     * @return the current respondent, or null if no valid session exists
     */
    public Respondent getRespondent() {
        return respondent;
    }

    /**
     * Sets the respondent for this session.
     *
     * @param respondent the respondent to set for this session
     */
    public void setRespondent(Respondent respondent) {
        this.respondent = respondent;
    }

    /**
     * Gets the navigation response for the current session.
     *
     * @return the current navigation response, or null if none is set
     */
    public NavResponse getNavResponse() {
        return navResponse;
    }

    /**
     * Sets the navigation response for the current session.
     *
     * @param navResponse the navigation response to set
     */
    public void setNavResponse(NavResponse navResponse) {
        this.navResponse = navResponse;
    }

    /**
     * Clears all session data by setting all fields to null.
     * This method is typically called when ending a survey session
     * or when the user logs out.
     */
    public void clear() {
        this.surveyId = null;
        this.respondent = null;
        this.navResponse = null;
    }

    /**
     * Returns a string representation of this UISessionDataService for debugging purposes.
     * The string includes survey ID, respondent ID, current step ID, and respondent details.
     *
     * @return a multi-line string containing the current session state
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Survey Id: " + surveyId + "\n");
        if (respondent != null) {
            sb.append(respondent.id + "\n");
        } else {
            sb.append("Respondent is null\n");
        }
        if (navResponse != null) {
            sb.append(navResponse.getStep().id + "\n");
        } else {
            sb.append("NavResponse is null\n");
        }
        sb.append("Respondent: " + respondent + "\n");
        return sb.toString();
    }
}
