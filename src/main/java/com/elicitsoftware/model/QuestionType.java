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
 * Represents a question type entity in the "question_types" table within the "survey" schema.
 * Each question type defines a specific type of question that can be used in a survey.
 * This class utilizes Hibernate ORM and extends PanacheEntityBase for simplified data access.
 * <p>
 * Attributes:
 * - id: The unique identifier for the question type.
 * - dataType: The data type used for storing the question's responses.
 * - description: A textual description of the question type.
 * - name: The name of the question type.
 */
@Entity
@Table(name = "question_types", schema = "survey")
public class QuestionType extends PanacheEntityBase {

    @Id
    @SequenceGenerator(name = "QUESTION_TYPES_ID_GENERATOR", schema = "survey", sequenceName = "question_types_seq", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "QUESTION_TYPES_ID_GENERATOR")
    @Column(name = "id", unique = true, nullable = false, precision = 20)
    public Integer id;

    @Column(name = "data_type", length = 255)
    public String dataType;

    @Column(name = "description", length = 255)
    public String description;

    @Column(name = "name", length = 255)
    public String name;

}
