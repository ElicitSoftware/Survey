package com.elicitsoftware.model;

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
 * Represents a navigation item in the section tree grid.
 * <p>
 * This class encapsulates the data needed to display section navigation
 * in a hierarchical tree structure where sections with section=0 are top-level
 * items and sections with section!=0 are children ordered by display_key.
 */
public class SectionNavigationItem {
    
    private Integer section;
    private String displayText;
    private String displayKey;
    private boolean isParent;
    private boolean isCurrent;
    
    /**
     * Constructor for creating a section navigation item.
     *
     * @param section the section number (0 for top-level, non-zero for children)
     * @param displayText the text to display in the navigation tree
     * @param displayKey the unique key for ordering and identification
     */
    public SectionNavigationItem(Integer section, String displayText, String displayKey) {
        this.section = section;
        this.displayText = displayText;
        this.displayKey = displayKey;
        this.isParent = (section != null && section == 0);
    }
    
    /**
     * Gets the section number.
     *
     * @return the section number
     */
    public Integer getSection() {
        return section;
    }
    
    /**
     * Sets the section number.
     *
     * @param section the section number
     */
    public void setSection(Integer section) {
        this.section = section;
        this.isParent = (section != null && section == 0);
    }
    
    /**
     * Gets the display text.
     *
     * @return the display text
     */
    public String getDisplayText() {
        return displayText;
    }
    
    /**
     * Sets the display text.
     *
     * @param displayText the display text
     */
    public void setDisplayText(String displayText) {
        this.displayText = displayText;
    }
    
    /**
     * Gets the display key.
     *
     * @return the display key
     */
    public String getDisplayKey() {
        return displayKey;
    }
    
    /**
     * Sets the display key.
     *
     * @param displayKey the display key
     */
    public void setDisplayKey(String displayKey) {
        this.displayKey = displayKey;
    }
    
    /**
     * Checks if this item is a parent (top-level) item.
     *
     * @return true if this is a parent item (section == 0), false otherwise
     */
    public boolean isParent() {
        return isParent;
    }
    
    /**
     * Sets whether this item is a parent.
     *
     * @param isParent true if this is a parent item
     */
    public void setParent(boolean isParent) {
        this.isParent = isParent;
    }
    
    /**
     * Checks if this item is the current section being viewed.
     *
     * @return true if this is the current section, false otherwise
     */
    public boolean isCurrent() {
        return isCurrent;
    }
    
    /**
     * Sets whether this item is the current section being viewed.
     *
     * @param isCurrent true if this is the current section
     */
    public void setCurrent(boolean isCurrent) {
        this.isCurrent = isCurrent;
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        SectionNavigationItem that = (SectionNavigationItem) obj;
        return displayKey != null ? displayKey.equals(that.displayKey) : that.displayKey == null;
    }
    
    @Override
    public int hashCode() {
        return displayKey != null ? displayKey.hashCode() : 0;
    }
    
    @Override
    public String toString() {
        return "SectionNavigationItem{" +
                "section=" + section +
                ", displayText='" + displayText + '\'' +
                ", displayKey='" + displayKey + '\'' +
                ", isParent=" + isParent +
                ", isCurrent=" + isCurrent +
                '}';
    }
}
