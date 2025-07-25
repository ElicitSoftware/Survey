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
        addHierarchyColumn(SectionNavigationItem::getDisplayText)
                .setHeader("Section Navigation")
                .setFlexGrow(1);
    }
    
    /**
     * Sets up the styling for the tree grid.
     */
    private void setupStyling() {
        setSizeFull(); // Make the tree grid take up all available space
        addClassName("section-navigation-tree");
        
        // Remove default grid borders and padding for cleaner look
        getStyle().set("border", "none");
        
        // Add spacing between rows
        getStyle().set("--lumo-size-xs", "0.75rem");
        
        // Improve tree hierarchy indentation
        getStyle().set("--vaadin-grid-tree-toggle-level-offset", "1rem");
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

            System.out.println("Loading section navigation data for respondent " + respondentId + 
                             ", found " + navigationMap.size() + " parent sections");

            for (Map.Entry<SectionNavigationItem, List<SectionNavigationItem>> entry : navigationMap.entrySet()) {
                SectionNavigationItem parent = entry.getKey();
                List<SectionNavigationItem> children = entry.getValue();

                // Add parent to tree data
                treeData.addItem(null, parent);
                System.out.println("Added parent section: " + parent.getDisplayText() + 
                                 " with " + children.size() + " children");

                // Add children to tree data
                for (SectionNavigationItem child : children) {
                    treeData.addItem(parent, child);
                    System.out.println("  Added child: " + child.getDisplayText() + 
                                     " (key: " + child.getDisplayKey() + ")");
                }
            }

            dataProvider.refreshAll();
            
            // Keep all nodes collapsed by default
            collapseAll();
            
        } catch (Exception e) {
            System.err.println("Error loading section navigation data: " + e.getMessage());
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
