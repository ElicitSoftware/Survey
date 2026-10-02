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

import com.elicitsoftware.util.DatabaseRetryUtil;
import io.quarkus.logging.Log;
import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

/**
 * The reporting ETL: builds, fills, renames and drops each survey's reporting star schema
 * (UC-008, UC-010, UC-011) and loads a finalized respondent's fact rows into it (UC-004).
 * <p>
 * Every survey has a schema of its own, named on {@code survey.surveys.report_schema}
 * (UC-008 BR-006). The common schema {@code surveyreport} holds only {@code dim_date} and
 * {@code dim_status}, which the migrations create. Every statement here runs through
 * {@link Sql#in(String, String)} against one survey's schema and reads one survey's
 * definition, so a second survey at the site never touches the first survey's star (BR-009).
 * <p>
 * All database access is native SQL on the {@code owner} persistence unit, the role that owns
 * the reporting schemas. The step methods are public and {@code @Transactional} so that each
 * step of a build commits on its own, as before: a failing step rolls back alone and the next
 * build, every step of which only creates what is missing, completes the schema (BR-001).
 */
@ApplicationScoped
public class ETLService {

    @PersistenceContext(unitName = "owner")
    EntityManager entityManager;

    @ConfigProperty(name = "quarkus.flyway.owner.placeholders.surveyreport_user", defaultValue = "surveyreport_user")
    String REPORT_USER;

    @ConfigProperty(name = "quarkus.flyway.owner.placeholders.survey_user", defaultValue = "survey_user")
    String SURVEY_USER;

    /**
     * {@code elicit.etl.enabled=false} turns the reporting ETL off for an instance that only
     * renders surveys -- the Author stack's preview instance runs against Author's working
     * database, which holds many drafts whose answers are never reported, and should not grow
     * one reporting schema per draft until the reporting work needs it to.
     */
    @ConfigProperty(name = "elicit.etl.enabled", defaultValue = "true")
    boolean etlEnabled;

    /**
     * {@code elicit.etl.drop.enabled=true} lets {@code DELETE /api/etl/schema/{key}} drop a
     * survey's reporting schema (UC-011 BR-001). Off by default: on a site the next build would
     * regenerate the schema with new surrogate ids, which breaks an extract keyed on them, so
     * only a database that holds no reporting anyone depends on -- Author's -- turns it on.
     */
    @ConfigProperty(name = "elicit.etl.drop.enabled", defaultValue = "false")
    boolean dropEnabled;

    /** Serializes builds, renames and drops; the DDL steps are not safe to interleave (BR-005). */
    private final ReentrantLock rebuildLock = new ReentrantLock();

    /** A survey as the ETL sees it: its id, portable key, name and schema (null until built). */
    public record SurveyRef(int id, UUID key, String name, String schema) {
    }

    /** Outcome of {@link #rebuildReportingSchema(Optional)}. */
    public enum RebuildStatus {
        /** The build sequence ran to completion for every survey asked for. */
        OK,
        /** {@code elicit.etl.enabled=false}: nothing was touched. */
        DISABLED,
        /** No survey has the key given: nothing was touched. */
        UNKNOWN,
        /** A build threw; the failing step was rolled back and the cause is in the message. */
        FAILED
    }

    /**
     * What {@link #rebuildReportingSchema(Optional)} did.
     *
     * @param status  whether the build ran, was disabled, found no such survey, or failed
     * @param message one summary line per survey, the disabled reason, or the failure's root cause
     */
    public record RebuildResult(RebuildStatus status, String message) {
    }

    /** Outcome of {@link #renameReportingSchema(UUID, String)} (UC-010). */
    public enum RenameStatus {
        OK, INVALID, TAKEN, UNBUILT, UNKNOWN, FAILED
    }

    public record RenameResult(RenameStatus status, String message, String schema) {
    }

    /** Outcome of {@link #dropReportingSchema(UUID)} (UC-011). */
    public enum DropStatus {
        OK, DISABLED, UNKNOWN, FAILED
    }

    public record DropResult(DropStatus status, String message) {
    }

    // ── startup (UC-008 A7) ──────────────────────────────────────────────────────────────────

    @Startup
    void init() {
        if (!etlEnabled) {
            Log.warn("Reporting ETL is disabled (elicit.etl.enabled=false): no reporting schema is built or updated by this instance.");
            return;
        }
        List<SurveyRef> surveys = listSurveys();
        if (surveys.isEmpty()) {
            // Nothing to build a reporting schema from: every dimension and fact this class
            // derives comes from a survey definition. Admin's apply calls the build endpoint
            // (UC-008) once a definition is imported, so no restart is needed.
            // WARN, not INFO: containers default to quarkus.log.level=WARN, and this is the
            // one startup condition an operator may have to act on.
            Log.warn("No survey is defined in the database (survey.surveys is empty). "
                    + "Reporting schema generation skipped. Import a survey definition through "
                    + "the Admin application; its apply builds the survey's reporting schema.");
            return;
        }
        Log.info("ETL Service initialization: " + surveys.size() + " survey(s).");
        rebuildLock.lock();
        try {
            for (SurveyRef survey : surveys) {
                try {
                    Log.info(build(survey));
                } catch (Exception e) {
                    // One broken survey must not stop the others from being reportable (A7).
                    Log.error("Reporting schema build failed for survey " + survey.name()
                            + " (" + survey.key() + "): " + rootMessage(e), e);
                }
            }
        } finally {
            rebuildLock.unlock();
        }
    }

    // ── build on request (UC-008) ────────────────────────────────────────────────────────────

    /** Every survey, as {@code POST /api/etl/build} without {@code survey} asks (A5). */
    public RebuildResult rebuildReportingSchema() {
        return rebuildReportingSchema(Optional.empty());
    }

    /**
     * Runs the build sequence for one survey, or for every survey, on request (UC-008).
     * <p>
     * Every step is idempotent -- the dimension upserts are keyed on the durable step/section
     * ids, the table and column builders only create what does not exist yet, and the views
     * are dropped and recreated -- so it can be called as often as needed and runs the same
     * code the startup build does. Never throws: a failure is logged at ERROR and returned,
     * so the caller can report it without the request blowing up (BR-003).
     *
     * @param surveyKey the survey to build, or empty for all of them
     * @return what happened, with a message fit for an operator
     */
    public RebuildResult rebuildReportingSchema(Optional<UUID> surveyKey) {
        if (!etlEnabled) {
            return new RebuildResult(RebuildStatus.DISABLED,
                    "Reporting ETL is disabled (elicit.etl.enabled=false)");
        }
        rebuildLock.lock();
        try {
            List<SurveyRef> surveys;
            if (surveyKey.isPresent()) {
                SurveyRef survey = findSurvey(surveyKey.get());
                if (survey == null) {
                    return new RebuildResult(RebuildStatus.UNKNOWN, "No survey has the key " + surveyKey.get());
                }
                surveys = List.of(survey);
            } else {
                surveys = listSurveys();
                if (surveys.isEmpty()) {
                    return new RebuildResult(RebuildStatus.OK,
                            "No survey is installed (survey.surveys is empty); nothing to build.");
                }
            }
            Log.info("Rebuilding the reporting schema on request for " + surveys.size() + " survey(s).");
            List<String> lines = new ArrayList<>();
            List<String> failures = new ArrayList<>();
            for (SurveyRef survey : surveys) {
                try {
                    String line = build(survey);
                    Log.info("Reporting schema rebuilt: " + line);
                    lines.add(line);
                } catch (Exception e) {
                    Log.error("Reporting schema rebuild failed for survey " + survey.name(), e);
                    String cause = survey.name() + ": " + rootMessage(e);
                    failures.add(cause);
                    lines.add(cause);
                }
            }
            if (!failures.isEmpty()) {
                return new RebuildResult(RebuildStatus.FAILED, String.join(" | ", failures));
            }
            return new RebuildResult(RebuildStatus.OK, String.join(" | ", lines));
        } finally {
            rebuildLock.unlock();
        }
    }

    /**
     * The build sequence for one survey (UC-008 steps 3-6). Runs under the rebuild lock.
     *
     * @return a one-line summary naming the survey, its schema and what each step did
     */
    String build(SurveyRef survey) {
        String schema = ensureSchema(survey);
        StringBuilder summary = new StringBuilder();
        summary.append(survey.name()).append(" [").append(schema).append("]: ");
        summary.append("step dimensions upserted: ").append(updateStepDimensionTable(schema, survey.id()));
        summary.append("; section dimensions upserted: ").append(updateSectionDimensionTable(schema, survey.id()));
        refuseReservedTags(survey);
        summary.append("; ").append(buildDimensionTables(schema, survey.id()));
        summary.append("; fact_respondents_view: ").append(buildFactRespondentsView(schema, survey.id()));
        summary.append("; fact_sections columns: ").append(buildFactSectionTable(schema, survey.id()).replace('\n', ' ').trim());
        String view = buildFactSectionView(schema, survey.id());
        summary.append("; fact_sections_view: ").append(view.startsWith("CREATE") ? "recreated" : view);
        summary.append("; respondents back-filled into fact_sections: ").append(populateAllFactSectionsTable(schema, survey.id()));
        return summary.toString();
    }

    /**
     * The innermost cause's message: a duplicate-key failure arrives wrapped in
     * {@link DatabaseRetryUtil}'s RuntimeException and Hibernate's PersistenceException, and
     * the driver's own text is the one that names the constraint.
     */
    static String rootMessage(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String message = root.getMessage();
        return message == null || message.isBlank() ? root.getClass().getName() : message.trim();
    }

    // ── surveys and their schemas ────────────────────────────────────────────────────────────

    /** Every installed survey, in display order. */
    @SuppressWarnings("unchecked")
    public List<SurveyRef> listSurveys() {
        List<Object[]> rows = entityManager.createNativeQuery(
                "SELECT id, survey_key, name, report_schema FROM survey.surveys ORDER BY display_order, id").getResultList();
        List<SurveyRef> surveys = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            surveys.add(toRef(row));
        }
        return surveys;
    }

    /** The survey with that portable key, or {@code null}. */
    @SuppressWarnings("unchecked")
    public SurveyRef findSurvey(UUID key) {
        List<Object[]> rows = entityManager.createNativeQuery(
                        "SELECT id, survey_key, name, report_schema FROM survey.surveys WHERE survey_key = CAST(:key AS uuid)")
                .setParameter("key", key.toString()).getResultList();
        return rows.isEmpty() ? null : toRef(rows.get(0));
    }

    /** The survey a respondent belongs to, or {@code null} for an unknown respondent. */
    @SuppressWarnings("unchecked")
    SurveyRef findSurveyOfRespondent(int respondentId) {
        List<Object[]> rows = entityManager.createNativeQuery(
                        "SELECT s.id, s.survey_key, s.name, s.report_schema FROM survey.surveys s "
                                + "JOIN survey.respondents r ON r.survey_id = s.id WHERE r.id = :id")
                .setParameter("id", respondentId).getResultList();
        return rows.isEmpty() ? null : toRef(rows.get(0));
    }

    private static SurveyRef toRef(Object[] row) {
        UUID key = row[1] instanceof UUID uuid ? uuid : UUID.fromString(String.valueOf(row[1]));
        return new SurveyRef(((Number) row[0]).intValue(), key, (String) row[2], (String) row[3]);
    }

    /**
     * Counts the survey definitions currently installed.
     *
     * @return the number of rows in {@code survey.surveys}
     */
    long countSurveys() {
        Query query = entityManager.createNativeQuery("SELECT COUNT(*) FROM survey.surveys");
        return ((Number) query.getSingleResult()).longValue();
    }

    /**
     * UC-008 step 3: the survey's schema, named on first contact (BR-006) and created with its
     * grants and fixed tables (BR-007). Idempotent: an existing schema is left as it is.
     *
     * @return the schema name
     */
    public String ensureSchema(SurveyRef survey) {
        String schema = survey.schema();
        if (schema == null) {
            schema = assignSchemaName(survey);
        }
        createSchemaObjects(schema);
        return schema;
    }

    /** Derives and stores the survey's schema name (BR-006), in its own transaction. */
    @Transactional
    public String assignSchemaName(SurveyRef survey) {
        String name = ReportSchemaNames.derive(survey.name(), this::schemaNameTaken);
        entityManager.createNativeQuery("UPDATE survey.surveys SET report_schema = :name WHERE id = :id")
                .setParameter("name", name).setParameter("id", survey.id()).executeUpdate();
        Log.info("Survey " + survey.name() + " (" + survey.key() + ") reports in schema " + name);
        return name;
    }

    /** Whether another survey holds the name, or a schema of that name exists (UC-010 BR-004). */
    boolean schemaNameTaken(String name) {
        long surveys = ((Number) entityManager.createNativeQuery(
                        "SELECT COUNT(*) FROM survey.surveys WHERE report_schema = :name")
                .setParameter("name", name).getSingleResult()).longValue();
        if (surveys > 0) {
            return true;
        }
        long schemas = ((Number) entityManager.createNativeQuery(Sql.SCHEMA_EXISTS_SQL)
                .setParameter("name", name).getSingleResult()).longValue();
        return schemas > 0;
    }

    /** Creates the schema, its grants and its fixed tables; every statement is IF NOT EXISTS. */
    @Transactional
    public void createSchemaObjects(String schema) {
        DatabaseRetryUtil.executeWithRetry(() -> {
            String script = Sql.in(schema, Sql.CREATE_SURVEY_SCHEMA_SQL)
                    .replace("<REPORT_USER>", REPORT_USER)
                    .replace("<SURVEY_USER>", SURVEY_USER);
            entityManager.createNativeQuery(script).executeUpdate();
            return null;
        }, "creating reporting schema " + schema);
    }

    // ── rename (UC-010) and drop (UC-011) ────────────────────────────────────────────────────

    /**
     * Renames a survey's reporting schema (UC-010): the schema and the survey's
     * {@code report_schema} change together or not at all (BR-001). Never throws.
     */
    public RenameResult renameReportingSchema(UUID surveyKey, String newName) {
        rebuildLock.lock();
        try {
            SurveyRef survey = findSurvey(surveyKey);
            if (survey == null) {
                return new RenameResult(RenameStatus.UNKNOWN, "No survey has the key " + surveyKey, null);
            }
            if (survey.schema() == null) {
                return new RenameResult(RenameStatus.UNBUILT,
                        "Survey " + survey.name() + " has no reporting schema yet; build it first.", null);
            }
            String objection = ReportSchemaNames.objection(newName);
            if (objection != null) {
                return new RenameResult(RenameStatus.INVALID, objection, survey.schema());
            }
            if (newName.equals(survey.schema())) {
                return new RenameResult(RenameStatus.OK, "The reporting schema is already named " + newName + ".", newName);
            }
            if (schemaNameTaken(newName)) {
                return new RenameResult(RenameStatus.TAKEN, "The name " + newName + " is already in use.", survey.schema());
            }
            try {
                renameSchema(survey, newName);
                String message = "Reporting schema of " + survey.name() + " renamed from " + survey.schema() + " to " + newName + ".";
                Log.info(message);
                return new RenameResult(RenameStatus.OK, message, newName);
            } catch (Exception e) {
                Log.error("Reporting schema rename failed for survey " + survey.name(), e);
                return new RenameResult(RenameStatus.FAILED, rootMessage(e), survey.schema());
            }
        } finally {
            rebuildLock.unlock();
        }
    }

    @Transactional
    public void renameSchema(SurveyRef survey, String newName) {
        String sql = Sql.RENAME_SCHEMA_SQL
                .replace("<OLD>", Sql.requireValidSchema(survey.schema()))
                .replace("<NEW>", Sql.requireValidSchema(newName));
        entityManager.createNativeQuery(sql).executeUpdate();
        entityManager.createNativeQuery("UPDATE survey.surveys SET report_schema = :name WHERE id = :id")
                .setParameter("name", newName).setParameter("id", survey.id()).executeUpdate();
    }

    /**
     * Drops a survey's reporting schema with everything in it and clears the survey's
     * {@code report_schema} (UC-011), when this instance allows it (BR-001). Independent of
     * {@code elicit.etl.enabled} (BR-004). Never throws.
     */
    public DropResult dropReportingSchema(UUID surveyKey) {
        if (!dropEnabled) {
            return new DropResult(DropStatus.DISABLED,
                    "Dropping a reporting schema is not enabled on this instance (elicit.etl.drop.enabled=false)");
        }
        rebuildLock.lock();
        try {
            SurveyRef survey = findSurvey(surveyKey);
            if (survey == null) {
                return new DropResult(DropStatus.UNKNOWN, "No survey has the key " + surveyKey);
            }
            if (survey.schema() == null) {
                return new DropResult(DropStatus.OK, "Survey " + survey.name() + " has no reporting schema; nothing to drop.");
            }
            try {
                dropSchema(survey);
                String message = "Reporting schema " + survey.schema() + " of " + survey.name() + " dropped.";
                Log.info(message);
                return new DropResult(DropStatus.OK, message);
            } catch (Exception e) {
                Log.error("Reporting schema drop failed for survey " + survey.name(), e);
                return new DropResult(DropStatus.FAILED, rootMessage(e));
            }
        } finally {
            rebuildLock.unlock();
        }
    }

    @Transactional
    public void dropSchema(SurveyRef survey) {
        entityManager.createNativeQuery(Sql.in(survey.schema(), Sql.DROP_SCHEMA_SQL)).executeUpdate();
        entityManager.createNativeQuery("UPDATE survey.surveys SET report_schema = NULL WHERE id = :id")
                .setParameter("id", survey.id()).executeUpdate();
    }

    // ── the build steps ──────────────────────────────────────────────────────────────────────

    /** UC-008 step 4: upserts the survey's current steps into its {@code dim_step}. */
    @Transactional
    public int updateStepDimensionTable(String schema, int surveyId) {
        return DatabaseRetryUtil.executeWithRetry(() -> {
            Query query = entityManager.createNativeQuery(Sql.in(schema, Sql.UPDATE_STEPS_DIMENSION_TABLE_SQL));
            query.setParameter("surveyId", surveyId);
            return query.executeUpdate();
        }, "updating step dimension table");
    }

    /** UC-008 step 4: upserts the survey's current sections into its {@code dim_section}. */
    @Transactional
    public int updateSectionDimensionTable(String schema, int surveyId) {
        return DatabaseRetryUtil.executeWithRetry(() -> {
            Query query = entityManager.createNativeQuery(Sql.in(schema, Sql.UPDATE_SECTIONS_DIMENSION_TABLE_SQL));
            query.setParameter("surveyId", surveyId);
            return query.executeUpdate();
        }, "updating section dimension table");
    }

    /**
     * BR-012: a tag whose fact column would be one of the fixed columns is refused before any
     * column is added, so the build fails with the tag named rather than silently reusing
     * {@code question_key} or {@code item_key} for something else.
     */
    @SuppressWarnings("unchecked")
    void refuseReservedTags(SurveyRef survey) {
        List<String> tags = entityManager.createNativeQuery(Sql.FIND_RESERVED_TAGS_SQL)
                .setParameter("surveyId", survey.id()).getResultList();
        if (!tags.isEmpty()) {
            throw new IllegalStateException("Survey " + survey.name() + " has reporting tag(s) " + tags
                    + " whose column name is reserved for a fixed column of fact_sections (step, section, question, item); rename the tag.");
        }
    }

    /**
     * UC-008 step 5: creates a {@code dim_<name>} table in the survey's schema for every
     * dimension its metadata names that has no table yet.
     *
     * @return "new Dimesions tables = [dimension1, dimension2, ...]"
     */
    @Transactional
    public String buildDimensionTables(String schema, int surveyId) {
        Query query = entityManager.createNativeQuery(Sql.FIND_NEW_DIMENSION_TABLES_SQL);
        query.setParameter("surveyId", surveyId);
        query.setParameter("schema", schema);
        @SuppressWarnings("unchecked")
        List<String> results = query.getResultList();
        for (String dimension : results) {
            buildDimension(schema, dimension);
        }
        return "new Dimesions tables = " + results;
    }

    private int buildDimension(String schema, String dimensionName) {
        return DatabaseRetryUtil.executeWithRetry(() -> {
            String script = Sql.in(schema, Sql.CREATE_NEW_DIMENSION_TABLE_SQL)
                    .replace("<TABLE_NAME>", Sql.requireValidIdentifier(dimensionName))
                    .replace("<REPORT_USER>", REPORT_USER);
            Query query = entityManager.createNativeQuery(script);
            return query.executeUpdate();
        }, "building dimension table for " + dimensionName);
    }

    /**
     * UC-008 step 6: loads every finalized respondent of the survey who has no fact rows yet.
     *
     * @return the number of respondents processed
     */
    private int populateAllFactSectionsTable(String schema, int surveyId) {
        Query respondentsQuery = entityManager.createNativeQuery(Sql.in(schema, Sql.FIND_MISSING_FACT_SECTION_RESPONDENTS));
        respondentsQuery.setParameter("surveyId", surveyId);
        @SuppressWarnings("unchecked")
        List<Object> respondents = respondentsQuery.getResultList();
        int r = 1;
        for (Object respondentId : respondents) {
            Integer id = ((Number) respondentId).intValue();
            Log.info("progress " + r + " of " + respondents.size());
            Log.info(loadRespondent(schema, id));
            r++;
        }
        return respondents.size();
    }

    // ── per respondent (UC-004 at finalize; UC-008 step 6) ───────────────────────────────────

    /**
     * Loads one respondent's dimension values and fact rows into their survey's schema.
     * Called at finalize (UC-004, through {@link ETLRespondentService}) and by the back-fill.
     * A survey that has no schema yet -- never built, or dropped -- is skipped with a message;
     * the next build's back-fill supplies the rows.
     *
     * @param respondentId the respondent
     * @return a summary of what was loaded, or why nothing was
     */
    public String populateFactSectionTable(Integer respondentId) {
        if (!etlEnabled) {
            return "Reporting ETL disabled (elicit.etl.enabled=false)";
        }
        SurveyRef survey = findSurveyOfRespondent(respondentId);
        if (survey == null) {
            return "Respondent " + respondentId + " not found; nothing loaded";
        }
        if (survey.schema() == null) {
            String message = "Survey " + survey.name() + " has no reporting schema yet; respondent "
                    + respondentId + " will be loaded by the next build";
            Log.warn(message);
            return message;
        }
        return loadRespondent(survey.schema(), respondentId);
    }

    String loadRespondent(String schema, Integer respondentId) {
        String dim = populateDimensionTables(schema, respondentId);
        String facts = "Added respondent " + respondentId + " to fact_sections:" + System.lineSeparator()
                + respondentId + ": " + addRespondentFactSections(schema, respondentId) + " keys";
        return facts + System.lineSeparator() + dim + System.lineSeparator();
    }

    /** Inserts the respondent's answer values into the survey's {@code dim_<tag>} tables. */
    @Transactional
    public String populateDimensionTables(String schema, Integer respondentId) {
        return DatabaseRetryUtil.executeWithRetry(() -> {
            Query query = entityManager.createNativeQuery(Sql.FIND_DIMENSTION_VALUES_SQL);
            query.setParameter("respondentId", respondentId);
            @SuppressWarnings("unchecked")
            List<Object[]> results = query.getResultList();
            for (Object[] result : results) {
                String dimension = (String) result[0];
                String value = (String) result[1];
                insertDimensionValue(schema, dimension, value);
            }
            return "Populated Dimesions tables = " + results.size();
        }, "populating dimension tables for respondent " + respondentId);
    }

    private int insertDimensionValue(String schema, String dim, String value) {
        String sql = Sql.in(schema, Sql.INSERT_INTO_DIMENSION).replace("<DIM>", Sql.requireValidIdentifier(dim));
        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("val", value);
        return query.executeUpdate();
    }

    /**
     * Inserts the respondent's fact rows (BR-011), resolves every tag key on them, and names the
     * question and item of each per-item section instance (BR-012).
     *
     * @return the number of tag keys written, plus one
     */
    @Transactional
    public String addRespondentFactSections(String schema, Integer respondentId) {
        //Add the base fact rows without the dimensional data
        Query factSectionQuery = entityManager.createNativeQuery(Sql.in(schema, Sql.INSERT_MISSING_FACT_SECTION_SQL));
        factSectionQuery.setParameter("respondent_id", respondentId);
        factSectionQuery.executeUpdate();

        Query query = entityManager.createNativeQuery(Sql.in(schema, Sql.FIND_MISSING_FACT_SECTION_DIMENSIONS_SQL));
        query.setParameter("respondent_id", respondentId);
        @SuppressWarnings("unchecked")
        List<Object[]> queryResults = query.getResultList();

        int item = 1;
        for (Object[] result : queryResults) {
            String key = (String) result[0];
            String dim = (String) result[1];
            String val = (String) result[2];
            Integer factId = ((Number) result[3]).intValue();

            String sql = Sql.in(schema, Sql.UPDATE_FACT_SECTION_DIMENSION_VALUE_SQL)
                    .replace("<KEY>", Sql.requireValidIdentifier(key))
                    .replace("<DIM>", Sql.requireValidIdentifier(dim));
            Query updateFactQuery = entityManager.createNativeQuery(sql);
            updateFactQuery.setParameter("val", val);
            updateFactQuery.setParameter("factId", factId);
            updateFactQuery.setParameter("respondentId", respondentId);
            updateFactQuery.executeUpdate();
            item++;
        }
        nameRepeatedItems(schema, respondentId);
        return String.valueOf(item);
    }

    /**
     * BR-012: for each of the respondent's fact rows that is a per-item section instance, upserts
     * the question and the item into {@code dim_question} and {@code dim_item} by their portable
     * keys and points the row at them. Rows the query does not return keep {@code -1}.
     */
    @SuppressWarnings("unchecked")
    private void nameRepeatedItems(String schema, Integer respondentId) {
        List<Object[]> rows = entityManager.createNativeQuery(Sql.in(schema, Sql.FIND_REPEATED_ITEM_FACTS_SQL))
                .setParameter("respondent_id", respondentId).getResultList();
        for (Object[] row : rows) {
            Integer factId = ((Number) row[0]).intValue();
            Number questionId = (Number) entityManager.createNativeQuery(Sql.in(schema, Sql.UPSERT_DIM_QUESTION_SQL))
                    .setParameter("key", String.valueOf(row[1]))
                    .setParameter("value", (String) row[2])
                    .getSingleResult();
            Number itemId = (Number) entityManager.createNativeQuery(Sql.in(schema, Sql.UPSERT_DIM_ITEM_SQL))
                    .setParameter("key", String.valueOf(row[3]))
                    .setParameter("value", (String) row[4])
                    .setParameter("displayText", (String) row[5])
                    .setParameter("listName", (String) row[6])
                    .setParameter("displayOrder", row[7] == null ? null : new BigDecimal(String.valueOf(row[7])))
                    .getSingleResult();
            entityManager.createNativeQuery(Sql.in(schema, Sql.UPDATE_FACT_SECTION_ITEM_SQL))
                    .setParameter("questionKey", questionId.intValue())
                    .setParameter("itemKey", itemId.intValue())
                    .setParameter("factId", factId)
                    .executeUpdate();
        }
    }

    /**
     * UC-008 step 5: adds a {@code <tag>_key} column to the survey's {@code fact_sections} for
     * every dimension of the survey that has none yet.
     *
     * @return the columns added, one per line
     */
    @Transactional
    public String buildFactSectionTable(String schema, int surveyId) {
        StringBuilder returnValue = new StringBuilder();
        Query query = entityManager.createNativeQuery(Sql.FIND_DIMENSIONS_TO_ADD_TO_FACT_SECTIONS_TABLE);
        query.setParameter("schema", schema);
        query.setParameter("surveyId", surveyId);
        @SuppressWarnings("unchecked")
        List<Object[]> results = query.getResultList();
        for (Object[] result : results) {
            String column = (String) result[0];
            String dimension = (String) result[1];
            returnValue.append(column).append('\n');
            addDimensionColumnsToFactSectionTable(schema, column, dimension);
        }
        return "new Dimesions tables = " + returnValue;
    }

    private int addDimensionColumnsToFactSectionTable(String schema, String column, String dimension) {
        String sql = Sql.in(schema, Sql.ADD_DIM_COLUMN_TO_FACT_SECTIONS_TABLE)
                .replace("<COL>", Sql.requireValidIdentifier(column))
                .replace("<DIM>", Sql.requireValidIdentifier(dimension));
        Log.info(sql);
        Query query = entityManager.createNativeQuery(sql);
        return query.executeUpdate();
    }

    /**
     * UC-008 step 5: drops and recreates the survey's {@code fact_sections_view} over every
     * dimension its fact table has a key for.
     *
     * @return the SQL used, or a message when a dependency an operator created by hand blocks
     * the drop (see UC-008 Notes)
     */
    @Transactional
    public String buildFactSectionView(String schema, int surveyId) {
        try {
            Query dropQuery = entityManager.createNativeQuery(Sql.in(schema, Sql.DROP_SECTION_VIEW_SQL));
            dropQuery.executeUpdate();
            StringBuilder selectSQL = new StringBuilder(Sql.in(schema, Sql.FACT_SECTION_VIEW_SELECT_SQL));
            StringBuilder fromSQL = new StringBuilder(Sql.in(schema, Sql.FACT_SECTION_VIEW_FROM_SQL));

            Query query = entityManager.createNativeQuery(Sql.FIND_FACT_SECTION_JOIN_COLUMNS);
            query.setParameter("schema", schema);
            query.setParameter("surveyId", surveyId);
            @SuppressWarnings("unchecked")
            List<Object[]> results = query.getResultList();
            for (Object[] result : results) {
                String column = Sql.requireValidIdentifier((String) result[0]);
                String dimension = Sql.requireValidIdentifier((String) result[1]);
                selectSQL.append("    ").append(column).append(".value as ").append(column.replace("_key", "")).append(",").append(System.lineSeparator());
                fromSQL.append(Sql.in(schema, Sql.FACT_VIEW_JOIN_CLAUSE_SQL).replace("<COL>", column).replace("<DIM>", dimension));
            }
            String createSQL = selectSQL.toString();
            // remove the last comma
            createSQL = createSQL.substring(0, createSQL.length() - 2);
            String grantReportUser = Sql.in(schema, Sql.FACT_SECTIONS_VIEW_GRANT_CLAUSE_SQL).replace("<REPORT_USER>", REPORT_USER);
            String grantSurveyUser = Sql.in(schema, Sql.FACT_SECTIONS_VIEW_GRANT_CLAUSE_SQL).replace("<REPORT_USER>", SURVEY_USER);
            createSQL = createSQL + fromSQL + "); " + grantReportUser + grantSurveyUser;
            Query query2 = entityManager.createNativeQuery(createSQL);
            query2.executeUpdate();

            return createSQL;
        } catch (Exception e) {
            Log.info("There may be a dependency that can't be worked arround. You may have to delete and recreate the dependency.");
            Log.info(e.getMessage());
            return "There may be a dependency that can't be worked arround. You may have to delete and recreate the dependency.";
        }
    }

    /**
     * UC-008 step 5, BR-010: (re)creates the survey's {@code fact_respondents} view and
     * {@code fact_respondents_view}.
     *
     * @return a status message, or the root cause when the create failed
     */
    @Transactional
    public String buildFactRespondentsView(String schema, int surveyId) {
        try {
            DatabaseRetryUtil.executeWithRetry(() -> {
                String sql = Sql.in(schema, Sql.CREATE_FACT_RESPONDENTS_VIEW_SQL)
                        .replace("<SURVEY_ID>", Integer.toString(surveyId))
                        .replace("<REPORT_USER>", REPORT_USER)
                        .replace("<SURVEY_USER>", SURVEY_USER);
                Query query = entityManager.createNativeQuery(sql);
                query.executeUpdate();
                return null;
            }, "building fact respondents view");
            return "Created " + schema + ".fact_respondents_view";
        } catch (Exception e) {
            return e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
        }
    }
}
