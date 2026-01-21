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
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;


/**
 * The OperatorType class represents an entity in the "operator_types" table within the "survey" schema.
 * This class is used to define various types of operators that may have specific characteristics
 * such as a name, a symbol, and a description.
 * <p>
 * Attributes:
 * - id: The unique identifier for the operator type.
 * - description: A descriptive text providing details about the operator type.
 * - name: The name of the operator type.
 * - symbol: A symbolic representation associated with the operator type.
 * <p>
 * The class utilizes Hibernate ORM and extends PanacheEntityBase for simplified data access
 * and manipulation.
 */
@Entity
@Table(name = "operator_types", schema = "survey")
@Cache(usage = CacheConcurrencyStrategy.READ_ONLY)
public class OperatorType extends PanacheEntityBase {

    /** The unique identifier for the operator type. */
    @Id
    @SequenceGenerator(name = "OPERATOR_TYPES_ID_GENERATOR", schema = "survey", sequenceName = "operator_types_seq", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "OPERATOR_TYPES_ID_GENERATOR")
    @Column(unique = true, nullable = false, precision = 20)
    public Integer id;

    /** A textual description of the operator type. */
    @Column(length = 255)
    public String description;

    /** The name of the operator type. */
    @Column(length = 255)
    public String name;

    /** The symbol representation of the operator type. */
    @Column(length = 10)
    public String symbol;

}
