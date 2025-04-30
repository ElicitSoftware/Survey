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

/**
 * ReportRequest represents the payload used for requesting a report from the ReportService.
 * <p>
 * This class holds the data necessary to define the parameters of the report to be generated
 * or retrieved. It is primarily used as an input parameter for APIs such as {@link ReportService#callReport(ReportRequest)}.
 * <p>
 * Fields:
 * - id: An integer representing the unique identifier for the report request.
 * <p>
 * The id field can be accessed and modified through its getter and setter methods.
 */
public class ReportRequest {
    private int id;

    public ReportRequest(int id) {
        super();
        this.id = id;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }
}
