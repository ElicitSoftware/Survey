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
 * The SelectItem class represents a selectable item within a survey and
 * corresponds to the "SELECT_ITEMS" table in the database under the "survey" schema.
 * Each SelectItem is linked to a survey and belongs to a select group, enabling
 * the definition of various selectable options for a survey question.
 * <p>
 * Attributes:
 * - id: Unique identifier for the select item.
 * - surveyId: Identifier for the survey to which this item belongs.
 * - codedValue: Optional textual code associated with the select item.
 * - displayText: Text displayed to represent this select item.
 * - selectGroupId: Identifier for the group to which this select item belongs.
 * - displayOrder: The order in which the select item should appear within its group.
 * <p>
 * This class uses Hibernate ORM for database interaction and extends
 * PanacheEntityBase for convenient data persistence and query methods.
 */
@Entity
@Table(name = "SELECT_ITEMS", schema = "survey")
public class SelectItem extends PanacheEntityBase {

    @Id
    @SequenceGenerator(name = "SELECT_ITEMS_ID_GENERATOR", schema = "survey", sequenceName = "select_items_seq", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "SELECT_ITEMS_ID_GENERATOR")
    @Column(name = "id", unique = true, nullable = false, precision = 20)
    public Integer id;

    @Column(name = "survey_id", nullable = false, precision = 20)
    public Integer surveyId;

    @Column(name = "coded_value", length = 255)
    public String codedValue;

    @Column(name = "display_text", length = 255)
    public String displayText;

    @Column(name = "group_id", nullable = false)
    public Integer selectGroupId;

    @Column(name = "display_order", nullable = false, precision = 20)
    public Integer displayOrder;

}
