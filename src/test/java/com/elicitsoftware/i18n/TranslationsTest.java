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

import com.elicitsoftware.i18n.PlainVaadin.PlainUi;
import com.elicitsoftware.i18n.PlainVaadin.StubSession;
import com.vaadin.flow.i18n.I18NProvider;
import com.vaadin.flow.server.VaadinService;
import com.vaadin.flow.server.VaadinSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * UC-009: Complete the Survey in My Language (BR-001, BR-003) -- translation lookup for code that
 * is not a component: the current UI's locale wins, then the session's, then English; the running
 * service's provider is used when there is one, otherwise a standalone classpath provider.
 */
class TranslationsTest {

    private static final Locale SPANISH = Locale.forLanguageTag("es-419");
    private static final Locale ARABIC = Locale.forLanguageTag("ar");

    @BeforeEach
    void setUp() throws Exception {
        PlainVaadin.clear();
        resetStandalone();
    }

    @AfterEach
    void tearDown() {
        PlainVaadin.clear();
    }

    private static void resetStandalone() throws Exception {
        Field field = Translations.class.getDeclaredField("standalone");
        field.setAccessible(true);
        field.set(null, null);
    }

    /** A provider that records the locale it is asked for and answers with its tag. */
    private static final class RecordingProvider implements I18NProvider {
        final List<Locale> asked = new ArrayList<>();

        @Override
        public List<Locale> getProvidedLocales() {
            return List.of(Locale.ENGLISH);
        }

        @Override
        public String getTranslation(String key, Locale locale, Object... params) {
            asked.add(locale);
            return key + "@" + (locale == null ? "null" : locale.toLanguageTag());
        }

        @Override
        public Map<String, String> getAllTranslations(Locale locale) {
            return Map.of();
        }
    }

    // ------------------------------------------------------------------ currentLocale

    @Test
    void given_currentUi_when_askingForLocale_then_uiLocale() {
        StubSession session = new StubSession();
        session.setLocale(ARABIC);
        VaadinSession.setCurrent(session);
        PlainUi.of(SPANISH, session);

        assertEquals(SPANISH, Translations.currentLocale());
    }

    @Test
    void given_sessionWithoutUi_when_askingForLocale_then_sessionLocale() {
        StubSession session = new StubSession();
        session.setLocale(ARABIC);
        VaadinSession.setCurrent(session);

        assertEquals(ARABIC, Translations.currentLocale());
    }

    @Test
    void given_noUiAndNoSession_when_askingForLocale_then_english() {
        assertEquals(Locale.ENGLISH, Translations.currentLocale(), "BR-001: English is the fallback");
    }

    // ------------------------------------------------------------------ provider

    @Test
    void given_runningServiceWithProvider_when_askingForProvider_then_servicesProvider() {
        RecordingProvider fromService = new RecordingProvider();
        VaadinService.setCurrent(PlainVaadin.service(fromService));

        assertSame(fromService, Translations.provider());
    }

    @Test
    void given_runningServiceWithoutProvider_when_askingForProvider_then_standalone() {
        VaadinService.setCurrent(PlainVaadin.service(null));

        assertInstanceOf(ElicitI18NProvider.class, Translations.provider());
    }

    @Test
    void given_noService_when_askingForProvider_then_oneStandaloneProviderIsReused() {
        I18NProvider first = Translations.provider();

        assertInstanceOf(ElicitI18NProvider.class, first);
        assertSame(first, Translations.provider());
    }

    // ------------------------------------------------------------------ get

    @Test
    void given_uiInSpanish_when_translating_then_spanishText() {
        PlainUi.of(SPANISH, null);
        assertEquals("Idioma", Translations.get("common.language"));
    }

    @Test
    void given_explicitLocale_when_translating_then_thatLanguage() {
        assertEquals("اللغة", Translations.get(ARABIC, "common.language"));
    }

    @Test
    void given_parameters_when_translating_then_passedToProvider() {
        RecordingProvider fromService = new RecordingProvider();
        VaadinService.setCurrent(PlainVaadin.service(fromService));

        assertEquals("some.key@ar", Translations.get(ARABIC, "some.key", 1, 2));
        assertEquals(List.of(ARABIC), fromService.asked);
    }

    @Test
    void given_nullLocale_when_translating_then_providerAskedForEnglish() {
        RecordingProvider fromService = new RecordingProvider();
        VaadinService.setCurrent(PlainVaadin.service(fromService));

        assertEquals("some.key@en", Translations.get((Locale) null, "some.key"));
        assertEquals(List.of(Locale.ENGLISH), fromService.asked,
                "BR-001: a missing locale is English before any provider sees it");
    }
}
