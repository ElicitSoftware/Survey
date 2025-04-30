package com.elicitsoftware.report;

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

import com.elicitsoftware.report.pdf.PDFDocument;

/**
 * ReportResponse represents the structure of the response obtained from a report service.
 * <p>
 * This class is used to encapsulate the various details necessary for rendering a report including
 * Title, HTML content, and an optional PDF representation of the report.
 * It is primarily intended to be used as a data structure for processes dealing with report creation
 * or rendering in the application.
 * <p>
 * Fields:
 * - Title: name of the report.
 * - innerHTML: The inner HTML content of the report, typically used for rendering the report on a web page.
 * - pdf: A PDFDocument object representing the report in PDF format, if applicable.
 */
public class ReportResponse {
    public String title;
    public String innerHTML;
    public PDFDocument pdf;

    public ReportResponse(){
        super();
    }

    public ReportResponse(String title, String innerHTML, PDFDocument pdf) {
        super();
        this.title = title;
        this.innerHTML = innerHTML;
        this.pdf = pdf;
    }
}

