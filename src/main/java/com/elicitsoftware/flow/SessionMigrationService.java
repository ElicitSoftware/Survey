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

import com.elicitsoftware.UISessionDataService;
import com.elicitsoftware.model.Respondent;
import com.elicitsoftware.response.NavResponse;
import com.vaadin.flow.component.UI;
import com.vaadin.quarkus.annotation.NormalUIScoped;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;

/**
 * Migration service to help transition from VaadinSession-based session management
 * to the UISessionDataService approach. This service ensures data consistency
 * and provides fallback mechanisms during the migration period.
 * <p>
 * This service will automatically migrate any existing session data to the
 * UI-scoped service on first access, ensuring seamless transition.
 */
@NormalUIScoped
public class SessionMigrationService {

    @Inject
    UISessionDataService sessionDataService;

    private boolean migrated = false;

    /**
     * Performs migration of existing session data to the UI-scoped service.
     * This method should be called once per UI session to ensure data consistency.
     */
    @PostConstruct
    public void migrate() {
        if (!migrated) {
            migrateSessionData();
            migrated = true;
        }
    }

    /**
     * Migrates existing VaadinSession data to the UISessionDataService.
     * This ensures backward compatibility during the transition period.
     */
    private void migrateSessionData() {
        try {
            // Migrate survey ID if present in session but not in service
            if (sessionDataService.getSurveyId() == null) {
                Object surveyId = UI.getCurrent().getSession().getAttribute(SessionKeys.SURVEY_ID);
                if (surveyId instanceof Integer) {
                    sessionDataService.setSurveyId((Integer) surveyId);
                }
            }

            // Migrate respondent if present in session but not in service
            if (sessionDataService.getRespondent() == null) {
                Object respondent = UI.getCurrent().getSession().getAttribute(SessionKeys.RESPONDENT);
                if (respondent instanceof Respondent) {
                    sessionDataService.setRespondent((Respondent) respondent);
                }
            }

            // Migrate navigation response if present in session but not in service
            if (sessionDataService.getNavResponse() == null) {
                Object navResponse = UI.getCurrent().getSession().getAttribute(SessionKeys.NAV_RESPONSE);
                if (navResponse instanceof NavResponse) {
                    sessionDataService.setNavResponse((NavResponse) navResponse);
                }
            }
        } catch (Exception e) {
            // Silently handle migration errors to avoid breaking the application
            // In production, you might want to log these for monitoring
        }
    }

    /**
     * Gets the survey ID, ensuring migration has occurred.
     *
     * @return the survey ID from the UI-scoped service
     */
    public Integer getSurveyId() {
        migrate();
        return sessionDataService.getSurveyId();
    }

    /**
     * Gets the respondent, ensuring migration has occurred.
     *
     * @return the respondent from the UI-scoped service
     */
    public Respondent getRespondent() {
        migrate();
        return sessionDataService.getRespondent();
    }

    /**
     * Gets the navigation response, ensuring migration has occurred.
     *
     * @return the navigation response from the UI-scoped service
     */
    public NavResponse getNavResponse() {
        migrate();
        return sessionDataService.getNavResponse();
    }

    /**
     * Sets the survey ID in the UI-scoped service.
     *
     * @param surveyId the survey ID to set
     */
    public void setSurveyId(Integer surveyId) {
        migrate();
        sessionDataService.setSurveyId(surveyId);
        // Also store in VaadinSession for compatibility during transition
        try {
            UI.getCurrent().getSession().setAttribute(SessionKeys.SURVEY_ID, surveyId);
        } catch (Exception e) {
            // Silently handle errors
        }
    }

    /**
     * Sets the respondent in the UI-scoped service.
     *
     * @param respondent the respondent to set
     */
    public void setRespondent(Respondent respondent) {
        migrate();
        sessionDataService.setRespondent(respondent);
        // Also store in VaadinSession for compatibility during transition
        try {
            UI.getCurrent().getSession().setAttribute(SessionKeys.RESPONDENT, respondent);
        } catch (Exception e) {
            // Silently handle errors
        }
    }

    /**
     * Sets the navigation response in the UI-scoped service.
     *
     * @param navResponse the navigation response to set
     */
    public void setNavResponse(NavResponse navResponse) {
        migrate();
        sessionDataService.setNavResponse(navResponse);
        // Also store in VaadinSession for compatibility during transition
        try {
            UI.getCurrent().getSession().setAttribute(SessionKeys.NAV_RESPONSE, navResponse);
        } catch (Exception e) {
            // Silently handle errors
        }
    }

    /**
     * Clears all session data from both the UI-scoped service and VaadinSession.
     * This ensures complete cleanup during logout.
     */
    public void clearAll() {
        sessionDataService.clear();

        // Also clear old session attributes to prevent confusion
        try {
            UI.getCurrent().getSession().setAttribute(SessionKeys.SURVEY_ID, null);
            UI.getCurrent().getSession().setAttribute(SessionKeys.RESPONDENT, null);
            UI.getCurrent().getSession().setAttribute(SessionKeys.NAV_RESPONSE, null);
        } catch (Exception e) {
            // Silently handle cleanup errors
        }

        migrated = false;
    }
}
