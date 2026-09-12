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
public class SectionsQuestion extends PanacheEntityBase {

    @Id
    @SequenceGenerator(name = "SECTIONS_QUESTIONS_ID_GENERATOR", schema = "survey", sequenceName = "sections_questions_seq", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "SECTIONS_QUESTIONS_ID_GENERATOR")
    @Column(unique = true, nullable = false, precision = 20)
    public Integer id;

    @Column(name = "display_order", nullable = false, precision = 3)
    public Integer displayOrder;

    // sections_questions.question_id now holds the durable questions.question_id
    // (Kimball Type 2 SCD retarget), not questions.id — referencedColumnName must
    // point at that durable column or this association silently matches nothing.
    @ManyToOne
    @JoinColumn(name = "question_id", referencedColumnName = "question_id", nullable = false)
    public Question question;

    // section_id is now the durable sections.section_id (Kimball Type 2 SCD retarget);
    // kept as a plain column (not an association) since callers only ever use it as an
    // opaque id for native-SQL joins, never navigate it as a Section object.
    @Column(name = "section_id", nullable = false, precision = 20)
    public Integer sectionId;

    @Column(name = "survey_id", nullable = false, precision = 20)
    public Integer surveyId;

    // Kimball Type 2 SCD (research/Kimball_type_2.md) — sections_question_id is the
    // durable key that survives re-versioning; id (above) is the surrogate, per-version
    // row id, and is what answers.section_question_id pins to at response time.
    @Column(name = "sections_question_id", nullable = false)
    public Integer sectionsQuestionId;

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

    // FK-companion columns pinning the referenced rows to their entity-existence checks
    // (always 0 — see research/Kimball_type_2.md's "Resolving the FK Cascade Problem").
    @Column(name = "question_version", nullable = false)
    public Integer questionVersion = 0;

    @Column(name = "section_version", nullable = false)
    public Integer sectionVersion = 0;

}
