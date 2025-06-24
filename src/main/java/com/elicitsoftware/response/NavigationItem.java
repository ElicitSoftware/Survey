package com.elicitsoftware.response;

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

import jakarta.xml.bind.annotation.XmlRootElement;

/**
 * Represents a navigation item in a workflow or survey process.
 * <p>
 * A NavigationItem stores information about a specific step in a sequence, including its
 * name, path, and its relationship to other steps (previous and next). It also tracks
 * whether the step is marked as complete.
 * <p>
 * This class is typically used in workflows to manage and navigate through ordered steps
 * of a process, supporting operations such as retrieving step details, navigating forward
 * and backward, and marking steps as complete.
 */
@XmlRootElement
public class NavigationItem {
    private final String name;
    private final String path;
    private final String previous;
    private final boolean complete;
    private String next;

    /**
     * Constructs a NavigationItem with the specified properties.
     *
     * @param name the display name of the navigation item
     * @param complete whether this navigation item is complete
     * @param path the path/URL for this navigation item
     * @param next the next navigation item identifier
     * @param previous the previous navigation item identifier
     */
    public NavigationItem(String name, boolean complete, String path, String next, String previous) {

        super();
        this.name = name;
        this.complete = complete;
        this.path = path;
        this.next = next;
        this.previous = previous;
    }

    /**
     * Gets the display name of this navigation item.
     *
     * @return the name of the navigation item
     */
    public String getName() {
        return name;
    }

    /**
     * Checks if this navigation item is complete.
     *
     * @return true if the navigation item is complete, false otherwise
     */
    public boolean isComplete() {
        return complete;
    }

    /**
     * Gets the path/URL for this navigation item.
     *
     * @return the path of the navigation item
     */
    public String getPath() {
        return path;
    }

    /**
     * Gets the next navigation item identifier.
     *
     * @return the next navigation item identifier
     */
    public String getNext() {
        return next;
    }

    /**
     * Sets the next navigation item identifier.
     *
     * @param next the next navigation item identifier to set
     */
    public void setNext(String next) {
        this.next = next;
    }

    /**
     * Gets the previous navigation item identifier.
     *
     * @return the previous navigation item identifier
     */
    public String getPrevious() {
        return previous;
    }

}
