package com.elicitsoftware.etl;

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

/**
 * The `Sql` class serves as a container for SQL query strings used for various database operations.
 * It contains predefined SQL statements as constants, which are used to interact with dimension tables,
 * fact tables, views, and other database structures.
 * <p>
 * This class is designed to provide a centralized place for managing SQL queries, ensuring easier
 * maintainability and reusability of code related to database operations.
 * <p>
 * Fields:
 * - UPDATE_STEPS_DIMENSION_TABLE_SQL: SQL query for updating the steps dimension table.
 * - UPDATE_SECTIONS_DIMENSION_TABLE_SQL: SQL query for updating the sections dimension table.
 * - FIND_NEW_DIMENSION_TABLES_SQL: SQL query to find new dimension tables.
 * - CREATE_NEW_DIMENSION_TABLE_SQL: SQL statement to create new dimension tables.
 * - FIND_DIMENSTION_VALUES_SQL: SQL query for finding dimension values.
 * - INSERT_INTO_DIMENSION: SQL query to insert values into a dimension table.
 * - ADD_DIM_COLUMN_TO_FACT_ANSWER_TABLE: SQL query to add a dimension column to a fact answer table.
 * - FACT_VIEW_JOIN_CLAUSE_SQL: SQL fragment representing a join clause in a fact view.
 * - FACT_SECTIONS_VIEW_GRANT_CLAUSE_SQL: SQL query to grant permissions on fact sections views.
 * - NEW_RESPONDENTS_SQL: SQL query to identify new respondents in a dataset.
 * - INSERT_MISSING_FACT_SECTION_SQL: SQL statement to insert missing fact section entries.
 * - INSERT_ALL_RESPONDENTS_INTO_FACT_RESPONDENTS_SQL: SQL query to insert all respondents into the fact respondents table.
 * - CREATE_FACT_RESPONDENTS_VIEW_SQL: SQL query to create a view for fact respondents.
 * - FIND_DIMENSIONS_TO_ADD_TO_FACT_SECTIONS_TABLE: SQL query for finding dimensions to add to the fact sections table.
 * - ADD_DIM_COLUMN_TO_FACT_SECTIONS_TABLE: SQL query to add a dimension column to the fact sections table.
 * - DROP_SECTION_VIEW_SQL: SQL query to drop a section view.
 * - FACT_SECTION_VIEW_SELECT_SQL: SQL fragment representing the "SELECT" portion of a fact section view.
 * - FACT_SECTION_VIEW_FROM_SQL: SQL fragment representing the "FROM" portion of a fact section view.
 * - FIND_FACT_SECTION_JOIN_COLUMNS: SQL query to identify join columns in fact sections.
 * - FACT_SECTIONS_KEYS: SQL statement representing keys used in fact sections.
 * - NEW_FIND_MISSING_FACT_SECTION_DIMENSIONS_SQL: SQL query to identify missing fact section dimensions in a new context.
 * - FIND_MISSING_FACT_SECTION_DIMENSIONS_SQL: SQL query to find missing fact section dimensions.
 * - UPDATE_FACT_SECTION_DIMENSION_VALUE_SQL: SQL query to update dimension values in a fact section.
 * - FIND_MISSING_FACT_SECTION_RESPONDENTS: SQL query to find respondents missing in fact sections.
 */
public final class Sql {

    public static final String UPDATE_STEPS_DIMENSION_TABLE_SQL = """
            INSERT INTO surveyreport.dim_step(id, value)
            select s.id, s.dimension_name
            from survey.steps s
            ON CONFLICT (id) DO UPDATE
                SET value = excluded.value
            """;

    public static final String UPDATE_SECTIONS_DIMENSION_TABLE_SQL = """
            INSERT INTO surveyreport.dim_section(id, value)
            select s.id, s.dimension_name
            from survey.sections s
            ON CONFLICT (id) DO UPDATE
                SET value = excluded.value
            """;
    public static final String FIND_NEW_DIMENSION_TABLES_SQL = """
                              select d.name from (
                               select distinct 'dim_'|| lower(d1.name) as name
                               from survey.metadata md1
                               join survey.ontology o1 on md1.ontology_id = o1.id
                               join survey.dimensions d1 on d1.id = o1.dimension
                               where 'dim_'|| lower(d1.name) not in (
                                   SELECT t1.table_name
                                   FROM information_schema.tables t1
                                   WHERE t1.table_schema = 'surveyreport'
                               )
            UNION
                select distinct lower('dim_' || replace(o4.tag,' ','_')) name
                               from survey.metadata md4
                               join survey.ontology o4 on md4.ontology_id = o4.id
                               where o4.dimension is null
                               and lower('dim_' || replace(o4.tag,' ','_')) not in (
                                   SELECT t4.table_name
                                   FROM information_schema.tables t4
                                   WHERE t4.table_schema = 'surveyreport'
                               )
            UNION
                               select distinct lower('dim_' || replace(o2.tag,' ','_')) name
                               from survey.metadata md2
                               join survey.ontology o2 on md2.ontology_id = o2.id
                               join survey.steps_sections ss2 ON md2.step_section_id = ss2.id
                               join survey.sections s2 on ss2.section_id = s2.id
                               where o2.dimension is null
                and lower('dim_' || replace(o2.tag,' ','_')) not in (
                                   SELECT t2.table_name
                                   FROM information_schema.tables t2
                                   WHERE t2.table_schema = 'surveyreport'
                               )
                           UNION
                               select distinct lower('dim_' || replace(o3.tag,' ','_')) name
                               from survey.metadata md3
                               join survey.ontology o3 on md3.ontology_id = o3.id
                               join survey.sections_questions sq3 ON md3.section_question_id = sq3.id
                               join survey.questions q3 on sq3.question_id = q3.id
                               where o3.dimension is null
                               and lower('dim_' || replace(o3.tag,' ','_')) not in (
                                   SELECT t3.table_name
                                   FROM information_schema.tables t3
                                   WHERE t3.table_schema = 'surveyreport'
                               )
                           )d
                           order by d.name;
            """;
    public static final String CREATE_NEW_DIMENSION_TABLE_SQL = """
            DROP TABLE IF EXISTS surveyreport.<TABLE_NAME>;
            DROP SEQUENCE IF EXISTS surveyreport.<TABLE_NAME>_seq;
            
            CREATE SEQUENCE surveyreport.<TABLE_NAME>_seq INCREMENT 1 START 1;
            
            CREATE TABLE surveyreport.<TABLE_NAME>(
                id integer NOT NULL DEFAULT NEXTVAL('surveyreport.<TABLE_NAME>_seq'),
                value character varying(255),
            	CONSTRAINT <TABLE_NAME>_pk PRIMARY KEY (id),
            	CONSTRAINT <TABLE_NAME>_un UNIQUE (value)
            );
            
            CREATE INDEX <TABLE_NAME>_idx ON surveyreport.<TABLE_NAME>(id);
            CREATE INDEX <TABLE_NAME>_val_idx ON surveyreport.<TABLE_NAME>(value);
            GRANT SELECT ON surveyreport.<TABLE_NAME> TO <REPORT_USER>; 
            INSERT INTO surveyreport.<TABLE_NAME>(id, value) values(-1,null);
            """;

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
                                  JOIN survey.metadata m1 ON a1.question_id = m1.question_id AND m1.survey_id = a1.survey_id
                                  JOIN survey.questions q1 ON m1.question_id = q1.id
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
                                  JOIN survey.metadata m2 ON a2.question_id = m2.question_id AND m2.survey_id = a2.survey_id
                                  JOIN survey.questions q2 ON m2.question_id = q2.id
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
                                  JOIN survey.questions q3 ON sq3.question_id = q3.id
                                  JOIN survey.metadata m3 ON sq3.id = m3.section_question_id AND m3.survey_id = a3.survey_id
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
                                  JOIN survey.questions q4 ON sq4.question_id = q4.id
                                  JOIN survey.metadata m4 ON sq4.id = m4.section_question_id AND m4.survey_id = a4.survey_id
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
                                  JOIN survey.steps_sections ss5 ON a5.step = ss5.step_display_order AND a5.section = ss5.section_display_order and a5.survey_id = ss5.survey_id
                                  JOIN survey.metadata m5 ON ss5.id = m5.step_section_id AND m5.survey_id = a5.survey_id
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
                                  JOIN survey.steps_sections ss6 ON a6.step = ss6.step_display_order AND a6.section = ss6.section_display_order and a6.survey_id = ss6.survey_id
                                  JOIN survey.metadata m6 ON ss6.id = m6.step_section_id AND m6.survey_id = a6.survey_id
                                  JOIN survey.ontology o6 ON m6.ontology_id = o6.id
                                 WHERE o6.dimension IS NULL
                               ) x
                     where x.val != ''
            and x.respondent_id = :respondentId
                        order by x.dim, x.val
            """;

    public static final String INSERT_INTO_DIMENSION = """
            INSERT INTO surveyreport.<DIM>(value)
            SELECT '<VAL>'
            WHERE NOT EXISTS (
                SELECT d.value FROM surveyreport.<DIM> d WHERE value = '<VAL>'
            );
            """;

    public static final String ADD_DIM_COLUMN_TO_FACT_ANSWER_TABLE = """
            ALTER TABLE surveyreport.fact_answers
            add column <COL> integer NOT NULL DEFAULT -1;
            
            ALTER TABLE surveyreport.fact_answers
            ADD CONSTRAINT <COL>_fk FOREIGN KEY (<COL>) REFERENCES surveyreport.<DIM>(id);
            """;

    public static final String FACT_VIEW_JOIN_CLAUSE_SQL = """
            join surveyreport.<DIM> <COL> on f.<COL> = <COL>.id
            """;

    public static final String FACT_SECTIONS_VIEW_GRANT_CLAUSE_SQL = """
            
            GRANT SELECT ON surveyreport.fact_sections_view TO <REPORT_USER>;
            """;

    public static final String NEW_RESPONDENTS_SQL = """
            SELECT r.id
            FROM survey.respondents r
            LEFT JOIN surveyreport.fact_sections f on r.id = f.respondent_id
            WHERE r.finalized_dt is not null
            and r.survey_id = 1
            AND f.respondent_id IS NULL
            order by r.id;
            """;

    public static final String INSERT_MISSING_FACT_SECTION_SQL = """
            insert into surveyreport.fact_sections (
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
            a.step,
            s.name,
            a.step_instance,
            a.section,
            a.section_instance
            FROM survey.answers a
            JOIN survey.respondents r on a.respondent_id = r.id
            JOIN survey.steps s on a.step = s.id
            where a.deleted!= true
            and a.text_value IS NOT NULL
            and a.saved_dt is not null
            and r.finalized_dt is not null
            and a.survey_id = 1
            and a.respondent_id = :respondent_id
            AND NOT EXISTS (
                SELECT 1 FROM surveyreport.fact_sections fs 
                WHERE fs.survey_id = a.survey_id
                AND fs.respondent_id = a.respondent_id
                AND fs.step_key = a.step
                AND fs.step_instance = a.step_instance
                AND fs.section_key = a.section
                AND fs.section_instance = a.section_instance
            );
            """;

    public static final String INSERT_ALL_RESPONDENTS_INTO_FACT_RESPONDENTS_SQL = """
            INSERT INTO surveyreport.fact_respondents (id,survey_id,active,logins,created_key,first_access_key,finalized_key,status,duration)
                SELECT r.id, r.survey_id, r.active::boolean, r.logins,
                COALESCE(to_char(r.created_dt,'YYYYMMDD')::numeric, 19700101) as created_key,
                COALESCE(to_char(r.first_access_dt,'YYYYMMDD')::numeric, 19700101) as first_access_key,
                COALESCE(to_char(r.finalized_dt,'YYYYMMDD')::numeric, 19700101) as finalized_key,
                CASE
                    WHEN r.first_access_dt IS NULL AND r.finalized_dt IS NULL THEN 0
                    WHEN r.first_access_dt IS NOT NULL AND r.finalized_dt IS NULL THEN 1
                    ELSE 2
                END AS status,
                CASE
                    WHEN r.finalized_dt IS NULL then INTERVAL '0'
                    ELSE r.finalized_dt - r.created_dt
                END as duration
            FROM survey.respondents r
            LEFT JOIN surveyreport.fact_respondents f on r.id = f.id
            where f.id is null
            ORDER BY r.id;
            """;

    public static final String CREATE_FACT_RESPONDENTS_VIEW_SQL = """
            CREATE OR REPLACE VIEW surveyreport.fact_respondents_view AS (
            	SELECT r.id, r.survey_id, r.active::boolean, r.logins,
            	cd.datename as created, fa.datename as first_access, fn.datename as finalized, s.value as status,
            	r.duration
            	FROM surveyreport.fact_respondents r
            	JOIN surveyreport.dim_date cd ON cd.datekey = r.created_key
            	JOIN surveyreport.dim_date fa ON fa.datekey = r.first_access_key
            	JOIN surveyreport.dim_date fn ON fn.datekey = r.finalized_key
            	JOIN surveyreport.dim_status s ON s.id = r.status
            );
            GRANT SELECT ON surveyreport.fact_respondents_view TO <REPORT_USER>; 
            """;

    public static final String FIND_DIMENSIONS_TO_ADD_TO_FACT_SECTIONS_TABLE = """
            SELECT X.* FROM(
                SELECT REPLACE(t.table_name, 'dim_', '') || '_key' AS COL,
                t.table_name AS DIM
                FROM information_schema.tables t
                WHERE t.table_schema = 'surveyreport'
                AND t.table_name NOT LIKE ('fact_%')
                AND t.table_name NOT IN ('dim_date', 'dim_status')
                AND t.table_name NOT IN (select 'dim_' || d.name from survey.dimensions d)
            UNION
            SELECT LOWER(REPLACE(REPLACE(o.tag,' ','_'),'-','') || '_key') AS col, LOWER('dim_' || d.name) AS dim
            FROM survey.ontology o
            JOIN survey.dimensions d ON o.dimension = d.id
            WHERE o.dimension IS NOT NULL) X
            WHERE X.COL not in (
            SELECT column_name
            FROM information_schema.columns
            WHERE table_schema = 'surveyreport'
            AND table_name = 'fact_sections'
            AND column_name like '%_key'
              )
              order by X.COL;
            """;

    public static final String ADD_DIM_COLUMN_TO_FACT_SECTIONS_TABLE = """
            ALTER TABLE surveyreport.fact_sections
            add column <COL> integer NOT NULL DEFAULT -1;
            
            ALTER TABLE surveyreport.fact_sections
            ADD CONSTRAINT <COL>_fk FOREIGN KEY (<COL>) REFERENCES surveyreport.<DIM>(id);
            """;

    public static final String DROP_SECTION_VIEW_SQL = """
            DROP VIEW IF EXISTS surveyreport.fact_sections_view CASCADE;
            """;

    public static final String FACT_SECTION_VIEW_SELECT_SQL = """          
            CREATE OR REPLACE VIEW surveyreport.fact_sections_view AS (
            SELECT f.id, f.survey_id, f.respondent_id, f.step_key, f.name, f.step_instance, f.section_key, f.section_instance, 
            """;

    public static final String FACT_SECTION_VIEW_FROM_SQL = """
            
            FROM surveyreport.fact_sections f
            """;

    public static final String FIND_FACT_SECTION_JOIN_COLUMNS = """
            SELECT X.* FROM(
                    SELECT REPLACE(t.table_name, 'dim_', '') || '_key' AS COL,
                    t.table_name AS DIM
                    FROM information_schema.tables t
                    WHERE t.table_schema = 'surveyreport'
                    AND t.table_name  not like ('fact_%')
                    AND t.table_name not in ('dim_date','dim_status')
                    AND t.table_name NOT IN (select 'dim_' || d.name from survey.dimensions d)
                UNION
                    SELECT LOWER(REPLACE(REPLACE( o.tag,' ','_'),'-','') || '_key') AS col,
                    LOWER(COALESCE('dim_' || d.name, 'dim_' || replace(o.tag,' ','_'))) as dim
                    FROM survey.
                    ontology o
                    JOIN survey.dimensions d ON o.dimension = d.id
            ) X
            order by X.COL;
            """;

    public static final String FACT_SECTIONS_KEYS = """
            select distinct 'dim_'|| lower(d1.name) as dim, lower(Replace(Replace(o1.tag,' ','_'),'-','')) || '_key' as key, lower(Replace(Replace(o1.tag,' ','_'),'-','')) as tag
            from survey.metadata md1
            join survey.ontology o1 on md1.ontology_id = o1.id
            join survey.dimensions d1 on d1.id = o1.dimension
            order by tag
            """;

    public static final String NEW_FIND_MISSING_FACT_SECTION_DIMENSIONS_SQL = """
            update surveyreport.fact_sections
            set <KEY> = sub.dim_id
            from (
            		select distinct y.id as dim_id, x.sec, x.val, x.id as fact_id
              			FROM (
                               --Question Tags
                                      SELECT f1.id, f1.name as sec, a1.respondent_id,
                                           CASE
                                               WHEN m1.value = '' THEN LOWER(TRIM(REPLACE(a1.text_value,'''', '''''')))
                                               ELSE LOWER(TRIM(m1.value))
                                           END AS val
                                          FROM survey.answers a1
                                        JOIN surveyreport.fact_sections f1 ON
                                                f1.respondent_id = a1.respondent_id
                                                AND f1.step_key = a1.step
                                                AND f1.step_instance = a1.step_instance
                                                AND f1.section_key = a1.section
                                                AND f1.section_instance = a1.section_instance
                                                AND f1.survey_id = a1.survey_id
                                          JOIN survey.metadata m1 ON a1.question_id = m1.question_id AND m1.survey_id = a1.survey_id
                                          JOIN survey.questions q1 ON m1.question_id = q1.id
                                          JOIN survey.ontology o1 ON m1.ontology_id = o1.id
                                          JOIN survey.dimensions d1 ON d1.id = o1.dimension
            					where lower(o1.tag) = '<TAG>'
            					-- and a1.respondent_id in :respondents
                               --Section_Question Tags
                               UNION
                                      SELECT f2.id, f2.name as sec, a2.respondent_id,
                                           CASE
                                               WHEN m2.value = '' THEN LOWER(TRIM(REPLACE(a2.text_value,'''', '''''')))
                                               ELSE LOWER(TRIM(m2.value))
                                           END AS val
                                          FROM survey.answers a2
                                        JOIN surveyreport.fact_sections f2 ON
                                                f2.respondent_id = a2.respondent_id
                                                AND f2.step_key = a2.step
                                                AND f2.step_instance = a2.step_instance
                                                AND f2.section_key = a2.section
                                                AND f2.section_instance = a2.section_instance
                                                AND f2.survey_id = a2.survey_id
                                            JOIN survey.sections_questions sq2 ON a2.section_question_id = sq2.id AND a2.survey_id = sq2.survey_id
                                            JOIN survey.questions q2 ON sq2.question_id = q2.id
                                            JOIN survey.metadata m2 ON sq2.id = m2.section_question_id AND m2.survey_id = a2.survey_id
                                            JOIN survey.ontology o2 ON m2.ontology_id = o2.id
                                           JOIN survey.dimensions d2 ON d2.id = o2.dimension
            					where lower(o2.tag) = '<TAG>'
            					-- and a2.respondent_id in :respondents
                                 --Step_Sections Tags
                               UNION
                                      SELECT f3.id, f3.name as sec, a3.respondent_id,
                                           CASE
                                               WHEN m3.value = '' THEN LOWER(TRIM(REPLACE(a3.text_value,'''', '''''')))
                                               ELSE LOWER(TRIM(m3.value))
                                           END AS val
                                          FROM survey.answers a3
                                        JOIN surveyreport.fact_sections f3 ON
                                                f3.respondent_id = a3.respondent_id
                                                AND f3.step_key = a3.step
                                                AND f3.step_instance = a3.step_instance
                                                AND f3.section_key = a3.section
                                                AND f3.section_instance = a3.section_instance
                                                AND f3.survey_id = a3.survey_id
                                            JOIN survey.steps_sections ss3 ON a3.step = ss3.step_display_order AND a3.section = ss3.section_display_order AND a3.survey_id = ss3.survey_id
                                            JOIN survey.metadata m3 ON ss3.id = m3.step_section_id AND m3.survey_id = a3.survey_id
                                            JOIN survey.ontology o3 ON m3.ontology_id = o3.id
                                           JOIN survey.dimensions d3 ON d3.id = o3.dimension
            							   where lower(o3.tag) = '<TAG>'
            							   -- and a3.respondent_id in :respondents
                                        ) x
            					Join surveyreport.<DIM> y	on y.value = x.val) sub
            where name = sub.sec
            and id = sub.fact_id
            and respondent_id in :respondents
            """;

    public static final String FIND_MISSING_FACT_SECTION_DIMENSIONS_SQL = """
            SELECT distinct x.key, x.dim, x.val, x.id FROM (
            --Question Tags from Dimensions table
                   SELECT f1.id, a1.respondent_id, LOWER(REPLACE(REPLACE(o1.tag,' ','_'),'-','') || '_key') AS key, 'dim_' || LOWER(d1.name) AS dim,
                        CASE
                            WHEN m1.value IS NULL THEN LOWER(TRIM(REPLACE(a1.text_value,'''', '''''')))
                            ELSE LOWER(TRIM(m1.value))
                        END AS val
                       FROM survey.answers a1
                     JOIN surveyreport.fact_sections f1 ON
                             f1.respondent_id = a1.respondent_id
                             AND f1.step_key = a1.step
                             AND f1.step_instance = a1.step_instance
                             AND f1.section_key = a1.section
                             AND f1.section_instance = a1.section_instance
                             AND f1.survey_id = a1.survey_id
                       JOIN survey.metadata m1 ON a1.question_id = m1.question_id AND m1.survey_id = a1.survey_id
                       JOIN survey.questions q1 ON m1.question_id = q1.id
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
                     JOIN surveyreport.fact_sections f2 ON
                             f2.respondent_id = a2.respondent_id
                             AND f2.step_key = a2.step
                             AND f2.step_instance = a2.step_instance
                             AND f2.section_key = a2.section
                             AND f2.section_instance = a2.section_instance
                             AND f2.survey_id = a2.survey_id
                       JOIN survey.metadata m2 ON a2.question_id = m2.question_id AND m2.survey_id = a2.survey_id
                       JOIN survey.questions q2 ON m2.question_id = q2.id
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
                     JOIN surveyreport.fact_sections f3 ON
                             f3.respondent_id = a3.respondent_id
                             AND f3.step_key = a3.step
                             AND f3.step_instance = a3.step_instance
                             AND f3.section_key = a3.section
                             AND f3.section_instance = a3.section_instance
                             AND f3.survey_id = a3.survey_id
                         JOIN survey.sections_questions sq3 ON a3.section_question_id = sq3.id AND a3.survey_id = sq3.survey_id
                         JOIN survey.questions q3 ON sq3.question_id = q3.id
                         JOIN survey.metadata m3 ON sq3.id = m3.section_question_id AND m3.survey_id = a3.survey_id
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
                     JOIN surveyreport.fact_sections f4 ON
                             f4.respondent_id = a4.respondent_id
                             AND f4.step_key = a4.step
                             AND f4.step_instance = a4.step_instance
                             AND f4.section_key = a4.section
                             AND f4.section_instance = a4.section_instance
                             AND f4.survey_id = a4.survey_id
                         JOIN survey.sections_questions sq4 ON a4.section_question_id = sq4.id AND a4.survey_id = sq4.survey_id
                         JOIN survey.questions q4 ON sq4.question_id = q4.id
                         JOIN survey.metadata m4 ON sq4.id = m4.section_question_id AND m4.survey_id = a4.survey_id
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
                     JOIN surveyreport.fact_sections f5 ON
                             f5.respondent_id = a5.respondent_id
                             AND f5.step_key = a5.step
                             AND f5.step_instance = a5.step_instance
                             AND f5.section_key = a5.section
                             AND f5.section_instance = a5.section_instance
                             AND f5.survey_id = a5.survey_id
                         JOIN survey.steps_sections ss5 ON a5.step = ss5.step_display_order AND a5.section = ss5.section_display_order AND a5.survey_id = ss5.survey_id
                         JOIN survey.metadata m5 ON ss5.id = m5.step_section_id AND m5.survey_id = a5.survey_id
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
                     JOIN surveyreport.fact_sections f6 ON
                             f6.respondent_id = a6.respondent_id
                             AND f6.step_key = a6.step
                             AND f6.step_instance = a6.step_instance
                             AND f6.section_key = a6.section
                             AND f6.section_instance = a6.section_instance
                             AND f6.survey_id = a6.survey_id
                         JOIN survey.steps_sections ss6 ON a6.step = ss6.step_display_order AND a6.section = ss6.section_display_order AND a6.survey_id = ss6.survey_id
                         JOIN survey.metadata m6 ON ss6.id = m6.step_section_id AND m6.survey_id = a6.survey_id
                         JOIN survey.ontology o6 ON m6.ontology_id = o6.id
                      WHERE o6.dimension IS NULL
                     ) x
            WHERE x.respondent_id = :respondent_id
            and x.val is not null
            ORDER BY x.id
            """;

    public static final String UPDATE_FACT_SECTION_DIMENSION_VALUE_SQL = """
            UPDATE surveyreport.fact_sections a
            SET <KEY> = x.id
            FROM (SELECT d.id FROM surveyreport.<DIM> d WHERE d.value = '<VAL>') x
            WHERE a.id=<FACT_ID>
            AND a.respondent_id=<RESPONDENT_ID>;
            """;


    public static final String FIND_MISSING_FACT_SECTION_RESPONDENTS = """
            SELECT r.id
            FROM survey.respondents r
            WHERE r.finalized_dt IS NOT NULL
            AND NOT EXISTS (
            	SELECT f.respondent_id
            	FROM surveyreport.fact_sections f
            	WHERE f.respondent_id = r.id
            )
            ORDER BY r.id
            """;
}
