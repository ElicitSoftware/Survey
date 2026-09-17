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
 * data based on survey and access code criteria.
 * <p>
 * Key features of this class include:
 * - Tracking of creation, first access, and finalization timestamps.
 * - Management of an active status to indicate if the respondent is currently participating.
 * - Storage of a unique access code the respondent enters to reach the survey.
 * - Reference to the associated survey.
 * <p>
 * Named Queries:
 * - "Respondent.findBySurveyAndAccessCode": Finds a respondent by the given survey ID and access code.
 * - "Respondent.findActiveByAccessCode": Retrieves active respondents associated with a specific access code,
 * ordered by survey ID.
 * <p>
 * The entity includes utility methods such as:
 * - `findBySurveyAndAccessCode`: Static method to retrieve a respondent based on survey ID and access code.
 * - `getElapsedTime`: Calculates the elapsed time between the first access and finalization
 * timestamps, if available, formatted as HH:mm:ss.
 */
@Entity
@Table(name = "respondents", schema = "survey")
@NamedQueries({
        @NamedQuery(name = "Respondent.findBySurveyAndAccessCode", query = "SELECT R FROM Respondent R where R.survey.id = :survey_id and R.accessCode = :accessCode"),
        @NamedQuery(name = "Respondent.findActiveByAccessCode", query = "SELECT R FROM Respondent R where R.accessCode = :accessCode and R.active = true order by R.survey.id")
})
public class Respondent extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "RESPONDENT_ID_GENERATOR")
    @SequenceGenerator(name = "RESPONDENT_ID_GENERATOR", schema = "survey", sequenceName = "respondents_seq", allocationSize = 1)
    @Column(name = "id", unique = true, nullable = false, precision = 20)
    public Integer id;

    @Column(name = "created_dt", nullable = false)
    @CreationTimestamp
    public OffsetDateTime createdDt;

    @Column(name = "first_access_dt")
    public OffsetDateTime firstAccessDt;

    @Column(name = "finalized_dt")
    public OffsetDateTime finalizedDt;

    public boolean active;
    public int logins;

    // uni-directional many-to-one association to ActionType
    @ManyToOne
    @JoinColumn(name = "survey_id", nullable = false)
    public Survey survey;
    @Column(name = "access_code")
    public String accessCode;

    @Transient
    public static Respondent findBySurveyAndAccessCode(Integer survey_id, String accessCode) {
        return Respondent.find("SELECT r FROM Respondent r JOIN FETCH r.survey s LEFT JOIN FETCH s.reports WHERE s.id = :survey_id and r.accessCode = :accessCode", Parameters.with("survey_id", survey_id).and("accessCode", accessCode)).firstResult();
    }

    @Transient
    public String getElapsedTime() {
        if (firstAccessDt != null && finalizedDt != null) {
            Duration duration = Duration.between(firstAccessDt, finalizedDt);
            long elapsedMilliseconds = duration.toMillis();
            return String.format("%02d:%02d:%02d",
                    TimeUnit.MILLISECONDS.toHours(elapsedMilliseconds),
                    TimeUnit.MILLISECONDS.toMinutes(elapsedMilliseconds)
                            - TimeUnit.HOURS.toMinutes(TimeUnit.MILLISECONDS
                            .toHours(elapsedMilliseconds)),
                    TimeUnit.MILLISECONDS.toSeconds(elapsedMilliseconds)
                            - TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS
                            .toMinutes(elapsedMilliseconds)));

        }
        return "Not calculated";
    }
}
