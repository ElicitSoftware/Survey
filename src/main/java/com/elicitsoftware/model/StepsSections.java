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

import java.math.BigDecimal;
import java.time.OffsetDateTime;
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

    // steps_sections.step_id now holds the durable steps.step_id (Kimball Type 2 SCD
    // retarget), not steps.id — referencedColumnName must point at that durable column
    // or this association silently matches nothing.
    @ManyToOne
    @JoinColumn(name = "step_id", referencedColumnName = "step_id", nullable = false)
    public Step step;

    // NUMERIC, not INTEGER (Kimball Type 2 SCD, research/Kimball_type_2.md's "Adding a new
    // question" section) -- supports decimal-midpoint insertion between existing positions.
    @Column(name = "step_display_order", nullable = false, precision = 4)
    public BigDecimal stepDisplayOrder;

    // steps_sections.section_id now holds the durable sections.section_id (Kimball Type 2
    // SCD retarget), not sections.id — same referencedColumnName requirement as step above.
    @ManyToOne
    @JoinColumn(name = "section_id", referencedColumnName = "section_id", nullable = false)
    public Section section;

    // NUMERIC, not INTEGER -- same reasoning as stepDisplayOrder above.
    @Column(name = "section_display_order", nullable = false, precision = 4)
    public BigDecimal sectionDisplayOrder;

    @Column(name = "display_key", nullable = false, length = 34)
    public String displaykey;

    // Kimball Type 2 SCD (research/Kimball_type_2.md) — steps_sections_id is the durable
    // key that survives re-versioning; id (above) is the surrogate, per-version row id.
    @Column(name = "steps_sections_id", nullable = false)
    public Integer stepsSectionsId;

    @Column(name = "version", nullable = false)
    public Integer version = 0;

    @Column(name = "effective_from")
    public OffsetDateTime effectiveFrom;

    @Column(name = "effective_to")
    public OffsetDateTime effectiveTo;

    @Column(name = "published_by")
    public String publishedBy;

    @Column(name = "published_comment")
    public String publishedComment;

    // FK-companion columns pinning the referenced rows to their entity-existence checks
    // (always 0 — see research/Kimball_type_2.md's "Resolving the FK Cascade Problem").
    @Column(name = "step_version", nullable = false)
    public Integer stepVersion = 0;

    @Column(name = "section_version", nullable = false)
    public Integer sectionVersion = 0;

    @Transient
    private DisplayKey key;

    public static StepsSections findByDisplayKey(DisplayKey key) {
        return find("#StepsSections.findByDisplayKey", Parameters.with("displaykey", key.getValue())).firstResult();
    }

    public static List<StepsSections> findBySurveyId(int surveyId) {
        return find("#StepsSections.findBySurveyId", Parameters.with("surveyId", surveyId)).list();
    }

    /**
     * Snapshot-anchored (research/Kimball_type_2.md) variant of findByDisplayKeyQuery:
     * resolves only the steps_sections row whose effective window covers {@code asOf}
     * (the respondent's firstAccessDt, or NOW() for a brand-new respondent).
     */
    public static List<StepsSections> findByDisplayKeyQueryAsOf(String key, OffsetDateTime asOf) {
        return find("displaykey like ?1 and effectiveFrom <= ?2 and effectiveTo > ?2 order by displaykey",
                key, asOf).list();
    }

    public static StepsSections findFirstByDisplayKeyQueryAsOf(String key, OffsetDateTime asOf) {
        return find("displaykey like ?1 and effectiveFrom <= ?2 and effectiveTo > ?2 order by displaykey",
                key, asOf).firstResult();
    }

    /**
     * Snapshot-anchored (research/Kimball_type_2.md) variant of findBySurveyIdWithJoins:
     * resolves only steps_sections rows whose effective window covers {@code asOf}
     * (the respondent's firstAccessDt, or NOW() for a brand-new respondent). Also fetches
     * Step and Section relationships in a single query to avoid N+1 problems.
     *
     * @param surveyId the ID of the survey
     * @param asOf     the snapshot instant to resolve structural rows as of
     * @return list of StepsSections with eager-loaded step and section relationships
     */
    public static List<StepsSections> findBySurveyIdWithJoinsAsOf(int surveyId, OffsetDateTime asOf) {
        return find("SELECT DISTINCT ss FROM StepsSections ss " +
                    "LEFT JOIN FETCH ss.step " +
                    "LEFT JOIN FETCH ss.section " +
                    "WHERE ss.surveyId = ?1 AND ss.effectiveFrom <= ?2 AND ss.effectiveTo > ?2 " +
                    "ORDER BY ss.displaykey",
                    surveyId, asOf)
                .list();
    }

    /**
     * Snapshot-anchored (research/Kimball_type_2.md) variant of findByDisplayKeyWithJoins:
     * resolves only the steps_sections row whose effective window covers {@code asOf}
     * (the respondent's firstAccessDt, or NOW() for a brand-new respondent). Also fetches
     * Step and Section relationships in a single query to avoid N+1 problems.
     *
     * @param key  the DisplayKey to search for
     * @param asOf the snapshot instant to resolve structural rows as of
     * @return StepsSections with eager-loaded step and section relationships
     */
    public static StepsSections findByDisplayKeyWithJoinsAsOf(DisplayKey key, OffsetDateTime asOf) {
        return find("SELECT ss FROM StepsSections ss " +
                    "LEFT JOIN FETCH ss.step " +
                    "LEFT JOIN FETCH ss.section " +
                    "WHERE ss.displaykey = ?1 AND ss.effectiveFrom <= ?2 AND ss.effectiveTo > ?2",
                    key.getValue(), asOf)
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
