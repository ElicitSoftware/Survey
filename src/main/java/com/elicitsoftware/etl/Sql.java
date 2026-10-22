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

/**
 * The SQL of the reporting ETL (UC-008), as templates over one survey's reporting schema.
 * <p>
 * Every survey has a star schema of its own (UC-008 BR-006/BR-007); the common schema
 * {@code surveyreport} holds only {@code dim_date} and {@code dim_status}. A statement that
 * reads or writes a survey's star therefore carries the placeholder {@code <SCHEMA>}, which
 * {@link #in(String, String)} replaces after validating the name, and a {@code :surveyId}
 * parameter wherever it reads the {@code survey} schema, so that nothing a second survey installs
 * can reach the first survey's tables (BR-009). Identifiers that cannot be bound as JDBC
 * parameters -- schema, table and column names -- pass {@link #requireValidIdentifier(String)}
 * or {@link ReportSchemaNames#objection(String)} before they are spliced in.
 */
public final class Sql {

    private static final java.util.regex.Pattern SQL_IDENTIFIER_PATTERN =
            java.util.regex.Pattern.compile("^[a-z0-9_]+$");

    /** The placeholder every per-survey template carries for the survey's schema name. */
    static final String SCHEMA = "<SCHEMA>";

    private Sql() {
    }

    /**
     * Validates a value that will be spliced directly into a native SQL string as a
     * table or column identifier (JDBC bind parameters can't stand in for identifiers).
     * Throws if the value doesn't look like a plain lowercase/underscore identifier.
     */
    public static String requireValidIdentifier(String name) {
        if (name == null || !SQL_IDENTIFIER_PATTERN.matcher(name).matches()) {
            throw new IllegalArgumentException("Invalid SQL identifier: " + name);
        }
        return name;
    }

    /**
     * A template with {@code <SCHEMA>} replaced by a survey's reporting schema name.
     *
     * @param schema   the schema name, which must obey UC-008 BR-006
     * @param template the SQL carrying {@code <SCHEMA>}
     * @return the SQL for that schema
     * @throws IllegalArgumentException when the name is not an acceptable schema name
     */
    public static String in(String schema, String template) {
        return template.replace(SCHEMA, requireValidSchema(schema));
    }

    /**
     * Validates a value that will be spliced into native SQL as a schema name.
     *
     * @throws IllegalArgumentException when the name is not an acceptable schema name (UC-008 BR-006)
     */
    public static String requireValidSchema(String schema) {
        String objection = ReportSchemaNames.objection(schema);
        if (objection != null) {
            throw new IllegalArgumentException(objection);
        }
        return schema;
    }

    // ── the survey's schema and its fixed tables (UC-008 step 3, BR-007) ──────────────────────

    /**
     * Creates a survey's reporting schema with its grants and fixed tables. Every statement is
     * {@code IF NOT EXISTS}, so running it against a schema that exists changes nothing.
     * {@code dim_step} and {@code dim_section} are what V002 used to create site-wide, with the
     * durable rekey columns; {@code dim_question} and {@code dim_item} name the question and item
     * a per-item section instance was built from (BR-012); {@code fact_sections} starts with its
     * fixed columns and gains one {@code <tag>_key} per reporting tag. The indexes are the ones
     * V008 used to create on the site-wide tables. Default privileges make every table the owner
     * creates in the schema later (the {@code dim_<tag>} tables) readable by the report user.
     */
    public static final String CREATE_SURVEY_SCHEMA_SQL = """
            CREATE SCHEMA IF NOT EXISTS <SCHEMA>;
            GRANT USAGE ON SCHEMA <SCHEMA> TO <REPORT_USER>;
            GRANT USAGE ON SCHEMA <SCHEMA> TO <SURVEY_USER>;
            ALTER DEFAULT PRIVILEGES IN SCHEMA <SCHEMA> GRANT SELECT ON TABLES TO <REPORT_USER>;

            CREATE TABLE IF NOT EXISTS <SCHEMA>.dim_step(
                id integer NOT NULL,
                value character varying(50) NOT NULL,
                step_id integer UNIQUE,
                CONSTRAINT dim_step_pk PRIMARY KEY (id),
                CONSTRAINT dim_step_un UNIQUE (value)
            );
            CREATE INDEX IF NOT EXISTS dim_step_idx ON <SCHEMA>.dim_step(id);
            CREATE INDEX IF NOT EXISTS dim_step_value_index ON <SCHEMA>.dim_step USING btree (value ASC NULLS LAST);
            CREATE INDEX IF NOT EXISTS idx_dim_step_value_id ON <SCHEMA>.dim_step(value, id);
            GRANT INSERT, SELECT, UPDATE ON <SCHEMA>.dim_step TO <SURVEY_USER>;
            GRANT SELECT ON <SCHEMA>.dim_step TO <REPORT_USER>;

            CREATE TABLE IF NOT EXISTS <SCHEMA>.dim_section(
                id integer NOT NULL,
                value character varying(50) NOT NULL,
                section_id integer UNIQUE,
                CONSTRAINT dim_section_pk PRIMARY KEY (id),
                CONSTRAINT dim_section_un UNIQUE (value)
            );
            CREATE INDEX IF NOT EXISTS dim_section_idx ON <SCHEMA>.dim_section(id);
            CREATE INDEX IF NOT EXISTS dim_section_value_index ON <SCHEMA>.dim_section USING btree (value ASC NULLS LAST);
            CREATE INDEX IF NOT EXISTS idx_dim_section_value_id ON <SCHEMA>.dim_section(value, id);
            GRANT INSERT, SELECT, UPDATE ON <SCHEMA>.dim_section TO <SURVEY_USER>;
            GRANT SELECT ON <SCHEMA>.dim_section TO <REPORT_USER>;

            CREATE SEQUENCE IF NOT EXISTS <SCHEMA>.dim_question_seq INCREMENT 1 START 1;
            CREATE TABLE IF NOT EXISTS <SCHEMA>.dim_question(
                id integer NOT NULL DEFAULT NEXTVAL('<SCHEMA>.dim_question_seq'),
                question_key uuid UNIQUE,
                value character varying(255),
                CONSTRAINT dim_question_pk PRIMARY KEY (id)
            );
            INSERT INTO <SCHEMA>.dim_question(id, question_key, value) SELECT -1, NULL, NULL
                WHERE NOT EXISTS (SELECT 1 FROM <SCHEMA>.dim_question WHERE id = -1);
            GRANT SELECT ON <SCHEMA>.dim_question TO <REPORT_USER>;

            CREATE SEQUENCE IF NOT EXISTS <SCHEMA>.dim_item_seq INCREMENT 1 START 1;
            CREATE TABLE IF NOT EXISTS <SCHEMA>.dim_item(
                id integer NOT NULL DEFAULT NEXTVAL('<SCHEMA>.dim_item_seq'),
                select_item_key uuid UNIQUE,
                value character varying(255),
                display_text character varying(255),
                list_name character varying(255),
                display_order numeric,
                CONSTRAINT dim_item_pk PRIMARY KEY (id)
            );
            INSERT INTO <SCHEMA>.dim_item(id, select_item_key, value) SELECT -1, NULL, NULL
                WHERE NOT EXISTS (SELECT 1 FROM <SCHEMA>.dim_item WHERE id = -1);
            GRANT SELECT ON <SCHEMA>.dim_item TO <REPORT_USER>;

            CREATE SEQUENCE IF NOT EXISTS <SCHEMA>.fact_sections_seq INCREMENT 1 START 1;
            CREATE TABLE IF NOT EXISTS <SCHEMA>.fact_sections
            (
                id integer NOT NULL DEFAULT NEXTVAL('<SCHEMA>.fact_sections_seq'),
                survey_id INTEGER NOT NULL,
                respondent_id INTEGER NOT NULL,
                step_key INTEGER NOT NULL,
                name CHARACTER VARYING(50),
                step_instance INTEGER NOT NULL DEFAULT 0,
                section_key INTEGER NOT NULL,
                section_instance INTEGER NOT NULL DEFAULT 0,
                question_key INTEGER NOT NULL DEFAULT -1,
                item_key INTEGER NOT NULL DEFAULT -1,
                CONSTRAINT fact_sections_pk PRIMARY KEY (id),
                CONSTRAINT step_fk FOREIGN KEY (step_key) REFERENCES <SCHEMA>.dim_step(id),
                CONSTRAINT section_fk FOREIGN KEY (section_key) REFERENCES <SCHEMA>.dim_section(id),
                CONSTRAINT question_key_fk FOREIGN KEY (question_key) REFERENCES <SCHEMA>.dim_question(id),
                CONSTRAINT item_key_fk FOREIGN KEY (item_key) REFERENCES <SCHEMA>.dim_item(id)
            );
            CREATE INDEX IF NOT EXISTS fact_sections_respondent_id_idx ON <SCHEMA>.fact_sections(respondent_id);
            CREATE INDEX IF NOT EXISTS idx_fact_sections_respondent_demographic_cancer
                ON <SCHEMA>.fact_sections(respondent_id, section_key, step_key, step_instance, section_instance);
            CREATE INDEX IF NOT EXISTS idx_fact_sections_respondent_step ON <SCHEMA>.fact_sections(respondent_id, step_key);
            CREATE INDEX IF NOT EXISTS idx_fact_sections_survey_respondent ON <SCHEMA>.fact_sections(survey_id, respondent_id);
            CREATE INDEX IF NOT EXISTS idx_fact_sections_section_step ON <SCHEMA>.fact_sections(section_key, step_key);
            CREATE INDEX IF NOT EXISTS idx_fact_sections_step_instance ON <SCHEMA>.fact_sections(step_key, step_instance);
            CREATE INDEX IF NOT EXISTS idx_fact_sections_section_instance ON <SCHEMA>.fact_sections(section_key, section_instance);
            CREATE INDEX IF NOT EXISTS idx_fact_sections_name ON <SCHEMA>.fact_sections(name) WHERE name IS NOT NULL;
            CREATE INDEX IF NOT EXISTS idx_fact_sections_item ON <SCHEMA>.fact_sections(item_key) WHERE item_key <> -1;
            CREATE INDEX IF NOT EXISTS idx_fact_sections_view_join
                ON <SCHEMA>.fact_sections(respondent_id, step_key, step_instance, section_instance, section_key);
            GRANT USAGE ON SEQUENCE <SCHEMA>.fact_sections_seq TO <SURVEY_USER>;
            GRANT INSERT, SELECT, UPDATE ON <SCHEMA>.fact_sections TO <SURVEY_USER>;
            GRANT SELECT ON <SCHEMA>.fact_sections TO <REPORT_USER>;
            """;

    /** Whether a schema of that name exists, whoever owns it (UC-010 BR-004). */
    public static final String SCHEMA_EXISTS_SQL = """
            SELECT COUNT(*) FROM pg_namespace WHERE nspname = :name
            """;

    /** UC-010 step 4: the rename; PostgreSQL carries every object in the schema along. */
    public static final String RENAME_SCHEMA_SQL = "ALTER SCHEMA <OLD> RENAME TO <NEW>";

    /** UC-011 step 4: the drop, with everything in the schema. */
    public static final String DROP_SCHEMA_SQL = "DROP SCHEMA IF EXISTS <SCHEMA> CASCADE";

    // ── dim_step / dim_section (UC-008 step 4) ───────────────────────────────────────────────

    // Upserts on the durable step_id, not the surrogate id — a renamed step gets a new
    // surrogate id on its next version row, and re-keying on step_id (see the Kimball Type 2
    // migration) is what stops that from splitting historical/new respondents across two
    // dim_step rows. `id` is deliberately excluded from the DO UPDATE SET: it must stay
    // stable once assigned (fact_sections.step_key FKs it with no ON UPDATE CASCADE) — the
    // first surrogate id ever seen for a given step_id becomes its permanent dim_step.id,
    // and later versions of the same step only update `value`.
    public static final String UPDATE_STEPS_DIMENSION_TABLE_SQL = """
            INSERT INTO <SCHEMA>.dim_step(id, step_id, value)
            select s.id, s.step_id, s.dimension_name
            from survey.steps s
            WHERE s.survey_id = :surveyId
              AND s.effective_from <= NOW() AND s.effective_to > NOW()
            ON CONFLICT (step_id) DO UPDATE
                SET value = excluded.value
            """;

    public static final String UPDATE_SECTIONS_DIMENSION_TABLE_SQL = """
            INSERT INTO <SCHEMA>.dim_section(id, section_id, value)
            select s.id, s.section_id, s.dimension_name
            from survey.sections s
            WHERE s.survey_id = :surveyId
              AND s.effective_from <= NOW() AND s.effective_to > NOW()
            ON CONFLICT (section_id) DO UPDATE
                SET value = excluded.value
            """;

    // ── dim_<tag> discovery and creation (UC-008 step 5) ──────────────────────────────────────

    /**
     * The dimension tables the survey's metadata asks for that its schema does not have yet.
     * Each branch reads one survey's metadata ({@code :surveyId}) and looks for the table in
     * that survey's schema ({@code :schema}), so a tag two surveys share gets a table in each.
     */
    public static final String FIND_NEW_DIMENSION_TABLES_SQL = """
                              select d.name from (
                               select distinct 'dim_'|| lower(d1.name) as name
                               from survey.metadata md1
                               join survey.ontology o1 on md1.ontology_id = o1.id
                               join survey.dimensions d1 on d1.id = o1.dimension
                               where md1.survey_id = :surveyId
                               and 'dim_'|| lower(d1.name) not in (
                                   SELECT t1.table_name
                                   FROM information_schema.tables t1
                                   WHERE t1.table_schema = :schema
                               )
            UNION
                select distinct lower('dim_' || replace(o4.tag,' ','_')) name
                               from survey.metadata md4
                               join survey.ontology o4 on md4.ontology_id = o4.id
                               where md4.survey_id = :surveyId
                               and o4.dimension is null
                               and lower('dim_' || replace(o4.tag,' ','_')) not in (
                                   SELECT t4.table_name
                                   FROM information_schema.tables t4
                                   WHERE t4.table_schema = :schema
                               )
            UNION
                               select distinct lower('dim_' || replace(o2.tag,' ','_')) name
                               from survey.metadata md2
                               join survey.ontology o2 on md2.ontology_id = o2.id
                               join survey.steps_sections ss2 ON md2.steps_sections_id = ss2.steps_sections_id
                                   AND ss2.effective_from <= NOW() AND ss2.effective_to > NOW()
                               join survey.sections s2 on ss2.section_id = s2.section_id
                                   AND s2.effective_from <= NOW() AND s2.effective_to > NOW()
                               where md2.survey_id = :surveyId
                               and o2.dimension is null
                and lower('dim_' || replace(o2.tag,' ','_')) not in (
                                   SELECT t2.table_name
                                   FROM information_schema.tables t2
                                   WHERE t2.table_schema = :schema
                               )
                           UNION
                               select distinct lower('dim_' || replace(o3.tag,' ','_')) name
                               from survey.metadata md3
                               join survey.ontology o3 on md3.ontology_id = o3.id
                               join survey.sections_questions sq3 ON md3.sections_question_id = sq3.sections_question_id
                                   AND sq3.effective_from <= NOW() AND sq3.effective_to > NOW()
                               join survey.questions q3 on sq3.question_id = q3.question_id
                                   AND q3.effective_from <= NOW() AND q3.effective_to > NOW()
                               where md3.survey_id = :surveyId
                               and o3.dimension is null
                               and lower('dim_' || replace(o3.tag,' ','_')) not in (
                                   SELECT t3.table_name
                                   FROM information_schema.tables t3
                                   WHERE t3.table_schema = :schema
                               )
                           )d
                           order by d.name;
            """;

    public static final String CREATE_NEW_DIMENSION_TABLE_SQL = """
            DROP TABLE IF EXISTS <SCHEMA>.<TABLE_NAME>;
            DROP SEQUENCE IF EXISTS <SCHEMA>.<TABLE_NAME>_seq;

            CREATE SEQUENCE <SCHEMA>.<TABLE_NAME>_seq INCREMENT 1 START 1;

            CREATE TABLE <SCHEMA>.<TABLE_NAME>(
                id integer NOT NULL DEFAULT NEXTVAL('<SCHEMA>.<TABLE_NAME>_seq'),
                value character varying(255),
            	CONSTRAINT <TABLE_NAME>_pk PRIMARY KEY (id),
            	CONSTRAINT <TABLE_NAME>_un UNIQUE (value)
            );

            CREATE INDEX <TABLE_NAME>_idx ON <SCHEMA>.<TABLE_NAME>(id);
            CREATE INDEX <TABLE_NAME>_val_idx ON <SCHEMA>.<TABLE_NAME>(value);
            GRANT SELECT ON <SCHEMA>.<TABLE_NAME> TO <REPORT_USER>;
            INSERT INTO <SCHEMA>.<TABLE_NAME>(id, value) values(-1,null);
            """;

    /**
     * The tags of a survey whose fact column would be one of the fixed columns (BR-012). A
     * tag named {@code step}, {@code section}, {@code question} or {@code item} would produce
     * {@code <tag>_key}, which already exists with another meaning, so the build refuses it.
     */
    public static final String FIND_RESERVED_TAGS_SQL = """
            SELECT DISTINCT o.tag
            FROM survey.ontology o
            WHERE o.survey_id = :surveyId
              AND LOWER(REPLACE(REPLACE(o.tag, ' ', '_'), '-', '')) IN ('step', 'section', 'question', 'item')
            ORDER BY o.tag
            """;

    // ── dimension values (per respondent; reads survey only) ─────────────────────────────────

    public static final String FIND_DIMENSTION_VALUES_SQL = """
                    select DISTINCT x.dim, x.val, x.respondent_id from (
                     --Question Tags from Dimension table
                             SELECT DISTINCT 'dim_' || LOWER(d1.name) as dim,
                                 case
                                     WHEN m1.value IS NULL THEN LOWER(TRIM(REPLACE(a1.text_value,'''', '''''')))
                                     else lower(trim(m1.value))
                                 end AS val,
                                a1.respondent_id
                                FROM survey.answers a1
                                  JOIN survey.questions q1 ON q1.id = a1.question_id
                                  JOIN survey.metadata m1 ON m1.question_id = q1.question_id AND m1.survey_id = a1.survey_id
                                  JOIN survey.ontology o1 ON m1.ontology_id = o1.id
                                  JOIN survey.dimensions d1 on d1.id = o1.dimension
                    --Question Tags from Ontology table
                    UNION
                            SELECT DISTINCT 'dim_' || LOWER(REPLACE(o2.tag,' ','_')) as dim,
                                 case
                                     WHEN m2.value IS NULL THEN LOWER(TRIM(REPLACE(a2.text_value,'''', '''''')))
                                     else lower(trim(m2.value))
                                 end AS val,
                                a2.respondent_id
                                FROM survey.answers a2
                                  JOIN survey.questions q2 ON q2.id = a2.question_id
                                  JOIN survey.metadata m2 ON m2.question_id = q2.question_id AND m2.survey_id = a2.survey_id
                                  JOIN survey.ontology o2 ON m2.ontology_id = o2.id
                                WHERE o2.dimension IS NULL
                     --Section_Question Tags from Dimension table
                     UNION
                             SELECT DISTINCT 'dim_' || LOWER(d3.name) as dim,
                                 case
                                    WHEN m3.value IS NULL THEN LOWER(TRIM(REPLACE(a3.text_value,'''', '''''')))
                                     else lower(trim(m3.value))
                                 end AS val,
                                a3.respondent_id
                                FROM survey.answers a3
                                  JOIN survey.sections_questions sq3 ON a3.section_question_id = sq3.id AND a3.survey_id = sq3.survey_id
                                  JOIN survey.questions q3 ON sq3.question_id = q3.question_id
                                  JOIN survey.metadata m3 ON sq3.sections_question_id = m3.sections_question_id AND m3.survey_id = a3.survey_id
                                  JOIN survey.ontology o3 ON m3.ontology_id = o3.id
                                  JOIN survey.dimensions d3 on d3.id = o3.dimension
                    --Section_Question Tags from Ontology table
                     UNION
                             SELECT DISTINCT 'dim_' || LOWER(REPLACE(o4.tag,' ','_')) as dim,
                                 case
                                    WHEN m4.value IS NULL THEN LOWER(TRIM(REPLACE(a4.text_value,'''', '''''')))
                                     else lower(trim(m4.value))
                                 end AS val,
                                a4.respondent_id
                                FROM survey.answers a4
                                  JOIN survey.sections_questions sq4 ON a4.section_question_id = sq4.id AND a4.survey_id = sq4.survey_id
                                  JOIN survey.questions q4 ON sq4.question_id = q4.question_id
                                  JOIN survey.metadata m4 ON sq4.sections_question_id = m4.sections_question_id AND m4.survey_id = a4.survey_id
                                  JOIN survey.ontology o4 ON m4.ontology_id = o4.id
                                WHERE o4.dimension IS NULL
                       --Step_Sections Tags from Dimension table
                     UNION
                             SELECT DISTINCT 'dim_' || LOWER(d5.name) as dim,
                                 case
                                     WHEN m5.value IS NULL THEN LOWER(TRIM(REPLACE(a5.text_value,'''', '''''')))
                                     else lower(trim(m5.value))
                                 end AS val,
                                a5.respondent_id
                                FROM survey.answers a5
                                  JOIN survey.steps_sections ss5 ON a5.step = ss5.step_display_order AND a5.section = ss5.section_display_order and a5.survey_id = ss5.survey_id AND ss5.effective_from <= NOW() AND ss5.effective_to > NOW()
                                  JOIN survey.metadata m5 ON ss5.steps_sections_id = m5.steps_sections_id AND m5.survey_id = a5.survey_id
                                  JOIN survey.ontology o5 ON m5.ontology_id = o5.id
                                  JOIN survey.dimensions d5 on d5.id = o5.dimension
                       --Step_Sections Tags from Ontology table
                     UNION
                             SELECT DISTINCT 'dim_' || LOWER(REPLACE(o6.tag,' ','_')) as dim,
                                 case
                                     WHEN m6.value IS NULL THEN LOWER(TRIM(REPLACE(a6.text_value,'''', '''''')))
                                     else lower(trim(m6.value))
                                 end AS val,
                                a6.respondent_id
                                FROM survey.answers a6
                                  JOIN survey.steps_sections ss6 ON a6.step = ss6.step_display_order AND a6.section = ss6.section_display_order and a6.survey_id = ss6.survey_id AND ss6.effective_from <= NOW() AND ss6.effective_to > NOW()
                                  JOIN survey.metadata m6 ON ss6.steps_sections_id = m6.steps_sections_id AND m6.survey_id = a6.survey_id
                                  JOIN survey.ontology o6 ON m6.ontology_id = o6.id
                                 WHERE o6.dimension IS NULL
                               ) x
                     where x.val != ''
            and x.respondent_id = :respondentId
                        order by x.dim, x.val
            """;

    public static final String INSERT_INTO_DIMENSION = """
            INSERT INTO <SCHEMA>.<DIM>(value)
            SELECT :val
            WHERE NOT EXISTS (
                SELECT d.value FROM <SCHEMA>.<DIM> d WHERE value = :val
            );
            """;

    // ── fact_sections rows (UC-004 at finalize, UC-008 step 6) ───────────────────────────────

    /**
     * The fact rows a finalized respondent's answers call for, one per distinct (step, step
     * instance, section, section instance) with a saved value. {@code answers.step} and
     * {@code answers.section} are display orders, not ids (a join on {@code steps.id} only ever
     * worked while steps had been inserted in display order, and a renamed step at display order 1
     * gets a new id); {@code respondents.first_access_dt}
     * anchors them to the step and placement the respondent actually saw, and the dimension rows
     * are found through those durable ids (BR-011). The NOT EXISTS guard makes the insert
     * idempotent on the resolved keys.
     */
    public static final String INSERT_MISSING_FACT_SECTION_SQL = """
            insert into <SCHEMA>.fact_sections (
            survey_id,
            respondent_id,
            step_key,
            name,
            step_instance,
            section_key,
            section_instance)
            SELECT DISTINCT
            a.survey_id,
            a.respondent_id,
            ds.id,
            s.name,
            a.step_instance,
            dsec.id,
            a.section_instance
            FROM survey.answers a
            JOIN survey.respondents r on a.respondent_id = r.id
            JOIN survey.steps s
                ON s.survey_id = a.survey_id
               AND s.display_order = a.step
               AND s.effective_from <= r.first_access_dt
               AND s.effective_to > r.first_access_dt
            JOIN <SCHEMA>.dim_step ds ON ds.step_id = s.step_id
            JOIN survey.steps_sections ss
                ON ss.survey_id = a.survey_id
               AND ss.step_display_order = a.step
               AND ss.section_display_order = a.section
               AND ss.effective_from <= r.first_access_dt
               AND ss.effective_to > r.first_access_dt
            JOIN <SCHEMA>.dim_section dsec ON dsec.section_id = ss.section_id
            where a.deleted!= true
            and a.text_value IS NOT NULL
            and a.saved_dt is not null
            and r.finalized_dt is not null
            and a.respondent_id = :respondent_id
            AND NOT EXISTS (
                SELECT 1 FROM <SCHEMA>.fact_sections fs
                WHERE fs.survey_id = a.survey_id
                AND fs.respondent_id = a.respondent_id
                AND fs.step_key = ds.id
                AND fs.step_instance = a.step_instance
                AND fs.section_key = dsec.id
                AND fs.section_instance = a.section_instance
            );
            """;

    /**
     * A respondent's fact rows with the display orders they were built from, so the tag
     * queries below can match an answer to its row the way the insert did (BR-011).
     */
    private static final String RESPONDENT_FACT_ROWS_CTE = """
            WITH fact AS (
                SELECT f.id, f.respondent_id, f.survey_id, f.step_instance, f.section_instance,
                       s.display_order AS step, ss.section_display_order AS section
                FROM <SCHEMA>.fact_sections f
                JOIN survey.respondents r ON r.id = f.respondent_id
                JOIN <SCHEMA>.dim_step ds ON ds.id = f.step_key
                JOIN survey.steps s ON s.step_id = ds.step_id AND s.survey_id = f.survey_id
                     AND s.effective_from <= r.first_access_dt AND s.effective_to > r.first_access_dt
                JOIN <SCHEMA>.dim_section dsec ON dsec.id = f.section_key
                JOIN survey.steps_sections ss ON ss.section_id = dsec.section_id AND ss.step_id = s.step_id
                     AND ss.survey_id = f.survey_id
                     AND ss.effective_from <= r.first_access_dt AND ss.effective_to > r.first_access_dt
                WHERE f.respondent_id = :respondent_id
            )
            """;

    public static final String FIND_MISSING_FACT_SECTION_DIMENSIONS_SQL = RESPONDENT_FACT_ROWS_CTE + """
            SELECT distinct x.key, x.dim, x.val, x.id FROM (
            --Question Tags from Dimensions table
                   SELECT f1.id, a1.respondent_id, LOWER(REPLACE(REPLACE(o1.tag,' ','_'),'-','') || '_key') AS key, 'dim_' || LOWER(d1.name) AS dim,
                        CASE
                            WHEN m1.value IS NULL THEN LOWER(TRIM(REPLACE(a1.text_value,'''', '''''')))
                            ELSE LOWER(TRIM(m1.value))
                        END AS val
                       FROM survey.answers a1
                     JOIN fact f1 ON
                             f1.respondent_id = a1.respondent_id
                             AND f1.step = a1.step
                             AND f1.step_instance = a1.step_instance
                             AND f1.section = a1.section
                             AND f1.section_instance = a1.section_instance
                             AND f1.survey_id = a1.survey_id
                       JOIN survey.questions q1 ON q1.id = a1.question_id
                       JOIN survey.metadata m1 ON m1.question_id = q1.question_id AND m1.survey_id = a1.survey_id
                       JOIN survey.ontology o1 ON m1.ontology_id = o1.id
                       JOIN survey.dimensions d1 ON d1.id = o1.dimension
            --Question Tags from Ontology table
            UNION
                   SELECT f2.id, a2.respondent_id, LOWER(REPLACE(REPLACE(o2.tag,' ','_'),'-','') || '_key') AS key, 'dim_' || LOWER(REPLACE(o2.tag,' ','_')) AS dim,
                        CASE
                            WHEN m2.value IS NULL THEN LOWER(TRIM(REPLACE(a2.text_value,'''', '''''')))
                            ELSE LOWER(TRIM(m2.value))
                        END AS val
                       FROM survey.answers a2
                     JOIN fact f2 ON
                             f2.respondent_id = a2.respondent_id
                             AND f2.step = a2.step
                             AND f2.step_instance = a2.step_instance
                             AND f2.section = a2.section
                             AND f2.section_instance = a2.section_instance
                             AND f2.survey_id = a2.survey_id
                       JOIN survey.questions q2 ON q2.id = a2.question_id
                       JOIN survey.metadata m2 ON m2.question_id = q2.question_id AND m2.survey_id = a2.survey_id
                       JOIN survey.ontology o2 ON m2.ontology_id = o2.id
                     WHERE o2.dimension IS NULL
            --Section_Question Tags from Dimensions table
            UNION
                   SELECT f3.id, a3.respondent_id, LOWER(REPLACE(REPLACE(o3.tag,' ','_'),'-','') || '_key') AS key, 'dim_' || LOWER(d3.name) AS dim,
                        CASE
                            WHEN m3.value IS NULL THEN LOWER(TRIM(REPLACE(a3.text_value,'''', '''''')))
                            ELSE LOWER(TRIM(m3.value))
                        END AS val
                       FROM survey.answers a3
                     JOIN fact f3 ON
                             f3.respondent_id = a3.respondent_id
                             AND f3.step = a3.step
                             AND f3.step_instance = a3.step_instance
                             AND f3.section = a3.section
                             AND f3.section_instance = a3.section_instance
                             AND f3.survey_id = a3.survey_id
                         JOIN survey.sections_questions sq3 ON a3.section_question_id = sq3.id AND a3.survey_id = sq3.survey_id
                         JOIN survey.questions q3 ON sq3.question_id = q3.question_id
                         JOIN survey.metadata m3 ON sq3.sections_question_id = m3.sections_question_id AND m3.survey_id = a3.survey_id
                         JOIN survey.ontology o3 ON m3.ontology_id = o3.id
                        JOIN survey.dimensions d3 ON d3.id = o3.dimension
            --Section_Question Tags from Ontology table
            UNION
                   SELECT f4.id, a4.respondent_id, LOWER(REPLACE(REPLACE(o4.tag,' ','_'),'-','') || '_key') AS key, 'dim_' || LOWER(REPLACE(o4.tag,' ','_')) AS dim,
                        CASE
                            WHEN m4.value IS NULL THEN LOWER(TRIM(REPLACE(a4.text_value,'''', '''''')))
                            ELSE LOWER(TRIM(m4.value))
                        END AS val
                       FROM survey.answers a4
                     JOIN fact f4 ON
                             f4.respondent_id = a4.respondent_id
                             AND f4.step = a4.step
                             AND f4.step_instance = a4.step_instance
                             AND f4.section = a4.section
                             AND f4.section_instance = a4.section_instance
                             AND f4.survey_id = a4.survey_id
                         JOIN survey.sections_questions sq4 ON a4.section_question_id = sq4.id AND a4.survey_id = sq4.survey_id
                         JOIN survey.questions q4 ON sq4.question_id = q4.question_id
                         JOIN survey.metadata m4 ON sq4.sections_question_id = m4.sections_question_id AND m4.survey_id = a4.survey_id
                         JOIN survey.ontology o4 ON m4.ontology_id = o4.id
                       WHERE o4.dimension IS NULL
              --Step_Sections Tags from Dimensions table
            UNION
                   SELECT f5.id, a5.respondent_id, LOWER(REPLACE(REPLACE(o5.tag,' ','_'),'-','') || '_key') AS key, 'dim_' || LOWER(d5.name) AS dim,
                        CASE
                            WHEN m5.value IS NULL THEN LOWER(TRIM(REPLACE(a5.text_value,'''', '''''')))
                            ELSE LOWER(TRIM(m5.value))
                        END AS val
                       FROM survey.answers a5
                     JOIN fact f5 ON
                             f5.respondent_id = a5.respondent_id
                             AND f5.step = a5.step
                             AND f5.step_instance = a5.step_instance
                             AND f5.section = a5.section
                             AND f5.section_instance = a5.section_instance
                             AND f5.survey_id = a5.survey_id
                         JOIN survey.steps_sections ss5 ON a5.step = ss5.step_display_order AND a5.section = ss5.section_display_order AND a5.survey_id = ss5.survey_id AND ss5.effective_from <= NOW() AND ss5.effective_to > NOW()
                         JOIN survey.metadata m5 ON ss5.steps_sections_id = m5.steps_sections_id AND m5.survey_id = a5.survey_id
                         JOIN survey.ontology o5 ON m5.ontology_id = o5.id
                        JOIN survey.dimensions d5 ON d5.id = o5.dimension
             --Step_Sections Tags from Ontology table
            UNION
                   SELECT f6.id, a6.respondent_id, LOWER(REPLACE(REPLACE(o6.tag,' ','_'),'-','') || '_key') AS key, 'dim_' || LOWER(REPLACE(o6.tag,' ','_')) AS dim,
                        CASE
                            WHEN m6.value IS NULL THEN LOWER(TRIM(REPLACE(a6.text_value,'''', '''''')))
                            ELSE LOWER(TRIM(m6.value))
                        END AS val
                       FROM survey.answers a6
                     JOIN fact f6 ON
                             f6.respondent_id = a6.respondent_id
                             AND f6.step = a6.step
                             AND f6.step_instance = a6.step_instance
                             AND f6.section = a6.section
                             AND f6.section_instance = a6.section_instance
                             AND f6.survey_id = a6.survey_id
                         JOIN survey.steps_sections ss6 ON a6.step = ss6.step_display_order AND a6.section = ss6.section_display_order AND a6.survey_id = ss6.survey_id AND ss6.effective_from <= NOW() AND ss6.effective_to > NOW()
                         JOIN survey.metadata m6 ON ss6.steps_sections_id = m6.steps_sections_id AND m6.survey_id = a6.survey_id
                         JOIN survey.ontology o6 ON m6.ontology_id = o6.id
                      WHERE o6.dimension IS NULL
                     ) x
            WHERE x.respondent_id = :respondent_id
            and x.val is not null
            ORDER BY x.id
            """;

    public static final String UPDATE_FACT_SECTION_DIMENSION_VALUE_SQL = """
            UPDATE <SCHEMA>.fact_sections a
            SET <KEY> = x.id
            FROM (SELECT d.id FROM <SCHEMA>.<DIM> d WHERE d.value = :val) x
            WHERE a.id=:factId
            AND a.respondent_id=:respondentId;
            """;

    /**
     * The survey's finalized respondents without fact rows, the back-fill's work list. Scoped
     * to the survey so a respondent of another survey is never selected again and again
     * (BR-009).
     */
    public static final String FIND_MISSING_FACT_SECTION_RESPONDENTS = """
            SELECT r.id
            FROM survey.respondents r
            WHERE r.finalized_dt IS NOT NULL
            AND r.survey_id = :surveyId
            AND NOT EXISTS (
            	SELECT f.respondent_id
            	FROM <SCHEMA>.fact_sections f
            	WHERE f.respondent_id = r.id
            )
            ORDER BY r.id
            """;

    // ── which item a repeated section is about (UC-008 BR-012) ───────────────────────────────

    /**
     * For a respondent's fact rows with a section instance, the question and the item the
     * instance was built from: the Repeat rule in effect at the respondent's anchor whose
     * downstream placement is the row's step and section and whose upstream question is a
     * multi-select, and the item at position {@code section_instance} of that question's list
     * as of the same anchor, ordered as {@code QuestionManager.repeatItems} orders it (display
     * order, then durable item id). Rows the query does not return keep {@code -1}.
     * <p>
     * Columns: fact id, question key, question text, item key, item value, item text, list
     * name, item display order.
     */
    public static final String FIND_REPEATED_ITEM_FACTS_SQL = """
            WITH fact AS (
                SELECT f.id, f.survey_id, f.section_instance, r.first_access_dt AS anchor, ss.steps_sections_id
                FROM <SCHEMA>.fact_sections f
                JOIN survey.respondents r ON r.id = f.respondent_id
                JOIN <SCHEMA>.dim_step ds ON ds.id = f.step_key
                JOIN <SCHEMA>.dim_section dsec ON dsec.id = f.section_key
                JOIN survey.steps_sections ss ON ss.survey_id = f.survey_id
                     AND ss.step_id = ds.step_id AND ss.section_id = dsec.section_id
                     AND ss.effective_from <= r.first_access_dt AND ss.effective_to > r.first_access_dt
                WHERE f.respondent_id = :respondent_id
                  AND f.section_instance > 0
            ),
            rule AS (
                SELECT f.id AS fact_id, f.section_instance, f.anchor,
                       q.question_key, COALESCE(q.short_text, LEFT(q.text, 255)) AS question_text,
                       q.select_group_id
                FROM fact f
                JOIN survey.relationships rel ON rel.survey_id = f.survey_id
                     AND rel.downstream_ss_id = f.steps_sections_id
                     AND rel.downstream_sq_id IS NULL
                     AND rel.effective_from <= f.anchor AND rel.effective_to > f.anchor
                JOIN survey.action_types act ON act.id = rel.action_id AND act.name = 'REPEAT'
                JOIN survey.sections_questions usq ON usq.sections_question_id = rel.upstream_sq_id
                     AND usq.effective_from <= f.anchor AND usq.effective_to > f.anchor
                JOIN survey.questions q ON q.question_id = usq.question_id
                     AND q.effective_from <= f.anchor AND q.effective_to > f.anchor
                JOIN survey.question_types qt ON qt.id = q.type_id
                     AND qt.name IN ('MULTI_SELECT', 'CHECKBOX_GROUP')
            )
            SELECT DISTINCT ON (ru.fact_id)
                   ru.fact_id, ru.question_key, ru.question_text,
                   si.select_item_key, LOWER(TRIM(si.coded_value)), si.display_text, si.list_name, si.display_order
            FROM rule ru
            JOIN LATERAL (
                SELECT s.select_item_key, s.coded_value, s.display_text, s.display_order, sg.name AS list_name,
                       ROW_NUMBER() OVER (ORDER BY s.display_order, s.select_item_id) AS position
                FROM survey.select_items s
                JOIN survey.select_groups sg ON sg.select_group_id = s.select_group_id
                     AND sg.effective_from <= ru.anchor AND sg.effective_to > ru.anchor
                WHERE s.select_group_id = ru.select_group_id
                  AND s.effective_from <= ru.anchor AND s.effective_to > ru.anchor
            ) si ON si.position = ru.section_instance
            ORDER BY ru.fact_id
            """;

    /** Upserts a question into {@code dim_question} by its portable key and returns its id. */
    public static final String UPSERT_DIM_QUESTION_SQL = """
            INSERT INTO <SCHEMA>.dim_question(question_key, value)
            VALUES (CAST(:key AS uuid), :value)
            ON CONFLICT (question_key) DO UPDATE SET value = excluded.value
            RETURNING id
            """;

    /** Upserts an item into {@code dim_item} by its portable key and returns its id. */
    public static final String UPSERT_DIM_ITEM_SQL = """
            INSERT INTO <SCHEMA>.dim_item(select_item_key, value, display_text, list_name, display_order)
            VALUES (CAST(:key AS uuid), :value, :displayText, :listName, :displayOrder)
            ON CONFLICT (select_item_key) DO UPDATE
                SET value = excluded.value, display_text = excluded.display_text,
                    list_name = excluded.list_name, display_order = excluded.display_order
            RETURNING id
            """;

    public static final String UPDATE_FACT_SECTION_ITEM_SQL = """
            UPDATE <SCHEMA>.fact_sections
            SET question_key = :questionKey, item_key = :itemKey
            WHERE id = :factId
            """;

    // ── fact_sections columns and the views (UC-008 step 5) ──────────────────────────────────

    /**
     * The {@code <tag>_key} columns the survey's {@code fact_sections} still lacks: one per
     * dimension table in its schema that is not a named dimension (those get their columns from
     * the tags that use them, the second branch), plus one per tag of a named dimension.
     * {@code survey.dimensions} is site-wide (it has no {@code survey_id}), so the exclusion is
     * narrowed to the dimensions this survey's ontology uses, compared lower-cased as the table
     * is named: another survey's dimension {@code Gender} must not swallow this survey's tag-only
     * {@code gender}, which would leave {@code dim_gender} without a {@code gender_key} and fail
     * every finalize (found by the multilingual e2e suite, 2026-10-03).
     */
    public static final String FIND_DIMENSIONS_TO_ADD_TO_FACT_SECTIONS_TABLE = """
            SELECT X.* FROM(
                SELECT REPLACE(t.table_name, 'dim_', '') || '_key' AS COL,
                t.table_name AS DIM
                FROM information_schema.tables t
                WHERE t.table_schema = :schema
                AND t.table_name NOT LIKE ('fact_%')
                AND t.table_name NOT IN (
                    SELECT LOWER('dim_' || d.name)
                    FROM survey.dimensions d
                    JOIN survey.ontology o ON o.dimension = d.id
                    WHERE o.survey_id = :surveyId)
            UNION
            SELECT LOWER(REPLACE(REPLACE(o.tag,' ','_'),'-','') || '_key') AS col, LOWER('dim_' || d.name) AS dim
            FROM survey.ontology o
            JOIN survey.dimensions d ON o.dimension = d.id
            WHERE o.survey_id = :surveyId) X
            WHERE X.COL not in (
            SELECT column_name
            FROM information_schema.columns
            WHERE table_schema = :schema
            AND table_name = 'fact_sections'
            AND column_name like '%_key'
              )
              order by X.COL;
            """;

    public static final String ADD_DIM_COLUMN_TO_FACT_SECTIONS_TABLE = """
            ALTER TABLE <SCHEMA>.fact_sections
            add column <COL> integer NOT NULL DEFAULT -1;

            ALTER TABLE <SCHEMA>.fact_sections
            ADD CONSTRAINT <COL>_fk FOREIGN KEY (<COL>) REFERENCES <SCHEMA>.<DIM>(id);
            """;

    public static final String DROP_SECTION_VIEW_SQL = """
            DROP VIEW IF EXISTS <SCHEMA>.fact_sections_view CASCADE;
            """;

    public static final String FACT_SECTION_VIEW_SELECT_SQL = """
            CREATE OR REPLACE VIEW <SCHEMA>.fact_sections_view AS (
            SELECT f.id, f.survey_id, f.respondent_id, f.step_key, f.name, f.step_instance, f.section_key, f.section_instance,
            """;

    public static final String FACT_SECTION_VIEW_FROM_SQL = """

            FROM <SCHEMA>.fact_sections f
            """;

    public static final String FACT_VIEW_JOIN_CLAUSE_SQL = """
            join <SCHEMA>.<DIM> <COL> on f.<COL> = <COL>.id
            """;

    public static final String FACT_SECTIONS_VIEW_GRANT_CLAUSE_SQL = """

            GRANT SELECT ON <SCHEMA>.fact_sections_view TO <REPORT_USER>;
            """;

    /**
     * Every dimension the survey's {@code fact_sections} has a key for, as (column, table):
     * the fixed ones ({@code step}, {@code section}, {@code question}, {@code item}) and the
     * tag ones. The view exposes each as the column name without {@code _key}.
     */
    public static final String FIND_FACT_SECTION_JOIN_COLUMNS = """
            SELECT X.* FROM(
                    SELECT REPLACE(t.table_name, 'dim_', '') || '_key' AS COL,
                    t.table_name AS DIM
                    FROM information_schema.tables t
                    WHERE t.table_schema = :schema
                    AND t.table_name  not like ('fact_%')
                    AND t.table_name NOT IN (
                        SELECT LOWER('dim_' || d.name)
                        FROM survey.dimensions d
                        JOIN survey.ontology o ON o.dimension = d.id
                        WHERE o.survey_id = :surveyId)
                UNION
                    SELECT LOWER(REPLACE(REPLACE( o.tag,' ','_'),'-','') || '_key') AS col,
                    LOWER(COALESCE('dim_' || d.name, 'dim_' || replace(o.tag,' ','_'))) as dim
                    FROM survey.ontology o
                    JOIN survey.dimensions d ON o.dimension = d.id
                    WHERE o.survey_id = :surveyId
            ) X
            order by X.COL;
            """;

    /**
     * {@code fact_respondents} as a view over {@code survey.respondents} (BR-010), and
     * {@code fact_respondents_view} over it with the conformed dimensions of the common schema.
     * {@code <SURVEY_ID>} is the survey's id as a literal: the view belongs to one survey.
     * The duration is the time spent answering, {@code finalized_dt - first_access_dt}.
     */
    public static final String CREATE_FACT_RESPONDENTS_VIEW_SQL = """
            CREATE OR REPLACE VIEW <SCHEMA>.fact_respondents AS (
                SELECT r.id, r.survey_id, r.active::boolean, r.logins,
                COALESCE(to_char(r.created_dt,'YYYYMMDD')::integer, 19700101) AS created_key,
                COALESCE(to_char(r.first_access_dt,'YYYYMMDD')::integer, 19700101) AS first_access_key,
                COALESCE(to_char(r.finalized_dt,'YYYYMMDD')::integer, 19700101) AS finalized_key,
                CASE
                    WHEN r.first_access_dt IS NULL AND r.finalized_dt IS NULL THEN 0
                    WHEN r.finalized_dt IS NULL THEN 1
                    ELSE 2
                END AS status,
                CASE
                    WHEN r.finalized_dt IS NULL OR r.first_access_dt IS NULL THEN INTERVAL '0'
                    ELSE r.finalized_dt - r.first_access_dt
                END AS duration
                FROM survey.respondents r
                WHERE r.survey_id = <SURVEY_ID>
            );
            GRANT SELECT ON <SCHEMA>.fact_respondents TO <REPORT_USER>;
            GRANT SELECT ON <SCHEMA>.fact_respondents TO <SURVEY_USER>;
            CREATE OR REPLACE VIEW <SCHEMA>.fact_respondents_view AS (
            	SELECT r.id, r.survey_id, r.active, r.logins,
            	cd.datename as created, fa.datename as first_access, fn.datename as finalized, s.value as status,
            	r.duration
            	FROM <SCHEMA>.fact_respondents r
            	LEFT JOIN surveyreport.dim_date cd ON cd.datekey = r.created_key
            	LEFT JOIN surveyreport.dim_date fa ON fa.datekey = r.first_access_key
            	LEFT JOIN surveyreport.dim_date fn ON fn.datekey = r.finalized_key
            	JOIN surveyreport.dim_status s ON s.id = r.status
            );
            GRANT SELECT ON <SCHEMA>.fact_respondents_view TO <REPORT_USER>;
            GRANT SELECT ON <SCHEMA>.fact_respondents_view TO <SURVEY_USER>;
            """;
}
