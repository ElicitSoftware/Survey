package com.elicitsoftware.report.pdfbox;

/*-
 * ***LICENSE_START***
 * Elicit Survey
 * %%
 * Copyright (C) 2025 - 2026 Elicit Software LLC
 * %%
 * PolyForm Noncommercial License 1.0.0
 * <https://polyformproject.org/licenses/noncommercial/1.0.0>
 * ***LICENSE_END***
 */

import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;

import java.util.List;

/**
 * Builds a {@link Table} for the PDF report by setting its properties one call at a time.
 * Each setter writes straight to the table under construction and returns this builder.
 */
public class TableBuilder {

    private final Table table = new Table();

    /**
     * Sets the most vertical space, in points, the table may take on one page.
     *
     * @param height the height budget in points
     * @return this builder for chaining
     */
    public TableBuilder setHeight(float height) {
        table.setHeight(height);
        return this;
    }

    /**
     * Sets the number of body rows.
     *
     * @param numberOfRows the number of rows in the content
     * @return this builder for chaining
     */
    public TableBuilder setNumberOfRows(Integer numberOfRows) {
        table.setNumberOfRows(numberOfRows);
        return this;
    }

    /**
     * Sets the height of one line of text, the unit a row is measured in.
     *
     * @param rowHeight the single-line row height in points
     * @return this builder for chaining
     */
    public TableBuilder setRowHeight(float rowHeight) {
        table.setRowHeight(rowHeight);
        return this;
    }

    /**
     * Sets the cell texts, one array per row.
     *
     * @param content the rows of cell texts
     * @return this builder for chaining
     */
    public TableBuilder setContent(String[][] content) {
        table.setContent(content);
        return this;
    }

    /**
     * Sets the actual rendered height of each body row.
     *
     * @param rowHeights array of per-row heights, in the same order as the content
     * @return this builder for chaining
     */
    public TableBuilder setRowHeights(float[] rowHeights) {
        table.setRowHeights(rowHeights);
        return this;
    }

    /**
     * Sets the table's columns.
     *
     * @param columns the columns, in display order
     * @return this builder for chaining
     */
    public TableBuilder setColumns(List<Column> columns) {
        table.setColumns(columns);
        return this;
    }

    /**
     * Sets the padding between a cell's border and its text.
     *
     * @param cellMargin the cell padding in points
     * @return this builder for chaining
     */
    public TableBuilder setCellMargin(float cellMargin) {
        table.setCellMargin(cellMargin);
        return this;
    }

    /**
     * Sets the distance from the left edge of the page to the table.
     *
     * @param margin the page margin in points
     * @return this builder for chaining
     */
    public TableBuilder setMargin(float margin) {
        table.setMargin(margin);
        return this;
    }

    /**
     * Sets the size of the pages the table adds when it breaks.
     *
     * @param pageSize the page size
     * @return this builder for chaining
     */
    public TableBuilder setPageSize(PDRectangle pageSize) {
        table.setPageSize(pageSize);
        return this;
    }

    /**
     * Sets whether pages the table adds are rotated to landscape.
     *
     * @param landscape {@code true} to rotate added pages
     * @return this builder for chaining
     */
    public TableBuilder setLandscape(boolean landscape) {
        table.setLandscape(landscape);
        return this;
    }

    /**
     * Sets the font the cells are written in.
     *
     * @param textFont the cell font
     * @return this builder for chaining
     */
    public TableBuilder setTextFont(PDFont textFont) {
        table.setTextFont(textFont);
        return this;
    }

    /**
     * Sets the font size of the cells.
     *
     * @param fontSize the font size in points
     * @return this builder for chaining
     */
    public TableBuilder setFontSize(float fontSize) {
        table.setFontSize(fontSize);
        return this;
    }

    /**
     * Returns the table built so far. The builder keeps a reference to it, so a later setter call
     * still changes the returned table.
     *
     * @return the table
     */
    public Table build() {
        return table;
    }
}
