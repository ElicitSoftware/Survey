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
 * Represents a mapping between sections and questions in the "sections_questions" table
 * within the "survey" schema. This entity is used to associate a question with a specific
 * section and survey, enabling the organization and ordering of questions within a survey.
 * <p>
 * Attributes:
 * - id: The unique identifier for the section-question mapping.
 * - displayOrder: The order in which the question is displayed within the section.
 * - question: A reference to the associated Question entity.
 * - sectionId: The unique identifier for the associated section.
 * - surveyId: The unique identifier for the associated survey.
 * <p>
 * This class utilizes Hibernate ORM and extends PanacheEntityBase for simplified data access.
 */
@Entity
@Table(name = "sections_questions", schema = "survey")
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class SectionsQuestion extends PanacheEntityBase {

    @Id
    @SequenceGenerator(name = "SECTIONS_QUESTIONS_ID_GENERATOR", schema = "survey", sequenceName = "sections_questions_seq", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "SECTIONS_QUESTIONS_ID_GENERATOR")
    @Column(unique = true, nullable = false, precision = 20)
    public Integer id;

    @Column(name = "display_order", nullable = false, precision = 3)
    public Integer displayOrder;

    @ManyToOne
    @JoinColumn(name = "question_id", nullable = false)
    public Question question;

    @Column(name = "section_id", nullable = false, precision = 20)
    public Integer sectionId;

    @Column(name = "survey_id", nullable = false, precision = 20)
    public Integer surveyId;

}
