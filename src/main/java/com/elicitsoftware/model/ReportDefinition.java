package com.elicitsoftware.model;

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

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.json.bind.annotation.JsonbTransient;
import jakarta.persistence.*;

/**
 * Represents a report definition in the system. Each report is associated with a survey
 * and contains information related to its name, description, URL, and display order.
 * <p>
 * This entity is mapped to the "reports" table within the "survey" schema and
 * uses Hibernate ORM for persistence. It extends the PanacheEntityBase class
 * for simplified data access.
 * <p>
 * Relationships:
 * - A report is linked to a parent Survey entity via a many-to-one relationship,
 * ensuring that each report is associated with a specific survey.
 * <p>
 * Attributes:
 * - id: The unique identifier for the report.
 * - survey: The associated survey entity for the report.
 * - name: The name of the report.
 * - description: A short textual description of the report.
 * - url: The URL for accessing the report.
 * - displayOrder: An integer indicating the order in which the reports should be displayed.
 */
@Entity
@Table(name = "reports", schema = "survey")
public class ReportDefinition extends PanacheEntityBase {

    @Id
    @SequenceGenerator(name = "REPORT_ID_GENERATOR", schema = "survey", sequenceName = "reports_seq", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "REPORT_ID_GENERATOR")
    @Column(name = "id", unique = true, nullable = false)
    public Integer id;

    @JsonbTransient
    @ManyToOne()
    @JoinColumn(name = "survey_id", nullable = false)
    public Survey survey;

    @Column(name = "name")
    public String name;

    @Column(name = "description")
    public String description;

    @Column(name = "url")
    public String url;

    @Column(name = "display_order")
    public Integer displayOrder;

}
