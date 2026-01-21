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

import com.elicitsoftware.model.*;
import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.transaction.Transactional;

/**
 * Cache warmer that pre-loads commonly accessed entities into Hibernate L2 cache
 * at application startup to improve initial request performance.
 */
@ApplicationScoped
public class CacheWarmer {

    /**
     * Warm up the Hibernate L2 cache on application startup.
     * Loads all reference/configuration data that is frequently accessed.
     */
    @Transactional
    public void onStart(@Observes StartupEvent ev) {
        Log.info("🔥 CACHE WARMER: Starting L2 cache pre-warming...");
        long startTime = System.currentTimeMillis();
        
        try {
            // Load all lookup/reference tables (these are READ_ONLY and rarely change)
            int actionTypes = ActionType.listAll().size();
            int operatorTypes = OperatorType.listAll().size();
            
            // Load all relationships for all surveys (frequently queried)
            int relationships = Relationship.find(
                "SELECT r FROM Relationship r " +
                "LEFT JOIN FETCH r.actionType " +
                "LEFT JOIN FETCH r.operatorType " +
                "LEFT JOIN FETCH r.upstreamStep " +
                "LEFT JOIN FETCH r.upstreamQuestion " +
                "LEFT JOIN FETCH r.downstreamQuestion " +
                "LEFT JOIN FETCH r.downstreamSection " +
                "LEFT JOIN FETCH r.downstreamStep"
            ).list().size();
            
            // Load all steps and sections for all surveys
            int steps = Step.listAll().size();
            int sections = Section.listAll().size();
            int stepsSections = StepsSections.listAll().size();
            int questions = SectionsQuestion.find(
                "SELECT sq FROM SectionsQuestion sq " +
                "LEFT JOIN FETCH sq.question"
            ).list().size();
            
            long duration = System.currentTimeMillis() - startTime;
            Log.info(String.format(
                "🔥 CACHE WARMER: Completed in %dms. Loaded: %d action types, %d operator types, " +
                "%d relationships, %d steps, %d sections, %d step-sections, %d questions",
                duration, actionTypes, operatorTypes, relationships, steps, sections, 
                stepsSections, questions
            ));
        } catch (Exception e) {
            Log.error("🔥 CACHE WARMER: Failed to warm cache: " + e.getMessage(), e);
        }
    }
}
