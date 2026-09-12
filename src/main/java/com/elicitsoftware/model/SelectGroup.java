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

import java.time.OffsetDateTime;
import java.util.List;


/**
 * The SelectGroup class represents a group of selectable items related to a survey.
 * It maps to the "select_groups" table within the "survey" schema in the database.
 * Each SelectGroup is associated with a survey and contains multiple SelectItems
 * that are part of the group. It allows the management of grouped selections
 * for survey questions.
 * <p>
 * Attributes:
 * - id: The unique identifier for the select group.
 * - surveyId: The identifier of the survey to which this group belongs.
 * - description: An optional description of the select group.
 * - name: The name of the select group.
 * - selectItems: The list of associated SelectItem entities that belong to this group,
 * sorted by their display order.
 * <p>
 * This class uses Hibernate ORM for database interaction and extends
 * PanacheEntityBase for simplified data persistence and querying.
 */
@Entity
@Table(name = "select_groups", schema = "survey")
public class SelectGroup extends PanacheEntityBase {

    @Id
    @SequenceGenerator(name = "SELECT_GROUPS_ID_GENERATOR", schema = "survey", sequenceName = "select_groups_seq", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "SELECT_GROUPS_ID_GENERATOR")
    @Column(unique = true, nullable = false, precision = 20)
    public Integer id;

    @Column(name = "survey_id", nullable = false, precision = 20)
    public Integer surveyId;

    @Column(length = 255)
    public String description;

    @Column(length = 255)
    public String name;

    // Kimball Type 2 SCD (research/Kimball_type_2.md) — select_group_id is the durable key
    // that survives re-versioning; id (above) is the surrogate, per-version row id.
    @Column(name = "select_group_id", nullable = false)
    public Integer selectGroupId;

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

    // select_items.select_group_id now holds the durable select_group_id (not
    // select_groups.id) — referencedColumnName must point at that durable column,
    // not the default surrogate @Id, or this association silently matches nothing.
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "select_group_id", referencedColumnName = "select_group_id")
    @OrderBy("displayOrder ASC")
    public List<SelectItem> selectItems;
}
