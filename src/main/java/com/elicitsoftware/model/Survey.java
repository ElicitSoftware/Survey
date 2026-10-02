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

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.util.Set;
import java.util.UUID;

/**
 * Represents a survey entity in the system. Each survey contains metadata such as name, title,
 * description, and display information, and references related entities such as reports
 * and post-survey actions.
 * <p>
 * This class is mapped to the "surveys" table within the "survey" schema.
 * It is persistent and managed through JPA.
 */
@Entity
@Table(name = "surveys", schema = "survey")
public class Survey extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "SURVEY_ID_GENERATOR")
    @SequenceGenerator(name = "SURVEY_ID_GENERATOR", schema = "survey", sequenceName = "surveys_seq", allocationSize = 1)
    @Column(name = "id", unique = true, nullable = false)
    public Integer id;

    /**
     * Element key (Survey V015): the identity that survives versioning and import, and what a
     * {@link Translation} names this element by. Read-only here -- it is minted in Author and
     * carried verbatim by Admin's import and update; Survey never writes it.
     */
    @Column(name = "survey_key", insertable = false, updatable = false)
    public UUID surveyKey;

    @Column(name = "display_order", nullable = false, precision = 3)
    public Integer displayOrder;

    @Column(name = "name")
    public String name;

    @Column(name = "title")
    public String title;

    @Column(name = "description")
    public String description;

    @Column(name = "initial_display_key")
    public String initialDisplayKey;

    /**
     * The language this survey's content was authored in, and the one every string falls back to
     * when no current translation applies (Survey V019; UC-009 BR-005).
     */
    @Column(name = "base_language", nullable = false, length = 35)
    public String baseLanguage = "en";

    /**
     * Comma-separated BCP-47 tags the author has published content translations for. What a
     * respondent is actually offered here is the intersection of this with the languages mounted
     * for the chrome at this site (UC-009 BR-009); {@code ContentTranslator} is the only reader.
     */
    @Column(name = "content_languages", length = 255)
    public String contentLanguages;

    // This is the URL to redirect after the survey is over.
    @Column(name = "post_survey_url")
    public String postSurveyURL;

    /**
     * The name of this survey's own reporting schema at this site (Survey V021; UC-008 BR-006).
     * Assigned by the ETL the first time the survey is built, changed only by a rename
     * (UC-010), cleared only by a drop (UC-011), and null until then. Site-local: a survey
     * definition file never carries it. The ETL is the only writer; this mapping is read-only so
     * that nothing persisting a {@code Survey} can overwrite what the ETL stored.
     */
    @Column(name = "report_schema", length = 63, insertable = false, updatable = false)
    public String reportSchema;

    @OneToMany(mappedBy = "survey", fetch = FetchType.LAZY)
    @OrderBy("displayOrder ASC")
    public Set<ReportDefinition> reports;

    // These restful actions are to be called after the survey is over.
    // e.g. export pdf, print, notify etc... 
    @OneToMany(mappedBy = "survey", fetch = FetchType.LAZY)
    @OrderBy("executionOrder ASC")
    public Set<PostSurveyAction> postSurveyActions;

}
