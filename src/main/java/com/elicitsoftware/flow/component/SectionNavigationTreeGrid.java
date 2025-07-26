package com.elicitsoftware.flow.component;

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
import com.elicitsoftware.model.SectionNavigationItem;
import com.elicitsoftware.service.SectionNavigationService;
import com.vaadin.flow.component.treegrid.TreeGrid;
import com.vaadin.flow.data.provider.hierarchy.TreeData;
import com.vaadin.flow.data.provider.hierarchy.TreeDataProvider;
import com.vaadin.quarkus.annotation.NormalUIScoped;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Map;

/**
 * A tree grid component for displaying section navigation in a hierarchical structure.
 * <p>
 * This component displays sections where:
 * - Sections with section = 0 are shown as top-level items
 * - Sections with section != 0 are shown as children under the corresponding parent sections
 * - Items are ordered by display_key
 * - Duplicate parent sections (same display_text) are grouped together
 */
@NormalUIScoped
public class SectionNavigationTreeGrid extends TreeGrid<SectionNavigationItem> {

    @Inject
    SectionNavigationService navigationService;

    @Inject
    UISessionDataService sessionDataService;

    private TreeData<SectionNavigationItem> treeData;
    private TreeDataProvider<SectionNavigationItem> dataProvider;

    /**
     * Default constructor.
     */
    public SectionNavigationTreeGrid() {
        super();
        this.treeData = new TreeData<>();
        this.dataProvider = new TreeDataProvider<>(treeData);
        setDataProvider(dataProvider);
    }

    /**
     * Initializes the tree grid after dependency injection.
     */
    @PostConstruct
    public void init() {
        setupColumns();
        setupStyling();
        refreshData();
    }

    /**
     * Sets up the columns for the tree grid.
     */
    private void setupColumns() {
        removeAllColumns();
        addHierarchyColumn(item -> {
            if (item.isCurrent()) {
                return "★ " + item.getDisplayText(); // Add a star to indicate current section
            }
            return item.getDisplayText();
        })
                .setHeader("Survey Sections")
                .setFlexGrow(1);
                
        // Add a part name generator to apply CSS classes to current section rows
        setPartNameGenerator(item -> {
            if (item.isCurrent()) {
                return "current-section-row";
            }
            return null;
        });
    }
    
    /**
     * Sets up the styling for the tree grid.
     */
    private void setupStyling() {
        setSizeFull(); // Make the tree grid take up all available space
        addClassName("section-navigation-tree");
        addClassName("section-navigation-tree-grid"); // Add the class that matches the CSS
        
        // Remove default grid borders and padding for cleaner look
        getStyle().set("border", "none");
        
        // Add spacing between rows
        getStyle().set("--lumo-size-xs", "0.75rem");
        
        // Improve tree hierarchy indentation
        getStyle().set("--vaadin-grid-tree-toggle-level-offset", "1rem");
        // Set smaller font size for the tree grid
        getStyle().set("font-size", "0.85rem");
    }
    
    /**
     * Updates the current section selection in the tree grid.
     *
     * @param currentDisplayKey the display key of the current section
     */
    public void updateCurrentSection(String currentDisplayKey) {
        System.out.println("DEBUG: updateCurrentSection called with: " + currentDisplayKey);
        
        // Clear all current selections
        clearCurrentSelections();
        
        if (currentDisplayKey != null) {
            // Find and mark the current section and its parent
            markCurrentSection(currentDisplayKey);
        }
        
        // Refresh the grid to apply styling changes
        dataProvider.refreshAll();
        System.out.println("DEBUG: Grid refreshed after current section update");
    }
    
    /**
     * Updates the current section based on session data.
     * This method extracts the current section from the session's navigation response.
     */
    public void updateCurrentSectionFromSession() {
        try {
            // Ensure tree data is loaded first
            refreshData();
            
            // Get current navigation response from session
            com.elicitsoftware.response.NavResponse navResponse = sessionDataService.getNavResponse();
            
            if (navResponse != null && navResponse.getCurrentNavItem() != null) {
                String currentPath = navResponse.getCurrentNavItem().getPath();
                System.out.println("DEBUG: Current path from session: " + currentPath);
                
                // The path should contain the display key (e.g., "0001-0001-0000-0001-0000-0000-0000")
                if (currentPath != null && !currentPath.isEmpty()) {
                    updateCurrentSection(currentPath);
                } else {
                    System.out.println("DEBUG: Current path is null or empty");
                    updateCurrentSection(null);
                }
            } else {
                System.out.println("DEBUG: NavResponse or currentNavItem is null");
                updateCurrentSection(null);
            }
        } catch (Exception e) {
            System.err.println("ERROR: Failed to update current section from session: " + e.getMessage());
            e.printStackTrace();
            updateCurrentSection(null);
        }
    }
    
    /**
     * Clears all current section markings.
     */
    private void clearCurrentSelections() {
        treeData.getRootItems().forEach(this::clearCurrentRecursively);
    }
    
    /**
     * Recursively clears current markings from items and their children.
     *
     * @param item the item to clear
     */
    private void clearCurrentRecursively(SectionNavigationItem item) {
        item.setCurrent(false);
        treeData.getChildren(item).forEach(this::clearCurrentRecursively);
    }
    
    /**
     * Marks the current section and its parent as current.
     *
     * @param currentDisplayKey the display key to mark as current
     */
    private void markCurrentSection(String currentDisplayKey) {
        System.out.println("DEBUG: markCurrentSection searching for: " + currentDisplayKey);
        
        if (treeData == null) {
            System.out.println("DEBUG: treeData is null!");
            return;
        }
        
        try {
            System.out.println("DEBUG: Tree has " + treeData.getRootItems().size() + " root items");
        } catch (Exception e) {
            System.out.println("DEBUG: Exception getting root items: " + e.getMessage());
            return;
        }
        
        // Search in all items (both parents and children)
        for (SectionNavigationItem parent : treeData.getRootItems()) {
            System.out.println("DEBUG: Checking parent: " + parent.getDisplayKey());
            if (currentDisplayKey.equals(parent.getDisplayKey())) {
                parent.setCurrent(true);
                System.out.println("DEBUG: Marked parent as current: " + parent.getDisplayKey());
                return;
            }
            
            // Check children
            for (SectionNavigationItem child : treeData.getChildren(parent)) {
                System.out.println("DEBUG: Checking child: " + child.getDisplayKey());
                if (currentDisplayKey.equals(child.getDisplayKey())) {
                    child.setCurrent(true);
                    parent.setCurrent(true); // Also mark parent as current
                    System.out.println("DEBUG: Marked child as current: " + child.getDisplayKey());
                    System.out.println("DEBUG: Also marked parent as current: " + parent.getDisplayKey());
                    return;
                }
            }
        }
        System.out.println("DEBUG: No matching section found for: " + currentDisplayKey);
    }

    /**
     * Refreshes the tree grid data from the database.
     * This method should be called when section data changes (e.g., after saveAnswer).
     */
    public void refreshData() {
        Respondent respondent = sessionDataService.getRespondent();
        if (respondent != null) {
            loadNavigationData(respondent.id);
        } else {
            clearData();
        }
    }

    /**
     * Loads navigation data for a specific respondent.
     *
     * @param respondentId the ID of the respondent
     */
    private void loadNavigationData(Integer respondentId) {
        try {
            treeData.clear();
            
            Map<SectionNavigationItem, List<SectionNavigationItem>> navigationMap = 
                navigationService.getSectionNavigationData(respondentId);

            for (Map.Entry<SectionNavigationItem, List<SectionNavigationItem>> entry : navigationMap.entrySet()) {
                SectionNavigationItem parent = entry.getKey();
                List<SectionNavigationItem> children = entry.getValue();

                // Add parent to tree data
                treeData.addItem(null, parent);

                // Add children to tree data
                for (SectionNavigationItem child : children) {
                    treeData.addItem(parent, child);
                }
            }

            dataProvider.refreshAll();
            
            // Keep all nodes collapsed by default
            collapseAll();
            
        } catch (Exception e) {
            System.err.println("Error loading navigation data: " + e.getMessage());
            e.printStackTrace();
            clearData();
        }
    }

    /**
     * Clears all data from the tree grid.
     */
    private void clearData() {
        treeData.clear();
        dataProvider.refreshAll();
    }

    /**
     * Expands all nodes in the tree grid.
     */
    public void expandAll() {
        treeData.getRootItems().forEach(item -> expandRecursively(item));
    }

    /**
     * Collapses all nodes in the tree grid.
     */
    public void collapseAll() {
        treeData.getRootItems().forEach(item -> collapseRecursively(item));
    }

    /**
     * Recursively expands a node and all its children.
     *
     * @param item the item to expand
     */
    private void expandRecursively(SectionNavigationItem item) {
        expand(item);
        treeData.getChildren(item).forEach(child -> expandRecursively(child));
    }

    /**
     * Recursively collapses a node and all its children.
     *
     * @param item the item to collapse
     */
    private void collapseRecursively(SectionNavigationItem item) {
        collapse(item);
        treeData.getChildren(item).forEach(child -> collapseRecursively(child));
    }

    /**
     * Gets the tree data provider.
     *
     * @return the tree data provider
     */
    public TreeDataProvider<SectionNavigationItem> getTreeDataProvider() {
        return dataProvider;
    }

    /**
     * Gets the tree data.
     *
     * @return the tree data
     */
    public TreeData<SectionNavigationItem> getTreeData() {
        return treeData;
    }
}
