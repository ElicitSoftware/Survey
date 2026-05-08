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

import com.elicitsoftware.report.pdf.Content;
import com.elicitsoftware.report.pdf.PDFDocument;
import com.elicitsoftware.report.pdf.Table;
import com.elicitsoftware.response.ReviewItem;
import com.elicitsoftware.response.ReviewSection;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.TupleElement;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * REST service that generates a survey summary report driven by
 * {@code surveyreport.fact_sections_view}.
 * <p>
 * The view contains one row per section per respondent. Structural columns
 * ({@code id}, {@code survey_id}, {@code respondent_id}, {@code step_key},
 * {@code step_instance}, {@code section_key}, {@code section_instance}) are
 * skipped. The {@code name} column is used as the section heading. Every other
 * non-null column represents an ontology dimension value: the column name (e.g.
 * {@code literary_genre}) is formatted as a human-readable label ("Literary
 * Genre") and printed alongside its value. This demonstrates the full metadata
 * → ontology → ETL → reporting-schema pipeline.
 */
@Path("/survey-summary")
@RequestScoped
public class SurveySummaryService {

    /**
     * Columns from {@code fact_sections_view} that carry structural/key data
     * and should not be rendered as dimension values in the report.
     */
    private static final Set<String> STRUCTURAL_COLUMNS = Set.of(
            "id", "survey_id", "respondent_id",
            "step_key", "step_instance", "step",
            "section_key", "section_instance", "section"
    );

    private static final String FACT_SECTIONS_VIEW_SQL = """
            SELECT *
            FROM surveyreport.fact_sections_view
            WHERE respondent_id = :respondentId
            ORDER BY step_key, step_instance, section_key, section_instance
            """;

    @Inject
    EntityManager entityManager;

    /**
     * Generates a survey summary report for the given respondent from the
     * reporting schema's {@code fact_sections_view}. Each section row becomes
     * a heading; non-null, non-structural columns are rendered as
     * (dimension label, value) pairs.
     *
     * @param req the report request containing the respondent ID
     * @return a {@link ReportResponse} with HTML content and a PDF document
     */
    @Path("/report")
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Transactional
    public ReportResponse report(ReportRequest req) {
        @SuppressWarnings("unchecked")
        List<Tuple> results = entityManager
                .createNativeQuery(FACT_SECTIONS_VIEW_SQL, Tuple.class)
                .setParameter("respondentId", req.getId())
                .getResultList();

        // Each Tuple row is one section; non-structural non-null columns are dimension values.
        List<ReviewSection> sections = new ArrayList<>();
        for (Tuple row : results) {
            String sectionName = (String) row.get("name");
            List<ReviewItem> items = new ArrayList<>();
            for (TupleElement<?> element : row.getElements()) {
                String colName = element.getAlias();
                if ("name".equals(colName) || STRUCTURAL_COLUMNS.contains(colName)) {
                    continue;
                }
                Object value = row.get(colName);
                if (value != null) {
                    items.add(new ReviewItem(formatColumnName(colName), String.valueOf(value)));
                }
            }
            if (!items.isEmpty()) {
                sections.add(new ReviewSection(sectionName != null ? sectionName : "", "", items));
            }
        }

        // Count total rows for PDF table pre-allocation (+1 per section for its header row)
        int totalRows = 0;
        for (ReviewSection section : sections) {
            totalRows += section.getItems().size() + 1;
        }

        StringBuilder innerHTML = new StringBuilder();

        // Build PDF table (3 columns: Section, Dimension, Value)
        Table table = new Table();
        table.headers = new String[]{"Section", "Dimension", "Value"};
        table.widths = new float[]{150f, 200f, 150f};
        table.body = new String[Math.max(totalRows, 1)][3];

        int rowIndex = 0;

        for (ReviewSection section : sections) {
            List<ReviewItem> sectionItems = section.getItems();

            // HTML: section heading
            innerHTML.append("<h3 style=\"margin-top:1em;\">")
                     .append(escapeHtml(section.getTitle()))
                     .append("</h3>");
            innerHTML.append("<table style=\"width:100%;border-collapse:collapse;margin-bottom:0.5em;\">");

            // PDF: section header row
            if (rowIndex < table.body.length) {
                table.body[rowIndex][0] = section.getTitle();
                table.body[rowIndex][1] = "";
                table.body[rowIndex][2] = "";
                rowIndex++;
            }

            for (ReviewItem item : sectionItems) {
                // HTML row
                innerHTML.append("<tr>")
                         .append("<td style=\"padding:4px 8px;font-weight:bold;width:40%;\">")
                         .append(escapeHtml(item.label()))
                         .append("</td>")
                         .append("<td style=\"padding:4px 8px;\">")
                         .append(escapeHtml(item.value()))
                         .append("</td>")
                         .append("</tr>");

                // PDF row
                if (rowIndex < table.body.length) {
                    table.body[rowIndex][0] = "";
                    table.body[rowIndex][1] = item.label() != null ? item.label() : "";
                    table.body[rowIndex][2] = item.value() != null ? item.value() : "";
                    rowIndex++;
                }
            }

            innerHTML.append("</table>");
        }

        if (innerHTML.length() == 0) {
            innerHTML.append("<p>No reporting data found for this respondent.</p>");
            table.body[0][0] = "";
            table.body[0][1] = "No reporting data found.";
            table.body[0][2] = "";
        }

        // Trim table body to actual rows used
        if (rowIndex < table.body.length) {
            String[][] trimmed = new String[rowIndex][3];
            System.arraycopy(table.body, 0, trimmed, 0, rowIndex);
            table.body = trimmed;
        }

        PDFDocument pdf = new PDFDocument();
        pdf.title = "Survey Summary";
        pdf.pageBreak = false;
        pdf.content = new Content[]{new Content(table)};

        return new ReportResponse("Survey Summary", innerHTML.toString(), pdf);
    }

    /**
     * Converts a snake_case column name (e.g. {@code literary_genre}) to a
     * title-cased label (e.g. "Literary Genre").
     */
    private static String formatColumnName(String colName) {
        if (colName == null || colName.isEmpty()) return "";
        String[] parts = colName.split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty()) {
                if (sb.length() > 0) sb.append(' ');
                sb.append(Character.toUpperCase(part.charAt(0)));
                sb.append(part.substring(1));
            }
        }
        return sb.toString();
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
