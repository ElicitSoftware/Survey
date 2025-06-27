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

import java.util.Map;

/**
 * PDFDocument represents the structure of a PDF document used in the application.
 * <p>
 * This class is designed to encapsulate the content and styles of a generated PDF,
 * which can then be used as part of a report. It organizes the document into a set
 * of content items and corresponding styles to allow flexible rendering.
 * <p>
 * Fields:
 * - content: An array of Content objects, each representing an individual section or
 * element of the PDF, including its text, images, or layout properties.
 * - styles: A map of Style objects keyed by their identifiers, which defines the
 * formatting and appearance details applied to the content elements of the PDF.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PDFDocument {
    /** An array of content elements that make up the PDF document. */
    public Content[] content;

    /** Flag indicating whether a page break should be inserted. */
    public boolean pageBreak = false;

    /** Flag indicating whether the document should be in landscape orientation. */
    public boolean landscape = false;

    /** The title of the PDF document. */
    public String title;

    /** A map of styles keyed by their identifiers for formatting content. */
    public Map<String, Style> styles;
}
