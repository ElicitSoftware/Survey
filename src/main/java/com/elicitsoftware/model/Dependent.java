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

import java.util.List;

/**
 * The Dependent class represents an entity in the "dependents" database table within the "survey" schema.
 * This class models the relationship between upstream, downstream answers, and their associated respondent
 * and relationship details. It includes several static methods to query the database based on specific criteria
 * and utility methods to verify the state or copy the object.
 * <p>
 * This class is an entity managed by JPA and leverages {@link PanacheEntityBase} for simplifying
 * persistence operations. It defines named queries to retrieve or filter records based on specific attributes.
 * <p>
 * The Dependent class also contains annotations for defining many-to-one relationships, making it clear
 * how this entity interacts with other entities like Answer and Relationship.
 */
@Entity
@Table(name = "dependents", schema = "survey")
@NamedQueries({
        @NamedQuery(name = "Dependent.findByUpstream", query = "SELECT d FROM Dependent d where d.upstream.id = :upstreamId and d.respondentId = :respondentId order by d.id"),
        @NamedQuery(name = "Dependent.findUnique", query = "SELECT d FROM Dependent d where d.respondentId = :respondentId and d.upstream.id = :upstreamId and d.downstream.id = :downstreamId and d.relationship.id = :relationshipId order by d.id"),
        @NamedQuery(name = "Dependent.findByDownstream", query = "SELECT d FROM Dependent d where d.downstream.id = :downstreamId and d.respondentId = :respondentId order by d.id"),
})
public class Dependent extends PanacheEntityBase {

    @Id
    @SequenceGenerator(name = "DEPENDENTS_ID_GENERATOR", schema = "survey", sequenceName = "dependents_seq", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "DEPENDENTS_ID_GENERATOR")
    @Column(unique = true, nullable = false, precision = 20)
    public Integer id;
    @Column(name = "respondent_id", nullable = false, precision = 20)
    public Integer respondentId;
    // uni-directional many-to-one association to Answer
    @ManyToOne
    @JoinColumn(name = "upstream_id", nullable = false)
    public Answer upstream;
    // uni-directional many-to-one association to Answer
    @ManyToOne
    @JoinColumn(name = "downstream_id", nullable = false)
    public Answer downstream;
    // uni-directional many-to-one association to Relationship
    @ManyToOne
    @JoinColumn(name = "relationship_id", nullable = false)
    public Relationship relationship;
    @Column(name = "deleted")
    public Boolean deleted = false;

    public Dependent() {
    }

    public Dependent(Integer respondentId, Answer upstream, Answer downstream, Relationship relationship) {
        this.respondentId = respondentId;
        this.downstream = downstream;
        this.upstream = upstream;
        this.relationship = relationship;
    }

    /**
     * Retrieves a list of Dependent entities based on the provided respondent ID and upstream ID.
     * This method queries the database using the named query "Dependent.findByUpstream".
     *
     * @param respondentId the identifier of the respondent associated with the requested Dependents
     * @param upstreamId   the identifier of the upstream entity associated with the requested Dependents
     * @return a list of Dependent entities that match the specified respondent ID and upstream ID
     */
    public static List<Dependent> findByUpstream(int respondentId, int upstreamId) {
        return find("#Dependent.findByUpstream", Parameters.with("respondentId", respondentId)
                .and("upstreamId", upstreamId)).list();
    }

    /**
     * Retrieves a list of Dependent entities based on the provided respondent ID and downstream ID.
     * This method queries the database using the named query "Dependent.findByDownstream".
     *
     * @param respondentId the identifier of the respondent associated with the requested Dependents
     * @param downstreamId the identifier of the downstream entity associated with the requested Dependents
     * @return a list of Dependent entities that match the specified respondent ID and downstream ID
     */
    public static List<Dependent> findByDownstream(int respondentId, int downstreamId) {
        return find("#Dependent.findByDownstream", Parameters.with("downstreamId", downstreamId)
                .and("respondentId", respondentId)).list();
    }

    /**
     * Retrieves a unique Dependent entity based on the provided identifiers.
     * This method queries the database using the named query "Dependent.findUnique".
     *
     * @param respondentId   the identifier of the respondent associated with the requested Dependent
     * @param upstreamId     the identifier of the upstream entity associated with the requested Dependent
     * @param downstreamId   the identifier of the downstream entity associated with the requested Dependent
     * @param relationshipId the identifier of the relationship associated with the requested Dependent
     * @return the unique Dependent entity that matches the specified identifiers, or null if no such entity exists
     */
    public static Dependent findUnique(int respondentId, int upstreamId, int downstreamId, int relationshipId) {
        return find("#Dependent.findUnique", Parameters.with("respondentId", respondentId)
                .and("upstreamId", upstreamId)
                .and("downstreamId", downstreamId)
                .and("relationshipId", relationshipId)).firstResult();
    }

    /**
     * Determines whether the current Dependent object is complete based on its fields.
     * A Dependent is considered complete if the respondentId, upstream, downstream,
     * and relationship fields are not null.
     *
     * @return {@code true} if all required fields (respondentId, upstream, downstream, and relationship) are not null;
     * {@code false} otherwise.
     */
    @Transient
    public boolean isComplete() {
        return this.respondentId != null && this.upstream != null && this.downstream != null
                && this.relationship != null;
    }

    /**
     * Creates a shallow copy of the current Dependent instance.
     * The shallow copy includes the same respondentId, upstream,
     * and relationship references as the original object, while
     * setting the downstream field to null.
     *
     * @return a new Dependent object with fields copied from the current instance,
     * but with a null value for the downstream field.
     */
    @Transient
    public Dependent shallowCopy() {
        return new Dependent(this.respondentId, this.upstream, null, this.relationship);
    }
}
