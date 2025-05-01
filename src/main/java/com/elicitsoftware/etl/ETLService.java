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

import io.quarkus.logging.Log;
import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.List;

/**
 * The ETLService class is responsible for performing Extract, Transform, and Load (ETL) operations
 * for the application. It provides methods for updating and building dimension tables,
 * managing fact sections, and creating views in the database. These operations are crucial
 * for integrating and organizing data for analysis and reporting.
 * <p>
 * Fields:
 * - LOGGER: Used for logging events and errors during the ETL process.
 * - entityManager: Provides database access and executes native SQL queries.
 * - REPORT_USER: Specifies the database user performing ETL tasks, often used in SQL scripts.
 * <p>
 * Methods include:
 * - Initialization of the ETL process during application startup.
 * - Updating and building dimension tables and fact sections in the database.
 * - Managing new respondents and their associated data.
 * - Building and updating database views for reporting purposes.
 * <p>
 * Many methods utilize native SQL queries for database interactions and are designed to maintain
 * transactional consistency.
 */
@ApplicationScoped
public class ETLService {

    @Inject
    EntityManager entityManager;

    @ConfigProperty(name = "quarkus.flyway.owner.placeholders.surveyreport_user", defaultValue = "surveyreport_user")
    String REPORT_USER;

    /**
     * Handles the application startup event and initializes the ETL process.
     *
     * @param ev the startup event that triggers the initialization process
     */
    // void onStart(@Observes StartupEvent ev) {
    @Startup
    void init() {

        // Replace this SQL with the appropriate SQL for checking table existence for your DB
        Query checkQuery = entityManager.createNativeQuery("SELECT COUNT(*) FROM surveyreport.dim_section");

        Long rows = (Long) checkQuery.getSingleResult();
        if (rows == 0) {
            Log.info("ETL Service initialization.");
            Log.info("Initializing ETL");
            Log.info("Update Step Dimension Table: " + updateStepDimensionTable());
            Log.info("Update Section Dimension Table: " + updateSectionDimensionTable());
            Log.info("Build Dimension Tables: " + buildDimensionTables());
            Log.info("Build Fact Respondents View: " + buildFactRespondentsView());
            Log.info("Build Fact Sections Table: " + buildFactSectionTable());
            Log.info("Build Dimension Tables: " + buildFactSectionView());
            populateAllFactSectionsTable();
        } else {
            Log.info("ETL Service Init found records in surveyreport.dim_section, No initialization needed.");
        }
    }

    /**
     * Updates the step dimension table in the database using a native SQL query.
     * This method executes an update query defined by the SQL statement referenced
     * in {@code Sql.UPDATE_STEPS_DIMENSION_TABLE_SQL}.
     *
     * @return the number of rows affected by the update operation.
     */
    @Transactional
    public int updateStepDimensionTable() {
        Query query = entityManager.createNativeQuery(Sql.UPDATE_STEPS_DIMENSION_TABLE_SQL);
        return query.executeUpdate();
    }

    /**
     * Updates the Section Dimension table in the database by executing a native SQL query.
     *
     * @return the number of rows affected by the update operation
     */
    @Transactional
    public int updateSectionDimensionTable() {
        Query query = entityManager.createNativeQuery(Sql.UPDATE_SECTIONS_DIMENSION_TABLE_SQL);
        return query.executeUpdate();
    }

    /**
     * Builds new dimension tables by identifying dimension tables that need to be created
     * and invoking the creation process for each identified dimension table.
     * <p>
     * This method uses a native SQL query to fetch the list of new dimension tables, iterates
     * through the result, and processes each dimension table accordingly. It returns a
     * summary string indicating the new dimension tables created.
     *
     * @return A string indicating the dimension tables that were processed, formatted as
     * "new Dimensions tables = [dimension1, dimension2, ...]".
     */
    @Transactional
    public String buildDimensionTables() {
        Query query = entityManager.createNativeQuery(Sql.FIND_NEW_DIMENSION_TABLES_SQL);
        List<String> results = query.getResultList();
        for (String dimension : results) {
            buildDimension(dimension);
        }
        return "new Dimesions tables = " + results;
    }

    /**
     * Builds a new dimension by creating a corresponding table in the database.
     * The method dynamically replaces placeholders in a predefined SQL script
     * with the provided dimension name and the report user, then executes the script.
     *
     * @param dimensionName the name of the dimension for which the table is to be created
     * @return the number of rows affected by the SQL execution
     */
    private int buildDimension(String dimensionName) {
        String script = Sql.CREATE_NEW_DIMENSION_TABLE_SQL.replaceAll("<TABLE_NAME>", dimensionName);
        script = script.replaceAll("<REPORT_USER>", REPORT_USER);
        Query query = entityManager.createNativeQuery(script);
        return query.executeUpdate();
    }

    /**
     * Populates the "Fact Sections" table for all missing respondents.
     * <p>
     * This method retrieves a list of respondents with missing fact section data using
     * a predefined SQL query. It iterates over the list of respondent IDs, processes
     * each respondent by populating their fact section data, and logs progress and
     * results for each operation.
     * <p>
     * The SQL query to find the missing fact section respondents is defined in the
     * Sql.FIND_MISSING_FACT_SECTION_RESPONDENTS constant.
     * <p>
     * For each respondent ID in the result list, the method:
     * 1. Logs the progress of the operation, indicating the current step and total respondents to process.
     * 2. Invokes the populateFactSectionTable method to populate the fact section data for the given respondent ID.
     * 3. Logs the result of the population operation for each respondent.
     */
    private void populateAllFactSectionsTable() {

        Query respondentsQuery = entityManager.createNativeQuery(Sql.FIND_MISSING_FACT_SECTION_RESPONDENTS);
        List<Object> respondents = respondentsQuery.getResultList();
        int r = 1;
        Integer id;
        for (Object respondent_id : respondents) {
            id = (Integer) respondent_id;
            Log.info("progress " + r + " of " + respondents.size());
            Log.info(populateFactSectionTable(id));
            r++;
        }
    }

    /**
     * Populates the fact section table for a given respondent by combining dimension data
     * and section facts related to the respondent.
     *
     * @param respondentId the unique identifier of the respondent for which the fact section
     *                     table will be populated.
     * @return a combined string summarizing the results of populating the dimension tables
     * and saving the section facts for the specified respondent.
     */
    public String populateFactSectionTable(Integer respondentId) {
        String dim = populateDimensionTables(respondentId);
        String facts = saveSectionFacts(respondentId);
        return facts + System.lineSeparator() + dim + System.lineSeparator();
    }

    /**
     * Populates the dimension tables with values associated with the specified respondent ID.
     * Queries for dimension values using the provided respondent ID, processes the results,
     * and inserts them into the appropriate dimension tables.
     *
     * @param respondentId the ID of the respondent whose dimension values are to be populated
     * @return a message indicating the number of dimension tables populated
     */
    @Transactional
    public String populateDimensionTables(Integer respondentId) {
        Query query = entityManager.createNativeQuery(Sql.FIND_DIMENSTION_VALUES_SQL);
        query.setParameter("respondentId", respondentId);
        List<Object[]> results = query.getResultList();
        for (Object[] result : results) {
            String dimension = (String) result[0];
            String value = (String) result[1];
            insertDimensionValue(dimension, value);
        }
        return "Populated Dimesions tables = " + results.size();
    }

    /**
     * Saves the section facts for a given respondent by adding them to the fact_sections table.
     * It associates the provided respondent identifier with specific fact sections and
     * returns a confirmation message including the number of keys processed.
     *
     * @param respondentId the ID of the respondent for whom the section facts are saved
     * @return a string message indicating that the respondent has been added to the fact_sections table and
     * showing the number of keys associated with the respondent
     */
    private String saveSectionFacts(Integer respondentId) {
        return "Addeded respondent " + respondentId + " to fact_sections:" + System.lineSeparator() +
                respondentId + ": " + addRespondentFactSections(respondentId) + " keys";
    }

    /**
     * Inserts a value into a specified dimension table in the database.
     * Constructs a SQL statement using the provided dimension and value,
     * executes the query, and returns the number of records updated.
     *
     * @param dim   the name of the dimension table where the value will be inserted
     * @param value the value to be inserted into the dimension table
     * @return the number of rows affected by the insert operation
     */
    private int insertDimensionValue(String dim, String value) {
        String sql = Sql.INSERT_INTO_DIMENSION.replaceAll("<DIM>", dim).replaceAll("<VAL>", value.replaceAll("'", "''"));
        Query query = entityManager.createNativeQuery(sql);
        return query.executeUpdate();
    }


    /**
     * Adds fact sections for the specified respondent, initializing them with missing fact section data
     * and updating their dimensions and values based on predefined SQL queries.
     *
     * @param respondent_id the unique identifier of the respondent for whom fact sections are being added
     * @return the total count of fact section updates made as a String
     */
    @Transactional
    public String addRespondentFactSections(Integer respondent_id) {

        //Add the base fact rows without the dimensional data
        Query factSectionQuery = entityManager.createNativeQuery(Sql.INSERT_MISSING_FACT_SECTION_SQL);
        factSectionQuery.setParameter("respondent_id", respondent_id);
        factSectionQuery.executeUpdate();

        Query query = entityManager.createNativeQuery(Sql.FIND_MISSING_FACT_SECTION_DIMENSIONS_SQL);
        query.setParameter("respondent_id", respondent_id);
        List<Object[]> queryResults = query.getResultList();

        Query updateFactQuery;
        int item = 1;
        String key;
        String dim;
        String val;
        Integer fact_id;
        for (Object[] result : queryResults) {
            key = (String) result[0];
            dim = (String) result[1];
            val = (String) result[2];
            fact_id = (Integer) result[3];

            String sql = Sql.UPDATE_FACT_SECTION_DIMENSION_VALUE_SQL;
            sql = sql.replace("<KEY>", key);
            sql = sql.replaceAll("<DIM>", dim);
            sql = sql.replaceAll("<VAL>", val);
            sql = sql.replaceAll("<FACT_ID>", String.valueOf(fact_id));
            updateFactQuery = entityManager.createNativeQuery(sql);
            updateFactQuery.executeUpdate();
            item++;
        }
        return String.valueOf(item);
    }

    /**
     * Builds the fact section table by identifying and adding necessary dimension columns.
     * Executes a native SQL query to retrieve dimensions to add to the fact sections table.
     * Iterates through the query results, appending column names to a string buffer and
     * invoking the method to add dimension columns to the table.
     *
     * @return A string containing the newly added dimension table columns with their names.
     */
    @Transactional
    public String buildFactSectionTable() {
        StringBuilder returnValue = new StringBuilder();
        Query query = entityManager.createNativeQuery(Sql.FIND_DIMENSIONS_TO_ADD_TO_FACT_SECTIONS_TABLE);
        List<Object[]> results = query.getResultList();
        for (Object[] result : results) {
            String column = (String) result[0];
            String dimension = (String) result[1];
            returnValue.append(column).append('\n');
            addDinensionColumnsToFactSectionTable(column, dimension);
        }
        return "new Dimesions tables = " + returnValue;
    }

    /**
     * Adds a dimension column to the fact sections table in the database.
     * This method customizes and executes a SQL query to add a new column
     * based on the provided column name and dimension name.
     *
     * @param column    the name of the column to be added to the fact sections table
     * @param dimension the dimension name associated with the column being added
     * @return the number of rows affected by the executed SQL query
     */
    private int addDinensionColumnsToFactSectionTable(String column, String dimension) {
        String sql = Sql.ADD_DIM_COLUMN_TO_FACT_SECTIONS_TABLE.replace("<COL>", column);
        sql = sql.replaceAll("<DIM>", dimension);
        Log.info(sql);
        Query query = entityManager.createNativeQuery(sql);
        return query.executeUpdate();
    }

    /**
     * Builds and updates the fact section view by dynamically constructing and
     * executing SQL queries for creating the view based on the results of a
     * pre-existing query. Handles dependencies and ensures proper formatting
     * of the generated SQL statements.
     * <p>
     * The method performs the following steps:
     * 1. Drops the existing view if it exists.
     * 2. Constructs a SELECT and FROM clause based on database content.
     * 3. Iterates through result columns to dynamically generate SQL join clauses.
     * 4. Executes the constructed SQL to create the view and apply necessary grants.
     * 5. Handles errors related to possible dependencies by logging relevant information.
     *
     * @return A string representation of the SQL query used to create the fact section view.
     * If an error occurs, a message indicating potential dependency issues is returned.
     */
    @Transactional
    public String buildFactSectionView() {

        try {
            Query dropQuery = entityManager.createNativeQuery(Sql.DROP_SECTION_VIEW_SQL);
            dropQuery.executeUpdate();
            StringBuilder selectSQL = new StringBuilder(Sql.FACT_SECTION_VIEW_SELECT_SQL);
            StringBuilder fromSQL = new StringBuilder(Sql.FACT_SECTION_VIEW_FROM_SQL);

            Query query = entityManager.createNativeQuery(Sql.FIND_FACT_SECTION_JOIN_COLUMNS);
            List<Object[]> results = query.getResultList();
            for (Object[] result : results) {
                String column = (String) result[0];
                String dimension = (String) result[1];
                selectSQL.append("    ").append(column).append(".value as ").append(column.replace("_key", "")).append(",").append(System.lineSeparator());
                fromSQL.append(Sql.FACT_VIEW_JOIN_CLAUSE_SQL.replace("<COL>", column).replace("<DIM>", dimension));
            }
            String createSQL = selectSQL.toString();
            // remove the last comma
            createSQL = createSQL.substring(0, createSQL.length() - 2);
            createSQL = createSQL + fromSQL + "); " + Sql.FACT_SECTIONS_VIEW_GRANT_CLAUSE_SQL.replace("<REPORT_USER>", REPORT_USER);
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
     * Builds the `fact_respondents_view` database view by executing a SQL script
     * that replaces a placeholder for the report user. The SQL script is defined
     * in `Sql.CREATE_FACT_RESPONDENTS_VIEW_SQL` and parameterized with the `REPORT_USER`
     * field of the containing class. This method ensures that the view is created
     * or updated as needed.
     * <p>
     * The method wraps the execution in a `try-catch` block to handle any exceptions
     * that may occur during the execution of the SQL. In case of an exception,
     * it returns the message from the root cause of the exception.
     * <p>
     * This method is transactional, ensuring that changes are applied in a database
     * transaction.
     *
     * @return A status message indicating either the successful creation of the
     * `surveyreport.fact_respondents_view` database view or an error message
     * in case of failure.
     */
    @Transactional
    public String buildFactRespondentsView() {
        try {
            Query query = entityManager.createNativeQuery(Sql.CREATE_FACT_RESPONDENTS_VIEW_SQL.replace("<REPORT_USER>", REPORT_USER));
            query.executeUpdate();
            return "Created surveyreport.fact_respondents_view";
        } catch (Exception e) {
            return e.getCause().getMessage();
        }
    }

    /**
     * Retrieves a list of identifiers for new respondents who have completed their surveys.
     * The results are limited to the specified maximum number.
     *
     * @param limit the maximum number of results to retrieve
     * @return a list of respondent identifiers (Long) who have completed their surveys
     */
    private List<Long> fineNewRespondents(Integer limit) {
        //First find all the respondents that have finished their surveys
        Query respondentsQuery = entityManager.createNativeQuery(Sql.NEW_RESPONDENTS_SQL);
        respondentsQuery.setMaxResults(limit);
        return respondentsQuery.getResultList();
    }

    /**
     * Adds the given list of respondent identifiers to the fact section in the database
     * by executing a native SQL query to insert missing fact section records.
     *
     * @param newRespondents a list of respondent IDs to be added to the fact section
     */
//    private void addRespondentsToFactSection(List<Long> newRespondents) {
//        //Add the base fact rows without the dimensional data
//        Query factSectionQuery = entityManager.createNativeQuery(Sql.INSERT_MISSING_FACT_SECTION_SQL);
//        factSectionQuery.setParameter("respondents", newRespondents);
//        factSectionQuery.executeUpdate();
//    }


    /**
     * Updates the facts in the database based on the specified parameters and returns the status report as a String.
     *
     * @param result An array of Objects containing necessary data fields used for building and executing the SQL query.
     *               It includes the dimension name, key, and tag values to replace placeholders in the SQL query.
     * @param newRespondents A List of Long values representing the IDs of new respondents to be processed and added
     *                       during the update operation.
     * @return A String representing the result of the SQL update operation, including the number of records updated
     *         for the specified key in the result parameter.
     */
//    private String updateFacts(Object[] result, List<Long> newRespondents) {
//        StringBuilder response = new StringBuilder();
//        String sql = Sql.NEW_FIND_MISSING_FACT_SECTION_DIMENSIONS_SQL;
//        sql = sql.replaceAll("<DIM>", (String) result[0]);
//        sql = sql.replaceAll("<KEY>", (String) result[1]);
//        sql = sql.replaceAll("<TAG>", (String) result[2]);
//        Query updateQuery = entityManager.createNativeQuery(sql);
//        updateQuery.setParameter("respondents", newRespondents);
//        response.append(result[1]).append(": ").append(updateQuery.executeUpdate()).append(System.lineSeparator());
//        LOGGER.info(result[1] + ": " + updateQuery.executeUpdate());
//        return response.toString();
//    }
}
