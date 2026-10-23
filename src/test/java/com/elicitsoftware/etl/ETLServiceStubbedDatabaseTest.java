package com.elicitsoftware.etl;

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

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The reporting ETL's sequencing, result reporting and SQL assembly without a database, against
 * a {@link ScriptedEntityManager}: the build of a survey's star (UC-008 steps 3-6, BR-001,
 * BR-003, BR-006, BR-007, BR-009, BR-012), the rename (UC-010 BR-001, BR-004), the drop (UC-011
 * BR-001, BR-002) and the finalize-time load of one respondent (UC-004). What these SQL
 * statements do to a real schema is covered by {@code ETLServiceTest} under Quarkus.
 * <p>
 * Every scripted failure is an {@link IllegalStateException} whose message names no transient
 * condition, so {@code DatabaseRetryUtil} fails at once instead of waiting between retries.
 */
class ETLServiceStubbedDatabaseTest {

    private static final String LIST_SURVEYS = "SELECT id, survey_key, name, report_schema FROM survey.surveys ORDER BY display_order, id";
    private static final String FIND_SURVEY = "SELECT id, survey_key, name, report_schema FROM survey.surveys WHERE survey_key = CAST(:key AS uuid)";
    private static final String FIND_SURVEY_OF_RESPONDENT = "SELECT s.id, s.survey_key, s.name, s.report_schema FROM survey.surveys s "
            + "JOIN survey.respondents r ON r.survey_id = s.id WHERE r.id = :id";
    private static final String COUNT_SURVEYS = "SELECT COUNT(*) FROM survey.surveys";
    private static final String NAME_HELD = "SELECT COUNT(*) FROM survey.surveys WHERE report_schema = :name";
    private static final String SET_SCHEMA = "UPDATE survey.surveys SET report_schema = :name WHERE id = :id";
    private static final String CLEAR_SCHEMA = "UPDATE survey.surveys SET report_schema = NULL WHERE id = :id";

    private static final String SCHEMA = "report_demo";
    private static final UUID KEY = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID QUESTION_KEY = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID ITEM_KEY = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
    private static final String NL = System.lineSeparator();

    private final ScriptedEntityManager db = new ScriptedEntityManager();

    private ETLService service() {
        ETLService service = new ETLService();
        service.entityManager = db.entityManager();
        service.REPORT_USER = "rep_user";
        service.SURVEY_USER = "srv_user";
        service.etlEnabled = true;
        service.dropEnabled = true;
        return service;
    }

    private static String in(String template) {
        return Sql.in(SCHEMA, template);
    }

    private static Object[] surveyRow(Object id, Object key, String name, String schema) {
        return new Object[]{id, key, name, schema};
    }

    /** Scripts every step of a build of survey 3 ("Demo") in {@link #SCHEMA}, with one respondent to back-fill. */
    private void scriptBuild() {
        db.update(in(Sql.UPDATE_STEPS_DIMENSION_TABLE_SQL), 3)
                .update(in(Sql.UPDATE_SECTIONS_DIMENSION_TABLE_SQL), 4)
                .list(Sql.FIND_NEW_DIMENSION_TABLES_SQL, List.of("color"))
                .list(Sql.FIND_DIMENSIONS_TO_ADD_TO_FACT_SECTIONS_TABLE, List.<Object[]>of(new Object[]{"color_key", "dim_color"}))
                .list(Sql.FIND_FACT_SECTION_JOIN_COLUMNS, List.<Object[]>of(
                        new Object[]{"step_key", "dim_step"}, new Object[]{"color_key", "dim_color"}))
                .list(in(Sql.FIND_MISSING_FACT_SECTION_RESPONDENTS), List.of(5L));
        scriptRespondent();
    }

    /** Scripts the load of respondent 5: one dimension value, one tag key and one per-item fact row. */
    private void scriptRespondent() {
        db.list(Sql.FIND_DIMENSTION_VALUES_SQL, List.<Object[]>of(new Object[]{"color", "red"}))
                .list(in(Sql.FIND_MISSING_FACT_SECTION_DIMENSIONS_SQL), List.<Object[]>of(new Object[]{"color_key", "dim_color", "red", 11L}))
                .list(in(Sql.FIND_REPEATED_ITEM_FACTS_SQL), List.<Object[]>of(
                        new Object[]{12L, QUESTION_KEY, "Which colour?", ITEM_KEY, "R", "Red", "colours", 2.5},
                        new Object[]{13L, QUESTION_KEY, "Which colour?", ITEM_KEY, "B", "Blue", "colours", null}))
                .single(in(Sql.UPSERT_DIM_QUESTION_SQL), 7L)
                .single(in(Sql.UPSERT_DIM_ITEM_SQL), 8);
    }

    // ── surveys ────────────────────────────────────────────────────────────────────────────────

    @Test
    void given_surveyRowsWithUuidAndTextKeys_when_listSurveys_then_bothMapToSurveyRefsInOrder() {
        db.list(LIST_SURVEYS, List.of(surveyRow(1, KEY, "First", "report_first"), surveyRow(2L, KEY.toString(), "Second", null)));

        List<ETLService.SurveyRef> surveys = service().listSurveys();

        assertEquals(List.of(new ETLService.SurveyRef(1, KEY, "First", "report_first"),
                new ETLService.SurveyRef(2, KEY, "Second", null)), surveys);
    }

    @Test
    void given_noSurveyWithTheKey_when_findSurvey_then_nullAndTheKeyBoundAsText() {
        assertNull(service().findSurvey(KEY));
        assertEquals(Map.of("key", KEY.toString()), db.runs(FIND_SURVEY).get(0).params());
    }

    @Test
    void given_aSurveyWithTheKey_when_findSurvey_then_itsRef() {
        db.list(FIND_SURVEY, List.<Object[]>of(surveyRow(4, KEY, "Demo", SCHEMA)));

        assertEquals(new ETLService.SurveyRef(4, KEY, "Demo", SCHEMA), service().findSurvey(KEY));
    }

    @Test
    void given_surveysInstalled_when_countSurveys_then_theCount() {
        db.single(COUNT_SURVEYS, 6L);

        assertEquals(6L, service().countSurveys());
    }

    // ── schema naming (UC-008 BR-006, UC-010 BR-004) ──────────────────────────────────────────

    @Test
    void given_anotherSurveyHoldsTheName_when_schemaNameTaken_then_trueWithoutLookingForASchema() {
        db.single(NAME_HELD, 1L);

        assertTrue(service().schemaNameTaken("report_x"));
        assertEquals(List.of(NAME_HELD), db.sqls());
        assertEquals(Map.of("name", "report_x"), db.executed.get(0).params());
    }

    @Test
    void given_aSchemaOfThatNameExists_when_schemaNameTaken_then_true() {
        db.single(Sql.SCHEMA_EXISTS_SQL, 1);

        assertTrue(service().schemaNameTaken("report_x"));
        assertEquals(List.of(NAME_HELD, Sql.SCHEMA_EXISTS_SQL), db.sqls());
        assertEquals(Map.of("name", "report_x"), db.executed.get(1).params());
    }

    @Test
    void given_theNameIsFree_when_schemaNameTaken_then_false() {
        assertFalse(service().schemaNameTaken("report_x"));
    }

    @Test
    void given_anUnbuiltSurveyWhoseSlugIsTaken_when_ensureSchema_then_theNextSuffixIsStoredAndCreated() {
        ETLService service = new ETLService() {
            @Override
            boolean schemaNameTaken(String name) {
                return name.equals("report_my_survey");
            }
        };
        service.entityManager = db.entityManager();
        service.REPORT_USER = "rep_user";
        service.SURVEY_USER = "srv_user";

        String schema = service.ensureSchema(new ETLService.SurveyRef(9, KEY, "My Survey!", null));

        assertEquals("report_my_survey_2", schema);
        ScriptedEntityManager.Executed stored = db.runs(SET_SCHEMA).get(0);
        assertEquals(Map.of("name", "report_my_survey_2", "id", 9), stored.params());
        String create = Sql.in("report_my_survey_2", Sql.CREATE_SURVEY_SCHEMA_SQL)
                .replace("<REPORT_USER>", "rep_user").replace("<SURVEY_USER>", "srv_user");
        assertEquals(List.of(SET_SCHEMA, create), db.sqls());
    }

    @Test
    void given_aBuiltSurvey_when_ensureSchema_then_itsNameIsKeptAndTheObjectsCreatedWithTheRoles() {
        String schema = service().ensureSchema(new ETLService.SurveyRef(3, KEY, "Demo", SCHEMA));

        assertEquals(SCHEMA, schema);
        String create = db.sqls().get(0);
        assertEquals(1, db.sqls().size(), "no name is assigned to a survey that has one");
        assertEquals(in(Sql.CREATE_SURVEY_SCHEMA_SQL).replace("<REPORT_USER>", "rep_user").replace("<SURVEY_USER>", "srv_user"), create);
        assertFalse(create.contains("<REPORT_USER>") || create.contains("<SURVEY_USER>") || create.contains("<SCHEMA>"));
        assertEquals("update", db.executed.get(0).how());
    }

    // ── build (UC-008) ─────────────────────────────────────────────────────────────────────────

    @Test
    void given_aBuiltSurvey_when_build_then_everyStepRunsAndTheSummaryNamesWhatEachDid() {
        scriptBuild();

        String summary = service().build(new ETLService.SurveyRef(3, KEY, "Demo", SCHEMA));

        assertEquals("Demo [report_demo]: step dimensions upserted: 3; section dimensions upserted: 4; "
                + "new Dimesions tables = [color]; fact_respondents_view: Created report_demo.fact_respondents_view; "
                + "fact_sections columns: new Dimesions tables = color_key; fact_sections_view: recreated; "
                + "respondents back-filled into fact_sections: 1", summary);
    }

    @Test
    void given_aBuiltSurvey_when_build_then_everyStatementIsBoundToTheSurveyAndItsSchema() {
        scriptBuild();

        service().build(new ETLService.SurveyRef(3, KEY, "Demo", SCHEMA));

        assertEquals(Map.of("surveyId", 3), db.runs(in(Sql.UPDATE_STEPS_DIMENSION_TABLE_SQL)).get(0).params());
        assertEquals(Map.of("surveyId", 3), db.runs(in(Sql.UPDATE_SECTIONS_DIMENSION_TABLE_SQL)).get(0).params());
        assertEquals(Map.of("surveyId", 3), db.runs(Sql.FIND_RESERVED_TAGS_SQL).get(0).params());
        assertEquals(Map.of("surveyId", 3, "schema", SCHEMA), db.runs(Sql.FIND_NEW_DIMENSION_TABLES_SQL).get(0).params());
        assertEquals(Map.of("schema", SCHEMA, "surveyId", 3), db.runs(Sql.FIND_DIMENSIONS_TO_ADD_TO_FACT_SECTIONS_TABLE).get(0).params());
        assertEquals(Map.of("schema", SCHEMA, "surveyId", 3), db.runs(Sql.FIND_FACT_SECTION_JOIN_COLUMNS).get(0).params());
        assertEquals(Map.of("surveyId", 3), db.runs(in(Sql.FIND_MISSING_FACT_SECTION_RESPONDENTS)).get(0).params());

        String dimensionTable = in(Sql.CREATE_NEW_DIMENSION_TABLE_SQL).replace("<TABLE_NAME>", "color").replace("<REPORT_USER>", "rep_user");
        assertEquals(1, db.runs(dimensionTable).size(), "dim_color is created with the report role's grant");
        String column = in(Sql.ADD_DIM_COLUMN_TO_FACT_SECTIONS_TABLE).replace("<COL>", "color_key").replace("<DIM>", "dim_color");
        assertEquals(1, db.runs(column).size(), "fact_sections gets the tag's key column");
        String respondentsView = in(Sql.CREATE_FACT_RESPONDENTS_VIEW_SQL).replace("<SURVEY_ID>", "3")
                .replace("<REPORT_USER>", "rep_user").replace("<SURVEY_USER>", "srv_user");
        assertEquals(1, db.runs(respondentsView).size(), "fact_respondents is the survey's own view");
        assertEquals(1, db.runs(in(Sql.DROP_SECTION_VIEW_SQL)).size());
        assertEquals(1, db.runs(in(Sql.INSERT_MISSING_FACT_SECTION_SQL)).size(), "the back-fill loads respondent 5");
    }

    @Test
    void given_joinColumns_when_buildFactSectionView_then_theViewSelectsEachDimensionsValueAndGrantsBothRoles() {
        db.list(Sql.FIND_FACT_SECTION_JOIN_COLUMNS, List.<Object[]>of(
                new Object[]{"step_key", "dim_step"}, new Object[]{"color_key", "dim_color"}));

        String sql = service().buildFactSectionView(SCHEMA, 3);

        String select = in(Sql.FACT_SECTION_VIEW_SELECT_SQL) + "    step_key.value as step," + NL + "    color_key.value as color," + NL;
        String expected = select.substring(0, select.length() - 2)
                + in(Sql.FACT_SECTION_VIEW_FROM_SQL)
                + in(Sql.FACT_VIEW_JOIN_CLAUSE_SQL).replace("<COL>", "step_key").replace("<DIM>", "dim_step")
                + in(Sql.FACT_VIEW_JOIN_CLAUSE_SQL).replace("<COL>", "color_key").replace("<DIM>", "dim_color")
                + "); "
                + in(Sql.FACT_SECTIONS_VIEW_GRANT_CLAUSE_SQL).replace("<REPORT_USER>", "rep_user")
                + in(Sql.FACT_SECTIONS_VIEW_GRANT_CLAUSE_SQL).replace("<REPORT_USER>", "srv_user");
        assertEquals(expected, sql);
        assertEquals(List.of(in(Sql.DROP_SECTION_VIEW_SQL), Sql.FIND_FACT_SECTION_JOIN_COLUMNS, expected), db.sqls());
        assertEquals("update", db.executed.get(2).how(), "the view is created, not only assembled");
    }

    @Test
    void given_theDropIsBlockedByAHandMadeDependency_when_buildFactSectionView_then_aMessageInsteadOfAFailure() {
        db.update(in(Sql.DROP_SECTION_VIEW_SQL), new IllegalStateException("cannot drop view because other objects depend on it"));

        String result = service().buildFactSectionView(SCHEMA, 3);

        assertEquals("There may be a dependency that can't be worked arround. You may have to delete and recreate the dependency.", result);
        assertEquals(1, db.sqls().size(), "nothing after the failed drop runs");
    }

    @Test
    void given_aJoinColumnThatIsNotAnIdentifier_when_buildFactSectionView_then_theViewIsNotCreated() {
        db.list(Sql.FIND_FACT_SECTION_JOIN_COLUMNS, List.<Object[]>of(new Object[]{"bad key; drop", "dim_step"}));

        String result = service().buildFactSectionView(SCHEMA, 3);

        assertTrue(result.startsWith("There may be a dependency"), result);
        assertEquals(2, db.sqls().size());
    }

    @Test
    void given_theViewCreateFails_when_buildFactRespondentsView_then_theCausesMessage() {
        db.on(sql -> sql.contains("fact_respondents AS ("), "update", new IllegalStateException("relation survey.respondents does not exist"));

        String result = service().buildFactRespondentsView(SCHEMA, 3);

        assertEquals("relation survey.respondents does not exist", result);
    }

    @Test
    void given_aReservedTag_when_build_then_theBuildStopsBeforeAnyColumnIsAdded() {
        db.list(Sql.FIND_RESERVED_TAGS_SQL, List.of("item", "step"));

        IllegalStateException thrown = org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> service().build(new ETLService.SurveyRef(3, KEY, "Demo", SCHEMA)));

        assertEquals("Survey Demo has reporting tag(s) [item, step] whose column name is reserved for a fixed column of "
                + "fact_sections (step, section, question, item); rename the tag.", thrown.getMessage());
        assertFalse(db.sqls().contains(Sql.FIND_NEW_DIMENSION_TABLES_SQL));
    }

    @Test
    void given_aKnownSurvey_when_rebuildReportingSchemaForItsKey_then_okWithItsSummary() {
        db.list(FIND_SURVEY, List.<Object[]>of(surveyRow(3, KEY, "Demo", SCHEMA)));
        scriptBuild();

        ETLService.RebuildResult result = service().rebuildReportingSchema(Optional.of(KEY));

        assertEquals(ETLService.RebuildStatus.OK, result.status());
        assertTrue(result.message().startsWith("Demo [report_demo]: step dimensions upserted: 3;"), result.message());
        assertFalse(db.sqls().contains(LIST_SURVEYS), "only the survey asked for is read");
    }

    @Test
    void given_noSurveyWithTheKey_when_rebuildReportingSchema_then_unknownAndNothingBuilt() {
        ETLService.RebuildResult result = service().rebuildReportingSchema(Optional.of(KEY));

        assertEquals(new ETLService.RebuildResult(ETLService.RebuildStatus.UNKNOWN, "No survey has the key " + KEY), result);
        assertEquals(List.of(FIND_SURVEY), db.sqls());
    }

    @Test
    void given_noSurveyInstalled_when_rebuildReportingSchemaForAll_then_okWithNothingToBuild() {
        ETLService.RebuildResult result = service().rebuildReportingSchema();

        assertEquals(new ETLService.RebuildResult(ETLService.RebuildStatus.OK,
                "No survey is installed (survey.surveys is empty); nothing to build."), result);
        assertEquals(List.of(LIST_SURVEYS), db.sqls());
    }

    @Test
    void given_twoSurveysThatBuild_when_rebuildReportingSchemaForAll_then_okWithBothSummaries() {
        db.list(LIST_SURVEYS, List.of(surveyRow(3, KEY, "Demo", SCHEMA), surveyRow(4, KEY, "Other", "report_other")));

        ETLService.RebuildResult result = service().rebuildReportingSchema();

        assertEquals(ETLService.RebuildStatus.OK, result.status());
        String[] parts = result.message().split(" \\| ");
        assertEquals(2, parts.length, result.message());
        assertTrue(parts[0].startsWith("Demo [report_demo]: "), parts[0]);
        assertTrue(parts[1].startsWith("Other [report_other]: "), parts[1]);
    }

    @Test
    void given_oneOfTwoSurveysFails_when_rebuildReportingSchemaForAll_then_failedNamingOnlyTheFailureAndTheOtherStillBuilt() {
        db.list(LIST_SURVEYS, List.of(surveyRow(3, KEY, "Demo", SCHEMA), surveyRow(4, KEY, "Other", "report_other")))
                .update(in(Sql.UPDATE_STEPS_DIMENSION_TABLE_SQL), new IllegalStateException("duplicate key value violates dim_step_un"));

        ETLService.RebuildResult result = service().rebuildReportingSchema();

        assertEquals(new ETLService.RebuildResult(ETLService.RebuildStatus.FAILED,
                "Demo: duplicate key value violates dim_step_un"), result);
        assertEquals(1, db.runs(Sql.in("report_other", Sql.UPDATE_SECTIONS_DIMENSION_TABLE_SQL)).size(),
                "a broken survey does not stop the next one (BR-003)");
    }

    @Test
    void given_theEtlDisabled_when_init_then_nothingIsRead() {
        ETLService service = service();
        service.etlEnabled = false;

        service.init();

        assertTrue(db.executed.isEmpty());
    }

    @Test
    void given_noSurveyInstalled_when_init_then_onlyTheSurveysAreRead() {
        service().init();

        assertEquals(List.of(LIST_SURVEYS), db.sqls());
    }

    @Test
    void given_aBrokenSurveyFirst_when_init_then_theNextSurveyIsStillBuilt() {
        db.list(LIST_SURVEYS, List.of(surveyRow(3, KEY, "Demo", SCHEMA), surveyRow(4, KEY, "Other", "report_other")))
                .update(in(Sql.UPDATE_STEPS_DIMENSION_TABLE_SQL), new IllegalStateException("broken"));

        service().init();

        assertEquals(1, db.runs(Sql.in("report_other", Sql.UPDATE_STEPS_DIMENSION_TABLE_SQL)).size());
        assertEquals(1, db.runs(Sql.in("report_other", Sql.FIND_MISSING_FACT_SECTION_RESPONDENTS)).size(),
                "the second survey's build runs to its last step");
    }

    // ── serialization (UC-008 BR-005) ─────────────────────────────────────────────────────────

    private static boolean locked(ETLService service) throws Exception {
        java.lang.reflect.Field field = ETLService.class.getDeclaredField("rebuildLock");
        field.setAccessible(true);
        return ((java.util.concurrent.locks.ReentrantLock) field.get(service)).isLocked();
    }

    @Test
    void given_anyOperationThatTakesTheLock_when_itEnds_then_theLockIsReleased() throws Exception {
        db.list(LIST_SURVEYS, List.<Object[]>of(surveyRow(3, KEY, "Demo", SCHEMA)))
                .list(FIND_SURVEY, List.<Object[]>of(surveyRow(3, KEY, "Demo", SCHEMA)));
        ETLService service = service();

        service.init();
        assertFalse(locked(service), "init");
        service.rebuildReportingSchema();
        assertFalse(locked(service), "rebuild");
        service.renameReportingSchema(KEY, "report_new");
        assertFalse(locked(service), "rename");
        service.dropReportingSchema(KEY);
        assertFalse(locked(service), "drop");
    }

    // ── rename (UC-010) ────────────────────────────────────────────────────────────────────────

    @Test
    void given_noSurveyWithTheKey_when_rename_then_unknown() {
        assertEquals(new ETLService.RenameResult(ETLService.RenameStatus.UNKNOWN, "No survey has the key " + KEY, null),
                service().renameReportingSchema(KEY, "report_new"));
    }

    @Test
    void given_anUnbuiltSurvey_when_rename_then_unbuilt() {
        db.list(FIND_SURVEY, List.<Object[]>of(surveyRow(3, KEY, "Demo", null)));

        assertEquals(new ETLService.RenameResult(ETLService.RenameStatus.UNBUILT,
                        "Survey Demo has no reporting schema yet; build it first.", null),
                service().renameReportingSchema(KEY, "report_new"));
    }

    @Test
    void given_aReservedName_when_rename_then_invalidAndTheCurrentNameKept() {
        db.list(FIND_SURVEY, List.<Object[]>of(surveyRow(3, KEY, "Demo", SCHEMA)));

        assertEquals(new ETLService.RenameResult(ETLService.RenameStatus.INVALID, "The name public is reserved.", SCHEMA),
                service().renameReportingSchema(KEY, "public"));
        assertEquals(List.of(FIND_SURVEY), db.sqls());
    }

    @Test
    void given_theCurrentName_when_rename_then_okWithoutTouchingTheSchema() {
        db.list(FIND_SURVEY, List.<Object[]>of(surveyRow(3, KEY, "Demo", SCHEMA)));

        assertEquals(new ETLService.RenameResult(ETLService.RenameStatus.OK,
                        "The reporting schema is already named report_demo.", SCHEMA),
                service().renameReportingSchema(KEY, SCHEMA));
        assertEquals(List.of(FIND_SURVEY), db.sqls());
    }

    @Test
    void given_aNameInUse_when_rename_then_takenAndTheCurrentNameKept() {
        db.list(FIND_SURVEY, List.<Object[]>of(surveyRow(3, KEY, "Demo", SCHEMA))).single(NAME_HELD, 1L);

        assertEquals(new ETLService.RenameResult(ETLService.RenameStatus.TAKEN, "The name report_new is already in use.", SCHEMA),
                service().renameReportingSchema(KEY, "report_new"));
        assertFalse(db.sqls().stream().anyMatch(sql -> sql.startsWith("ALTER SCHEMA")));
    }

    @Test
    void given_aFreeName_when_rename_then_theSchemaAndTheSurveyChangeTogether() {
        db.list(FIND_SURVEY, List.<Object[]>of(surveyRow(3, KEY, "Demo", SCHEMA)));

        ETLService.RenameResult result = service().renameReportingSchema(KEY, "report_new");

        assertEquals(new ETLService.RenameResult(ETLService.RenameStatus.OK,
                "Reporting schema of Demo renamed from report_demo to report_new.", "report_new"), result);
        assertEquals(List.of(FIND_SURVEY, NAME_HELD, Sql.SCHEMA_EXISTS_SQL, "ALTER SCHEMA report_demo RENAME TO report_new", SET_SCHEMA),
                db.sqls());
        assertEquals(Map.of("name", "report_new", "id", 3), db.runs(SET_SCHEMA).get(0).params());
    }

    @Test
    void given_theAlterFails_when_rename_then_failedWithTheRootCauseAndTheCurrentNameKept() {
        db.list(FIND_SURVEY, List.<Object[]>of(surveyRow(3, KEY, "Demo", SCHEMA)))
                .update("ALTER SCHEMA report_demo RENAME TO report_new",
                        new IllegalStateException("wrapper", new IllegalArgumentException("must be owner of schema report_demo")));

        assertEquals(new ETLService.RenameResult(ETLService.RenameStatus.FAILED, "must be owner of schema report_demo", SCHEMA),
                service().renameReportingSchema(KEY, "report_new"));
        assertFalse(db.sqls().contains(SET_SCHEMA));
    }

    // ── drop (UC-011) ──────────────────────────────────────────────────────────────────────────

    @Test
    void given_noSurveyWithTheKey_when_drop_then_unknown() {
        assertEquals(new ETLService.DropResult(ETLService.DropStatus.UNKNOWN, "No survey has the key " + KEY),
                service().dropReportingSchema(KEY));
    }

    @Test
    void given_anUnbuiltSurvey_when_drop_then_okWithNothingDropped() {
        db.list(FIND_SURVEY, List.<Object[]>of(surveyRow(3, KEY, "Demo", null)));

        assertEquals(new ETLService.DropResult(ETLService.DropStatus.OK, "Survey Demo has no reporting schema; nothing to drop."),
                service().dropReportingSchema(KEY));
        assertEquals(List.of(FIND_SURVEY), db.sqls());
    }

    @Test
    void given_aBuiltSurvey_when_drop_then_theSchemaIsDroppedAndTheSurveyCleared() {
        db.list(FIND_SURVEY, List.<Object[]>of(surveyRow(3, KEY, "Demo", SCHEMA)));

        assertEquals(new ETLService.DropResult(ETLService.DropStatus.OK, "Reporting schema report_demo of Demo dropped."),
                service().dropReportingSchema(KEY));
        assertEquals(List.of(FIND_SURVEY, "DROP SCHEMA IF EXISTS report_demo CASCADE", CLEAR_SCHEMA), db.sqls());
        assertEquals(Map.of("id", 3), db.runs(CLEAR_SCHEMA).get(0).params());
    }

    @Test
    void given_theDropFails_when_drop_then_failedWithTheRootCause() {
        db.list(FIND_SURVEY, List.<Object[]>of(surveyRow(3, KEY, "Demo", SCHEMA)))
                .update("DROP SCHEMA IF EXISTS report_demo CASCADE", new IllegalStateException("must be owner"));

        assertEquals(new ETLService.DropResult(ETLService.DropStatus.FAILED, "must be owner"), service().dropReportingSchema(KEY));
        assertFalse(db.sqls().contains(CLEAR_SCHEMA));
    }

    // ── one respondent (UC-004) ────────────────────────────────────────────────────────────────

    @Test
    void given_theEtlDisabled_when_populateFactSectionTable_then_nothingIsLoaded() {
        ETLService service = service();
        service.etlEnabled = false;

        assertEquals("Reporting ETL disabled (elicit.etl.enabled=false)", service.populateFactSectionTable(5));
        assertTrue(db.executed.isEmpty());
    }

    @Test
    void given_anUnknownRespondent_when_populateFactSectionTable_then_nothingIsLoaded() {
        assertEquals("Respondent 5 not found; nothing loaded", service().populateFactSectionTable(5));
        assertEquals(Map.of("id", 5), db.runs(FIND_SURVEY_OF_RESPONDENT).get(0).params());
    }

    @Test
    void given_aRespondentOfAnUnbuiltSurvey_when_populateFactSectionTable_then_leftForTheNextBuild() {
        db.list(FIND_SURVEY_OF_RESPONDENT, List.<Object[]>of(surveyRow(3, KEY, "Demo", null)));

        assertEquals("Survey Demo has no reporting schema yet; respondent 5 will be loaded by the next build",
                service().populateFactSectionTable(5));
        assertEquals(List.of(FIND_SURVEY_OF_RESPONDENT), db.sqls());
    }

    @Test
    void given_aRespondentOfABuiltSurvey_when_populateFactSectionTable_then_dimensionsFactsAndItemsAreLoaded() {
        db.list(FIND_SURVEY_OF_RESPONDENT, List.<Object[]>of(surveyRow(3, KEY, "Demo", SCHEMA)));
        scriptRespondent();

        String result = service().populateFactSectionTable(5);

        assertEquals("Added respondent 5 to fact_sections:" + NL + "5: 2 keys" + NL + "Populated Dimesions tables = 1" + NL, result);

        ScriptedEntityManager.Executed value = db.runs(in(Sql.INSERT_INTO_DIMENSION).replace("<DIM>", "color")).get(0);
        assertEquals(Map.of("val", "red"), value.params());
        assertEquals(Map.of("respondentId", 5), db.runs(Sql.FIND_DIMENSTION_VALUES_SQL).get(0).params());
        assertEquals(Map.of("respondent_id", 5), db.runs(in(Sql.INSERT_MISSING_FACT_SECTION_SQL)).get(0).params());
        assertEquals("update", db.runs(in(Sql.INSERT_MISSING_FACT_SECTION_SQL)).get(0).how());
        assertEquals(Map.of("respondent_id", 5), db.runs(in(Sql.FIND_MISSING_FACT_SECTION_DIMENSIONS_SQL)).get(0).params());

        String tagUpdate = in(Sql.UPDATE_FACT_SECTION_DIMENSION_VALUE_SQL).replace("<KEY>", "color_key").replace("<DIM>", "dim_color");
        ScriptedEntityManager.Executed tag = db.runs(tagUpdate).get(0);
        assertEquals("update", tag.how());
        assertEquals(Map.of("val", "red", "factId", 11, "respondentId", 5), tag.params());

        List<ScriptedEntityManager.Executed> questions = db.runs(in(Sql.UPSERT_DIM_QUESTION_SQL));
        assertEquals(2, questions.size(), "one upsert per per-item fact row");
        assertEquals(Map.of("key", QUESTION_KEY.toString(), "value", "Which colour?"), questions.get(0).params());

        List<ScriptedEntityManager.Executed> items = db.runs(in(Sql.UPSERT_DIM_ITEM_SQL));
        assertEquals(Map.of("key", ITEM_KEY.toString(), "value", "R", "displayText", "Red", "listName", "colours",
                "displayOrder", new BigDecimal("2.5")), items.get(0).params());
        assertTrue(items.get(1).params().containsKey("displayOrder"));
        assertNull(items.get(1).params().get("displayOrder"), "a missing display order stays null");

        List<ScriptedEntityManager.Executed> points = db.runs(in(Sql.UPDATE_FACT_SECTION_ITEM_SQL));
        assertEquals(Map.of("questionKey", 7, "itemKey", 8, "factId", 12), points.get(0).params());
        assertEquals(Map.of("questionKey", 7, "itemKey", 8, "factId", 13), points.get(1).params());
        assertEquals("update", points.get(1).how());
    }

    @Test
    void given_noTagKeys_when_addRespondentFactSections_then_oneIsReported() {
        assertEquals("1", service().addRespondentFactSections(SCHEMA, 5));
    }

    @Test
    void given_aDimensionNameThatIsNotAnIdentifier_when_populateDimensionTables_then_refusedBeforeAnyInsert() {
        db.list(Sql.FIND_DIMENSTION_VALUES_SQL, List.<Object[]>of(new Object[]{"color; drop table x", "red"}));

        RuntimeException thrown = org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
                () -> service().populateDimensionTables(SCHEMA, 5));

        assertEquals("Non-retriable exception during populating dimension tables for respondent 5", thrown.getMessage());
        assertEquals(1, db.sqls().size());
    }
}
