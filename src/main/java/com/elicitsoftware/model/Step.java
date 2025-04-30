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
 * The Step class represents an entity in the "steps" table within the "survey" schema.
 * Each Step is associated with a survey and represents a specific step
 * in the survey process, with a defined order, name, and optional description.
 * This class utilizes Hibernate ORM and extends PanacheEntityBase for
 * simplified data access.
 * <p>
 * Attributes:
 * - id: The unique identifier for the step.
 * - surveyId: The identifier of the survey to which this step belongs.
 * - displayOrder: The order in which this step is displayed.
 * - description: A textual description of the step.
 * - name: The name of the step.
 */
@Entity
@Table(name = "steps", schema = "survey")
public class Step extends PanacheEntityBase {

    @Id
    @SequenceGenerator(name = "STEP_ID_GENERATOR", schema = "survey", sequenceName = "steps_seq", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "STEP_ID_GENERATOR")
    @Column(name = "id", unique = true, nullable = false, precision = 20)
    public Integer id;

    @Column(name = "survey_id", nullable = false, precision = 20)
    public Integer surveyId;

    @Column(name = "display_order", nullable = false, precision = 3)
    public Integer displayOrder;

    @Column(length = 255)
    public String description;

    @Column(length = 255)
    public String name;
}
