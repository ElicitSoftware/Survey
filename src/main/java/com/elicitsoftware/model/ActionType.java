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
 * The ActionType class represents an entity in the "action_types" table within the "survey" schema.
 * This entity is used to define different types of actions supported in the system,
 * with a unique identifier, name, and an optional description.
 * <p>
 * This class utilizes Hibernate ORM and extends PanacheEntityBase for simplified data access.
 * <p>
 * Attributes:
 * - id: The unique identifier for the action type.
 * - name: The name of the action type.
 * - description: An optional textual description of the action type.
 */
@Entity
@Table(name = "action_types", schema = "survey")
public class ActionType extends PanacheEntityBase {

    /** The unique identifier for the action type. */
    @Id
    @SequenceGenerator(name = "ACTION_TYPES_ID_GENERATOR", schema = "survey", sequenceName = "action_types_seq", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ACTION_TYPES_ID_GENERATOR")
    @Column(name = "id", unique = true, nullable = false, precision = 20)
    public Integer id;

    /** An optional textual description of the action type. */
    @Column(name = "description", length = 255)
    public String description;

    /** The name of the action type. */
    @Column(name = "name", length = 255)
    public String name;

}
