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

import java.math.BigDecimal;
import java.time.OffsetDateTime;

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

    // NUMERIC, not INTEGER (Kimball Type 2 SCD, research/Kimball_type_2.md's "Adding a new
    // question" section) -- supports decimal-midpoint insertion between existing positions
    // without renumbering, once an eventual Author Tool can create fractional values here.
    @Column(name = "display_order", nullable = false, precision = 3)
    public BigDecimal displayOrder;

    @Column(length = 255)
    public String description;

    @Column(length = 255)
    public String name;

    // Kimball Type 2 SCD (research/Kimball_type_2.md) — step_id is the durable key
    // that survives re-versioning; id (above) is the surrogate, per-version row id.
    @Column(name = "step_id", nullable = false)
    public Integer stepId;

    @Column(name = "version", nullable = false)
    public Integer version = 0;

    @Column(name = "effective_from")
    public OffsetDateTime effectiveFrom;

    @Column(name = "effective_to")
    public OffsetDateTime effectiveTo;

    @Column(name = "is_draft", nullable = false)
    public boolean isDraft = false;

    @Column(name = "published_by")
    public String publishedBy;

    @Column(name = "published_comment")
    public String publishedComment;
}
