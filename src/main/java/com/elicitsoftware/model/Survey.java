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

    // This is the URL to redirect after the survey is over.
    @Column(name = "post_survey_url")
    public String postSurveyURL;

    @OneToMany(mappedBy = "survey", fetch = FetchType.EAGER)
    @OrderBy("displayOrder ASC")
    public Set<ReportDefinition> reports;

    // These restful actions are to be called after the survey is over.
    // e.g. export pdf, print, notify etc... 
    @OneToMany(mappedBy = "survey", fetch = FetchType.EAGER)
    @OrderBy("executionOrder ASC")
    public Set<PostSurveyAction> postSurveyActions;

}
