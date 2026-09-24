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

    // NUMERIC, not INTEGER (Kimball Type 2 SCD, research/Kimball_type_2.md's "Adding a new
    // question" section) -- supports decimal-midpoint insertion between existing positions
    // without renumbering, once an eventual Author Tool can create fractional values here.
    @Column(name = "display_order", nullable = false, precision = 3)
    public BigDecimal displayOrder;

    @Column(length = 255)
    public String name;

    @Column(length = 255)
    public String description;

    // Kimball Type 2 SCD (research/Kimball_type_2.md) — section_id is the durable key
    // that survives re-versioning; id (above) is the surrogate, per-version row id.
    @Column(name = "section_id", nullable = false)
    public Integer sectionId;

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
     * The version of the durable {@code section_id} in effect at {@code asOf} (research/
     * Kimball_type_2.md, "Snapshot Anchor"). See {@link Step#findAsOf} for why durable keys
     * are resolved through a finder rather than mapped as JPA associations.
     *
     * @param sectionId the durable {@code sections.section_id}; {@code null} yields {@code null}
     * @param asOf      the respondent's snapshot anchor
     * @return the section in effect at {@code asOf}, or {@code null} if none covers it
     */
    public static Section findAsOf(Integer sectionId, OffsetDateTime asOf) {
        if (sectionId == null) {
            return null;
        }
        return Section.<Section>find("sectionId = ?1 and effectiveFrom <= ?2 and effectiveTo > ?2", sectionId, asOf)
                .singleResultOptional().orElse(null);
    }
}
