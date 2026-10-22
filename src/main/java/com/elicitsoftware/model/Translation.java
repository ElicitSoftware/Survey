package com.elicitsoftware.model;

/*-
 * ***LICENSE_START***
 * Elicit Survey
 * %%
 * Copyright (C) 2025 - 2026 The Regents of the University of Michigan - Rogel Cancer Center
 * %%
 * PolyForm Noncommercial License 1.0.0
 * <https://polyformproject.org/licenses/noncommercial/1.0.0>
 * ***LICENSE_END***
 */

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * One field of one survey element in one language (Survey V019; UC-009 BR-005).
 * <p>
 * A translation attaches to what it translates by that element's {@code *_key} UUID and the name of
 * the base column, never by a surrogate or durable id: surrogate ids change on every version and
 * durable ids are per instance, while the element key survives both versioning and import. The base
 * text stays in the structural row; this table never replaces it.
 * <p>
 * Kimball Type 2 like the eight structural tables, so a respondent resolves the version effective at
 * their first access. Survey <em>reads</em> translations and never writes them: they are authored in
 * Author and installed by Admin's apply pipeline. Deliberately not mapped as a collection on
 * {@link Question} or any other entity -- a {@code @OneToMany Map<String, Translation>} would
 * reintroduce an N+1 per rendered element and pin a detached entity to one language. The runtime
 * reads them in bulk through {@code ContentTranslator} instead.
 */
@Entity
@Table(name = "translations", schema = "survey")
public class Translation extends PanacheEntityBase {

    /** {@code effective_to} of a current row. */
    public static final OffsetDateTime SENTINEL = OffsetDateTime.parse("9999-12-31T23:59:59Z");

    @Id
    @SequenceGenerator(name = "TRANSLATIONS_ID_GENERATOR", schema = "survey", sequenceName = "translations_seq", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "TRANSLATIONS_ID_GENERATOR")
    @Column(name = "id", nullable = false)
    public Integer id;

    @Column(name = "survey_id", nullable = false)
    public Integer surveyId;

    /** {@code surveys}, {@code steps}, {@code sections}, {@code questions}, {@code select_items}, {@code relationships} or {@code reports}. */
    @Column(name = "element_type", nullable = false, length = 32)
    public String elementType;

    /**
     * The {@code *_key} of the <em>translated</em> element. Named {@code targetKey} rather than
     * {@code elementKey} to keep one vocabulary with Author, where "element key" is already a row's
     * own cross-instance identity ({@link #translationKey} here).
     */
    @Column(name = "element_key", nullable = false)
    public UUID targetKey;

    /** Column name of the base text this row translates. */
    @Column(name = "field", nullable = false, length = 32)
    public String field;

    /** BCP-47 tag, as the mounted chrome bundles write it: {@code es-419}, {@code ar}. */
    @Column(name = "language", nullable = false, length = 35)
    public String language;

    @Column(name = "value", nullable = false)
    public String value;

    /**
     * Lower-case hex SHA-256 of the UTF-8 bytes of the base text this was translated from. When it
     * no longer matches the element's current base text the translation is stale: the wording was
     * edited after the translation was made, and the runtime serves the base text instead.
     */
    @Column(name = "source_hash", nullable = false, length = 64)
    public String sourceHash;

    /** Authoring-only snapshot of that base text; written by Author, never exported, unread here. */
    @Column(name = "source_text")
    public String sourceText;

    /** Durable key shared by every version of this translation within one database. */
    @Column(name = "translation_id", nullable = false)
    public Integer translationId;

    /** Cross-instance identity, minted once in Author and preserved verbatim by import and update. */
    @Column(name = "translation_key", nullable = false)
    public UUID translationKey;

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

    /**
     * Every version of every language of one survey, for the translator's cache to resolve in
     * memory. One query per survey per cache period; the as-of test is applied per lookup rather
     * than here, so that one load serves respondents pinned to different instants.
     *
     * @param surveyId the survey whose translations to load
     * @return every row of that survey, current and historical
     */
    public static List<Translation> findBySurvey(Integer surveyId) {
        return Translation.list("surveyId", surveyId);
    }

    /**
     * The version of one target string effective at {@code asOf}, in the same half-open form the
     * structural finders use ({@code effective_from <= asOf < effective_to}).
     *
     * @param targetKey  the {@code *_key} of the translated element
     * @param field      the base column's name
     * @param language   the BCP-47 tag
     * @param asOf       the respondent's snapshot anchor
     * @return the translation in effect at {@code asOf}, or {@code null} if none covers it
     */
    public static Translation findAsOf(UUID targetKey, String field, String language, OffsetDateTime asOf) {
        if (targetKey == null || field == null || language == null) {
            return null;
        }
        return Translation.<Translation>find(
                        "targetKey = ?1 and field = ?2 and language = ?3 and effectiveFrom <= ?4 and effectiveTo > ?4",
                        targetKey, field, language, asOf)
                .singleResultOptional().orElse(null);
    }

    public boolean isCurrent() {
        return SENTINEL.equals(effectiveTo);
    }
}
