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
 * Represents a mapping between sections and questions in the "sections_questions" table
 * within the "survey" schema. This entity is used to associate a question with a specific
 * section and survey, enabling the organization and ordering of questions within a survey.
 * <p>
 * Attributes:
 * - id: The unique identifier for the section-question mapping.
 * - displayOrder: The order in which the question is displayed within the section.
 * - questionId: The durable id of the placed question, resolved as of the respondent's
 *   snapshot anchor (never a JPA association).
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

    // NUMERIC, not INTEGER (Kimball Type 2 SCD, research/Kimball_type_2.md's "Adding a new
    // question" section) -- supports decimal-midpoint insertion between existing positions.
    // DisplayKey's fixed-width zero-padded string encoding does not support decimals, so
    // every read into a DisplayKey segment truncates via .intValue() until that encoding is
    // redesigned as part of a future Author Tool effort.
    @Column(name = "display_order", nullable = false, precision = 3)
    public BigDecimal displayOrder;

    // sections_questions.question_id holds the durable questions.question_id (Kimball Type 2
    // SCD retarget), not questions.id. A plain column, never a @ManyToOne: a durable id has
    // one row per version, so a JPA association on it fails with "More than one row with the
    // given identifier" as soon as a revision exists. Resolve it with Question.findAsOf at
    // the respondent's snapshot anchor.
    @Column(name = "question_id", nullable = false)
    public Integer questionId;

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

    /**
     * The version of the durable {@code sections_question_id} in effect at {@code asOf}
     * (research/Kimball_type_2.md, "Snapshot Anchor"). See {@link Step#findAsOf} for why
     * durable keys are resolved through a finder rather than mapped as JPA associations.
     *
     * @param sectionsQuestionId the durable {@code sections_questions.sections_question_id}; {@code null} yields {@code null}
     * @param asOf               the respondent's snapshot anchor
     * @return the placement in effect at {@code asOf}, or {@code null} if none covers it
     */
    public static SectionsQuestion findAsOf(Integer sectionsQuestionId, OffsetDateTime asOf) {
        if (sectionsQuestionId == null) {
            return null;
        }
        return SectionsQuestion.<SectionsQuestion>find("sectionsQuestionId = ?1 and effectiveFrom <= ?2 and effectiveTo > ?2", sectionsQuestionId, asOf)
                .singleResultOptional().orElse(null);
    }
}
