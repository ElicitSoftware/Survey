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

import com.elicitsoftware.model.*;
import edu.umich.elicit.model.*;
import com.elicitsoftware.response.NavResponse;
import com.elicitsoftware.response.NavigationItem;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.transaction.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.TreeMap;

/**
 * The {@code QuestionManager} class is responsible for managing the lifecycle of survey questions,
 * their associated answers, dependencies, and navigation logic for respondents within a survey system.
 * It provides methods to initialize survey answers, navigate through survey sections, manage
 * upstream and downstream dependencies, and handle answer persistence or deletion.
 * <p>
 * The class primarily interacts with answers, survey sections, steps, and questions, maintaining
 * the relationships and hierarchical structure among them. It ensures the correct flow for both
 * repeated and dependent answers, along with creating navigation items for respondents.
 */
@ApplicationScoped
public class QuestionManager {
    @Inject
    EntityManager entityManager;

    /**
     * Replaces tokens in the given text with corresponding values from the provided map.
     * The tokens are identified by keys enclosed in curly braces and replaced with their associated values.
     * Any unmatched tokens are removed, leaving default text.
     * Additionally, certain specific text replacements and formatting adjustments are applied to the final result.
     *
     * @param text   the input string containing tokens to be replaced
     * @param values a {@code TreeMap} containing key-value pairs, where keys correspond to tokens in the text,
     *               and values are the replacements for those tokens
     * @return the resulting string after replacing all specified tokens and applying formatting adjustments
     */
    private static String replaceTokens(String text, TreeMap<String, String> values) {

        // Split the string into parts
        String[] displayText = text.split("}");

        if (!values.isEmpty()) {
            // Replace all the Token values
            for (String key : values.keySet()) {
                for (int i = 0; i < displayText.length; i++) {
                    String string = displayText[i];
                    if (string.contains(key)) {
                        string = string.replaceFirst("\\{", "");
                        string = string.replaceFirst("\\|.*", "");
                        string = string.replaceFirst(key, values.get(key));
                    }
                    if (string.contains("}")) {
                        string = replaceTokens(string, values);
                    }
                    displayText[i] = string;
                }
            }
        }

        for (int i = 0; i < displayText.length; i++) {
            String string = displayText[i];

            // Remove any tokens values that were not passed, leaving the
            // default
            // part
            // of the text
            string = string.replaceAll("\\{.*\\|", "");
            displayText[i] = string;
        }

        // Rebuild the sting from its parts
        StringBuilder textBuilder = new StringBuilder();
        for (String s : displayText) {
            textBuilder.append(s);
        }
        text = textBuilder.toString();

        // Until I can come up with a better solution to this problem I'll
        // force it here. I know this is a hack.
        text = text.replace(" her's ", " her ");
        text = text.replace(" his's ", " his ");
        text = text.replace(" Your's ", " Your ");

        // Lastly replace any s's with s' this if for names like Dennis as in
        // what is Dennis'name
        text = text.replaceAll("s's", "s'");

        return text;
    }

    /**
     * Initializes the initial answers for a specified respondent based on the survey steps
     * associated with the given key.
     *
     * @param respondentId the identifier of the respondent for whom the initial answers
     *                     need to be generated
     * @param key          the display key associated with the survey, used to locate relevant steps
     */
    public void init(int respondentId, String key) {

        DisplayKey displaykey = new DisplayKey(key);

        List<StepsSections> steps = StepsSections.findBySurveyId(displaykey.getSurvey());
        for (StepsSections step : steps) {
            buildInitialAnswers(respondentId, step.getKey());
        }
    }

    /**
     * Retrieves an answer for a given respondent and display key, considering whether to include deleted answers.
     *
     * @param respondentId the identifier of the respondent whose answer needs to be retrieved
     * @param key          the display key used to locate the specific answer
     * @param showDeleted  a boolean indicating if deleted answers should be included in the query
     * @return the Answer object matching the specified display key for the given respondent,
     * or null if no match is found
     */
    private Answer getAnswerByDisplayKey(Integer respondentId, String key, boolean showDeleted) {
        Answer answer;
        if (showDeleted) {
            answer = Answer.findByDisplayKeyAll(respondentId, key);
        } else {
            answer = Answer.findByDisplayKeyActive(respondentId, key);
        }

        return answer;
    }

    /**
     * Retrieves an Answer object based on the respondent ID, step ID, section question ID,
     * and question instance.
     *
     * @param respondentId      the identifier of the respondent
     * @param stepId            the identifier of the step
     * @param sqId              the identifier of the section question
     * @param question_instance the instance number of the question
     * @return the Answer object corresponding to the provided parameters, or null in case
     * of an exception or if no matching answer is found
     */
    private Answer getAnswersByStepAndSectionQuestion(int respondentId, int stepId, int sqId, int question_instance) {
        try {
            return Answer.findByStepSQ(respondentId, sqId, question_instance);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Retrieves a Section object corresponding to the provided display key.
     * The method constructs a DisplayKey object with the given key and
     * attempts to find and return the associated Section from the database.
     * If no matching Section is found or an exception occurs, null is returned.
     *
     * @param key the display key used to locate the associated Section
     * @return the Section object corresponding to the provided display key,
     * or null if no match is found or an exception occurs
     */
    private Section getSectionByDisplayKey(String key) {
        DisplayKey dkey = new DisplayKey(key);
        dkey.setStepInstance(0);
        try {
            StepsSections stepsSections = StepsSections.findByDisplayKey(dkey);
            return stepsSections.section;
        } catch (Exception e) {
            // There may not be one in the database. Return the null value;
            return null;
        }
    }

    /**
     * Retrieves a Step object associated with the given display key.
     * The method queries the database using the provided DisplayKey to find the step.
     * It handles potential exceptions, such as missing data, and returns null if no matching step is found.
     *
     * @param key the DisplayKey used to locate the step. The key contains identifiers
     *            for querying either a section or step in the database.
     * @return the Step object corresponding to the provided DisplayKey, or null if no matching step is found
     * or an exception occurs.
     */
    private Step getStepByDisplayKey(DisplayKey key) {

        StepsSections stepsSections;
        try {
            if (key.getSection() != 0) {
                stepsSections = StepsSections.findFirstByDisplayKeyQuery(key.getSectionQueryString());
            } else {
                stepsSections = StepsSections.findFirstByDisplayKeyQuery(key.getStepQueryString());
            }
        } catch (Exception e) {
            // There may not be one in the database. Return the null value;
            return null;
        }

        if (stepsSections == null || stepsSections.step == null) {
            // There may not be one in the database. Return the null value;
            return null;
        } else {
            return stepsSections.step;
        }
    }

    /**
     * Navigates through the survey framework by retrieving the current step,
     * navigation items, and relevant answers for a particular respondent
     * based on a given section display key.
     *
     * @param respondentId      the ID of the respondent for whom the navigation data is being retrieved
     * @param sectionDisplaykey the key used to identify the specific section within the survey
     * @return a NavResponse object containing the current step, current navigation item,
     * list of answers for the section, and all navigation items for the respondent
     */
    public NavResponse navigate(Integer respondentId, String sectionDisplaykey) {

        DisplayKey key = new DisplayKey(sectionDisplaykey);

        List<Answer> answers = getAnswersBySection(respondentId, sectionDisplaykey);
        ArrayList<NavigationItem> ls = buildNavItems(respondentId);
        NavigationItem[] navItems = new NavigationItem[ls.size()];
        navItems = ls.toArray(navItems);
        NavigationItem curreNavItem = getCurrentNavItem(navItems, sectionDisplaykey);

        Step step = getStepByDisplayKey(key);

        return new NavResponse(step, curreNavItem, answers, navItems);
    }

    /**
     * Retrieves the current NavigationItem that matches the given key from the provided array of NavigationItems.
     * This method iterates through the navItems array, comparing the path of each item to the section string
     * derived from the given key, and returns the first matching NavigationItem.
     *
     * @param navItems an array of NavigationItem objects to be searched
     * @param key      a string representing the key used to identify the current navigation item
     * @return the NavigationItem that matches the provided key, or null if no match is found
     */
    private NavigationItem getCurrentNavItem(NavigationItem[] navItems, String key) {

        DisplayKey dkey = new DisplayKey(key);

        for (NavigationItem navigationItem : navItems) {
            if (navigationItem.getPath().equals(dkey.getSectionString())) {
                return navigationItem;
            }
        }
        return null;
    }

    /**
     * Retrieves a list of answers associated with a specific section for a given respondent.
     * This method ensures that all initial answers for the specified section are generated,
     * and it organizes the answers in a specific order before returning them.
     *
     * @param respondentId      the ID of the respondent for whom answers are retrieved
     * @param sectionDisplaykey the display key of the section for which answers are retrieved
     * @return a list of Answer objects ordered by their display keys
     */
    private List<Answer> getAnswersBySection(Integer respondentId, String sectionDisplaykey) {

        DisplayKey key = new DisplayKey(sectionDisplaykey);

        // This section may or may not be empty.
        // It may have answers that were the result of a relationship.
        // It may be the first time they have been in this section.
        // this next call ensures that all the initial answers are generated.
        buildInitialAnswers(respondentId, key);
        List<Answer> existing = Answer.findBySectionDisplaykey(respondentId, key);
        TreeMap<String, Answer> answers = new TreeMap<>();
        // move all the answers to an ordered map.
        for (Answer answer : existing) {
            answers.put(answer.getDisplayKey(), answer);
        }

        return new ArrayList<>(answers.values());
    }

    /**
     * Retrieves the initial set of section questions for a given respondent, based on
     * the provided survey key and whether the step-level questions should be loaded.
     * Initial questions are not generated by a relationship, meaning they are not
     * downstream questions, sections, or steps.
     *
     * @param respondentId The ID of the respondent for whom the initial section questions are being retrieved.
     * @param key          The {@code DisplayKey} object containing the survey, step, and section information needed for the query.
     * @param loadStep     A boolean value indicating whether to retrieve step-specific questions (if {@code true}),
     *                     or to retrieve section-level questions (if {@code false}).
     * @return A list of {@code SectionsQuestion} objects representing the initial section questions.
     */
    private ArrayList<SectionsQuestion> getInitialSectionSectionsQuestion(Integer respondentId, DisplayKey key,
                                                                          boolean loadStep) {

        // Initial Answers are questions that are not generated by a
        // relationship. i.e. they are not a downstream question, downstream
        // section
        // or downstream step.
        ArrayList<SectionsQuestion> sectionsQuestions = new ArrayList<>();

        String sqlStep = "SELECT SQ.ID, SQ.DISPLAY_ORDER FROM SURVEY.SECTIONS_QUESTIONS SQ "
                + "JOIN SURVEY.STEPS_SECTIONS SS ON SQ.SECTION_ID = SS.SECTION_ID AND SQ.SURVEY_ID = SS.SURVEY_ID "
                + "WHERE SS.SURVEY_ID = :surveyId AND SS.STEP_ID = :stepId AND SS.SECTION_DISPLAY_ORDER = :displayOrder "
                + "AND SS.ID NOT IN (SELECT R.DOWNSTREAM_S_ID FROM SURVEY.RELATIONSHIPS R WHERE R.SURVEY_ID = SS.SURVEY_ID AND R.DOWNSTREAM_STEP_ID = SS.STEP_ID AND R.DOWNSTREAM_SQ_ID IS NULL AND R.DOWNSTREAM_S_ID IS NOT NULL AND R.ACTION_ID != 3) "
                + "AND Sq.id NOT IN (SELECT R.DOWNSTREAM_SQ_ID FROM SURVEY.RELATIONSHIPS R WHERE R.SURVEY_ID = SS.SURVEY_ID AND R.UPSTREAM_STEP_ID = SS.STEP_ID AND R.ACTION_ID != 3 AND R.DOWNSTREAM_S_ID IS NOT NULL AND R.DOWNSTREAM_SQ_ID IS NOT NULL) "
                + "order by SQ.DISPLAY_ORDER";

        String sqlSection = "SELECT SQ.ID, SQ.DISPLAY_ORDER FROM SURVEY.SECTIONS_QUESTIONS SQ "
                + "JOIN SURVEY.STEPS_SECTIONS SS ON SQ.SECTION_ID = SS.SECTION_ID AND SQ.SURVEY_ID = SS.SURVEY_ID "
                + "WHERE SS.SURVEY_ID = :surveyId AND SS.STEP_ID = :stepId AND SS.SECTION_DISPLAY_ORDER = :displayOrder "
                + "AND Sq.id NOT IN ( "
                + "SELECT R.DOWNSTREAM_SQ_ID FROM SURVEY.RELATIONSHIPS R WHERE R.SURVEY_ID = SS.SURVEY_ID AND R.UPSTREAM_STEP_ID = SS.STEP_ID AND R.ACTION_ID != 3 AND R.DOWNSTREAM_S_ID IS NOT NULL AND R.DOWNSTREAM_SQ_ID IS NOT NULL "
                + "UNION "
                + "SELECT R.DOWNSTREAM_SQ_ID FROM SURVEY.RELATIONSHIPS R WHERE R.SURVEY_ID = SS.SURVEY_ID AND R.ACTION_ID != 3 AND R.DOWNSTREAM_S_ID IS NULL AND R.DOWNSTREAM_SQ_ID IS NOT NULL) "
                + "order by SQ.DISPLAY_ORDER";

        Query q;

        if (loadStep) {
            q = entityManager.createNativeQuery(sqlStep);
        } else {
            q = entityManager.createNativeQuery(sqlSection);
        }

        q.setParameter("surveyId", key.getSurvey());
        q.setParameter("stepId", key.getStep());
        q.setParameter("displayOrder", key.getSection());

        @SuppressWarnings("unchecked")
        List<Object[]> rs = q.getResultList();

        if (!rs.isEmpty()) {
            SectionsQuestion sectionsQuestion;
            Integer id;
            for (Object[] record : rs) {
                id = (Integer) record[0];
                sectionsQuestion = SectionsQuestion.findById(id);
                sectionsQuestions.add(sectionsQuestion);
            }
        }
        return sectionsQuestions;
    }

    /**
     * Retrieves a list of initial sections questions for a specific step in the survey.
     * Initial sections questions are ones not generated by relationships, i.e., not downstream questions,
     * downstream sections, or downstream steps. The method eliminates questions that have already been
     * answered or linked to a downstream relationship.
     *
     * @param respondentId the unique identifier of the respondent. Used to filter out answered questions.
     * @param key          the display key containing survey-specific identifiers such as survey ID
     *                     and step display order.
     * @return a list of SectionsQuestion objects representing the initial sections questions
     * for the specified step.
     */
    private ArrayList<SectionsQuestion> getInitialStepSectionsQuestion(Integer respondentId, DisplayKey key) {

        // Initial Answers are questions that are not generated by a
        // relationship. i.e. they are not a downstream question, downstream
        // section
        // or downstream step.
        ArrayList<SectionsQuestion> sectionsQuestions = new ArrayList<>();
        String sql = "SELECT S.ID, S.DISPLAY_ORDER " + "FROM SURVEY.SECTIONS_QUESTIONS S "
                + "Join SURVEY.STEPS_SECTIONS SS on S.SECTION_ID = SS.SECTION_ID AND S.SURVEY_ID = SS.SURVEY_ID "
                + "WHERE SS.SURVEY_ID = :surveyId AND SS.STEP_DISPLAY_ORDER = :stepDisplayOrder "
                + "AND S.ID NOT IN (SELECT R.DOWNSTREAM_SQ_ID FROM SURVEY.RELATIONSHIPS R WHERE R.DOWNSTREAM_SQ_ID IS NOT NULL) "
                + "AND S.ID NOT IN (SELECT A.SECTION_QUESTION_ID FROM SURVEY.Answers A WHERE A.RESPONDENT_ID = :respondentId and A.SECTION_QUESTION_ID = S.ID ) "
                + "AND S.ID NOT IN (SELECT SQ.ID from SURVEY.SECTIONS_QUESTIONS SQ JOIN SURVEY.STEPS_SECTIONS SS on SQ.SECTION_ID = SS.SECTION_ID JOIN SURVEY.RELATIONSHIPS R ON SS.ID = R.DOWNSTREAM_S_ID WHERE R.DOWNSTREAM_S_ID IS NOT NULL) "
                + "AND S.ID NOT IN (SELECT T.ID FROM SURVEY.SECTIONS_QUESTIONS T JOIN SURVEY.STEPS_SECTIONS SS ON T.SECTION_ID = SS.SECTION_ID JOIN SURVEY.RELATIONSHIPS R ON SS.STEP_ID = R.DOWNSTREAM_STEP_ID) "
                + "order by S.DISPLAY_ORDER";


        //entityManager.joinTransaction();
        Query q = entityManager.createNativeQuery(sql);
        q.setParameter("surveyId", key.getSurvey());
        q.setParameter("respondentId", respondentId);
        q.setParameter("stepDisplayOrder", key.getStep());

        @SuppressWarnings("unchecked")
        List<Object[]> rs = q.getResultList();
        if (!rs.isEmpty()) {
            SectionsQuestion sectionsQuestion;
            for (Object[] record : rs) {
                sectionsQuestion = SectionsQuestion.findById(record[0]);
                sectionsQuestions.add(sectionsQuestion);
            }
        }
        return sectionsQuestions;
    }

    /**
     * Builds the initial set of answers for a given respondent and display key.
     * This method retrieves the initial step sections of questions, creates answers for each
     * question, and saves them. It also recursively builds downstream questions for certain types of questions.
     *
     * @param respondentId the unique identifier of the respondent for whom the answers are being built
     * @param key          the display key used to associate questions and answers, which contains information about sections or questions
     */
    private void buildInitialAnswers(Integer respondentId, DisplayKey key) {

        ArrayList<SectionsQuestion> sectionsQuestions = getInitialStepSectionsQuestion(respondentId, key);
        if (!sectionsQuestions.isEmpty()) {
            // First we should build the answer for the section name.
            buildSectionAnswer(respondentId, key.getSectionString(), null);

            Answer answer;
            DisplayKey dkey;
            for (SectionsQuestion sectionsQuestion : sectionsQuestions) {
                // Build a display key for the answer
                dkey = new DisplayKey(key.getValue());
                dkey.setQuestion(sectionsQuestion.displayOrder);
                answer = new Answer(dkey, sectionsQuestion, sectionsQuestion.question.text, respondentId, sectionsQuestion.question.defaultValue);
                answer = saveAnswer(answer, null);
                if ("HTML".equals(sectionsQuestion.question.questionType.name) || answer.getTextValue() != null) {
                    buildDownstreamQuestions(answer);
                }
            }
        }
    }

    /**
     * Builds a section answer for the given respondent based on the provided key and dependents.
     * If an answer corresponding to the given key does not exist for the respondent,
     * a new answer is created and saved using the associated section data and dependents.
     *
     * @param respondentId The ID of the respondent for whom the section answer is being built.
     * @param key          The display key associated with the section and answer.
     * @param dependents   A map of dependent data associated with the answer.
     */
    private void buildSectionAnswer(Integer respondentId, String key, HashMap<Integer, Dependent> dependents) {

        Answer sectionAanswer = getAnswerByDisplayKey(respondentId, key, true);
        if (sectionAanswer == null) {

            Section section = getSectionByDisplayKey(key);
            assert section != null;
            Answer answer = new Answer(new DisplayKey(key), null, section.name, respondentId);
            saveAnswer(answer, dependents);
        }
    }

    /**
     * Builds the answer for a step using the given relationship, upstream answer, key,
     * and a map of dependent relationships. This method associates dependent answers
     * based on relationships between steps and evaluates conditions to establish dependencies.
     *
     * @param relationship The relationship that determines how steps are connected.
     * @param upstream     The upstream answer used for evaluating dependencies.
     * @param key          The display key for the current step being processed.
     * @param dependents   A map where the IDs of relationship dependencies are the keys,
     *                     and the Dependent objects are the values.
     */
    private void buildStepAnswer(Relationship relationship, Answer upstream, String key,
                                 HashMap<Integer, Dependent> dependents) {

        DisplayKey dkey = new DisplayKey(key);
        Step step = getStepByDisplayKey(dkey);
        assert step != null;
        Answer answer = new Answer(dkey, null, step.name, upstream.respondentId);
        // Find upstream relationships by downstream section id
        List<Relationship> relationships = Relationship.findRepeatByDownstreamStep(dkey.getSurvey(), dkey.getStep());
        Answer a;
        Dependent dependent;
        for (Relationship r : relationships) {
            a = getAnswersByStepAndSectionQuestion(upstream.respondentId, r.upstreamStep.id,
                    r.upstreamQuestion.id, dkey.getStepInstance());

            if (a != null && r.evaluateOperator(a)) {
                // Add a dependent.
                dependent = new Dependent(upstream.respondentId, a, answer, r);
                dependents.put(r.id, dependent);
                // }
            }
        }
        saveAnswer(answer, dependents);
    }

    /**
     * Builds and processes downstream questions or sections based on the provided upstream answer
     * and its associated relationships. Depending on the conditions and relationship configurations,
     * it may create new answers, repeat sections, or modify text for existing relationships.
     *
     * @param upstreamAnswer The answer to the upstream question, which will be used to determine
     *                       and create corresponding downstream questions, sections, or steps based
     *                       on predefined relationships and conditions.
     */
    public void buildDownstreamQuestions(Answer upstreamAnswer) {
        Log.info("Build downstream questions for " + upstreamAnswer.id + " - " + upstreamAnswer.question.shortText);
        // First we need to find all the relationships where this
        // question is the upstream question.
        ArrayList<Relationship> relationships = findRelationshipsByUpstreamQuestion(upstreamAnswer);

        HashMap<Integer, Dependent> dependents;

        // now loop through them and see if the operator evaluates to true
        for (Relationship relationship : relationships) {
            // Only build the answers for SHOW or REPEAT.
            if (!relationship.actionType.name.equals("TEXT")) {
                dependents = new HashMap<>();
                if (allRelationshipsSatisfide(relationship, upstreamAnswer, dependents)) {
                    // What type of Action do we need to take?
                    switch (relationship.actionType.name) {
                        case "SHOW":
                            // Are we going to show a Question, Section or Step?
                            if (relationship.downstreamQuestion != null) {
                                DisplayKey key = buildDisplayKey(upstreamAnswer, relationship);

                                key.setQuestion(relationship.downstreamQuestion.displayOrder);
                                // this may be the first answer in a section
                                buildSectionAnswer(upstreamAnswer.respondentId, key.getSectionString(), dependents);
                                Answer a = new Answer(key, relationship.downstreamQuestion,
                                        relationship.downstreamQuestion.question.text,
                                        upstreamAnswer.respondentId, relationship.downstreamQuestion.question.defaultValue);
                                a = saveAnswer(a, dependents);
                                if (a.getTextValue() != null) {
                                    buildDownstreamQuestions(a);
                                }
                            } else if (relationship.downstreamSection != null) {
                                // build the initial answers of the section.
                                DisplayKey newSectionKey = buildDisplayKey(upstreamAnswer, relationship);
                                buildInitialSectionAnswers(relationship, upstreamAnswer, newSectionKey, dependents, false);
                            } else if (relationship.downstreamStep != null) {
                                // Show a step
                                DisplayKey newStepKey = buildDisplayKey(upstreamAnswer, relationship);
                                newStepKey.setStepInstance(upstreamAnswer.question_instance);
                                buildStepAnswer(relationship, upstreamAnswer, newStepKey.getSectionString(), dependents);
                                buildInitialStepAnswers(relationship, upstreamAnswer, newStepKey, dependents);
                            }

                            break;
                        case "REPEAT":
                            // Are we going to repeat a Question or Section?
                            if (relationship.downstreamQuestion != null) {
                                // Repeat a question
                                buildRepeatedAnswers(upstreamAnswer.respondentId, upstreamAnswer,
                                        relationship.downstreamQuestion, relationship, dependents);
                            } else if (relationship.downstreamSection != null) {
                                // repeat the section
                                buildRepeatedSections(upstreamAnswer, relationship.downstreamSection.section,
                                        relationship.id, dependents);
                            } else if (relationship.downstreamStep != null) {
                                // Repeat a step
                                buildRepeatedStep(upstreamAnswer, relationship.downstreamStep, relationship.id,
                                        dependents);
                            }

                            break;
                    }
                } else {
                    // If the user changes the answer we may need to delete all
                    // downstream questions. This is done in
                    // deleteDownstreamAnswers
                    // function
                }
            }
        }

        dependents = new HashMap<>();
        // Now that all the answers are built. See if we need to replace some
        // text.
        for (Relationship relationship : relationships) {
            if (relationship.actionType.name.equals("TEXT")) {
                if (relationship.evaluateOperator(upstreamAnswer)) {

                    // Are we going to alter a Question or Section?
                    if (relationship.downstreamQuestion != null) {
                        // a question
                        DisplayKey key = buildDisplayKey(upstreamAnswer, relationship);
                        key.setQuestion(relationship.downstreamQuestion.displayOrder);
                        Answer a = getAnswerByDisplayKey(upstreamAnswer.respondentId, key.getValue(), false);
                        if (a != null) {
                            dependents.put(relationship.id,
                                    new Dependent(upstreamAnswer.respondentId, upstreamAnswer, a, relationship));
                            saveAnswer(a, dependents);
                        }
                    } else if (relationship.downstreamSection != null) {
                        // a section
                        ArrayList<Answer> answers = getDownstreamSectionAnswers(relationship,
                                upstreamAnswer.respondentId);

                        for (Answer a : answers) {
                            Dependent dependent = new Dependent(upstreamAnswer.respondentId, upstreamAnswer, a,
                                    relationship);
                            dependents = new HashMap<>();
                            dependents.put(dependent.respondentId, dependent);
                            saveAnswer(a, dependents);
                        }
                    }
                    replaceText(relationship, upstreamAnswer);
                } else {
                    // We may have to reset the default values

                    // Are we going to alter a Question or Section?
                    if (relationship.downstreamQuestion != null) {
                        // a question
                        DisplayKey key = buildDisplayKey(upstreamAnswer, relationship);
                        key.setQuestion(relationship.downstreamQuestion.displayOrder);
                        Answer a = getAnswerByDisplayKey(upstreamAnswer.respondentId, key.getValue(), false);
                        if (a != null) {
                            saveAnswer(a, dependents);
                        }
                    } else if (relationship.downstreamSection != null) {
                        // a section
                        ArrayList<Answer> answers = getDownstreamSectionAnswers(relationship,
                                upstreamAnswer.respondentId);
                        for (Answer a : answers) {
                            saveAnswer(a, dependents);
                        }
                    }
                }
            }
        }
    }

    /**
     * Retrieves a list of Answer objects for a downstream section associated with a given relationship and respondent ID.
     *
     * @param relationship The relationship object containing details like survey ID and relationship ID.
     * @param respondentId The ID of the respondent whose answers are to be retrieved.
     * @return An ArrayList of Answer objects corresponding to the downstream section of the specified relationship
     * and respondent.
     */
    private ArrayList<Answer> getDownstreamSectionAnswers(Relationship relationship, Integer respondentId) {
        ArrayList<Answer> answers = new ArrayList<>();

        String sql = "SELECT A.ID FROM survey.RELATIONSHIPS R " + " JOIN survey.STEPS_SECTIONS SS ON R.DOWNSTREAM_S_ID = SS.ID "
                + " JOIN survey.ANSWERS A ON SS.STEP_ID = A.STEP AND SS.SECTION_DISPLAY_ORDER = A.SECTION "
                + " WHERE A.DELETED = false AND R.SURVEY_ID = :surveyId" + " AND A.SURVEY_ID = :surveyId"
                + " AND A.RESPONDENT_ID = :respondentId" + " AND R.ID = :rid"
                + " ORDER BY A.DISPLAY_KEY";

        //entityManager.joinTransaction();
        Query q = entityManager.createNativeQuery(sql);
        q.setParameter("surveyId", relationship.surveyId);
        q.setParameter("respondentId", respondentId);
        q.setParameter("rid", relationship.id);

        @SuppressWarnings("unchecked")
        List<Integer> rs = q.getResultList();

        for (Integer id : rs) {
            answers.add(Answer.findById(id));
        }
        return answers;

    }

    /**
     * Updates the text of answers based on a given relationship and upstream answer.
     * Depending on whether the downstream question in the relationship is null,
     * the method determines the appropriate query to fetch related answers.
     * The answers are then updated with their respective display text and persisted.
     *
     * @param relationship   The relationship object containing linkage information
     *                       to either a downstream section or question.
     * @param upstreamAnswer The upstream answer object containing respondent details
     *                       needed for query execution.
     */
    private void replaceText(Relationship relationship, Answer upstreamAnswer) {

        String sectionSQL = "SELECT A.ID,  A.SECTION, A.SECTION_QUESTION_ID FROM survey.RELATIONSHIPS R JOIN survey.STEPS_SECTIONS SS ON R.DOWNSTREAM_S_ID = SS.ID JOIN survey.ANSWERS A ON SS.STEP_ID = A.STEP AND SS.SECTION_DISPLAY_ORDER = A.SECTION WHERE A.DELETED = false AND R.ID = :rid AND A.RESPONDENT_ID = :respondentId ORDER BY A.DISPLAY_KEY";
        String questionSQL = "SELECT A.ID,  A.SECTION, A.SECTION_QUESTION_ID FROM survey.RELATIONSHIPS R JOIN survey.SECTIONS_QUESTIONS SQ ON R.DOWNSTREAM_SQ_ID = SQ.ID JOIN survey.ANSWERS A ON SQ.ID = A.SECTION_QUESTION_ID WHERE A.DELETED = false AND R.ID = :rid AND A.RESPONDENT_ID = :respondentId ORDER BY A.DISPLAY_KEY";

        Query q;
        //entityManager.joinTransaction();
        if (relationship.downstreamQuestion != null) {
            q = entityManager.createNativeQuery(questionSQL);

        } else {
            q = entityManager.createNativeQuery(sectionSQL);
        }

        q.setParameter("rid", relationship.id);
        q.setParameter("respondentId", upstreamAnswer.respondentId);

        @SuppressWarnings("unchecked")
        List<Object[]> rs = q.getResultList();
        Answer answer;
        for (Object[] record : rs) {
            answer = Answer.findById(record[0]);
            if (answer.question != null) {
                answer.displayText = answer.question.text;
            } else {

                Section section = getSectionByDisplayKey(answer.getDisplayKey());
                answer.displayText = section.name;
            }
            buildDipslayText(answer);
            answer.persistAndFlush();
        }
    }

    /**
     * Constructs a DisplayKey object for use in determining the display order of survey elements
     * based on the upstream answer and relationship provided. This method handles the mapping of
     * steps, sections, and their instances from the upstreamAnswer and the relationship information.
     *
     * @param upstreamAnswer the answer object that contains the current display key and question instance
     * @param relationship   the relationship object that provides details about the downstream step,
     *                       section, and survey identifiers
     * @return a DisplayKey object populated with the required step, section, and instance information
     */
    private DisplayKey buildDisplayKey(Answer upstreamAnswer, Relationship relationship) {
        // Set the step and section instance to the question instance.
        // if this is a repeat section it will be overwritten in the
        // calling method
        DisplayKey key = new DisplayKey(upstreamAnswer.getDisplayKey());

        // Relationships may not have a downstream step.
        if (relationship.downstreamStep != null) {
            //MFD this will have to be reworked ID will not work!!
            key.setStep(getStepDisplayOrder(relationship.surveyId, relationship.downstreamStep.id));
            // if the section is empty then create an instance of the step.
            if (relationship.downstreamSection == null) {
                key.setStepInstance(upstreamAnswer.question_instance);
            }
            key.setSection(0);
        }

        if (relationship.downstreamSection != null) {
            //MFD this will have to be reworked ID will not work!!
            key.setSection(getSectionDisplayOrder(relationship.surveyId, relationship.downstreamSection.id));
            // if the step is empty set the instance on the section.
            if (relationship.downstreamStep == null) {
                key.setSectionInstance(upstreamAnswer.question_instance);
            }
        }

        // clear out the question and instance.
        key.setQuestion(0);
        key.setQuestionInstance(0);
        return key;
    }

    /**
     * Retrieves the display order of a specific step within a survey.
     *
     * @param surveyId the identifier of the survey to which the step belongs
     * @param stepId   the identifier of the step whose display order is to be retrieved
     * @return the step display order as an Integer, or -1 if an exception occurs
     */
    private Integer getStepDisplayOrder(long surveyId, long stepId) {
        try {
            String sql = "select distinct ss.step_display_order from survey.steps_sections ss where ss.survey_id = :surveyId and ss.step_id = :stepId order by ss.step_display_order";
            // entityManager.joinTransaction();
            Query q = entityManager.createNativeQuery(sql);
            q.setParameter("surveyId", surveyId);
            q.setParameter("stepId", stepId);
            return (Integer) q.getSingleResult();
        } catch (Exception e) {
            return -1;
        }
    }

    /**
     * Retrieves the display order of a specific section within a survey.
     *
     * @param surveyId  the identifier of the survey
     * @param sectionId the identifier of the section within the survey
     * @return the display order of the section as an Integer or -1 in case of an exception
     */
    private Integer getSectionDisplayOrder(long surveyId, long sectionId) {
        try {
            String sql = "select distinct ss.section_display_order from survey.steps_sections ss where ss.survey_id = :surveyId and ss.id = :sectionId order by ss.section_display_order";
            // entityManager.joinTransaction();
            Query q = entityManager.createNativeQuery(sql);
            q.setParameter("surveyId", surveyId);
            q.setParameter("sectionId", sectionId);
            return (Integer) q.getSingleResult();
        } catch (Exception e) {
            return -1;
        }
    }

    /**
     * Finds and retrieves a list of relationships based on the upstream question information
     * contained within the provided Answer object.
     *
     * @param upstreamAnswer The Answer object containing the upstream question details,
     *                       including survey ID, section question ID, and step information.
     * @return An ArrayList of Relationship objects that match the upstream question criteria.
     */
    private ArrayList<Relationship> findRelationshipsByUpstreamQuestion(Answer upstreamAnswer) {
        String sql = "SELECT r.ID FROM survey.RELATIONSHIPS r WHERE r.UPSTREAM_SQ_ID = :upstream_sq_id and r.SURVEY_ID = :surveyId and (r.UPSTREAM_STEP_ID = :upstream_step_id or r.UPSTREAM_STEP_ID is null) order by r.ID";
        // entityManager.joinTransaction();
        Query q = entityManager.createNativeQuery(sql);
        q.setParameter("surveyId", upstreamAnswer.surveyId);
        q.setParameter("upstream_sq_id", upstreamAnswer.section_question_id);
        q.setParameter("upstream_step_id", upstreamAnswer.getKey().getStep());

        @SuppressWarnings("unchecked")
        List<Integer> rs = q.getResultList();

        ArrayList<Relationship> relationships = new ArrayList<>();
        for (Integer record : rs) {
            relationships.add(Relationship.findById(record));
        }
        return relationships;
    }

    /**
     * Finds and returns a list of Dependent objects that are associated with the given upstream answer.
     *
     * @param upstreamAnswer the Answer object representing the upstream data, containing the respondent's ID
     *                       and the ID of the answer used to locate dependents
     * @return a list of Dependent objects associated with the specified upstream answer
     */
    private List<Dependent> findDependentsByUpstreamAnswer(Answer upstreamAnswer) {
        return Dependent.findByUpstream(upstreamAnswer.respondentId, upstreamAnswer.id);

    }

    /**
     * Builds the initial step answers for the provided step sections within a defined step.
     *
     * @param r              The relationship information that may be used during the answer-building process.
     * @param upstreamAnswer The upstream answer object containing previous or dependent information needed for building answers.
     * @param sectionKey     The display key representing the step section to be processed and initialized.
     * @param dependents     A map of dependents identified by their unique IDs, used to extract dependent-related information.
     */
    private void buildInitialStepAnswers(Relationship r, Answer upstreamAnswer, DisplayKey sectionKey,
                                         HashMap<Integer, Dependent> dependents) {
        // We want to build the initial section questions for all sections in
        // this step
        List<StepsSections> sections = StepsSections.findByDisplayKeyQuery(sectionKey.getStepQueryString());
        for (StepsSections stepSection : sections) {
            stepSection.getKey().setStepInstance(sectionKey.getStepInstance());
            buildInitialSectionAnswers(r, upstreamAnswer, stepSection.getKey(), dependents, true);
        }
    }

    /**
     * Builds initial section answers based on the given parameters. This method initializes or retrieves
     * answers corresponding to a specific section and sets up downstream questions as needed.
     *
     * @param r              The relationship object containing information about upstream and downstream connections
     *                       between sections and steps.
     * @param upstreamAnswer The answer from the upstream question that influences the current section's
     *                       initialization.
     * @param sectionKey     The display key identifying the section being processed.
     * @param dependents     A map of dependents used for saving answers or related entities.
     * @param loadStep       A flag to indicate whether the step associated with the section should be loaded.
     */
    private void buildInitialSectionAnswers(Relationship r, Answer upstreamAnswer, DisplayKey sectionKey,
                                            HashMap<Integer, Dependent> dependents, boolean loadStep) {

        // See if we already have a sectionAnswer.
        Answer sectionAanswer = getAnswerByDisplayKey(upstreamAnswer.respondentId, sectionKey.getValue(), true);

        // now find the section
        Section section;
        if (sectionAanswer == null) {
            if (r.downstreamStep == null) {
                // use the current step
                section = getSectionByDisplayKey(upstreamAnswer.getKey().getSectionString());
            } else {
                if (r.downstreamSection != null) {
                    section = r.downstreamSection.section;
                } else {
                    section = getSectionByDisplayKey(sectionKey.getValue());
                }
            }
        } else {
            section = getSectionByDisplayKey(sectionKey.getSectionString());
        }

        List<Answer> answers = new ArrayList<>();

        ArrayList<SectionsQuestion> initial = getInitialSectionSectionsQuestion(upstreamAnswer.respondentId,
                sectionKey, loadStep);
        if (!initial.isEmpty()) {
            saveAnswer(new Answer(sectionKey, null, section.name, upstreamAnswer.respondentId), dependents);

            Answer answer;
            for (SectionsQuestion sectionsQuestion : initial) {
                DisplayKey dkey = new DisplayKey(sectionKey.getValue());
                dkey.setQuestion(sectionsQuestion.displayOrder);
                answer = new Answer(dkey, sectionsQuestion, sectionsQuestion.question.text,
                        upstreamAnswer.respondentId, sectionsQuestion.question.defaultValue);
                answer = saveAnswer(answer, dependents);
                // build the downstream answers. they can be triggered by
                // IF_EXISTS
                buildDownstreamQuestions(answer);
                answers.add(answer);
            }
        }
    }

    /**
     * Builds and manages repeated answers based on the provided upstream answer and downstream question.
     * It either retrieves or creates new answers to match the required repeat count specified in the upstream answer.
     * The method updates or generates answers while ensuring the relationship constraints between survey components.
     *
     * @param respondentId       The ID of the respondent for whom the answers are being built.
     * @param upstreamAnswer     The upstream answer which determines the number of repeated answers to be created.
     * @param downstreamQuestion The downstream question for which repeated answers will be associated.
     * @param relationship       The relationship details between the survey steps, sections, and questions.
     * @param dependents         A map of dependents that may require updates or are affected by the creation of new answers.
     */
    private void buildRepeatedAnswers(Integer respondentId, Answer upstreamAnswer, SectionsQuestion downstreamQuestion,
                                      Relationship relationship, HashMap<Integer, Dependent> dependents) {
        // if they are increasing the number then there may already be some
        // answers.

        DisplayKey answerKey = new DisplayKey(upstreamAnswer.getDisplayKey());
        answerKey.setStep(getStepDisplayOrder(relationship.surveyId, relationship.downstreamStep.id));
        answerKey.setSection(getSectionDisplayOrder(relationship.surveyId, relationship.downstreamSection.id));
        answerKey.setQuestion(relationship.downstreamQuestion.displayOrder);

        List<Answer> answers = Answer.findByAnswerQueryString(respondentId, answerKey.getAnswerQueryString());

        int repeatValue = Integer.parseInt(upstreamAnswer.getTextValue());
        if (repeatValue >= answers.size()) {
            // We are adding new Answers here. These could be based on sub
            // sections or section question
            Answer answer;
            DisplayKey key = new DisplayKey(upstreamAnswer.getDisplayKey());
            if (relationship.downstreamStep != null) {
                key.setStep(getStepDisplayOrder(relationship.surveyId, relationship.downstreamStep.id));
            }

            if (relationship.downstreamSection != null) {
                //MFD this will have to be reworked ID will not work!!
                key.setSection(getSectionDisplayOrder(relationship.surveyId, relationship.downstreamSection.id));
            }
            if (downstreamQuestion.displayOrder != null) {
                key.setQuestion(downstreamQuestion.displayOrder);
            }
            DisplayKey newKey;
            for (int i = answers.size() + 1; i <= repeatValue; i++) {
                newKey = new DisplayKey(key.getValue());
                newKey.setQuestionInstance(i);
                answer = new Answer(newKey, downstreamQuestion, downstreamQuestion.question.text,
                        respondentId, downstreamQuestion.question.defaultValue);
                answer = saveAnswer(answer, dependents);
                if (answer.getTextValue() != null) {
                    buildDownstreamQuestions(answer);
                }
            }
        }
    }

    /**
     * Builds repeated sections based on the upstream answer and the relationship details.
     * If the upstream answer specifies a count greater than the existing repeated sections,
     * new repeated sections are generated, and their initial answers are populated.
     *
     * @param upstreamAnswer    The answer from the upstream section used to determine the number of repeated sections.
     * @param downstreamSection The section where the repeated sections are to be created.
     * @param relationshipId    The unique identifier of the relationship linking the upstream and downstream sections.
     * @param dependents        A map of dependents used to apply certain dependent rules while creating new sections.
     */
    private void buildRepeatedSections(Answer upstreamAnswer, Section downstreamSection, Integer relationshipId,
                                       HashMap<Integer, Dependent> dependents) {
        // if they are increasing the number then there may already be some
        // sections

        // There may be more than one relationship but only one with action type
        // REPEAT
        Relationship r = Relationship.findById(relationshipId);
        if (r.actionType.name.equals("REPEAT")) {

            DisplayKey answerKey = new DisplayKey(upstreamAnswer.getDisplayKey());
            //MFD this will have to be reworked ID will not work!!
            answerKey.setStep(getStepDisplayOrder(r.surveyId, r.downstreamStep.id));
            //MFD this will have to be reworked ID will not work!!
            answerKey.setSection(getSectionDisplayOrder(r.surveyId, r.downstreamSection.id));

            List<Answer> answers = Answer.findBySectionInstancesQueryString(relationshipId, answerKey);

            if (upstreamAnswer.getTextValue() != null) {
                long count = Long.parseLong(upstreamAnswer.getTextValue());
                if (count > answers.size()) {
                    DisplayKey key;
                    for (int i = answers.size(); i < count; i++) {
                        key = buildDisplayKey(upstreamAnswer, r);
                        key.setSectionInstance(i + 1);
                        saveAnswer(new Answer(key, null, downstreamSection.name, upstreamAnswer.respondentId), dependents);
                        buildInitialSectionAnswers(r, upstreamAnswer, key, dependents, false);
                    }
                }
            }
        }
    }

    /**
     * Builds a repeated step by associating an upstream answer with a downstream step
     * based on the specified relationship ID and updates the dependents map as needed.
     *
     * @param upstreamAnswer the answer object from the upstream which will be linked to the step
     * @param downstreamStep the step object in the downstream to be built or updated
     * @param relationshipId the identifier representing the relationship between the answer and step
     * @param dependents     a map of dependent objects indexed by their unique identifiers to be updated
     */
    private void buildRepeatedStep(Answer upstreamAnswer, Step downstreamStep, Integer relationshipId,
                                   HashMap<Integer, Dependent> dependents) {
        // TODO
        Log.info("buildRepeatedStep not yet implemented");
    }

    /**
     * Builds and sets the display text for the given Answer object
     * based on its associated Question, Section, or Step instances.
     * Updates placeholder tokens in the text with actual values
     * derived from the Answer's key and associated data.
     *
     * @param answer The Answer object for which the display text
     *               is to be built and saved.
     */
    private void buildDipslayText(Answer answer) {

        ArrayList<Dependent> dependents = findRelationshipsByDownstreamAnswer(answer);
        for (Dependent dep : dependents) {
            saveDependent(dep);
        }

        String text = answer.displayText;
        if (answer.question != null) {
            // use the answer text.
            text = answer.question.text;
        } else if (answer.sectionId != null) {
            // use the section name
            Section s = getSectionByDisplayKey(answer.getDisplayKey());
            if (s != null) {
                text = s.name;
            }
        } else {
            // use the step name
            Step s = getStepByDisplayKey(answer.getKey());
            if (s != null) {
                text = s.name;
            }
        }

        // Substitute the Question instances and Section Instances.
        text = text.replaceAll("\\{Q#\\}", answer.getKey().getQuestionInstance() + "");
        text = text.replaceAll("\\{S#\\}", answer.getKey().getStepInstance() + "");

        TreeMap<String, String> values;

        values = getValuesMap(answer);

        answer.displayText = replaceTokens(text, values);
    }


    /**
     * Saves an answer to the database along with its dependent entities. Handles scenarios
     * where the answer is new or involves reactivating an existing answer, as well as ensuring
     * all associated dependents are processed.
     *
     * @param answer     The answer object that needs to be saved or updated in the database.
     * @param dependents A map of dependent data associated with the answer, where the key is an integer
     *                   identifier and the value is a {@code Dependent} object.
     * @return The saved {@code Answer} object, which includes any updated properties after being persisted.
     */
    private Answer saveAnswer(Answer answer, HashMap<Integer, Dependent> dependents) {

        Answer existing = getAnswerByDisplayKey(answer.respondentId, answer.getDisplayKey(), true);
        if (existing == null) {
            // Save the answer so we have an id
            answer.persistAndFlush();
        } else {
            // They already have an ID so it must be an undelete
            answer = existing;
            answer.deleted = false;
            answer.persistAndFlush();

            List<Dependent> deps = Dependent.findByDownstream(answer.id, answer.respondentId);
            for (Dependent dependent : deps) {
                dependent.deleted = false;
                saveDependent(dependent);
            }
            if (answer.getTextValue() != null) {
                buildDownstreamQuestions(answer);
            }
        }

        if (dependents != null && !dependents.isEmpty()) {
            Dependent newDependent;
            for (Entry<Integer, Dependent> entry : dependents.entrySet()) {
                newDependent = entry.getValue().shallowCopy();
                newDependent.downstream = answer;
                if (newDependent.isComplete()
                        && newDependent.relationship.evaluateOperator(newDependent.upstream)) {
                    saveDependent(newDependent);
                }
            }
        }

        // make sure the dependents are in the database.
        if (entityManager.isJoinedToTransaction()) {
            entityManager.flush();
        }
        // The dependents need to be saved before we build the display text

        buildDipslayText(answer);

        // Save the answer again so we have the correct display text
        answer.persistAndFlush();
        return answer;
    }

    /**
     * Finds and evaluates relationships for a given downstream answer, identifying dependents
     * based on the relationships and their evaluation criteria.
     *
     * @param answer The downstream answer for which relationships are to be identified and
     *               evaluated.
     * @return An ArrayList of Dependent objects derived from relationships that meet the
     * specified evaluation criteria.
     */
    private ArrayList<Dependent> findRelationshipsByDownstreamAnswer(Answer answer) {
        ArrayList<Dependent> dependents = new ArrayList<>();

        ArrayList<Relationship> relationships = new ArrayList<>(Relationship.findRelationshipsByDownstreamAnswer(answer.respondentId, answer.id));

        // Now find the upstream Answers and evaluate the relationship.
        Answer upstream;
        for (Relationship rel : relationships) {
            upstream = getUpstreamAnswerByRelationshipId(rel.id, answer.respondentId);
            if (upstream != null) {
                if (rel.evaluateOperator(upstream)) {
                    dependents.add(new Dependent(answer.respondentId, upstream, answer, rel));
                }
            }
        }

        return dependents;
    }

    /**
     * Retrieves the upstream answer based on the provided relationship ID by querying the database.
     *
     * @param answerId     The ID of the relationship used to locate the upstream answer.
     * @param respondentId The ID of the respondent whose answer is being searched.
     * @return The upstream answer matching the provided criteria, or null if no matching answer is found.
     */
    private Answer getUpstreamAnswerByRelationshipId(int answerId, int respondentId) {

        Answer answer = null;
        String sql = "SELECT A.ID FROM survey.ANSWERS A JOIN survey.RELATIONSHIPS R ON A.STEP = R.UPSTREAM_STEP_ID AND A.SECTION_QUESTION_ID = R.UPSTREAM_SQ_ID WHERE A.RESPONDENT_ID = "
                + respondentId + " AND R.id = " + answerId + " order by A.DISPLAY_KEY";

        //  entityManager.joinTransaction();
        Query q = entityManager.createNativeQuery(sql);

        @SuppressWarnings("unchecked")
        List<Object[]> rs = q.getResultList();
        for (Object record : rs) {
            try {
                answer = Answer.findById(record);
            } catch (Exception e) {
                // no value yet.
            }
        }
        return answer;
    }

    /**
     * Saves a dependent entity to the database. If the entity already exists in the database,
     * it is updated; otherwise, a new entity is persisted. The method verifies if a dependent
     * with the provided characteristics already exists to avoid duplication.
     *
     * @param dependent the dependent entity to be saved. This includes properties such as
     *                  respondentId, upstream, downstream, and relationship, which are used
     *                  to determine the uniqueness of the entity.
     */
    private void saveDependent(Dependent dependent) {

        // if this has an ID it came from the database.
        if (dependent.id != null) {
            dependent.persistAndFlush();
            return;
        }

        Dependent existing = null;
        try {
            existing = Dependent.findUnique(dependent.respondentId, dependent.upstream.id, dependent.downstream.id, dependent.relationship.id);
            // Found one so we do not need to create another.
        } catch (Exception e) {
            // most likely no result found.
        }

        if (existing == null) {
            // Dependent passed in is not in the database and we didn't find an existing one.
            // Save the entity passed in.
            dependent.persistAndFlush();
        }
    }

    /**
     * Deletes downstream answers based on the evaluated conditions of a given upstream answer.
     * The method recursively processes downstream relationships and determines the appropriate
     * actions (deletion or updates) for each relationship type.
     *
     * @param respondentId   The identifier of the respondent for whom the downstream answers
     *                       are being evaluated and deleted.
     * @param upstreamAnswer The upstream answer whose relationships are evaluated to determine
     *                       the impact on downstream answers.
     * @param rootAnswerId   The ID of the root answer that initiated the delete operation, used
     *                       to distinguish between recursive and initial calls.
     */
    public void deleteDownstreamAnswers(Integer respondentId, Answer upstreamAnswer, int rootAnswerId) {

        // Find all the downstream relationships
        List<Dependent> dependents = findDependentsByUpstreamAnswer(upstreamAnswer);

        for (Dependent dependent : dependents) {
            // What type of Action do we need to take?
            switch (dependent.relationship.actionType.name) {
                case "SHOW":
                case "EXISTS":
                    if (upstreamAnswer.id == rootAnswerId) {
                        // If this is the root answer and the relationship
                        // evaluates to false then delete the answers.
                        if (!dependent.relationship.evaluateOperator(upstreamAnswer)) {
                            // Delete all downstream values;
                            deleteAnswers(dependent.downstream, rootAnswerId);
                        }
                    } else {
                        // This was passed as a recursive call
                        // The root answer is causing the delete.
                        deleteAnswers(dependent.downstream, rootAnswerId);
                    }
                    break;
                case "REPEAT":
                    // If this is the root answer then they are altering it we
                    // may have to only remove some of the downstream elements.
                    if (upstreamAnswer.id == rootAnswerId) {
                        if (dependent.relationship.downstreamQuestion != null
                                && !dependent.upstream.getTextValue().isBlank()) {
                            if (Integer.parseInt(dependent.upstream.getTextValue()) < dependent.downstream.question_instance) {
                                deleteAnswers(dependent.downstream, rootAnswerId);
                            }
                        } else {
                            if (!upstreamAnswer.getTextValue().isBlank()) {
                                deleteSomeDownstreamAnswers(respondentId,
                                        Integer.valueOf(upstreamAnswer.getTextValue()),
                                        dependent.relationship.downstreamSection.getKey().getValue(), rootAnswerId);
                            }
                        }
                    } else {
                        // If it is not the root answer it is being deleted upstream
                        // and we will have to remove all elements.
                        deleteAnswers(dependent.downstream, rootAnswerId);
                    }
                    break;
                case "TEXT":
                    dependent.deleted = true;
                    dependent.persistAndFlush();
                    break;
            }
        }
    }

    /**
     * Deletes specific downstream section answers based on the provided criteria.
     *
     * @param respondentId  the unique identifier of the respondent
     * @param upstreamKey   the key associated with the upstream section
     * @param instances     the maximum number of section instances to retain
     * @param downstreamKey the key associated with the downstream section
     * @param rootAnswerId  the identifier for the root answer to associate deletions
     */
    private void deleteSomeDownstreamSectionAnswers(Integer respondentId, String upstreamKey, Integer instances,
                                                    DisplayKey downstreamKey, int rootAnswerId) {
        List<Answer> relationships = Answer.findByAnswerQueryString(respondentId, downstreamKey.getSectionQueryString());
        for (Answer answer : relationships) {
            if (answer.sectionInstance > instances - 1) {
                deleteAnswers(answer, rootAnswerId);
            }
        }
    }

    /**
     * Deletes answers recursively, including downstream answers and handling
     * specific types of answers (question, section, or step).
     *
     * @param deletableAnswer The answer object to be deleted, containing details required
     *                        for identifying and processing the deletion.
     * @param rootAnswerId    The identifier of the root answer associated with the
     *                        hierarchical deletion process.
     */
    private void deleteAnswers(Answer deletableAnswer, int rootAnswerId) {
        // Recursive function.
        deleteDownstreamAnswers(deletableAnswer.respondentId, deletableAnswer, rootAnswerId);
        // Is this a question, section or step?
        if (deletableAnswer.getKey().getQuestion() != 0) {
            // question we will remove it at the end of this section.
        } else if (deletableAnswer.getKey().getSection() != 0) {
            deleteSectionAnswers(deletableAnswer, rootAnswerId);
        } else if (deletableAnswer.getKey().getStep() != 0) {
            deleteStepAnswers(deletableAnswer, rootAnswerId);
        }

        markAnswerAndDependentsAsDeleted(deletableAnswer);
    }

    /**
     * Marks the provided answer and all its dependent entities as deleted.
     * Sets the `deleted` flag to true for the given answer and its dependents,
     * and persists the changes to the database.
     *
     * @param answer the answer entity to be marked as deleted, along with its dependents
     */
    private void markAnswerAndDependentsAsDeleted(Answer answer) {

        answer.deleted = true;
        answer.persistAndFlush();

        List<Dependent> deps = Dependent.findByDownstream(answer.respondentId, answer.id);
        for (Dependent dependent : deps) {
            dependent.deleted = true;
            dependent.persistAndFlush();
        }
    }

    /**
     * Deletes all answers within a specific section based on the provided upstream answer
     * and the root answer identifier. It fetches relevant answers by section query string
     * and processes each to remove associated downstream answers.
     *
     * @param upstreamAnswer The upstream Answer object containing the respondent ID and section query string.
     * @param rootAnswerId   The ID of the root answer associated with the answer section being deleted.
     */
    private void deleteSectionAnswers(Answer upstreamAnswer, int rootAnswerId) {
        List<Answer> answers = Answer.findByAnswerQueryString(upstreamAnswer.respondentId, upstreamAnswer.getKey().getSectionQueryString());
        for (Answer answer : answers) {
            deleteDownstreamAnswers(answer.respondentId, answer, rootAnswerId);
        }
    }

    /**
     * Deletes all step answers associated with the given upstream answer and root answer ID.
     * Iterates through the list of matching answers based on the keys derived from the upstream answer
     * and performs recursive deletion of downstream answers.
     *
     * @param upstreamAnswer The Answer object representing the upstream answer.
     * @param rootAnswerId   The ID of the root answer to maintain deletion context.
     */
    private void deleteStepAnswers(Answer upstreamAnswer, int rootAnswerId) {
        List<Answer> answers = Answer.findByAnswerQueryString(upstreamAnswer.respondentId, upstreamAnswer.getKey().getStepQueryString());
        for (Answer answer : answers) {
            deleteDownstreamAnswers(answer.respondentId, answer, rootAnswerId);
        }
    }

    /**
     * Deletes downstream answers based on provided parameters and conditions.
     *
     * @param respondentId  the ID of the respondent whose answers are to be processed
     * @param instances     the number of instances used to determine which answers to delete
     * @param downstreamKey the key representing the downstream answers to be deleted
     * @param rootAnswerId  the ID of the root answer associated with the downstream answers
     */
    private void deleteSomeDownstreamAnswers(Integer respondentId, Integer instances,
                                             String downstreamKey, int rootAnswerId) {
        DisplayKey key = new DisplayKey(downstreamKey);
        List<Answer> relationships = Answer.findByAnswerQueryString(respondentId, key.getAnswerQueryString());
        for (Answer answer : relationships) {
            if (answer.question_instance > instances - 1) {
                deleteAnswers(answer, rootAnswerId);
            }
        }
    }

    /**
     * Deletes all downstream answers associated with the provided upstream answer
     * recursively by marking them and their dependents as deleted.
     *
     * @param respondentId the identifier of the respondent whose answers are to be processed
     * @param upstream     the upstream answer from which to determine downstream dependents
     */
    private void deleteAllDownstreamAnswers(Integer respondentId, Answer upstream) {

        // This function needs to loop through all downstream dependents.
        List<Dependent> dependents = findDependentsByUpstreamAnswer(upstream);

        Answer deletableAnswer;
        // Now loop through the dependents and delete their dependents.
        for (Dependent dependent : dependents) {
            deletableAnswer = dependent.downstream;

            markAnswerAndDependentsAsDeleted(deletableAnswer);
        }
    }

    /**
     * Checks if all relationships associated with the given relationship object are satisfied
     * based on the provided answer and updates the dependents map with relevant dependency information.
     * <p>
     * Relationships could be associated with downstream questions, sections, or steps.
     * The method evaluates whether the conditions of these relationships are met.
     *
     * @param relationship The relationship object to check for satisfaction.
     * @param answer       The answer object used for evaluating the relationship conditions.
     * @param dependents   A map of dependents to be updated with any dependencies found during evaluation.
     *                     Keys are relationship IDs, and values are Dependent objects containing the dependency data.
     * @return {@code true} if all relationships are satisfied; {@code false} otherwise.
     */
    private boolean allRelationshipsSatisfide(Relationship relationship, Answer answer,
                                              HashMap<Integer, Dependent> dependents) {

        Log.info("All relationships satisfide?");
        // Question or Section Step?
        if (relationship.downstreamQuestion != null) {
            // Questions
            List<Relationship> relationships = Relationship.findByDownstream_SQ_ID(relationship.surveyId, relationship.downstreamQuestion.id);
            for (Relationship r : relationships) {
                // Find all upstreamAnswers for this relationship
                Answer upstreamAnswer = getUpstreamAnswer(r, answer);
                if (!r.evaluateOperator(upstreamAnswer)) {
                    Log.info("relationships " + r.id + " not satisfied.");
                    return false;
                }
                dependents.put(r.id, new Dependent(answer.respondentId, upstreamAnswer, null, r));
            }
        } else if (relationship.downstreamSection != null) {
            // Section
            int stepId;
            if (relationship.upstreamStep == null) {
                stepId = answer.stepId;
            } else {
                stepId = relationship.upstreamStep.id;
            }
            List<Relationship> relationships = Relationship.findByDownstream_S_ID(relationship.surveyId, relationship.downstreamSection.id, stepId);
            for (Relationship r : relationships) {
                Answer upstreamAnswer = getUpstreamAnswer(r, answer);
                if (!r.evaluateOperator(upstreamAnswer)) {
                    Log.info("relationships " + r.id + " not satisfied.");
                    return false;
                }
                dependents.put(r.id, new Dependent(answer.respondentId, upstreamAnswer, null, r));
            }
        } else {
            // Step
            int stepId;
            if (relationship.upstreamStep == null) {
                stepId = answer.stepId;
            } else {
                stepId = relationship.upstreamStep.id;
            }
            List<Relationship> relationships = Relationship.findByDownstream_Step_ID(relationship.surveyId, relationship.downstreamStep.id, stepId);
            for (Relationship r : relationships) {
                // Answer upstreamAnswer = getUpstreamAnswer(r, answer);
                if (!r.evaluateOperator(answer)) {
                    Log.info("relationships " + r.id + " not satisfied.");
                    return false;
                }
                dependents.put(r.id, new Dependent(answer.respondentId, answer, null, r));
            }
        }
        Log.info("All relationships satisfide = true");
        return true;
    }

    /**
     * Retrieves the upstream answer for a given relationship and answer.
     *
     * @param r The relationship object used to specify the context for finding the upstream answer.
     * @param a The answer object containing the details required for parameter binding in the query.
     * @return The upstream answer if found, or null if no answer is associated with the provided relationship and answer.
     */
    private Answer getUpstreamAnswer(Relationship r, Answer a) {

        String sql = "SELECT a.ID FROM survey.RELATIONSHIPS r join survey.SECTIONS_QUESTIONS sq on r.UPSTREAM_SQ_ID = sq.id "
                + "join survey.QUESTIONS q on sq.QUESTION_ID = q.ID left join survey.ANSWERS a on q.ID = a.QUESTION_ID  "
                + "WHERE a.deleted = false and a.RESPONDENT_ID = :respondentId and r.id = :relationshioId "
                + "and a.step = :stepId and a.STEP_INSTANCE = :stepInstance and a.section = :sectionId "
                + "and a.SECTION_INSTANCE = :sectionInstance order by a.display_key";
        //entityManager.joinTransaction();
        Query q = entityManager.createNativeQuery(sql);
        q.setParameter("relationshioId", r.id);
        q.setParameter("respondentId", a.respondentId);
        q.setParameter("stepId", a.stepId);
        q.setParameter("stepInstance", a.stepInstance);
        q.setParameter("sectionId", a.sectionId);
        q.setParameter("sectionInstance", a.sectionInstance);

        @SuppressWarnings("unchecked")
        List<Object[]> rs = q.getResultList();
        Integer id;
        Answer answer = null;
        for (Object record : rs) {
            id = (Integer) record;
            try {
                answer = entityManager.find(Answer.class, id);
            } catch (Exception e) {
                // no value yet.
            }
        }
        return answer;
    }


    /**
     * Retrieves a map of key-value pairs based on the provided Answer object.
     * Combines step, section, and question key-value pairs into a single TreeMap.
     *
     * @param answer the Answer object used to extract key-value pairs
     * @return a TreeMap containing the combined key-value pairs from step, section, and question
     */
    private TreeMap<String, String> getValuesMap(Answer answer) {

        TreeMap<String, String> values = getStepKeyValues(answer);
        values.putAll(getSectionKeyValues(answer));
        values.putAll(getQuestionKeyValues(answer));

        return values;
    }

    /**
     * Retrieves a sorted map of step key-value pairs associated with a given answer.
     *
     * @param answer the answer object containing the key and respondent details to fetch the step key-value pairs
     * @return a TreeMap containing the key-value pairs for the specified step, sorted by keys
     */
    private TreeMap<String, String> getStepKeyValues(Answer answer) {
        DisplayKey key = new DisplayKey(answer.getKey().getStepString());

        int stepID = getAnswerIdByDisplayKey(answer.respondentId, key.getValue());
        return getKeyValues(answer.respondentId, stepID);
    }

    /**
     * Retrieves a TreeMap containing key-value pairs representing section information
     * based on the given answer and its associated display key.
     *
     * @param answer the Answer object containing the respondent information and display key
     * @return a TreeMap with keys and values representing the section data
     */
    private TreeMap<String, String> getSectionKeyValues(Answer answer) {
        DisplayKey key = new DisplayKey(answer.getDisplayKey());
        key.setQuestionInstance(0);
        key.setQuestion(0);
        int sectionId = getAnswerIdByDisplayKey(answer.respondentId, key.getValue());
        return getKeyValues(answer.respondentId, sectionId);
    }

    /**
     * Retrieves a TreeMap containing key-value pairs associated with a question,
     * based on the given Answer object.
     *
     * @param answer The Answer object containing the respondent ID and answer ID
     *               to be used for retrieving the key-value pairs.
     * @return A TreeMap where the keys and values correspond to data derived
     * from the provided Answer object.
     */
    private TreeMap<String, String> getQuestionKeyValues(Answer answer) {
        return getKeyValues(answer.respondentId, answer.id);
    }

    /**
     * Retrieves the ID of an answer based on the given respondent ID and display key.
     *
     * @param respondentId the unique identifier of the respondent
     * @param displaykey   the display key associated with the answer
     * @return the ID of the answer if found, or 0 if no matching answer is found
     */
    private int getAnswerIdByDisplayKey(int respondentId, String displaykey) {
        Answer a = Answer.findByDisplayKeyAll(respondentId, displaykey);
        if (a != null) {
            return a.id;
        } else {
            return 0;
        }
    }

    /**
     * Retrieves a mapping of key-value pairs for dependents associated with a given
     * respondent and downstream ID. The method processes dependents to extract
     * relationship tokens and values based on question types and upstream configurations.
     *
     * @param respondentId The ID of the respondent for whom the dependents are being retrieved.
     * @param downstreamId The ID associated with the downstream to filter dependents.
     * @return A TreeMap containing key-value pairs where keys are relationship tokens and
     * values are derived based on dependent configurations and upstream data.
     */
    private TreeMap<String, String> getKeyValues(int respondentId, int downstreamId) {

        TreeMap<String, String> values = new TreeMap<>();
        try {
            List<Dependent> dependents = Dependent.findByDownstream(respondentId, downstreamId);
            String key;
            String value;
            // TODO GET THE DEFAULT VALUES
            for (Dependent dependent : dependents) {
                value = null;
                if (dependent.relationship.token != null
                        && !dependent.relationship.token.isEmpty()) {
                    key = dependent.relationship.token;
                    switch (dependent.upstream.question.questionType.name) {
                        case "CHECKBOX":
                        case "DROPDOWN":
                        case "HTML":
                        case "NUMBER":
                        case "RADIO":
                            if (dependent.relationship.defaultUpstreamValue != null) {
                                value = dependent.relationship.defaultUpstreamValue;
                            } else if (dependent.upstream.getTextValue() != null) {
                                value = dependent.upstream.getTextValue();
                            }
                            break;
                        case "TEXT":
                        case "DATE":
                            if (dependent.upstream.getTextValue() != null) {
                                value = dependent.upstream.getTextValue();
                            }
                            break;
                    }
                    if (value != null) {
                        values.put(key, value);
                    }
                }
            }
        } catch (Exception e) {
            Log.error("Error getting dependents with downstream id " + downstreamId);
        }
        return values;
    }

    /**
     * Builds and returns a list of navigation items based on answers retrieved
     * for a given respondent from the database.
     * <p>
     * The method queries the database to retrieve answers associated with
     * a specific respondent ID and constructs navigation items. It sets up
     * the references to the previous and next navigation paths for each item.
     *
     * @param respondentId The ID of the respondent whose navigation items
     *                     are to be built.
     * @return A list of NavigationItem objects representing the navigation paths
     * for the specified respondent.
     */
    private ArrayList<NavigationItem> buildNavItems(int respondentId) {

        String pathSQL = "SELECT a.display_text, a.display_key" + " FROM survey.answers a" + " WHERE a.deleted = false"
                + " AND a.respondent_id = :respondentId" + " AND a.question_id is null" + " AND a.section != 0"
                + " ORDER BY a.display_key";

        ArrayList<NavigationItem> paths = new ArrayList<>();

        //entityManager.joinTransaction();
        Query q = entityManager.createNativeQuery(pathSQL);
        q.setParameter("respondentId", respondentId);

        @SuppressWarnings("unchecked")
        List<Object[]> rs = q.getResultList();

        NavigationItem navItem;
        String name;
        String path;
        String next;
        String previous = null;
        for (Object[] record : rs) {
            name = (String) record[0];
            path = (String) record[1];

            navItem = new NavigationItem(name, false, path, null, previous);
            paths.add(navItem);
            previous = path;
        }

        // Set the next paths
        next = null;
        for (int i = paths.size() - 1; i > -1; i--) {
            navItem = paths.get(i);
            navItem.setNext(next);
            next = navItem.getPath();
        }
        return paths;

    }

    /**
     * Removes all deleted dependents and answers associated with the given respondent ID
     * from the database. This method executes two database queries to permanently delete
     * the entries marked as deleted for the specified respondent.
     *
     * @param respondentId the ID of the respondent whose deleted dependents and answers
     *                     should be purged from the database
     */
    @Transactional
    public void removeDeleted(Integer respondentId) {
        // this function will purge the deleted answers.
        Query purgeDeleted = entityManager.createNativeQuery("DELETE FROM survey.dependents d where d.deleted = true and d.respondent_id = :respondentId");
        purgeDeleted.setParameter("respondentId", respondentId);
        purgeDeleted.executeUpdate();

        purgeDeleted = entityManager.createNativeQuery("DELETE FROM survey.answers a WHERE a.deleted = true and a.respondent_id = :respondentId");
        purgeDeleted.setParameter("respondentId", respondentId);
        purgeDeleted.executeUpdate();
    }
}
