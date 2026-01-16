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
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import io.quarkus.panache.common.Parameters;
import jakarta.persistence.*;

import java.util.List;

/**
 * Represents the relationship between steps and sections in a survey.
 * This entity allows the mapping of steps within sections and manages
 * display orders of both steps and sections in a survey.
 * <p>
 * This class is annotated as an entity and mapped to the "steps_sections" table
 * in the "survey" schema. It is used to define the relationships between steps
 * and sections and provides query methods to retrieve the data based on specific criteria.
 * <p>
 * Named Queries:
 * 1. StepsSections.findByDisplayKey - Finds all entries by an exact display key value.
 * 2. StepsSections.findByDisplayKeyQuery - Finds all entries where the display key matches
 * a given pattern using a "like" query.
 * 3. StepsSections.findBySurveyId - Finds all entries by the survey ID.
 * <p>
 * Relationships:
 * - Many-to-one relationship with the `Step` entity.
 * - Many-to-one relationship with the `Section` entity.
 * <p>
 * Key Fields:
 * - `displaykey`: Represents the display key used to uniquely identify the record
 * and assist in querying.
 * - `stepDisplayOrder` and `sectionDisplayOrder`: Used for ordering the steps and sections display.
 * <p>
 * Utility Methods:
 * - `findByDisplayKeyQuery(String key)`: Retrieves a list of entries where the `displaykey` matches the specified pattern.
 * - `findFirstByDisplayKeyQuery(String key)`: Retrieves the first entry where the `displaykey` matches the specified pattern.
 * - `findByDisplayKey(DisplayKey key)`: Retrieves a single entry by the exact `DisplayKey` object value.
 * - `findBySurveyId(int surveyId)`: Retrieves a list of entries filtered by a specified survey ID.
 * <p>
 * Additionally, this class manages a transient `DisplayKey` to wrap around the `displaykey` string for additional processing.
 */
@Entity
@Table(name = "steps_sections", schema = "survey")
@NamedQueries({@NamedQuery(name = "StepsSections.findByDisplayKey", query = "SELECT s FROM StepsSections s where s.displaykey = :displaykey order by s.displaykey"),
        @NamedQuery(name = "StepsSections.findByDisplayKeyQuery", query = "SELECT s FROM StepsSections s where s.displaykey like :displaykey order by s.displaykey"),
        @NamedQuery(name = "StepsSections.findBySurveyId", query = "select s from StepsSections s where s.surveyId = :surveyId order by s.displaykey")})
public class StepsSections extends PanacheEntityBase {

    @Id
    @SequenceGenerator(name = "STEPS_SECTIONS_ID_GENERATOR", schema = "survey", sequenceName = "steps_sections_seq", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "STEPS_SECTIONS_ID_GENERATOR")
    @Column(unique = true, nullable = false, precision = 20)
    public Integer id;

    @Column(name = "survey_id", nullable = false, precision = 20)
    public Integer surveyId;

    @ManyToOne
    @JoinColumn(name = "step_id", nullable = false)
    public Step step;

    @Column(name = "step_display_order", nullable = false, precision = 4)
    public Integer stepDisplayOrder;

    @ManyToOne
    @JoinColumn(name = "section_id", nullable = false)
    public Section section;

    @Column(name = "section_display_order", nullable = false, precision = 4)
    public Integer sectionDisplayOrder;

    @Column(name = "display_key", nullable = false, length = 34)
    public String displaykey;

    @Transient
    private DisplayKey key;

    public static List<StepsSections> findByDisplayKeyQuery(String key) {
        return find("#StepsSections.findByDisplayKeyQuery", Parameters.with("displaykey", key)).list();
    }

    public static StepsSections findFirstByDisplayKeyQuery(String key) {
        return find("#StepsSections.findByDisplayKeyQuery", Parameters.with("displaykey", key)).firstResult();
    }

    public static StepsSections findByDisplayKey(DisplayKey key) {
        StepsSections stepsSections = find("#StepsSections.findByDisplayKey", Parameters.with("displaykey", key.getValue())).firstResult();
        return stepsSections;
    }

    public static List<StepsSections> findBySurveyId(int surveyId) {
        return find("#StepsSections.findBySurveyId", Parameters.with("surveyId", surveyId)).list();
    }

    /**
     * Optimized query that fetches StepsSections with Step and Section relationships
     * in a single query to avoid N+1 problems. Use this instead of findBySurveyId()
     * when you need to access step and section details.
     *
     * @param surveyId the ID of the survey
     * @return list of StepsSections with eager-loaded step and section relationships
     */
    public static List<StepsSections> findBySurveyIdWithJoins(int surveyId) {
        return find("SELECT DISTINCT ss FROM StepsSections ss " +
                    "LEFT JOIN FETCH ss.step " +
                    "LEFT JOIN FETCH ss.section " +
                    "WHERE ss.surveyId = :surveyId " +
                    "ORDER BY ss.displaykey", 
                    Parameters.with("surveyId", surveyId))
                .list();
    }

    /**
     * Optimized query that fetches a single StepsSections with Step and Section relationships
     * in a single query to avoid N+1 problems. Use this instead of findByDisplayKey()
     * when you need to access step and section details.
     *
     * @param key the DisplayKey to search for
     * @return StepsSections with eager-loaded step and section relationships
     */
    public static StepsSections findByDisplayKeyWithJoins(DisplayKey key) {
        return find("SELECT ss FROM StepsSections ss " +
                    "LEFT JOIN FETCH ss.step " +
                    "LEFT JOIN FETCH ss.section " +
                    "WHERE ss.displaykey = :displaykey", 
                    Parameters.with("displaykey", key.getValue()))
                .firstResult();
    }

    public String getDisplaykey() {
        if (this.key == null) {
            this.key = new DisplayKey(this.displaykey);
        }
        return key.getValue();
    }

    public void setDisplaykey(String displaykey) {
        this.displaykey = displaykey;
        this.key = new DisplayKey(displaykey);
    }

    @Transient
    public DisplayKey getKey() {
        if (this.key == null) {
            this.key = new DisplayKey(this.displaykey);
        }
        return key;
    }
}
