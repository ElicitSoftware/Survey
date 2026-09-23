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
 * - selectGroupId: The durable id of the optional select group whose items the question offers,
 *   resolved as of the respondent's snapshot anchor (never a JPA association).
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

    // questions.select_group_id holds the durable select_groups.select_group_id (Kimball Type 2
    // SCD retarget), not select_groups.id. It is a plain column, never a @ManyToOne: a durable
    // id has one row per version, so a JPA association on it fails with "More than one row
    // with the given identifier" as soon as a revision exists. Resolve the group's items with
    // SelectItem.findByGroupAsOf (or the group with SelectGroup.findAsOf) at the respondent's
    // snapshot anchor -- Answer.getSelectItems() does exactly that for the UI.
    @Column(name = "select_group_id")
    public Integer selectGroupId;

    @Column(name = "variant", length = 255)
    public String variant;

    // Kimball Type 2 SCD (research/Kimball_type_2.md) — question_id is the durable key
    // that survives re-versioning; id (above) is the surrogate, per-version row id, and
    // is what answers.question_id pins to at response time.
    @Column(name = "question_id", nullable = false)
    public Integer questionId;

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

    // FK-companion column pinning the referenced select_groups row to its entity-existence
    // check (always 0 — see research/Kimball_type_2.md's "Resolving the FK Cascade Problem").
    @Column(name = "select_group_version", nullable = false)
    public Integer selectGroupVersion = 0;

    /**
     * The version of the durable {@code question_id} in effect at {@code asOf} (research/
     * Kimball_type_2.md, "Snapshot Anchor"). See {@link Step#findAsOf} for why durable keys
     * are resolved through a finder rather than mapped as JPA associations. Note that an
     * {@link Answer} pins the surrogate {@code questions.id} it was created against, so
     * {@code Answer.question} stays a plain JPA association; this finder is for reaching a
     * question from a {@link SectionsQuestion#questionId} before any answer exists.
     *
     * @param questionId the durable {@code questions.question_id}; {@code null} yields {@code null}
     * @param asOf       the respondent's snapshot anchor
     * @return the question in effect at {@code asOf}, or {@code null} if none covers it
     */
    public static Question findAsOf(Integer questionId, OffsetDateTime asOf) {
        if (questionId == null) {
            return null;
        }
        return Question.<Question>find("questionId = ?1 and effectiveFrom <= ?2 and effectiveTo > ?2", questionId, asOf)
                .singleResultOptional().orElse(null);
    }
}
