package com.elicitsoftware;

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

import jakarta.xml.bind.annotation.XmlRootElement;

/**
 * Represents a display key used to uniquely identify survey-related entities such as steps,
 * sections, and questions in a hierarchical structure.
 * <p>
 * The DisplayKey class provides methods to parse, manipulate, and generate different formats
 * of the key. It implements {@link Comparable}, allowing DisplayKey instances to be compared
 * based on their string representation.
 * <p>
 * The key structure comprises of several components:
 * - Survey ID
 * - Step Display Order
 * - Step Instance
 * - Section Display Order
 * - Section Instance
 * - Question Display Order
 * - Question Instance
 * <p>
 * Each part is represented as an integer and formatted with zero-padding to four digits.
 */
@XmlRootElement
public class DisplayKey implements Comparable<DisplayKey> {
    private int survey; //Survey id
    private int step; // Step Display Order
    private int stepInstance;
    private int section; // Section Display Order
    private int sectionInstance;
    private int question; //SectionQuestion Display order
    private int questionInstance;

    /**
     * Constructs a DisplayKey by parsing a key string.
     * The key string should be in the format: "survey-step-stepInstance-section-sectionInstance-question-questionInstance"
     *
     * @param key the string representation of the display key to parse
     */
    public DisplayKey(String key) {
        super();
        String[] keyStrings = key.split("-");
        this.survey = Integer.parseInt(keyStrings[0]);
        this.step = Integer.parseInt(keyStrings[1]);
        this.stepInstance = Integer.parseInt(keyStrings[2]);
        this.section = Integer.parseInt(keyStrings[3]);
        this.sectionInstance = Integer.parseInt(keyStrings[4]);
        this.question = Integer.parseInt(keyStrings[5]);
        this.questionInstance = Integer.parseInt(keyStrings[6]);
    }

    /**
     * Gets the survey ID.
     *
     * @return the survey ID
     */
    public int getSurvey() {
        return survey;
    }

    /**
     * Sets the survey ID.
     *
     * @param survey the survey ID to set
     */
    public void setSurvey(int survey) {
        this.survey = survey;
    }

    /**
     * Gets the step display order.
     *
     * @return the step display order
     */
    public int getStep() {
        return step;
    }

    /**
     * Sets the step display order.
     *
     * @param step the step display order to set
     */
    public void setStep(int step) {
        this.step = step;
    }

    /**
     * Gets the step instance.
     *
     * @return the step instance
     */
    public int getStepInstance() {
        return stepInstance;
    }

    /**
     * Sets the step instance.
     *
     * @param stepInstance the step instance to set
     */
    public void setStepInstance(int stepInstance) {
        this.stepInstance = stepInstance;
    }

    /**
     * Gets the section display order.
     *
     * @return the section display order
     */
    public int getSection() {
        return section;
    }

    /**
     * Sets the section display order.
     *
     * @param section the section display order to set
     */
    public void setSection(int section) {
        this.section = section;
    }

    /**
     * Gets the section instance.
     *
     * @return the section instance
     */
    public int getSectionInstance() {
        return sectionInstance;
    }

    /**
     * Sets the section instance.
     *
     * @param sectionInstance the section instance to set
     */
    public void setSectionInstance(int sectionInstance) {
        this.sectionInstance = sectionInstance;
    }

    /**
     * Gets the question display order.
     *
     * @return the question display order
     */
    public int getQuestion() {
        return question;
    }

    /**
     * Sets the question display order.
     *
     * @param question the question display order to set
     */
    public void setQuestion(int question) {
        this.question = question;
    }

    /**
     * Gets the question instance.
     *
     * @return the question instance
     */
    public int getQuestionInstance() {
        return questionInstance;
    }

    /**
     * Sets the question instance.
     *
     * @param questionInstance the question instance to set
     */
    public void setQuestionInstance(int questionInstance) {
        this.questionInstance = questionInstance;
    }

    /**
     * Constructs and returns a formatted composite string value based on the fields of the class.
     * Each field value is left-padded with zeros up to 4 digits and combined using hyphens.
     *
     * @return A formatted composite string in the form of "survey-step-stepInstance-section-sectionInstance-question-questionInstance",
     * where each section is left-padded with zeros to ensure a width of 4 digits.
     */
    public String getValue() {
        return leftPad(this.survey) + "-" + leftPad(this.step) + "-" + leftPad(this.stepInstance) + "-" + leftPad(this.section) + "-"
                + leftPad(this.sectionInstance) + "-" + leftPad(this.question) + "-" + leftPad(this.questionInstance);
    }

    /**
     * Constructs and returns the answer query string based on the fields of the class.
     * Each field contributing to the string (survey, step, stepInstance, section,
     * sectionInstance, and question) is left-padded with zeros up to 4 digits.
     * The fields are concatenated in the order listed, separated by hyphens,
     * and followed by a wildcard character "%".
     *
     * @return A formatted string representing the answer query, in the format
     * "survey-step-stepInstance-section-sectionInstance-question.%",
     * where each numeric section is left-padded with zeros to ensure a width of 4 digits.
     */
    public String getAnswerQueryString() {
        return leftPad(survey) + "-" + leftPad(step) + "-" + leftPad(stepInstance) + "-" + leftPad(section) + "-"
                + leftPad(sectionInstance) + "-" + leftPad(question) + ".%";
    }

    /**
     * Constructs and returns the section query string based on the fields of the class.
     * The fields contributing to the string include survey, step, stepInstance, and section.
     * Each field is left-padded with zeros up to 4 digits and concatenated with hyphens.
     * A wildcard character "%" is appended at the end of the string.
     *
     * @return A formatted string representing the section query, in the format
     * "survey-step-stepInstance-section-%", where each section is left-padded
     * with zeros to ensure a width of 4 digits.
     */
    public String getSectionQueryString() {
        return leftPad(this.survey) + "-" + leftPad(this.step) + "-" + leftPad(this.stepInstance) + "-" + leftPad(this.section) + "-%";
    }

    /**
     * Constructs and returns a formatted string representation of the section identifier
     * based on the values of the survey, step, stepInstance, and section fields.
     * Each numeric value is left-padded with zeros to ensure a width of 4 digits.
     * The formatted parts are concatenated with hyphens and followed by placeholder segments.
     *
     * @return A formatted string in the format "survey-step-stepInstance-section-0000-0000-0000",
     * where each numeric field is left-padded with zeros to have a width of 4 digits.
     */
    public String getSectionString() {
        return leftPad(this.survey) + "-" + leftPad(this.step) + "-" + leftPad(this.stepInstance) + "-" + leftPad(this.section)
                + "-0000-0000-0000";
    }

    /**
     * Constructs and returns a formatted string representation of the step identifier
     * based on the values of the survey, step, and stepInstance fields.
     * Each numerical value is left-padded with zeros to ensure a width of 4 digits.
     * The formatted parts are concatenated with hyphens and followed by placeholder segments.
     *
     * @return A formatted string in the format "survey-step-stepInstance-0000-0000-0000-0000",
     * where each numeric field is left-padded with zeros to have a width of 4 digits.
     */
    public String getStepString() {
        return leftPad(this.survey) + "-" + leftPad(this.step) + "-" + leftPad(this.stepInstance) + "-0000-0000-0000-0000";
    }

    /**
     * Constructs and returns a formatted query string specific to the step.
     * The query string is composed of the survey and step fields, both left-padded
     * with zeros to ensure a width of 4 digits, concatenated with a hyphen.
     * A wildcard character "%" is appended to indicate a pattern match.
     *
     * @return A formatted string in the format "survey-step-%", where both fields
     * are left-padded with zeros to have a width of 4 digits.
     */
    public String getStepQueryString() {
        return leftPad(this.survey) + "-" + leftPad(this.step) + "-%";
    }

    /**
     * Left-pads the given integer with zeros to ensure it has a minimum width of 4 digits.
     *
     * @param n the integer to be left-padded
     * @return a string representation of the integer, padded with leading zeros if necessary, to ensure a width of 4 characters
     */
    public String leftPad(int n) {
        return String.format("%04d", n);
    }

    @Override
    public String toString() {
        return this.getValue();
    }

    @Override
    public int compareTo(DisplayKey key) {
        String value = this.getValue();
        return value.compareTo(key.getValue());
    }
}
