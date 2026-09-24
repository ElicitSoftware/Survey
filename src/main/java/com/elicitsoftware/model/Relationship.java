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
import io.quarkus.panache.common.Parameters;
import jakarta.persistence.*;

import java.text.SimpleDateFormat;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * The Relationship class represents the relationship between different components
 * within a survey, such as steps, sections, and questions. Relationships also
 * capture operators, actions, and rules used to evaluate conditions within the
 * survey context.
 * <p>
 * This class is backed by the RELATIONSHIPS table in the "survey" schema.
 * It supports various Named Queries to retrieve relationships based on specific
 * criteria from the database.
 * <p>
 * Key fields include:
 * - Upstream and downstream steps, sections, and questions representing the
 * hierarchy and dependencies of survey components.
 * - An operator type and action type that define the behavior or rules of the
 * relationship.
 * - Several attributes including description, reference values, tokens, and
 * default upstream values to further specify the relationship.
 * <p>
 * This class also contains utility methods to execute named queries for
 * fetching relationships and performing evaluation logic between answers and
 * relationships using the defined operators.
 * <p>
 * Notable Methods:
 * - `findRepeatByDownstreamStep(int surveyId, int downstreamStepId)`: Retrieves
 * repetitive relationships scoped to a specific downstream step.
 * - `findRepeatByDownstreamStepSection(int surveyId, int downstreamStepId, int downstreamSectionId)`:
 * Retrieves repetitive relationships for a downstream step within a
 * section.
 * - `findByDownstream_SQ_ID(int surveyId, int downstream_sq_id)`: Retrieves
 * relationships for a particular downstream question.
 * - `findByDownstream_SS_ID(int surveyId, int downstream_ss_id, int stepId)`:
 * Retrieves relationships for a specific downstream section and upstream step.
 * - `findByDownstream_Step_ID(int surveyId, int downstream_step_id, int stepId)`:
 * Retrieves relationships for a specific downstream step and upstream step.
 * - `findRelationshipsByDownstreamAnswer` was removed: it compared a step's durable id with an
 *   answer's display order; `QuestionManager.findRelationshipsByDownstreamAnswer` bridges the two.
 * - `findRelationshipsByUpstreamQuestion(int surveyId, int upstream_step_id, int upstream_sq_id)`:
 * Retrieves relationships related to an upstream question and step.
 * - `evaluateOperator(Answer answer)`: Evaluates the relationship's operator
 * against the given answer, returning true or false based on the operator's
 * logic and the provided answer's details.
 * <p>
 * The five endpoint columns (`upstreamStepId`, `upstreamSqId`, `downstreamStepId`,
 * `downstreamSsId`, `downstreamSqId`) hold durable ids and are deliberately plain columns:
 * the endpoint rows are resolved with the `findAsOf` finders on `Step`, `SectionsQuestion`
 * and `StepsSections` at the respondent's snapshot anchor (research/Kimball_type_2.md).
 * <p>
 * The class leverages JPA for database interactions and includes transient fields
 * for internal utility purposes (e.g., date formatting during operator evaluation).
 */
@Entity
@Table(name = "RELATIONSHIPS", schema = "survey")
@NamedQueries({
        // Every endpoint parameter below is a DURABLE id compared with the relationship's own
        // column; nothing joins the versioned endpoint tables (see the field comments).
        @NamedQuery(name = "Relationship.findByDownstream_Step_ID", query = "SELECT r FROM Relationship r WHERE r.surveyId = :surveyId and r.actionType.name <> 'TEXT' and r.downstreamSsId is null and r.downstreamSqId is null and r.downstreamStepId = :downstream_step_id and r.upstreamStepId = :stepId and r.effectiveFrom <= :asOf and r.effectiveTo > :asOf order by r.id"),
        @NamedQuery(name = "Relationship.findByDownstream_SS_ID", query = "SELECT r FROM Relationship r WHERE r.surveyId = :surveyId and r.actionType.name <> 'TEXT' and r.downstreamSsId = :downstream_ss_id and r.upstreamStepId = :stepId and r.effectiveFrom <= :asOf and r.effectiveTo > :asOf order by r.id"),
        @NamedQuery(name = "Relationship.findByDownstream_SQ_ID", query = "SELECT r FROM Relationship r WHERE r.surveyId = :surveyId and r.actionType.name <> 'TEXT' and r.downstreamSqId = :downstream_sq_id and r.effectiveFrom <= :asOf and r.effectiveTo > :asOf order by r.id"),
        @NamedQuery(name = "Relationship.findRepeatByDownstreamStep", query = "SELECT r FROM Relationship r WHERE r.surveyId = :surveyId and r.downstreamStepId = :downstreamStepId and r.downstreamSsId is null and r.downstreamSqId is null and r.effectiveFrom <= :asOf and r.effectiveTo > :asOf order by r.id"),
        @NamedQuery(name = "Relationship.findRepeatByDownstreamStepSection", query = "SELECT r FROM Relationship r WHERE r.surveyId = :surveyId and r.downstreamStepId = :downstreamStepId and r.downstreamSsId = :downstreamSectionId and r.downstreamSqId is null order by r.id"),
        @NamedQuery(name = "Relationship.findRelationshipsByUpstreamQuestion", query = "SELECT r FROM Relationship r WHERE r.upstreamSqId = :upstream_sq_id and r.surveyId = :surveyId and (r.upstreamStepId = :upstream_step_id or r.upstreamStepId is null) and r.effectiveFrom <= :asOf and r.effectiveTo > :asOf order by r.id")})
public class Relationship extends PanacheEntityBase {

    @Transient
    private final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");

    @Id
    @SequenceGenerator(name = "RELATIONSHIPS_ID_GENERATOR", schema = "survey", sequenceName = "RELATIONSHIPS_SEQ", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "RELATIONSHIPS_ID_GENERATOR")
    @Column(name = "ID", unique = true, nullable = false, precision = 20)
    public Integer id;

    @Column(name = "DEFAULT_UPSTREAM_VALUE", length = 255)
    public String defaultUpstreamValue;

    @Column(name = "DESCRIPTION", length = 255)
    public String description;

    @Column(name = "REFERENCE_VALUE", length = 255)
    public String referenceValue;

    @Column(name = "TOKEN", length = 10)
    public String token;

    // uni-directional many-to-one association to ActionType
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "ACTION_ID", nullable = false)
    public ActionType actionType;

    // uni-directional many-to-one association to OperatorType
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "OPERATOR_ID", nullable = false)
    public OperatorType operatorType;

    // The five endpoint columns hold DURABLE ids (Kimball Type 2 SCD retarget): steps.step_id,
    // sections_questions.sections_question_id and steps_sections.steps_sections_id, never the
    // surrogate ids. They are plain columns, not @ManyToOne associations: a durable id has one
    // row per version, so a JPA association on it fails with "More than one row with the given
    // identifier" as soon as a revision exists, marking the transaction rollback-only. Callers
    // resolve the endpoint they need with Step.findAsOf / SectionsQuestion.findAsOf /
    // StepsSections.findAsOf at the respondent's snapshot anchor (research/Kimball_type_2.md).
    @Column(name = "UPSTREAM_STEP_ID")
    public Integer upstreamStepId;

    @Column(name = "UPSTREAM_SQ_ID", nullable = false)
    public Integer upstreamSqId;

    @Column(name = "DOWNSTREAM_STEP_ID")
    public Integer downstreamStepId;

    @Column(name = "DOWNSTREAM_SS_ID")
    public Integer downstreamSsId;

    @Column(name = "DOWNSTREAM_SQ_ID")
    public Integer downstreamSqId;

    @Column(name = "SURVEY_ID", nullable = false, precision = 20)
    public Integer surveyId;

    // Kimball Type 2 SCD (research/Kimball_type_2.md) — relationship_id is the durable
    // key that survives re-versioning; id (above) is the surrogate, per-version row id.
    @Column(name = "relationship_id", nullable = false)
    public Integer relationshipId;

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
    @Column(name = "upstream_step_version", nullable = false)
    public Integer upstreamStepVersion = 0;

    @Column(name = "upstream_sq_version", nullable = false)
    public Integer upstreamSqVersion = 0;

    @Column(name = "downstream_step_version", nullable = false)
    public Integer downstreamStepVersion = 0;

    @Column(name = "downstream_ss_version", nullable = false)
    public Integer downstreamSsVersion = 0;

    @Column(name = "downstream_sq_version", nullable = false)
    public Integer downstreamSqVersion = 0;

    /**
     * Finds and retrieves a list of {@link Relationship} entities based on the specified survey ID
     * and downstream step ID. This method is typically used to identify relationships that are
     * associated with a given downstream step in the context of a survey.
     *
     * @param surveyId         the identifier of the survey in which the relationships are searched
     * @param downstreamStepId the DURABLE {@code steps.step_id} of the downstream step (what the
     *                         relationship row stores), not a surrogate id or a display order
     * @return a list of {@link Relationship} entities that match the given survey ID and downstream step ID
     */
    public static List<Relationship> findRepeatByDownstreamStep(int surveyId, int downstreamStepId, OffsetDateTime asOf) {
        return find("#Relationship.findRepeatByDownstreamStep", Parameters.with("surveyId", surveyId)
                .and("downstreamStepId", downstreamStepId).and("asOf", asOf)).list();
    }

    /**
     * Finds and retrieves a list of {@link Relationship} entities based on the specified survey ID
     * and downstream question ID. This method is used to identify relationships associated with a
     * particular downstream question in the context of a survey.
     *
     * @param surveyId         the identifier of the survey in which the relationships are searched
     * @param downstream_sq_id the DURABLE {@code sections_questions.sections_question_id} of the
     *                         downstream question (what the relationship row stores), not a surrogate id
     * @return a list of {@link Relationship} entities that match the given survey ID and downstream question ID
     */
    public static List<Relationship> findByDownstream_SQ_ID(int surveyId, int downstream_sq_id, OffsetDateTime asOf) {
        return find("#Relationship.findByDownstream_SQ_ID", Parameters.with("surveyId", surveyId)
                .and("downstream_sq_id", downstream_sq_id).and("asOf", asOf)).list();
    }

    /**
     * Finds and retrieves a list of {@link Relationship} entities based on the specified survey ID,
     * downstream section ID, and step ID. This method is used to identify relationships associated
     * with a particular downstream section within the context of a specific survey and step.
     *
     * @param surveyId        the identifier of the survey in which the relationships are searched
     * @param downstream_ss_id the DURABLE {@code steps_sections.steps_sections_id} of the downstream
     *                         section (what the relationship row stores), not a surrogate id
     * @param stepId          the DURABLE {@code steps.step_id} of the upstream step, never a display order
     * @return a list of {@link Relationship} entities that match the given survey ID, downstream section ID, and step ID
     */
    public static List<Relationship> findByDownstream_SS_ID(int surveyId, int downstream_ss_id, int stepId, OffsetDateTime asOf) {
        return find("#Relationship.findByDownstream_SS_ID", Parameters.with("surveyId", surveyId)
                .and("downstream_ss_id", downstream_ss_id)
                .and("stepId", stepId).and("asOf", asOf)).list();
    }

    /**
     * Finds and retrieves a list of {@link Relationship} entities based on the specified survey ID,
     * downstream step ID, and step ID. This method is used to identify relationships
     * associated with a specific downstream step and step within the context of a survey.
     *
     * @param surveyId           the identifier of the survey in which the relationships are searched
     * @param downstream_step_id the DURABLE {@code steps.step_id} of the downstream step (what the
     *                           relationship row stores), not a surrogate id
     * @param stepId             the DURABLE {@code steps.step_id} of the upstream step, never a display order
     * @return a list of {@link Relationship} entities that match the given survey ID, downstream step ID, and step ID
     */
    public static List<Relationship> findByDownstream_Step_ID(int surveyId, int downstream_step_id, int stepId, OffsetDateTime asOf) {
        return find("#Relationship.findByDownstream_Step_ID", Parameters.with("surveyId", surveyId)
                .and("downstream_step_id", downstream_step_id)
                .and("stepId", stepId).and("asOf", asOf)).list();
    }

    /**
     * Evaluates the result of applying an operator to an answer, based on the operator type
     * and reference values configured in the {@link Relationship} object. This method handles
     * various operator types such as BOOLEAN, LESS THAN, GREATER THAN, EQUAL, NOT_EQUAL,
     * FIELD_EXIST, and CONTAINS, performing appropriate comparisons or validations based on
     * the input and configuration.
     *
     * @param answer the {@link Answer} object containing the input data to evaluate against
     *               the operator and reference value(s)
     * @return a boolean result indicating whether the evaluation of the operator on the
     * given input answer satisfies the operator's condition
     */
    @Transient
    public boolean evaluateOperator(Answer answer) {
        boolean returnValue = false;

        // Catch any errors from trying to transform data types.
        try {
            switch (operatorType.name) {
                case "BOOLEAN":
                    returnValue = Boolean.parseBoolean(answer.getTextValue());
                    break;
                case "LESS THAN":
                    // The answer pins the exact question version the respondent saw, so its
                    // type is read from there rather than by resolving upstreamSqId again.
                    if (isDateQuestion(answer)) {
                        Date dateValue = sdf.parse(answer.getTextValue());
                        Date dateRef;
                        try {
                            dateRef = sdf.parse(this.referenceValue);
                        } catch (Exception e) {
                            // this is not a date in the date format
                            dateRef = new Date();
                        }
                        returnValue = dateValue.compareTo(dateRef) < 0;

                    } else {
                        Double dValue = Double.valueOf(answer.getTextValue());
                        if (this.referenceValue != null) {
                            Double rVal = Double.valueOf(this.referenceValue);
                            // Strictly less than, matching the DATE branch above.
                            returnValue = dValue < rVal;
                        } else {
                            // the default is false.
                        }
                    }
                    break;
                case "GREATER THAN":
                    if (isDateQuestion(answer)) {
                        Date dateValue = sdf.parse(answer.getTextValue());
                        Date dateRef;
                        try {
                            dateRef = sdf.parse(this.referenceValue);
                        } catch (Exception e) {
                            // this is not a date in the date format
                            dateRef = new Date();
                        }
                        returnValue = dateValue.compareTo(dateRef) > -1;

                    } else {
                        double dValue = Double.parseDouble(answer.getTextValue());
                        if (this.referenceValue != null) {
                            double rVal = Double.parseDouble(this.referenceValue);
                            // Strictly greater than. Note: the DATE branch above deliberately
                            // stays inclusive (compareTo(dateRef) > -1) - not changed to match.
                            returnValue = dValue > rVal;
                        } else {
                            // the default is false.
                        }
                    }
                    break;
                case "EQUAL":
                    if (this.referenceValue != null) {
                        returnValue = answer.getTextValue().equalsIgnoreCase(this.referenceValue);
                    }
                    break;
                case "NOT_EQUAL":
                    if (answer.getTextValue() != null) {
                        returnValue = !answer.getTextValue().equalsIgnoreCase(this.referenceValue);
                    }
                    break;
                case "FIELD_EXIST":
                    // If there is an answer then it does exists ( or was presented
                    // )
                    returnValue = true;
                    break;
                case "CONTAINS":
                    // If the array contains the value then true
                    String[] values = answer.getTextValue().split(",");
                    if (Arrays.asList(values).contains(this.referenceValue)) {
                        returnValue = true;
                    }
                    break;
                default:
                    break;
            }
        } catch (Exception e) {
            // TODO log exception
            // return default value
        }
        return returnValue;
    }

    private static boolean isDateQuestion(Answer answer) {
        return answer.question != null && answer.question.questionType != null
                && "DATE".equals(answer.question.questionType.name);
    }
}
