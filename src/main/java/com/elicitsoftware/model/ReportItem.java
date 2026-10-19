/*-
 * ***LICENSE_START***
 * Elicit Survey
 * %%
 * Copyright (C) 2025 - 2026 The Regents of the University of Michigan - Rogel Cancer Center
 * %%
 * PolyForm Noncommercial License 1.0.0
 * <https://polyformproject.org/licenses/noncommercial/1.0.0>
 * ***LICENSE_END***
 */
package com.elicitsoftware.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.util.UUID;

/**
 * A report mark ({@code survey.report_items}, V023, UC-013): one structural element, named by
 * durable id, marked for the respondent report or the administrator report, in report order and
 * under an optional label. Authored in Author and installed by Admin's apply; Survey only reads
 * it, to render the respondent report (UC-014), so the mapping is immutable.
 */
@Entity
@Immutable
@Table(name = "report_items", schema = "survey")
public class ReportItem extends PanacheEntityBase {

    public static final String RESPONDENT = "RESPONDENT";
    public static final String ADMINISTRATOR = "ADMINISTRATOR";

    /** Which of the three element columns a mark uses (UC-013 BR-001). */
    public enum ElementRef {
        STEPS_SECTIONS,
        QUESTION,
        SECTIONS_QUESTION
    }

    @Id
    @Column(name = "id", nullable = false)
    public Integer id;

    @Column(name = "survey_id", nullable = false)
    public Integer surveyId;

    @Column(name = "report_item_key", nullable = false)
    public UUID reportItemKey;

    @Column(name = "report_type", nullable = false)
    public String reportType;

    @Column(name = "steps_sections_id")
    public Integer stepsSectionsId;

    @Column(name = "question_id")
    public Integer questionId;

    @Column(name = "sections_question_id")
    public Integer sectionsQuestionId;

    @Column(name = "label")
    public String label;

    @Column(name = "display_order", nullable = false)
    public Integer displayOrder;

    public ElementRef elementRef() {
        if (stepsSectionsId != null) {
            return ElementRef.STEPS_SECTIONS;
        }
        if (questionId != null) {
            return ElementRef.QUESTION;
        }
        return sectionsQuestionId != null ? ElementRef.SECTIONS_QUESTION : null;
    }
}
