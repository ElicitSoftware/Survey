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
import com.vaadin.flow.server.VaadinSession;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import io.quarkus.logging.Log;

/**
 * Service for persisting and restoring session data across browser refreshes.
 * This service stores critical session information in the HTTP session to survive
 * UI recreation when the browser is refreshed.
 * 
 * The service automatically persists session data when it changes and can restore
 * it when a new UI is created after a browser refresh.
 */
@RequestScoped
public class SessionPersistenceService {
    
    private static final String SESSION_KEY_SURVEY_ID = "elicit.survey.id";
    private static final String SESSION_KEY_RESPONDENT_ID = "elicit.respondent.id";
    private static final String SESSION_KEY_RESPONDENT_TOKEN = "elicit.respondent.token";
    private static final String SESSION_KEY_CURRENT_NAV_PATH = "elicit.current.nav.path";
    
    @Inject
    TokenService tokenService;
    
    @Inject
    QuestionService questionService;
    
    /**
     * Persists essential session data to the HTTP session.
     * This method should be called whenever critical session data changes.
     * 
     * @param surveyId the current survey ID
     * @param respondent the current respondent
     * @param navResponse the current navigation response (optional)
     */
    public void persistSessionData(Integer surveyId, Respondent respondent, NavResponse navResponse) {
        if (VaadinSession.getCurrent() == null) {
            Log.warn("No VaadinSession available for persistence");
            return;
        }
        
        try {
            VaadinSession session = VaadinSession.getCurrent();
            
            if (surveyId != null) {
                session.setAttribute(SESSION_KEY_SURVEY_ID, surveyId);
            }
            
            if (respondent != null) {
                session.setAttribute(SESSION_KEY_RESPONDENT_ID, respondent.id);
                session.setAttribute(SESSION_KEY_RESPONDENT_TOKEN, respondent.token);
            }
            
            if (navResponse != null && navResponse.getCurrentNavItem() != null) {
                String navPath = navResponse.getCurrentNavItem().getPath();
                if (navPath != null && !navPath.isEmpty()) {
                    session.setAttribute(SESSION_KEY_CURRENT_NAV_PATH, navPath);
                }
            }
            
            Log.info("Session data persisted - Survey: " + surveyId + ", Respondent: " + 
                    (respondent != null ? respondent.id : "null"));
                    
        } catch (Exception e) {
            Log.error("Failed to persist session data", e);
        }
    }
    
    /**
     * Attempts to restore session data from the HTTP session.
     * This method should be called when initializing a new UI to check if there's
     * existing session data that can be restored.
     * 
     * @return a SessionData object containing the restored data, or null if no valid session exists
     */
    @Transactional
    public SessionData restoreSessionData() {
        if (VaadinSession.getCurrent() == null) {
            Log.warn("No VaadinSession available for restoration");
            return null;
        }
        
        try {
            VaadinSession session = VaadinSession.getCurrent();
            
            Integer surveyId = (Integer) session.getAttribute(SESSION_KEY_SURVEY_ID);
            Integer respondentId = (Integer) session.getAttribute(SESSION_KEY_RESPONDENT_ID);
            String respondentToken = (String) session.getAttribute(SESSION_KEY_RESPONDENT_TOKEN);
            String currentNavPath = (String) session.getAttribute(SESSION_KEY_CURRENT_NAV_PATH);
            
            // Check if we have all required session data for restoration
            if (surveyId == null || respondentId == null || respondentToken == null) {
                Log.info("Incomplete session data found - Survey: " + surveyId + 
                        ", Respondent: " + respondentId + ", Token: " + (respondentToken != null ? "present" : "null") +
                        " - cannot restore");
                return null;
            }
            
            // Verify the respondent still exists and is valid
            Respondent respondent = tokenService.login(surveyId, respondentToken);
            if (respondent == null || !respondent.id.equals(respondentId)) {
                Log.warn("Invalid respondent found during restoration, clearing session");
                clearSessionData();
                return null;
            }
            
            // Restore navigation state using the stored navigation path if available
            NavResponse navResponse = null;
            if (currentNavPath != null && !currentNavPath.isEmpty()) {
                try {
                    // Try to restore to the exact navigation path where the user was
                    navResponse = questionService.init(respondent.id, currentNavPath);
                    Log.info("Restored to navigation path: " + currentNavPath);
                } catch (Exception e) {
                    Log.warn("Failed to restore to navigation path: " + currentNavPath + ", falling back to initial step", e);
                }
            }
            
            // If we couldn't restore to the specific path, start from the beginning
            if (navResponse == null) {
                String initialDisplayKey = null;
                if (respondent.survey != null) {
                    initialDisplayKey = respondent.survey.initialDisplayKey;
                }
                if (initialDisplayKey == null) {
                    initialDisplayKey = "0-0-0-0-0-0-0"; // Default display key
                }
                navResponse = questionService.init(respondent.id, initialDisplayKey);
                Log.info("Started from initial display key: " + initialDisplayKey);
            }
            
            Log.info("Session data restored - Survey: " + surveyId + ", Respondent: " + respondentId + 
                    ", NavPath: " + currentNavPath + 
                    ", Restored to path: " + (navResponse != null && navResponse.getCurrentNavItem() != null ? 
                        navResponse.getCurrentNavItem().getPath() : "unknown"));
                    
            return new SessionData(surveyId, respondent, navResponse);
            
        } catch (Exception e) {
            Log.error("Failed to restore session data", e);
            clearSessionData();
            return null;
        }
    }
    
    /**
     * Clears all persisted session data from the HTTP session.
     * This method should be called when the user logs out or when invalid session data is detected.
     */
    public void clearSessionData() {
        if (VaadinSession.getCurrent() == null) {
            return;
        }
        
        try {
            VaadinSession session = VaadinSession.getCurrent();
            session.setAttribute(SESSION_KEY_SURVEY_ID, null);
            session.setAttribute(SESSION_KEY_RESPONDENT_ID, null);
            session.setAttribute(SESSION_KEY_RESPONDENT_TOKEN, null);
            session.setAttribute(SESSION_KEY_CURRENT_NAV_PATH, null);
            
            Log.info("Session data cleared");
            
        } catch (Exception e) {
            Log.error("Failed to clear session data", e);
        }
    }
    
    /**
     * Data class to hold restored session information.
     */
    public static class SessionData {
        private final Integer surveyId;
        private final Respondent respondent;
        private final NavResponse navResponse;
        
        public SessionData(Integer surveyId, Respondent respondent, NavResponse navResponse) {
            this.surveyId = surveyId;
            this.respondent = respondent;
            this.navResponse = navResponse;
        }
        
        public Integer getSurveyId() {
            return surveyId;
        }
        
        public Respondent getRespondent() {
            return respondent;
        }
        
        public NavResponse getNavResponse() {
            return navResponse;
        }
    }
}
