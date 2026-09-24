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

    @Column(name = "published_by")
    public String publishedBy;

    @Column(name = "published_comment")
    public String publishedComment;

    /**
     * The version of the durable {@code step_id} in effect at {@code asOf} (research/
     * Kimball_type_2.md, "Snapshot Anchor"): the one row whose effective window covers the
     * instant. Durable keys are never mapped as JPA associations -- once a revision exists a
     * durable id has a row per version and Hibernate refuses to pick one ("More than one row
     * with the given identifier was found"), which marked the whole transaction rollback-only.
     * Every caller that holds a durable step id resolves it here with the respondent's anchor.
     *
     * @param stepId the durable {@code steps.step_id}; {@code null} yields {@code null}
     * @param asOf   the respondent's snapshot anchor ({@code respondents.first_access_dt}, or now)
     * @return the step in effect at {@code asOf}, or {@code null} if none covers it
     * @throws jakarta.persistence.NonUniqueResultException if two versions overlap the instant
     */
    public static Step findAsOf(Integer stepId, OffsetDateTime asOf) {
        if (stepId == null) {
            return null;
        }
        return Step.<Step>find("stepId = ?1 and effectiveFrom <= ?2 and effectiveTo > ?2", stepId, asOf)
                .singleResultOptional().orElse(null);
    }
}
