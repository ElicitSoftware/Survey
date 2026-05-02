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

import com.vaadin.quarkus.annotation.NormalUIScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
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
@NormalUIScoped
public class ETLRespondentService {

    @PersistenceContext(unitName = "owner")
    EntityManager entityManager;

    @ConfigProperty(name = "quarkus.flyway.owner.placeholders.surveyreport_user", defaultValue = "surveyreport_user")
    String REPORT_USER;

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
    @SuppressWarnings("unchecked")
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
                respondentId + ": " + addOrUpdateRespondentFactSections(respondentId) + " keys";
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
    @SuppressWarnings("unchecked")
    @Transactional
    public String addOrUpdateRespondentFactSections(Integer respondent_id) {

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
            sql = sql.replaceAll("<RESPONDENT_ID>", String.valueOf(respondent_id));
            updateFactQuery = entityManager.createNativeQuery(sql);
            updateFactQuery.executeUpdate();
            item++;
        }
        return String.valueOf(item);
    }
}
