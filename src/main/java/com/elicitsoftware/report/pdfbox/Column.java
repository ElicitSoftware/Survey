package com.elicitsoftware.report.pdfbox;

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
 * Represents a column in a PDF table with a name and width.
 * This class is used when generating PDF reports to define the structure
 * and layout of table columns.
 */
public class Column {

    /** The name/header text of the column. */
    private String name;

    /** The width of the column in PDF units. */
    private float width;

    /**
     * Constructs a new Column with the specified name and width.
     *
     * @param name the name/header text for the column
     * @param width the width of the column in PDF units
     */
    public Column(String name, float width) {
        this.name = name;
        this.width = width;
    }

    /**
     * Gets the name of the column.
     *
     * @return the column name/header text
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the name of the column.
     *
     * @param name the column name/header text to set
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Gets the width of the column.
     *
     * @return the column width in PDF units
     */
    public float getWidth() {
        return width;
    }

    /**
     * Sets the width of the column.
     *
     * @param width the column width in PDF units to set
     */
    public void setWidth(float width) {
        this.width = width;
    }
}
