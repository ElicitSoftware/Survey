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
import io.quarkus.panache.common.Parameters;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.concurrent.TimeUnit;

/**
 * Represents a respondent in a survey system. A respondent is associated with a survey
 * and interacts with it through various actions such as accessing and submitting responses.
 * This entity tracks key events and properties of the respondent's interaction with the survey.
 * <p>
 * The Respondent entity is mapped to the "respondents" table within the "survey" schema
 * and is managed through JPA. It supports named queries for retrieving specific respondent
 * data based on survey and token criteria.
 * <p>
 * Key features of this class include:
 * - Tracking of creation, first access, and finalization timestamps.
 * - Management of an active status to indicate if the respondent is currently participating.
 * - Storage of a unique token to identify individual respondents securely.
 * - Reference to the associated survey.
 * <p>
 * Named Queries:
 * - "Respondent.findBySurveyAndToken": Finds a respondent by the given survey ID and token.
 * - "Respondent.findActiveByToken": Retrieves active respondents associated with a specific token,
 * ordered by survey ID.
 * <p>
 * The entity includes utility methods such as:
 * - `findBySurveyAndToken`: Static method to retrieve a respondent based on survey ID and token.
 * - `getElapsedTime`: Calculates the elapsed time between the first access and finalization
 * timestamps, if available, formatted as HH:mm:ss.
 */
@Entity
@Table(name = "respondent_psa", schema = "survey")
public class RespondentPSA extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "RESPONDENT_PSA_ID_GENERATOR")
    @SequenceGenerator(name = "RESPONDENT_PSA_ID_GENERATOR", schema = "survey", sequenceName = "post_survey_actions_seq", allocationSize = 1)
    @Column(name = "id", unique = true, nullable = false, precision = 20)
    public Integer id;

    @Column(name = "respondent_id")
    public long respondentId;

    @Column(name = "post_survey_action_id")
    public long psaId;

    @Column(name = "status")
    public String status;

    @Column(name = "error_msg")
    public String error;

    @Column(name = "created_dt")
    @CreationTimestamp
    public OffsetDateTime createdDt;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "finished_dt")
    public OffsetDateTime finishedDt;

}
