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
 * - `findByDownstream_S_ID(int surveyId, int downstream_s_id, int stepId)`:
 * Retrieves relationships for a specific downstream section and upstream step.
 * - `findByDownstream_Step_ID(int surveyId, int downstream_step_id, int stepId)`:
 * Retrieves relationships for a specific downstream step and upstream step.
 * - `findRelationshipsByDownstreamAnswer(int respondentId, int answerId)`:
 * Retrieves relationships associated with a specific downstream answer.
 * - `findRelationshipsByUpstreamQuestion(int surveyId, int upstream_step_id, int upstream_sq_id)`:
 * Retrieves relationships related to an upstream question and step.
 * - `evaluateOperator(Answer answer)`: Evaluates the relationship's operator
 * against the given answer, returning true or false based on the operator's
 * logic and the provided answer's details.
 * <p>
 * The class leverages JPA for database interactions and includes transient fields
 * for internal utility purposes (e.g., date formatting during operator evaluation).
 */
@Entity
@Table(name = "RELATIONSHIPS", schema = "survey")
@NamedQueries({
        @NamedQuery(name = "Relationship.findByDownstream_Step_ID", query = "SELECT r FROM Relationship r WHERE r.surveyId = :surveyId and r.actionType.name <> 'TEXT' and r.downstreamSection is null and r.downstreamQuestion is null and r.downstreamStep.id = :downstream_step_id and r.upstreamStep.id = :stepId order by r.id"),
        @NamedQuery(name = "Relationship.findByDownstream_S_ID", query = "SELECT r FROM Relationship r WHERE r.surveyId = :surveyId and r.actionType.name <> 'TEXT' and r.downstreamSection.id = :downstream_s_id and r.upstreamStep.id = :stepId order by r.id"),
        @NamedQuery(name = "Relationship.findByDownstream_SQ_ID", query = "SELECT r FROM Relationship r WHERE r.surveyId = :surveyId and r.actionType.name <> 'TEXT' and r.downstreamQuestion.id = :downstream_sq_id order by r.id"),
        @NamedQuery(name = "Relationship.findRepeatByDownstreamStep", query = "SELECT r FROM Relationship r WHERE r.surveyId = :surveyId and r.downstreamStep.id = :downstreamStepId and r.downstreamSection is null and r.downstreamQuestion is null order by r.id"),
        @NamedQuery(name = "Relationship.findRepeatByDownstreamStepSection", query = "SELECT r FROM Relationship r WHERE r.surveyId = :surveyId and r.downstreamStep.id = :downstreamStepId and r.downstreamSection.id = :downstreamSectionId and r.downstreamQuestion is null order by r.id"),
        @NamedQuery(name = "Relationship.findRelationshipsByDownstreamAnswer", query = "SELECT r FROM Relationship r inner JOIN Answer a on r.downstreamStep.id = a.stepId AND r.downstreamQuestion.id is null AND r.surveyId = a.surveyId WHERE a.section_question_id is null AND a.respondentId = :respondentId AND r.actionType.id = 3 AND a.id = :answerId order by r.id"),
        @NamedQuery(name = "Relationship.findRelationshipsByUpstreamQuestion", query = "SELECT r FROM Relationship r WHERE r.upstreamQuestion.id = :upstream_sq_id and r.surveyId = :surveyId and (r.upstreamStep.id = :upstream_step_id or r.upstreamStep.id is null)  order by r.id")})
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
    // Keep EAGER - small lookup table, always needed
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "ACTION_ID", nullable = false)
    public ActionType actionType;

    // uni-directional many-to-one association to OperatorType
    // Keep EAGER - small lookup table, always needed
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "OPERATOR_ID", nullable = false)
    public OperatorType operatorType;

    // uni-directional many-to-one association to Step
    // Changed to LAZY - load only when needed
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "UPSTREAM_STEP_ID")
    public Step upstreamStep;

    // uni-directional many-to-one association to SectionsQuestion
    // Changed to LAZY - load only when needed
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "UPSTREAM_SQ_ID", nullable = false)
    public SectionsQuestion upstreamQuestion;

    // uni-directional many-to-one association to Step
    // Changed to LAZY - load only when needed
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "DOWNSTREAM_STEP_ID")
    public Step downstreamStep;

    // uni-directional many-to-one association to Section
    // Changed to LAZY - load only when needed
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "DOWNSTREAM_S_ID")
    public StepsSections downstreamSection;

    // uni-directional many-to-one association to SectionsQuestion
    // Changed to LAZY - load only when needed
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "DOWNSTREAM_SQ_ID")
    public SectionsQuestion downstreamQuestion;

    @Column(name = "SURVEY_ID", nullable = false, precision = 20)
    public Integer surveyId;

    /**
     * Finds and retrieves a list of {@link Relationship} entities based on the specified survey ID
     * and downstream step ID. This method is typically used to identify relationships that are
     * associated with a given downstream step in the context of a survey.
     *
     * @param surveyId         the identifier of the survey in which the relationships are searched
     * @param downstreamStepId the identifier of the downstream step to filter the relationships
     * @return a list of {@link Relationship} entities that match the given survey ID and downstream step ID
     */
    public static List<Relationship> findRepeatByDownstreamStep(int surveyId, int downstreamStepId) {
        return find("#Relationship.findRepeatByDownstreamStep", Parameters.with("surveyId", surveyId)
                .and("downstreamStepId", downstreamStepId)).list();
    }

    /**
     * Finds and retrieves a list of {@link Relationship} entities based on the specified survey ID
     * and downstream question ID. This method is used to identify relationships associated with a
     * particular downstream question in the context of a survey.
     *
     * @param surveyId         the identifier of the survey in which the relationships are searched
     * @param downstream_sq_id the identifier of the downstream question to filter the relationships
     * @return a list of {@link Relationship} entities that match the given survey ID and downstream question ID
     */
    public static List<Relationship> findByDownstream_SQ_ID(int surveyId, int downstream_sq_id) {
        return find("#Relationship.findByDownstream_SQ_ID", Parameters.with("surveyId", surveyId)
                .and("downstream_sq_id", downstream_sq_id)).list();
    }

    /**
     * Finds and retrieves a list of {@link Relationship} entities based on the specified survey ID,
     * downstream section ID, and step ID. This method is used to identify relationships associated
     * with a particular downstream section within the context of a specific survey and step.
     *
     * @param surveyId        the identifier of the survey in which the relationships are searched
     * @param downstream_s_id the identifier of the downstream section to filter the relationships
     * @param stepId          the identifier of the step to filter the relationships
     * @return a list of {@link Relationship} entities that match the given survey ID, downstream section ID, and step ID
     */
    public static List<Relationship> findByDownstream_S_ID(int surveyId, int downstream_s_id, int stepId) {
        return find("#Relationship.findByDownstream_S_ID", Parameters.with("surveyId", surveyId)
                .and("downstream_s_id", downstream_s_id)
                .and("stepId", stepId)).list();
    }

    /**
     * Finds and retrieves a list of {@link Relationship} entities based on the specified survey ID,
     * downstream step ID, and step ID. This method is used to identify relationships
     * associated with a specific downstream step and step within the context of a survey.
     *
     * @param surveyId           the identifier of the survey in which the relationships are searched
     * @param downstream_step_id the identifier of the downstream step to filter the relationships
     * @param stepId             the identifier of the step to filter the relationships
     * @return a list of {@link Relationship} entities that match the given survey ID, downstream step ID, and step ID
     */
    public static List<Relationship> findByDownstream_Step_ID(int surveyId, int downstream_step_id, int stepId) {
        return find("#Relationship.findByDownstream_Step_ID", Parameters.with("surveyId", surveyId)
                .and("downstream_step_id", downstream_step_id)
                .and("stepId", stepId)).list();
    }

    /**
     * Finds and retrieves a list of {@link Relationship} entities based on the specified respondent ID
     * and answer ID. This method is used to identify relationships associated with a particular respondent
     * and their corresponding downstream answer.
     *
     * @param respondentId the identifier of the respondent whose relationships are being searched
     * @param answerId     the identifier of the answer to filter the relationships
     * @return a list of {@link Relationship} entities that match the given respondent ID and answer ID
     */
    public static List<Relationship> findRelationshipsByDownstreamAnswer(int respondentId, int answerId) {
        return find("#Relationship.findRelationshipsByDownstreamAnswer", Parameters.with("respondentId", respondentId).and("answerId", answerId)).list();
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
                    if (this.upstreamQuestion.question.questionType.name.equals("DATE")) {
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
                            returnValue = dValue >= rVal;
                        } else {
                            // the default is false.
                        }
                    }
                    break;
                case "GREATER THAN":
                    if (this.upstreamQuestion.question.questionType.name.equals("DATE")) {
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
                            returnValue = dValue >= rVal;
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
}
