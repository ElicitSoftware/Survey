package com.elicitsoftware.report.pdf;

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

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Content represents an individual element of a PDF document, which can include
 * images, text, or layout properties. This class is used to specify the content's
 * structure and formatting properties.
 * <p>
 * Fields:
 * - svg: A string representation of an SVG image to be included in the PDF.
 * - pngURL: A URL pointing to a PNG image resource for the content.
 * - width: The specified width of the content in pixels or other appropriate units.
 * - text: The textual content to be rendered within this element.
 * - style: A reference to the style settings associated with this content, typically
 * identified by a Style object in the enclosing context.
 * - pageOrientation: Specifies the orientation of the page ("portrait" or "landscape").
 * Defaults to "portrait".
 * - pageBreak: Indicates the presence of a page break before or after the content.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Content {
    /** The textual content to be rendered within this element. */
    public String text;

    /** A string representation of an SVG image to be included in the PDF. */
    public String svg;

    /** A table to be included in the PDF content. */
    public Table table;

    /** A reference to the style settings associated with this content. */
    public String style;

    /**
     * Default constructor for Content.
     */
    public Content() {
        super();
    }

    /**
     * Constructor that initializes Content with a table.
     *
     * @param table the table to be included in this content
     */
    public Content(Table table) {
        super();
        this.table = table;
    }
}
