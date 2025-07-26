package com.elicitsoftware.service;

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

import com.elicitsoftware.model.SectionNavigationItem;
import com.vaadin.quarkus.annotation.NormalUIScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

import java.util.*;

/**
 * Service for managing section navigation data.
 * <p>
 * This service provides methods to retrieve and organize section navigation data
 * from the database in a hierarchical structure suitable for tree grid display.
 */
@NormalUIScoped
public class SectionNavigationService {

    @Inject
    EntityManager entityManager;

    /**
     * Retrieves section navigation items for a specific respondent organized in a hierarchical structure.
     * <p>
     * Executes the query:
     * SELECT a.step, a.section, a.display_text
     * FROM survey.answers a 
     * WHERE a.respondent_id = :respondentId
     * AND a.question_id IS NULL
     * ORDER BY a.display_key
     * <p>
     * Returns a map where keys are parent items (steps) and values are lists of their children (sections).
     * Steps become the top-level parents and sections become the children under their corresponding step.
     *
     * @param respondentId the ID of the respondent
     * @return a map of step parents to their section children
     */
    public Map<SectionNavigationItem, List<SectionNavigationItem>> getSectionNavigationData(Integer respondentId) {
        String sql = "SELECT a.step, a.section, a.display_text, a.display_key " +
                    "FROM survey.answers a " +
                    "WHERE a.respondent_id = :respondentId " +
                    "AND a.deleted = false " +
                    "AND a.question_id IS NULL " +
                    "ORDER BY a.display_key";

        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("respondentId", respondentId);

        @SuppressWarnings("unchecked")
        List<Object[]> results = query.getResultList();

        // Use LinkedHashMap to maintain order
        Map<SectionNavigationItem, List<SectionNavigationItem>> navigationMap = new LinkedHashMap<>();
        Map<Integer, SectionNavigationItem> stepMap = new HashMap<>();

        for (Object[] row : results) {
            Integer step = (Integer) row[0];
            Integer section = (Integer) row[1];
            String displayText = (String) row[2];
            String displayKey = (String) row[3];
            
            // Use the actual display_key from the database, which is unique
            String itemKey = displayKey;
            
            if (section == 0) {
                // This is a step (parent item)
                SectionNavigationItem stepItem = new SectionNavigationItem(section, displayText, itemKey);
                
                // Check if we already have this step
                if (!stepMap.containsKey(step)) {
                    stepMap.put(step, stepItem);
                    navigationMap.put(stepItem, new ArrayList<>());
                }
            } else {
                // This is a section (child item) - find its parent step
                SectionNavigationItem sectionItem = new SectionNavigationItem(section, displayText, itemKey);
                
                // Find the corresponding step (parent)
                SectionNavigationItem parentStep = stepMap.get(step);
                
                if (parentStep != null) {
                    // Add this section to its parent step
                    navigationMap.get(parentStep).add(sectionItem);
                } else {
                    // Create a step parent if it doesn't exist yet
                    // This happens when we encounter a section before its corresponding step
                    // Use the section's display text as the step name for single-section steps  
                    SectionNavigationItem newStep = new SectionNavigationItem(0, displayText, step + "-0");
                    stepMap.put(step, newStep);
                    navigationMap.put(newStep, new ArrayList<>());
                    navigationMap.get(newStep).add(sectionItem);
                }
            }
        }

        // Sort children by their keys (which include step and section info)
        for (List<SectionNavigationItem> children : navigationMap.values()) {
            children.sort(Comparator.comparing(SectionNavigationItem::getDisplayKey));
        }

        return navigationMap;
    }

    /**
     * Gets a flat list of all section navigation items for a respondent.
     *
     * @param respondentId the ID of the respondent
     * @return a list of all section navigation items
     */
    public List<SectionNavigationItem> getAllSectionNavigationItems(Integer respondentId) {
        String sql = "SELECT a.section, a.display_text, a.display_key " +
                    "FROM survey.answers a " +
                    "WHERE a.respondent_id = :respondentId " +
                    "AND a.question_id IS NULL " +
                    "ORDER BY a.display_key";

        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("respondentId", respondentId);

        @SuppressWarnings("unchecked")
        List<Object[]> results = query.getResultList();

        List<SectionNavigationItem> items = new ArrayList<>();
        for (Object[] row : results) {
            Integer section = (Integer) row[0];
            String displayText = (String) row[1];
            String displayKey = (String) row[2];

            items.add(new SectionNavigationItem(section, displayText, displayKey));
        }

        return items;
    }
}
