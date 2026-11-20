package com.elicitsoftware.i18n;

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

import com.elicitsoftware.PostgresTestResource;
import com.elicitsoftware.model.Survey;
import com.vaadin.browserless.quarkus.QuarkusBrowserlessTest;
import com.vaadin.flow.component.UI;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UC-009 BR-005, BR-008, BR-009, BR-010: when a translation is served and when the base text is.
 * <p>
 * Runs browserless because the language comes from the UI's locale, which is the whole point:
 * off the UI thread there is no language and every accessor returns base text, and that is what the
 * ETL, the migrator and every other non-respondent reader depends on.
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class ContentTranslatorTest extends QuarkusBrowserlessTest {

    private static final Locale SPANISH = Locale.forLanguageTag("es-419");
    private static final OffsetDateTime EPOCH = OffsetDateTime.parse("1970-01-01T00:00:00Z");
    private static final String BASE_TEXT = "Original wording";
    private static final String SPANISH_TEXT = "Redacción original";

    @Inject
    ContentTranslator translator;

    @Inject
    EntityManager em;

    private final UUID targetKey = UUID.randomUUID();
    private Integer surveyId;

    @AfterEach
    void cleanup() {
        if (surveyId != null) {
            QuarkusTransaction.requiringNew().run(() -> {
                em.createNativeQuery("DELETE FROM survey.translations WHERE survey_id = ?1")
                        .setParameter(1, surveyId).executeUpdate();
                em.createNativeQuery("DELETE FROM survey.surveys WHERE id = ?1")
                        .setParameter(1, surveyId).executeUpdate();
            });
            translator.invalidate(surveyId);
            surveyId = null;
        }
    }

    /**
     * A committed survey publishing {@code contentLanguages}, with the translator's cache dropped.
     * <p>
     * Inserted with SQL rather than through the entity because {@code survey_key} is mapped
     * read-only in Survey -- an element key is minted in Author and carried by the file, and this
     * application never writes one.
     */
    private Survey survey(String contentLanguages) {
        Integer id = QuarkusTransaction.requiringNew().call(() -> {
            Integer newId = ((Number) em.createNativeQuery("SELECT nextval('survey.surveys_seq')")
                    .getSingleResult()).intValue();
            em.createNativeQuery("""
                    INSERT INTO survey.surveys (id, survey_key, name, display_order, title, base_language, content_languages)
                    VALUES (?1, ?2, ?3, ?4, 'Content translator fixture', 'en', ?5)
                    """)
                    .setParameter(1, newId).setParameter(2, UUID.randomUUID())
                    .setParameter(3, "CT " + newId).setParameter(4, 900000 + newId)
                    .setParameter(5, contentLanguages).executeUpdate();
            return newId;
        });
        surveyId = id;
        translator.invalidate(surveyId);
        return QuarkusTransaction.requiringNew().call(() -> Survey.findById(id));
    }

    private void translation(String language, String value, String sourceHash,
                             OffsetDateTime from, OffsetDateTime to) {
        QuarkusTransaction.requiringNew().run(() -> em.createNativeQuery("""
                        INSERT INTO survey.translations
                            (id, survey_id, translation_key, element_type, element_key, field, language, value,
                             source_hash, effective_from, effective_to)
                        VALUES (nextval('survey.translations_seq'), ?1, ?2, 'questions', ?3, 'text', ?4, ?5, ?6, ?7, ?8)
                        """)
                .setParameter(1, surveyId).setParameter(2, UUID.randomUUID()).setParameter(3, targetKey)
                .setParameter(4, language).setParameter(5, value).setParameter(6, sourceHash)
                .setParameter(7, from).setParameter(8, to).executeUpdate());
        translator.invalidate(surveyId);
    }

    @Test
    void withNoPublishedLanguage_thereIsNoContentLanguage() {
        UI.getCurrent().setLocale(SPANISH);
        assertNull(translator.language(survey(null)));
        cleanup();
        assertNull(translator.language(survey("")));
    }

    @Test
    void aPublishedAndMountedLanguageMatchingTheSession_applies() {
        Survey survey = survey("es-419");
        UI.getCurrent().setLocale(SPANISH);
        assertEquals("es-419", translator.language(survey));
    }

    @Test
    void thePublishedLanguageMustAlsoBeMountedHere() {
        // BR-009: Japanese is published for the survey but nothing on this site's mount is
        // Japanese, so the site holds the rows and never serves them.
        Survey survey = survey("ja");
        UI.getCurrent().setLocale(SPANISH);
        assertNull(translator.language(survey));
    }

    @Test
    void theSessionLanguageMustBePublishedForThisSurvey() {
        Survey survey = survey("ar");
        UI.getCurrent().setLocale(SPANISH);
        assertNull(translator.language(survey), "the survey publishes Arabic, the respondent reads Spanish");
    }

    @Test
    void theBaseLanguageIsNeverAContentLanguage() {
        Survey survey = survey("en,es-419");
        UI.getCurrent().setLocale(Locale.ENGLISH);
        assertNull(translator.language(survey), "English content is the base text, not a translation");
    }

    @Test
    void aCurrentTranslationWhoseHashMatches_isServed() {
        Survey survey = survey("es-419");
        translation("es-419", SPANISH_TEXT, ContentHash.of(BASE_TEXT), EPOCH, ContentTranslator.sentinel());
        UI.getCurrent().setLocale(SPANISH);

        assertEquals(SPANISH_TEXT,
                translator.get(survey, targetKey, "text", BASE_TEXT, OffsetDateTime.now()).orElse(null));
    }

    @Test
    void aStaleTranslationIsNotServed() {
        // BR-008: the base text was edited after this translation was made, so it may no longer ask
        // the same question. The base text is shown instead.
        Survey survey = survey("es-419");
        translation("es-419", SPANISH_TEXT, ContentHash.of("Some earlier wording"), EPOCH, ContentTranslator.sentinel());
        UI.getCurrent().setLocale(SPANISH);

        assertTrue(translator.get(survey, targetKey, "text", BASE_TEXT, OffsetDateTime.now()).isEmpty());
    }

    @Test
    void aTranslationOfAnotherFieldOrLanguageDoesNotLeak() {
        Survey survey = survey("es-419");
        translation("ar", "نص عربي", ContentHash.of(BASE_TEXT), EPOCH, ContentTranslator.sentinel());
        UI.getCurrent().setLocale(SPANISH);

        assertTrue(translator.get(survey, targetKey, "text", BASE_TEXT, OffsetDateTime.now()).isEmpty());
        assertTrue(translator.get(survey, targetKey, "short_text", BASE_TEXT, OffsetDateTime.now()).isEmpty());
    }

    @Test
    void asOfPicksTheVersionEffectiveAtTheRespondentsFirstAccess() {
        // BR-010: a respondent who started before a correction keeps the wording they started with.
        Survey survey = survey("es-419");
        OffsetDateTime corrected = OffsetDateTime.now().minusDays(1);
        translation("es-419", "Primera redacción", ContentHash.of(BASE_TEXT), EPOCH, corrected);
        translation("es-419", "Redacción corregida", ContentHash.of(BASE_TEXT), corrected, ContentTranslator.sentinel());
        UI.getCurrent().setLocale(SPANISH);

        assertEquals("Primera redacción",
                translator.get(survey, targetKey, "text", BASE_TEXT, corrected.minusDays(3)).orElse(null),
                "a respondent pinned before the correction keeps the first wording");
        assertEquals("Redacción corregida",
                translator.get(survey, targetKey, "text", BASE_TEXT, OffsetDateTime.now()).orElse(null),
                "a respondent starting now gets the correction");
    }

    @Test
    void inEnglishEveryAccessorReturnsTheBaseText() {
        Survey survey = survey("es-419");
        translation("es-419", SPANISH_TEXT, ContentHash.of(BASE_TEXT), EPOCH, ContentTranslator.sentinel());
        UI.getCurrent().setLocale(Locale.ENGLISH);

        assertTrue(translator.get(survey, targetKey, "text", BASE_TEXT, OffsetDateTime.now()).isEmpty());
        assertEquals("Content translator fixture", translator.title(survey, OffsetDateTime.now()));
    }
}
