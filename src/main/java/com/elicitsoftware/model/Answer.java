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

import com.elicitsoftware.DisplayKey;
import com.elicitsoftware.flow.GlobalStrings;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import io.quarkus.logging.Log;
import io.quarkus.panache.common.Parameters;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

/**
 * Represents an answer provided by a respondent to a specific question within a survey.
 * <p>
 * The Answer class serves as the primary data model for capturing and managing
 * responses to survey questions. It includes multiple attributes referring to
 * the respondent, question, section, and other associated survey metadata.
 * This class provides multiple constructors and methods to query and manipulate
 * these responses, ensuring appropriate interaction with the survey system.
 * <p>
 * Fields:
 * - id: The unique identifier for this answer.
 * - surveyId: The identifier for the survey to which this answer belongs.
 * - respondentId: The unique identifier of the respondent associated with this answer.
 * - stepId: The identifier for the step in the survey associated with this answer.
 * - stepInstance: The specific instance of the step related to the answer.
 * - sectionId: The identifier for the section of the survey tied to this answer.
 * - sectionInstance: The specific instance of the section associated with the answer.
 * - question_instance: The instance number of the question answered.
 * - textValue: The textual value representing the respondent's answer.
 * - question: A reference to the question being answered.
 * - displayText: The text to be displayed for this answer.
 * - section_question_id: An identifier linking the section and question for this answer.
 * - deleted: A flag indicating whether the answer is marked as deleted.
 * - createdDt: The date and time when this answer was created.
 * - savedDt: The date and time when this answer was saved.
 * - displayKey: The display key linking the answer to various metadata.
 * - textArray: Represents a textual representation in an array format (if applicable).
 * <p>
 * Methods:
 * - Default constructor and parameterized constructors for creating instances of the Answer class with varying levels of detail.
 * - Methods for retrieving Answer objects based on various criteria including respondent ID, section question ID, display key, etc.
 * - Methods for handling queries related to section instances, upstream relationships, and purging deleted answers.
 * - Accessor and mutator methods for critical fields, such as displayKey.
 * <p>
 * This class extends `PanacheEntityBase` to leverage Panache ORM functionalities for persistence operations.
 */
@Entity
@Table(name = "answers", schema = "survey")
@NamedQueries({
        @NamedQuery(name = "Answer.findByStepSQ", query = "SELECT a FROM Answer a where a.respondentId = :respondentId and a.section_question_id = :sectionQuestionId and a.question_instance = :question_instance order by a.displayKey"),
        @NamedQuery(name = "Answer.findByDisplayKeyActive", query = "SELECT a FROM Answer a WHERE a.deleted = false and a.displayKey = :displaykey and a.respondentId = :respondentId order by a.displayKey"),
        @NamedQuery(name = "Answer.findByDisplayKeyAll", query = "SELECT a FROM Answer a WHERE a.displayKey = :displaykey and a.respondentId = :respondentId order by a.displayKey"),
        @NamedQuery(name = "Answer.findByAnswerQueryString", query = "SELECT a FROM Answer a WHERE a.deleted = false and a.displayKey Like :answerQuery and a.respondentId = :respondentId order by a.displayKey"),
        @NamedQuery(name = "Answer.findBySectionInstancesQueryString", query = "SELECT a FROM Answer a WHERE a.deleted = false and a.question is null and a.displayKey Like :sectionQuery and a.respondentId = :respondentId order by a.displayKey"),
        @NamedQuery(name = "Answer.findBySectionDisplaykey", query = "SELECT DISTINCT a FROM Answer a WHERE a.deleted = false and a.respondentId = :respondentId AND a.surveyId = :surveyId and a.stepId = :stepId and a.stepInstance = :stepInstance and a.sectionId = :sectionId and a.sectionInstance = :sectionInstance order by a.displayKey"),
        @NamedQuery(name = "Answer.findUpstreamAnswerByRelationshipId", query = "SELECT a FROM Answer a inner JOIN Relationship r ON a.stepId = r.upstreamStep.id AND a.section_question_id = r.upstreamQuestion.id WHERE a.respondentId = :respondentId and r.id = :relationshipID order by a.displayKey")})
public class Answer extends PanacheEntityBase {

    /**
     * Represents the unique identifier for an Answer entity.
     * <p>
     * This field is annotated with JPA annotations to configure it as the primary key
     * of the Answer table. It is generated using a sequence generator named
     * "ANSWERS_ID_GENERATOR" with sequences configured in the "survey" schema.
     * The generator ensures unique, sequential values for each Answer entity.
     * <p>
     * Attributes:
     * - Marked with @Id to define it as the primary key.
     * - Uses @SequenceGenerator to define the sequence generator details.
     * - Uses @GeneratedValue to specify the generation strategy as SEQUENCE,
     * enabling the use of the defined "ANSWERS_ID_GENERATOR".
     * - Annotated with @Column to enforce uniqueness, non-nullability, and specify precision.
     */
    @Id
    @SequenceGenerator(name = "ANSWERS_ID_GENERATOR", schema = "survey", sequenceName = "ANSWERS_SEQ", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ANSWERS_ID_GENERATOR")
    @Column(unique = true, nullable = false, precision = 20)
    public Integer id;

    /**
     * Represents the survey identifier associated with the Answer entity.
     * This field establishes a linkage to the corresponding survey that this answer pertains to.
     * It is a mandatory attribute and cannot be null.
     * <p>
     * Mapped to the "survey_id" column in the database with a precision of 20.
     */
    @Column(name = "survey_id", nullable = false, precision = 20)
    public Integer surveyId;

    /**
     * Represents the unique identifier of the respondent associated with the survey
     * answer. This field is mandatory and is mapped to the "respondent_id" column
     * in the database with a precision of 20.
     * <p>
     * This property is used to establish a link between an answer and the individual
     * respondent who provided it, enabling the tracking and retrieval of survey responses
     * by specific users.
     */
    @Column(name = "respondent_id", nullable = false, precision = 20)
    public Integer respondentId;

    /**
     * Represents the identifier of a specific step associated with the answer.
     * This variable maps to the "step" column in the database and holds
     * a reference to a particular step within the survey process.
     * <p>
     * Attributes:
     * - Mandatory: This field is required (nullable = false).
     * - Precision: Limited to 4 digits.
     * <p>
     * Typically relates to the "Step" entity in the system, which defines
     * the steps within a survey.
     */
    @Column(name = "step", nullable = false, precision = 4)
    public Integer stepId;

    /**
     * Represents the specific instance of a step within a survey or section.
     * This is used to identify and differentiate between repeated steps in
     * survey responses or iterations.
     * <p>
     * The value is stored in the database column "step_instance" with a precision of 4.
     * It is a mandatory field, meaning it cannot be null.
     */
    @Column(name = "step_instance", nullable = false, precision = 4)
    public Integer stepInstance;

    /**
     * Represents the identifier of a section associated with an answer in the survey system.
     * <p>
     * This variable is mapped to the "section" column in the database and serves as
     * a foreign key referencing the corresponding section entity. It stores the unique identifier
     * of the section to which the answer pertains.
     * <p>
     * Attributes:
     * - The column is non-nullable, ensuring that an answer is always associated with a valid section.
     * - Precision of 20 denotes the maximum size of the integer value.
     */
    @Column(name = "section", nullable = false, precision = 20)
    public Integer sectionId;

    /**
     * Represents the instance of a specific section in a survey. This field
     * is used to identify and differentiate between multiple occurrences of
     * the same section within a survey.
     * <p>
     * Constraints:
     * - Maps to the "section_instance" column in the database.
     * - Cannot be null.
     * - Limited to a numeric precision of 10.
     */
    @Column(name = "section_instance", nullable = false, precision = 10)
    public Integer sectionInstance;

    /**
     * Represents the instance of a specific question within a survey.
     * This field is used to track and differentiate multiple occurrences
     * of the same question in cases where a question may appear multiple times,
     * such as in repeated sections or dynamic instances of a survey.
     * <p>
     * Attributes:
     * - Mapped to the "question_instance" column in the database.
     * - Cannot be null.
     * - Precision of 10.
     */
    @Column(name = "question_instance", nullable = false, precision = 10)
    public Integer question_instance;

    /**
     * Represents the textual value of an answer within a survey.
     * <p>
     * Mapped to the "text_value" column in the associated database table.
     * This field holds a string value with a maximum length of 255 characters.
     * <p>
     * The value may store user-provided input or response data tied to
     * a specific question or section in the survey process.
     */
    @Column(name = "text_value", length = 255)
    private String textValue;

    public String getTextValue() {
        return textValue;
    }

    public void setTextValue(String textValue) {
        this.textValue = textValue;
    }

    /**
     * Represents a uni-directional many-to-one association between the `Answer` entity
     * and the `Question` entity. This association links an instance of `Answer` to a
     * specific `Question` entity, allowing answers to be tied to their respective questions
     * in a survey context.
     * <p>
     * The association uses eager loading, meaning the related `Question` entity
     * will be fetched immediately when the `Answer` entity is retrieved. The foreign key
     * linking the two entities is represented by the `question_id` column in the database,
     * and it is nullable, which allows an `Answer` to exist without a related `Question`.
     * <p>
     * This relationship is defined and managed using JPA annotations for ORM mapping.
     */
    // uni-directional many-to-one association to Question
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "question_id", nullable = true)
    public Question question;

    /**
     * Represents the display text associated with an answer in a survey.
     * This field stores detailed textual information or a label
     * describing the answer in a human-readable format.
     * <p>
     * Constraints:
     * - Cannot be null, ensuring that all answers have an associated display text.
     * - Maximum length is 8000 characters.
     */
    @Column(name = "display_text", nullable = false, length = 8000)
    public String displayText;

    /**
     * Represents the identifier of a specific question within a section of a survey.
     * <p>
     * This field is mapped to the "section_question_id" column in the database and
     * supports nullable values. It relates to a question presented within a section
     * of a survey, enabling the linkage and identification of questions in context
     * to their corresponding sections.
     * <p>
     * Characteristics:
     * - Database Column Name: section_question_id
     * - Nullable: Yes
     * - Precision: 20
     */
    @Column(name = "section_question_id", nullable = true, precision = 20)
    public Integer section_question_id;

    /**
     * Indicates whether the answer has been marked as deleted.
     * The default value is `false`, meaning the answer is not deleted.
     * This field is used for soft deletion, allowing the answer to be marked
     * as deleted without being physically removed from the database.
     */
    @Column(name = "deleted")
    public Boolean deleted = Boolean.valueOf(false);

    /**
     * Represents the timestamp when the answer was created.
     * This field is mapped to the "created_dt" column in the database
     * and stores the date and time of the answer's creation.
     * The field uses the `@Temporal` annotation with `TemporalType.TIMESTAMP`
     * to specify that it should store both date and time information.
     */
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "created_dt")
    public Date createdDt;

    /**
     * Represents the timestamp when the associated entity was last saved or updated.
     * This field is mapped to the "saved_dt" column in the database and is stored
     * as a TIMESTAMP type.
     * <p>
     * This field is managed by the application to keep track of the last save/update
     * event for this entity. It is primarily used for auditing or tracking changes.
     */
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "saved_dt")
    public Date savedDt;

    /**
     * Represents the display key associated with an answer in the survey context.
     * This key uniquely identifies the context of the answer, including its
     * survey, section, question, and instances details.
     * <p>
     * It is stored in the database column "display_key" with a maximum length of 34
     * characters and cannot be null.
     * <p>
     * Default value is "0.0.0.0.0.0.0".
     */
    @Column(name = "display_key", nullable = false, length = 34)
    public String displayKey = "0.0.0.0.0.0.0";

    /**
     * Represents a transient list of textual string values associated with an Answer entity.
     * This field does not persist to the database and is used for in-memory operations
     * or dynamic data handling at runtime.
     */
    @Transient
    public List<String> textArray = new ArrayList<String>();

    /**
     * Default constructor for the Answer class.
     * This constructor initializes an Answer instance and sets it up using
     * the default behavior provided by the superclass.
     * <p>
     * The Answer class is a representation of a respondent's answer to a question
     * within a survey. Instances of this class are associated with various survey-related
     * attributes like respondentId, question_instance, sectionId, and more,
     * which are managed through this entity.
     */
    public Answer() {
        super();
    }

    /**
     * Constructs an Answer object with the provided DisplayKey, SectionsQuestion, display text, and respondent ID.
     * Initializes the question and section_question_id if the provided SectionsQuestion is not null.
     *
     * @param key              the DisplayKey associated with this Answer
     * @param sectionsQuestion the SectionsQuestion object containing question details
     * @param displayText      the text to be displayed for this Answer
     * @param respondentId     the identifier of the respondent associated with this Answer
     */
    public Answer(DisplayKey key, SectionsQuestion sectionsQuestion, String displayText, int respondentId) {
        super();
        setDisplayKeyValues(key);
        this.displayText = displayText;
        this.respondentId = respondentId;
        if (sectionsQuestion != null) {
            this.question = sectionsQuestion.question;
            this.section_question_id = sectionsQuestion.id;
        }
    }

    /**
     * Constructor for creating an Answer instance.
     *
     * @param key              the display key associated with the answer
     * @param sectionsQuestion the sections question object that contains the question and section question ID
     * @param displayText      the text to be displayed for this answer
     * @param respondentId     the identifier of the respondent who provided this answer
     * @param textValue        the textual value of the answer, may also represent a comma-separated list
     */
    public Answer(DisplayKey key, SectionsQuestion sectionsQuestion, String displayText,
                  int respondentId, String textValue) {
        super();
        setDisplayKeyValues(key);
        this.displayText = displayText;
        this.respondentId = respondentId;
        if (sectionsQuestion != null) {
            this.question = sectionsQuestion.question;
            this.section_question_id = sectionsQuestion.id;
        }
        if (textValue != null) {
            this.textValue = textValue;
            this.textArray = new ArrayList<>(Arrays.asList(textValue.split(",")));
            this.savedDt = new Date();
        }
    }

    /**
     * Retrieves the first result of an Answer entity that matches the specified
     * respondent ID, section question ID, and question instance.
     *
     * @param respondentId      the unique identifier of the respondent
     * @param sectionQuestionId the unique identifier of the section question
     * @param question_instance the instance number of the question
     * @return the first matching Answer entity, or null if no match is found
     */
    public static Answer findByStepSQ(Integer respondentId, int sectionQuestionId, int question_instance) {
        return find("#Answer.findByStepSQ", Parameters.with("respondentId", respondentId)
                .and("sectionQuestionId", sectionQuestionId).and("question_instance", question_instance)).firstResult();
    }

    /**
     * Retrieves an active {@link Answer} entity based on the given respondent ID and display key.
     * This method queries the database for the first result matching the specified parameters.
     *
     * @param respondentId the ID of the respondent for whom the answer is being retrieved
     * @param displaykey   the display key associated with the answer
     * @return the first active {@link Answer} matching the given respondent ID and display key, or null if none found
     */
    public static Answer findByDisplayKeyActive(int respondentId, String displaykey) {
        return find("#Answer.findByDisplayKeyActive", Parameters.with("displaykey", displaykey)
                .and("respondentId", respondentId)).firstResult();
    }

    /**
     * Finds an answer by the given respondent ID and display key. The result includes all
     * matching answers, regardless of their state (active or not).
     *
     * @param respondentId the unique identifier of the respondent whose answer is being retrieved
     * @param displaykey   the display key used to locate the specific answer
     * @return the first {@code Answer} instance matching the provided respondent ID and display key,
     * or {@code null} if no matching answer is found
     */
    public static Answer findByDisplayKeyAll(int respondentId, String displaykey) {
        return find("#Answer.findByDisplayKeyAll", Parameters.with("displaykey", displaykey)
                .and("respondentId", respondentId)).firstResult();
    }

    /**
     * Finds a list of Answer objects based on the provided respondent ID and answer query string.
     *
     * @param respondentId the unique identifier of the respondent whose answers are being queried
     * @param answerQuery  the query string used to search for specific answers
     * @return a list of Answer objects that match the given respondent ID and query string criteria
     */
    public static List<Answer> findByAnswerQueryString(int respondentId, String answerQuery) {
        return find("#Answer.findByAnswerQueryString", Parameters.with("answerQuery", answerQuery)
                .and("respondentId", respondentId)).list();
    }

    /**
     * Retrieves a list of Answer objects that match the section instances query string
     * for the given respondent ID and display key.
     *
     * @param respondentId the ID of the respondent to filter the answers.
     * @param key          the display key providing the query string for section filtering.
     * @return a list of answers matching the specified criteria.
     */
    public static List<Answer> findBySectionInstancesQueryString(int respondentId, DisplayKey key) {
        return find("#Answer.findBySectionInstancesQueryString", Parameters.with("respondentId", respondentId)
                .and("sectionQuery", key.getAnswerQueryString())).list();
    }

    /**
     * Finds and retrieves a list of Answer objects based on the provided respondent ID and section display key.
     *
     * @param respondentId the identifier of the respondent whose answers are to be fetched
     * @param key          the display key containing survey, step, step instance, section, and section instance details
     * @return a list of Answer objects matching the criteria
     */
    public static List<Answer> findBySectionDisplaykey(int respondentId, DisplayKey key) {
        return find("#Answer.findBySectionDisplaykey", Parameters.with("respondentId", respondentId)
                .and("surveyId", key.getSurvey())
                .and("stepId", key.getStep())
                .and("stepInstance", key.getStepInstance())
                .and("sectionId", key.getSection())
                .and("sectionInstance", key.getSectionInstance())).list();
    }

    /**
     * Finds the upstream answer associated with the given respondent and relationship ID.
     *
     * @param respondentId   the unique identifier of the respondent
     * @param relationshipID the unique identifier of the relationship
     * @return the upstream answer corresponding to the provided respondent and relationship ID,
     * or null if no matching answer is found
     */
    public static Answer findUpstreamAnswerByRelationshipId(int respondentId, int relationshipID) {
        return find("#Answer.findUpstreamAnswerByRelationshipId", Parameters.with("respondentId", respondentId).and("relationshipID", relationshipID)).firstResult();
    }

    /**
     * Purges the deleted answers associated with the specified respondent ID.
     *
     * @param respondentId the unique identifier of the respondent whose deleted answers are to be purged
     */
    public static void purgeDeleted(int respondentId) {
        Answer.delete("#Answer.purgeDeleted", Parameters.with("respondentId", respondentId));
    }

    @Column(name = "display_key", length = 34)
    public String getDisplayKey() {
        return this.displayKey;
    }

    /**
     * Sets the display key values by extracting information from the provided DisplayKey object.
     *
     * @param displayKey an instance of DisplayKey containing values to set for display key,
     *                   survey, step, step instance, section, section instance, question, and
     *                   question instance. Nullable foreign key values are handled appropriately.
     */
    private void setDisplayKeyValues(DisplayKey displayKey) {
        // Some of the foreign keys are nullable.
        // In the Display key they are value 0 but in this class null. see valueOrNull
        this.displayKey = displayKey.getValue();
        this.surveyId = displayKey.getSurvey();
        this.stepId = displayKey.getStep();
        this.stepInstance = displayKey.getStepInstance();
        this.sectionId = displayKey.getSection();
        this.sectionInstance = displayKey.getSectionInstance();
        this.section_question_id = valueOrNull(displayKey.getQuestion());
        this.question_instance = displayKey.getQuestionInstance();
    }

    /**
     * Returns the given value as an Integer, or null if the value is 0.
     *
     * @param value the integer value to evaluate
     * @return the Integer representation of the value, or null if the value is 0
     */
    private Integer valueOrNull(int value) {
        if (value == 0) {
            return null;
        } else return value;
    }

    /**
     * Retrieves the display key associated with this answer.
     * This method creates and returns a new instance of {@link DisplayKey}
     * using the current value of the displayKey field.
     *
     * @return a {@link DisplayKey} object representing the display key.
     */
    @Transient
    public DisplayKey getKey() {
        return new DisplayKey(this.displayKey);
    }

    /**
     * Converts the text value of this object to a double.
     * If the conversion fails due to an invalid format, logs the error
     * and returns 0 as a fallback value.
     *
     * @return the double representation of the text value, or 0 if the conversion fails.
     */
    @Transient
    public double getDouble() {
        try {
            return Double.valueOf(textValue);
        } catch (NumberFormatException e) {
            Log.error(e);
            return 0;
        }
    }

    /**
     * Sets the value of this object as a double.
     * The double value is converted to a string and stored in the textValue field.
     *
     * @param value the double value to set
     */
    public void setDouble(double value) {
        this.textValue = Double.toString(value);
    }

    /**
     * Converts the textValue field to an integer.
     *
     * @return The integer representation of the textValue field.
     *         Returns 0 if the textValue cannot be parsed as an integer.
     * @throws NumberFormatException if the textValue is not a valid integer format.
     */
    @Transient
    public int getInteger() {
        try {
            return Integer.valueOf(textValue);
        } catch (NumberFormatException e) {
            Log.error(e);
            return 0;
        }
    }

    /**
     * Sets the integer value by converting it to a string and storing it in the textValue field.
     *
     * @param value the integer value to be set
     */
    public void setInteger(int value) {
        this.textValue = Integer.toString(value);
    }

    /**
     * Retrieves the selected item from the associated question's select group
     * based on the current text value of this answer.
     *
     * @return The {@link SelectItem} that matches the current text value if the
     *         question type is either RADIO or CHECKBOX, or {@code null} if no
     *         match is found or the conditions are not met.
     */
    @Transient
    public SelectItem getSelectedItem() {
        if (this.textValue != null && (question.questionType.equals(GlobalStrings.QUESTION_TYPE_RADIO)
                || question.questionType.equals(GlobalStrings.QUESTION_TYPE_CHECKBOX))) {
            for (SelectItem item : question.selectGroup.selectItems) {
                if (item.codedValue.equals(this.textValue)) {
                    return item;
                }
            }
        }
        return null;
    }

    /**
     * Sets the selected item for the answer. Updates the text value of the answer
     * based on the coded value of the provided SelectItem if the question type is
     * either RADIO or CHECKBOX.
     *
     * @param item the SelectItem to set as the selected item
     * @throws NullPointerException if the question or questionType is null
     */
    public void setSelectedItem(SelectItem item) {
        if (question.questionType.equals(GlobalStrings.QUESTION_TYPE_RADIO)
                || question.questionType.equals(GlobalStrings.QUESTION_TYPE_CHECKBOX)) {
            this.textValue = item.codedValue;
        }
    }

    /**
     * Retrieves the set of selected items based on the text value and the question type.
     * This method processes the `textValue` field, which contains a comma-separated list
     * of selected values, and matches them against the coded values of the select items
     * in the associated question's select group.
     *
     * @return A set of {@link SelectItem} objects that match the selected values in
     *         the `textValue` field, or {@code null} if no items are selected or the
     *         conditions are not met.
     */
    public Set<SelectItem> getSelectedItems() {
        HashSet<SelectItem> selectedItems = new HashSet<>();
        if (this.textValue != null && (question.questionType.equals(GlobalStrings.QUESTIION_TYPE_CHECKBOX_GROUP)
                || question.questionType.equals(GlobalStrings.QUESTIION_TYPE_MULTI_SELECT_COMBOBOX))) {
            for (SelectItem item : question.selectGroup.selectItems) {
                String[] values = this.textValue.split(",");
                for (String value : values) {
                    if (item.codedValue.equals(value)) {
                        selectedItems.add(item);
                    }
                }
            }
            return selectedItems;
        }
        return null;
    }

    /**
     * Sets the selected items for the answer. This method processes the provided set of
     * {@link SelectItem} objects and updates the textValue field with a comma-separated
     * string of their coded values. This is applicable only for questions of type
     * CHECKBOX_GROUP or MULTI_SELECT_COMBOBOX.
     *
     * @param items the set of {@link SelectItem} objects representing the selected items
     *              for the answer
     */
    public void setSelectedItems(Set<SelectItem> items) {
        if (question.questionType.equals(GlobalStrings.QUESTIION_TYPE_CHECKBOX_GROUP)
                || question.questionType.equals(GlobalStrings.QUESTIION_TYPE_MULTI_SELECT_COMBOBOX)) {
            HashSet<String> values = new HashSet<>();
            for (SelectItem item : items) {
                values.add(item.codedValue);
            }
            this.textValue = String.join(",", values);
        }
    }

    /**
     * Converts the text value of this answer to a {@link LocalDate}.
     *
     * @return the {@link LocalDate} representation of the text value.
     * @throws DateTimeParseException if the text value cannot be parsed into a valid {@link LocalDate}.
     */
    @Transient
    public LocalDate getLocalDate() {
        return LocalDate.parse(this.textValue);
    }

    /**
     * Sets the local date value by converting the given {@link LocalDate}
     * to its string representation and storing it in the textValue field.
     *
     * @param date the {@link LocalDate} to be set
     */
    public void setLocalDate(LocalDate date) {
        this.textValue = date.toString();
    }

    /**
     * Converts the text value of this object to a {@link LocalDateTime}.
     *
     * @return a {@link LocalDateTime} object parsed from the text value.
     * @throws DateTimeParseException if the text value cannot be parsed into a valid {@link LocalDateTime}.
     */
    @Transient
    public LocalDateTime getLocalDateTime() {
        return LocalDateTime.parse(this.textValue);
    }

    /**
     * Sets the value of the textValue field to the string representation of the given LocalDateTime.
     * This method is marked as @Transient, indicating that it is not intended to be persisted.
     *
     * @param dateTime the LocalDateTime object to be converted to a string and set as textValue
     */
    public void setLocalDateTime(LocalDateTime dateTime) {
        this.textValue = dateTime.toString();
    }

    /**
     * Parses the text value of this object into a {@link java.time.LocalTime} instance.
     *
     * @return a {@link java.time.LocalTime} object representing the time parsed from the text value.
     * @throws java.time.format.DateTimeParseException if the text value cannot be parsed into a valid {@link java.time.LocalTime}.
     */
    @Transient
    public LocalTime getLocalTime() {
        return LocalTime.parse(this.textValue);
    }

    /**
     * Sets the local time for this object by converting the provided {@link LocalTime}
     * to its string representation and storing it in the textValue field.
     *
     * @param time the {@link LocalTime} to set
     */
    public void setLocalTime(LocalTime time) {
        this.textValue = time.toString();
    }
}
