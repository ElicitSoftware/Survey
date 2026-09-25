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

    /**
     * BCP-47 tag of the language the respondent was reading, or {@code null} when that was the
     * survey's base language (Survey V019).
     * <p>
     * A report body is produced by an external service from the respondent's answers, so Elicit
     * cannot translate it; what it can do is say which language was asked for and let a service
     * that has translations of its own use it. A service that ignores the field behaves exactly as
     * it does today.
     */
    private String language;

    public ReportRequest(int id) {
        super();
        this.id = id;
    }

    public ReportRequest(int id, String language) {
        super();
        this.id = id;
        this.language = language;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }
}
