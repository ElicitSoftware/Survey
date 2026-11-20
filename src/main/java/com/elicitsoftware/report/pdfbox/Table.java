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

import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;

import java.util.List;

/**
 * The layout of a table that {@code PDFService} draws with PDFBox: its columns and body
 * rows, the page it is drawn on, its font and its spacing. Built by {@link TableBuilder}.
 */
public class Table {

    // Table attributes
    private float margin;
    private float height;
    private PDRectangle pageSize;
    private boolean isLandscape;
    private float rowHeight;

    // font attributes
    private PDFont textFont;
    private float fontSize;

    // Content attributes
    private Integer numberOfRows;
    private List<Column> columns;
    private String[][] content;

    /**
     * Actual rendered height of each body row, in the same order as {@link #content}.
     * <p>
     * A row's height may exceed {@link #rowHeight} (the single-line unit) when one of
     * its cells wraps onto multiple lines.
     */
    private float[] rowHeights;

    private float cellMargin;

    /**
     * Creates an empty table; every attribute is set afterwards, normally through
     * {@link TableBuilder}.
     */
    public Table() {
    }

    /**
     * Returns the number of columns.
     *
     * @return the size of {@link #getColumns()}
     */
    public Integer getNumberOfColumns() {
        return this.getColumns().size();
    }

    /**
     * Returns the table's width, the sum of its columns' widths.
     *
     * @return the total width of all columns, in PDF points
     */
    public float getWidth() {
        float tableWidth = 0f;
        for (Column column : columns) {
            tableWidth += column.getWidth();
        }
        return tableWidth;
    }

    /**
     * Returns the left margin the table is drawn from.
     *
     * @return the x position of the table's left edge, in PDF points
     */
    public float getMargin() {
        return margin;
    }

    /**
     * Sets the left margin the table is drawn from.
     *
     * @param margin the x position of the table's left edge, in PDF points
     */
    public void setMargin(float margin) {
        this.margin = margin;
    }

    /**
     * Returns the size of the pages the table is drawn on.
     *
     * @return the page size
     */
    public PDRectangle getPageSize() {
        return pageSize;
    }

    /**
     * Sets the size of the pages the table is drawn on.
     *
     * @param pageSize the page size
     */
    public void setPageSize(PDRectangle pageSize) {
        this.pageSize = pageSize;
    }

    /**
     * Returns the font the header and the cells are written in.
     *
     * @return the text font
     */
    public PDFont getTextFont() {
        return textFont;
    }

    /**
     * Sets the font the header and the cells are written in.
     *
     * @param textFont the text font
     */
    public void setTextFont(PDFont textFont) {
        this.textFont = textFont;
    }

    /**
     * Returns the size of the text font.
     *
     * @return the font size, in points
     */
    public float getFontSize() {
        return fontSize;
    }

    /**
     * Sets the size of the text font.
     *
     * @param fontSize the font size, in points
     */
    public void setFontSize(float fontSize) {
        this.fontSize = fontSize;
    }

    /**
     * Returns the column names, which make up the header row.
     *
     * @return one name per column, in column order
     */
    public String[] getColumnsNamesAsArray() {
        String[] columnNames = new String[getNumberOfColumns()];
        for (int i = 0; i < getNumberOfColumns(); i++) {
            columnNames[i] = columns.get(i).getName();
        }
        return columnNames;
    }

    /**
     * Returns the table's columns.
     *
     * @return the columns, in display order
     */
    public List<Column> getColumns() {
        return columns;
    }

    /**
     * Sets the table's columns.
     *
     * @param columns the columns, in display order
     */
    public void setColumns(List<Column> columns) {
        this.columns = columns;
    }

    /**
     * Returns the number of body rows, not counting the header row.
     *
     * @return the number of body rows
     */
    public Integer getNumberOfRows() {
        return numberOfRows;
    }

    /**
     * Sets the number of body rows, not counting the header row.
     *
     * @param numberOfRows the number of body rows
     */
    public void setNumberOfRows(Integer numberOfRows) {
        this.numberOfRows = numberOfRows;
    }

    /**
     * Returns the most height the table may take on one page; the rows beyond it continue
     * on the next page.
     *
     * @return the maximum table height per page, in PDF points
     */
    public float getHeight() {
        return height;
    }

    /**
     * Sets the most height the table may take on one page.
     *
     * @param height the maximum table height per page, in PDF points
     */
    public void setHeight(float height) {
        this.height = height;
    }

    /**
     * Returns the height of one line of text: the header row's height and the unit a
     * wrapped cell grows by.
     *
     * @return the single-line row height, in PDF points
     */
    public float getRowHeight() {
        return rowHeight;
    }

    /**
     * Sets the height of one line of text.
     *
     * @param rowHeight the single-line row height, in PDF points
     */
    public void setRowHeight(float rowHeight) {
        this.rowHeight = rowHeight;
    }

    /**
     * Returns the body rows' cell values.
     *
     * @return one array of cell values per body row
     */
    public String[][] getContent() {
        return content;
    }

    /**
     * Sets the body rows' cell values.
     *
     * @param content one array of cell values per body row
     */
    public void setContent(String[][] content) {
        this.content = content;
    }

    /**
     * Returns the actual rendered height of each body row.
     *
     * @return array of per-row heights, in the same order as {@link #getContent()}
     */
    public float[] getRowHeights() {
        return rowHeights;
    }

    /**
     * Sets the actual rendered height of each body row.
     *
     * @param rowHeights array of per-row heights, in the same order as {@link #getContent()}
     */
    public void setRowHeights(float[] rowHeights) {
        this.rowHeights = rowHeights;
    }

    /**
     * Returns the space between a cell's border and its text.
     *
     * @return the cell margin, in PDF points
     */
    public float getCellMargin() {
        return cellMargin;
    }

    /**
     * Sets the space between a cell's border and its text.
     *
     * @param cellMargin the cell margin, in PDF points
     */
    public void setCellMargin(float cellMargin) {
        this.cellMargin = cellMargin;
    }

    /**
     * Returns whether the pages the table continues on are rotated to landscape.
     *
     * @return {@code true} for landscape
     */
    public boolean isLandscape() {
        return isLandscape;
    }

    /**
     * Sets whether the pages the table continues on are rotated to landscape.
     *
     * @param isLandscape {@code true} for landscape
     */
    public void setLandscape(boolean isLandscape) {
        this.isLandscape = isLandscape;
    }
}
