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
 * The Question class represents a survey question entity stored in the "questions" table
 * within the "survey" schema. It encapsulates metadata details about a specific question,
 * such as its text, type, and various optional attributes for validation and display.
 * <p>
 * This class utilizes Hibernate ORM and extends PanacheEntityBase for simplified
 * data persistence and querying. It includes associations with other entities
 * like QuestionType and SelectGroup to define the type and selectable items for the question.
 * <p>
 * Attributes:
 * - id: A unique identifier for the question.
 * - surveyId: The identifier of the survey to which the question belongs.
 * - required: Indicates whether this question is mandatory.
 * - maxValue: An optional maximum value for validation.
 * - minValue: An optional minimum value for validation.
 * - text: The full text of the question.
 * - shortText: An optional shorter version of the question text.
 * - toolTip: An optional tooltip containing additional information.
 * - mask: An optional input mask for formatting the user input.
 * - placeholder: An optional placeholder text displayed in the input field.
 * - validationText: An optional message shown to the user if their input fails validation.
 * - defaultValue: An optional default value for the question's response.
 * - questionType: A mandatory association to define the type of question.
 * - selectGroup: An optional association to define a selectable group of items for the question.
 * - variant: An optional variant of the question for customization purposes.
 */
@Entity
@Table(name = "questions", schema = "survey")
public class Question extends PanacheEntityBase {

    @Id
    @SequenceGenerator(name = "QUESTIONS_ID_GENERATOR", schema = "survey", sequenceName = "questions_seq", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "QUESTIONS_ID_GENERATOR")
    @Column(unique = true, nullable = false, precision = 20)
    public Integer id;

    @Column(name = "survey_id", nullable = false, precision = 20)
    public Integer surveyId;

    @Column(name = "required")
    public boolean required = false;

    @Column(name = "max_value", precision = 10)
    public Integer maxValue;

    @Column(name = "min_value", precision = 10)
    public Integer minValue;

    @Column(nullable = false, length = 8000)
    public String text;

    @Column(name = "short_text", length = 100)
    public String shortText;

    @Column(name = "tool_tip", length = 255)
    public String toolTip;

    @Column(name = "mask", length = 255)
    public String mask;

    @Column(name = "placeholder", length = 255)
    public String placeholder;

    @Column(name = "validation_text", length = 255)
    public String validationText;

    @Column(name = "default_value", length = 255)
    public String defaultValue;

    //uni-directional many-to-one association to QuestionType
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "type_id", nullable = false)
    public QuestionType questionType;

    //uni-directional many-to-one association to SelectGroup
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "select_group_id")
    public SelectGroup selectGroup;

    @Column(name = "variant", length = 255)
    public String variant;
}
