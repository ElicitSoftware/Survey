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
import jakarta.json.bind.annotation.JsonbTransient;
import jakarta.persistence.*;

/**
 * The PostSurveyAction class represents an entity in the "post_survey_actions" table
 * within the "survey" schema. This entity defines actions to be executed after a survey
 * is completed.
 * <p>
 * Attributes:
 * - id: The unique identifier for the post-survey action.
 * - survey: The associated survey to which this action belongs, represented as a
 * many-to-one relationship with the Survey entity.
 * - name: The name of the post-survey action.
 * - description: A textual description of the action.
 * - url: The URL associated with the action, which may point to further resources
 * or actions.
 * - executionOrder: The order in which this action should be executed, relative to
 * other actions associated with the same survey.
 */
@Entity
@Table(name = "post_survey_actions", schema = "survey")
public class PostSurveyAction extends PanacheEntityBase {

    @Id
    @SequenceGenerator(name = "ACTION_ID_GENERATOR", schema = "survey", sequenceName = "post_survey_actions_seq", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ACTION_ID_GENERATOR")
    @Column(name = "id", unique = true, nullable = false)
    public Integer id;

    @JsonbTransient
    @ManyToOne()
    @JoinColumn(name = "survey_id", nullable = false)
    public Survey survey;

    @Column(name = "name")
    public String name;

    @Column(name = "description")
    public String description;

    @Column(name = "url")
    public String url;

    @Column(name = "execution_order")
    public Integer executionOrder;

}
