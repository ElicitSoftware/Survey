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
import jakarta.persistence.*;

/**
 * The Section class represents an entity in the "sections" table within the "survey" schema.
 * Each Section is associated with a survey and represents a specific section
 * in the survey, with a defined display order, name, and optional description.
 * This class utilizes Hibernate ORM and extends PanacheEntityBase to facilitate
 * simplified ORM operations.
 * <p>
 * Attributes:
 * - id: The unique identifier for the section.
 * - surveyId: The identifier of the survey to which this section belongs.
 * - displayOrder: The order in which this section is displayed.
 * - name: The name of the section.
 * - description: A textual description of the section.
 */
@Entity
@Table(name = "sections", schema = "survey")
public class Section extends PanacheEntityBase {

    @Transient
    private final Integer instance = 0;

    @Id
    @SequenceGenerator(name = "SECTIONS_ID_GENERATOR", schema = "survey", sequenceName = "sections_seq", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "SECTIONS_ID_GENERATOR")
    @Column(name = "ID", unique = true, nullable = false, precision = 20)
    public Integer id;

    @Column(name = "survey_id", nullable = false, precision = 20)
    public Integer surveyId;

    @Column(name = "display_order", nullable = false, precision = 3)
    public Integer displayOrder;

    @Column(length = 255)
    public String name;

    @Column(length = 255)
    public String description;
}
