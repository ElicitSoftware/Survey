package com.elicitsoftware;

/*-
 * ***LICENSE_START***
 * Elicit Survey
 * %%
 * Copyright (C) 2025 - 2026 The Regents of the University of Michigan - Rogel Cancer Center
 * %%
 * PolyForm Noncommercial License 1.0.0
 * <https://polyformproject.org/licenses/noncommercial/1.0.0>
 * ***LICENSE_END***
 */

import jakarta.persistence.EntityManager;

/**
 * The football-satisfaction survey in miniature, for the tests of UC-002 A3b (a question or section
 * repeated once per selected item).
 * <p>
 * Step "Games attended" asks which games the respondent went to ({@code MULTI_SELECT} over Western
 * Michigan, Oklahoma, Texas A&amp;M and Iowa) and who came along ({@code CHECKBOX_GROUP} over
 * spouse, child and friend). Step "Game" holds the section "Michigan vs. {<GAME>|this game}",
 * repeated per game, whose first question carries a second token filled by a Text rule from the
 * same multi-select and reveals a follow-up question when answered "yes". The companion question
 * repeats a name question in its own section.
 * <p>
 * Surrogate ids are 93xx and durable ids 83xx, as in {@link QuestionManagerStepIdOrderTest}, so no
 * id space coincides with a display order.
 */
public final class RepeatPerItemFixture {

    private RepeatPerItemFixture() {
    }

    public static final int GROUP_GAMES = 9301, GROUP_GAMES_DURABLE = 8301;
    public static final int GROUP_WITH = 9302, GROUP_WITH_DURABLE = 8302;
    public static final int STEP_ONE = 9361, STEP_ONE_DURABLE = 8361;
    public static final int STEP_TWO = 9362, STEP_TWO_DURABLE = 8362;
    public static final int SECTION_GAMES = 9371, SECTION_GAMES_DURABLE = 8371;
    public static final int SECTION_GAME = 9372, SECTION_GAME_DURABLE = 8372;
    public static final int SS_ONE = 9381, SS_ONE_DURABLE = 8381;
    public static final int SS_TWO = 9382, SS_TWO_DURABLE = 8382;
    public static final int Q_GAMES = 9391, Q_GAMES_DURABLE = 8391;
    public static final int Q_WITH = 9392, Q_WITH_DURABLE = 8392;
    public static final int Q_NAME = 9393, Q_NAME_DURABLE = 8393;
    public static final int Q_BOUGHT = 9394, Q_BOUGHT_DURABLE = 8394;
    public static final int Q_FOOD = 9395, Q_FOOD_DURABLE = 8395;
    public static final int SQ_GAMES = 9401, SQ_GAMES_DURABLE = 8401;
    public static final int SQ_WITH = 9402, SQ_WITH_DURABLE = 8402;
    public static final int SQ_NAME = 9403, SQ_NAME_DURABLE = 8403;
    public static final int SQ_BOUGHT = 9404, SQ_BOUGHT_DURABLE = 8404;
    public static final int SQ_FOOD = 9405, SQ_FOOD_DURABLE = 8405;

    public static final int TYPE_TEXT = 8, TYPE_MULTI_SELECT = 10, TYPE_CHECKBOX_GROUP = 11;
    public static final int OP_EQUAL = 3, OP_FIELD_EXIST = 5;
    public static final int ACTION_SHOW = 1, ACTION_REPEAT = 2, ACTION_TEXT = 3;

    private static int exec(EntityManager em, String sql, Object... params) {
        var q = em.createNativeQuery(sql);
        for (int i = 0; i < params.length; i++) {
            q.setParameter(i + 1, params[i]);
        }
        return q.executeUpdate();
    }

    public static String key(int surveyId, String rest) {
        return String.format("%04d-%s", surveyId, rest);
    }

    /** Builds the survey in the caller's transaction and returns its id. */
    public static int build(EntityManager em) {
        int surveyId = ((Number) em.createNativeQuery(
                "INSERT INTO survey.surveys(id, name, display_order, title, description, initial_display_key, post_survey_url, survey_key) "
                        + "VALUES (NEXTVAL('survey.surveys_seq'), 'RepeatPerItemFixture', 904, 'Repeat per item fixture', "
                        + "'A section and a question repeated per selected item', NULL, NULL, gen_random_uuid()) RETURNING id")
                .getSingleResult()).intValue();

        String groups = "INSERT INTO survey.select_groups(id, select_group_id, survey_id, name, description, data_type, select_group_key) "
                + "VALUES (?1, ?2, ?3, ?4, ?5, 'Text', gen_random_uuid())";
        exec(em, groups, GROUP_GAMES, GROUP_GAMES_DURABLE, surveyId, "Games", "The games of the season");
        exec(em, groups, GROUP_WITH, GROUP_WITH_DURABLE, surveyId, "Companions", "Who came along");

        String items = "INSERT INTO survey.select_items(id, select_item_id, survey_id, select_group_id, display_text, display_order, coded_value, select_item_key) "
                + "VALUES (NEXTVAL('survey.select_items_seq'), NEXTVAL('survey.select_items_durable_seq'), ?1, ?2, ?3, ?4, ?5, gen_random_uuid())";
        exec(em, items, surveyId, GROUP_GAMES_DURABLE, "Western Michigan", 1, "G01");
        exec(em, items, surveyId, GROUP_GAMES_DURABLE, "Oklahoma", 2, "G02");
        exec(em, items, surveyId, GROUP_GAMES_DURABLE, "Texas A&M", 3, "G03");
        exec(em, items, surveyId, GROUP_GAMES_DURABLE, "Iowa", 4, "G04");
        exec(em, items, surveyId, GROUP_WITH_DURABLE, "your spouse", 1, "SPOUSE");
        exec(em, items, surveyId, GROUP_WITH_DURABLE, "your child", 2, "CHILD");
        exec(em, items, surveyId, GROUP_WITH_DURABLE, "your friend", 3, "FRIEND");

        String steps = "INSERT INTO survey.steps(id, step_id, survey_id, display_order, name, dimension_name, description, step_key) "
                + "VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, gen_random_uuid())";
        exec(em, steps, STEP_ONE, STEP_ONE_DURABLE, surveyId, 1, "Games attended", "PerItemGames", "Which games, and with whom");
        exec(em, steps, STEP_TWO, STEP_TWO_DURABLE, surveyId, 2, "Game", "PerItemGame", "One section per game attended");

        String sections = "INSERT INTO survey.sections(id, section_id, survey_id, display_order, name, dimension_name, description, section_key) "
                + "VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, gen_random_uuid())";
        exec(em, sections, SECTION_GAMES, SECTION_GAMES_DURABLE, surveyId, 1, "Your games", "PerItemYourGames", "First step's section");
        exec(em, sections, SECTION_GAME, SECTION_GAME_DURABLE, surveyId, 2, "Michigan vs. {<GAME>|this game}", "PerItemVisit", "Repeated per game");

        String ss = "INSERT INTO survey.steps_sections(id, steps_sections_id, survey_id, step_id, step_display_order, section_id, section_display_order, display_key, steps_sections_key) "
                + "VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, gen_random_uuid())";
        exec(em, ss, SS_ONE, SS_ONE_DURABLE, surveyId, STEP_ONE_DURABLE, 1, SECTION_GAMES_DURABLE, 1, key(surveyId, "0001-0000-0001-0000-0000-0000"));
        exec(em, ss, SS_TWO, SS_TWO_DURABLE, surveyId, STEP_TWO_DURABLE, 2, SECTION_GAME_DURABLE, 1, key(surveyId, "0002-0000-0001-0000-0000-0000"));

        exec(em, "UPDATE survey.surveys SET initial_display_key = ?1 WHERE id = ?2", key(surveyId, "0001-0000-0001-0000-0000-0000"), surveyId);

        String questions = "INSERT INTO survey.questions(id, question_id, survey_id, type_id, text, short_text, tool_tip, required, "
                + "min_value, max_value, validation_text, select_group_id, mask, placeholder, default_value, question_key) "
                + "VALUES (?1, ?2, ?3, ?4, ?5, ?6, '', false, NULL, NULL, NULL, ?7, NULL, NULL, NULL, gen_random_uuid())";
        exec(em, questions, Q_GAMES, Q_GAMES_DURABLE, surveyId, TYPE_MULTI_SELECT, "Which games did you attend?", "Q_GAMES", GROUP_GAMES_DURABLE);
        exec(em, questions, Q_WITH, Q_WITH_DURABLE, surveyId, TYPE_CHECKBOX_GROUP, "Who came with you?", "Q_WITH", GROUP_WITH_DURABLE);
        exec(em, questions, Q_NAME, Q_NAME_DURABLE, surveyId, TYPE_TEXT, "What is the name of {<WHO>|your companion}?", "Q_NAME", null);
        exec(em, questions, Q_BOUGHT, Q_BOUGHT_DURABLE, surveyId, TYPE_TEXT, "Did you buy anything at the {<OPP>|last} game?", "Q_BOUGHT", null);
        exec(em, questions, Q_FOOD, Q_FOOD_DURABLE, surveyId, TYPE_TEXT, "How was the food at the {<GAME>|last} game?", "Q_FOOD", null);

        String sq = "INSERT INTO survey.sections_questions(id, sections_question_id, survey_id, question_id, section_id, display_order, sections_question_key) "
                + "VALUES (?1, ?2, ?3, ?4, ?5, ?6, gen_random_uuid())";
        exec(em, sq, SQ_GAMES, SQ_GAMES_DURABLE, surveyId, Q_GAMES_DURABLE, SECTION_GAMES_DURABLE, 1);
        exec(em, sq, SQ_WITH, SQ_WITH_DURABLE, surveyId, Q_WITH_DURABLE, SECTION_GAMES_DURABLE, 2);
        exec(em, sq, SQ_NAME, SQ_NAME_DURABLE, surveyId, Q_NAME_DURABLE, SECTION_GAMES_DURABLE, 3);
        exec(em, sq, SQ_BOUGHT, SQ_BOUGHT_DURABLE, surveyId, Q_BOUGHT_DURABLE, SECTION_GAME_DURABLE, 1);
        exec(em, sq, SQ_FOOD, SQ_FOOD_DURABLE, surveyId, Q_FOOD_DURABLE, SECTION_GAME_DURABLE, 2);

        String rel = "INSERT INTO survey.relationships(id, survey_id, upstream_step_id, upstream_sq_id, "
                + "downstream_step_id, downstream_ss_id, downstream_sq_id, operator_id, action_id, description, token, "
                + "reference_value, default_upstream_value, relationship_key) "
                + "VALUES (NEXTVAL('survey.relationships_seq'), ?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11, ?12, gen_random_uuid())";
        // REPEAT per item: one "Michigan vs." section per game selected, carrying the game as GAME.
        exec(em, rel, surveyId, STEP_ONE_DURABLE, SQ_GAMES_DURABLE, STEP_TWO_DURABLE, SS_TWO_DURABLE, null, OP_FIELD_EXIST, ACTION_REPEAT,
                "Repeat the game section per game attended", "GAME", "", "");
        // TEXT from the same multi-select onto the repeated section. Its constant is what a coded
        // answer would normally yield (BR-010); inside an instance the item's text outranks it.
        exec(em, rel, surveyId, STEP_ONE_DURABLE, SQ_GAMES_DURABLE, STEP_TWO_DURABLE, SS_TWO_DURABLE, null, OP_FIELD_EXIST, ACTION_TEXT,
                "Name the game in the concession question", "OPP", "", "some");
        // SHOW inside the repeated section: a follow-up revealed within one instance.
        exec(em, rel, surveyId, null, SQ_BOUGHT_DURABLE, null, null, SQ_FOOD_DURABLE, OP_EQUAL, ACTION_SHOW,
                "Ask about the food when something was bought", null, "yes", "");
        // Question-only REPEAT per item: one name question per companion selected.
        exec(em, rel, surveyId, null, SQ_WITH_DURABLE, null, null, SQ_NAME_DURABLE, OP_FIELD_EXIST, ACTION_REPEAT,
                "Ask a name per companion", "WHO", "", "");
        return surveyId;
    }
}
