package com.elicitsoftware.i18n;

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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * UC-009 BR-001: the image carries English only; every other language is read from this
 * application's sub-directory of the translations mount, {@code <i18n.file.system.path>/survey/},
 * and overlays English key by key. The offered set is English plus every mounted language, narrowed
 * by {@code i18n.offered.locales}. The fields are package-private (the BrandUtilTest pattern) so they
 * can be set without CDI. {@link #CURATED} is the umbrella's {@code elicit-i18n/}, one level up from
 * this module in a local checkout and fetched to the same place by CI.
 */
class ElicitI18NProviderTest {

    static final String CURATED = "../elicit-i18n";

    private static final Locale ES_419 = Locale.forLanguageTag("es-419");
    private static final Locale AR = Locale.forLanguageTag("ar");
    private static final Locale AR_EG = Locale.forLanguageTag("ar-EG");
    private static final Locale FR = Locale.FRENCH;

    private static ElicitI18NProvider provider(String mount, String offered) {
        ElicitI18NProvider p = new ElicitI18NProvider();
        p.fileSystemPath = mount;
        p.offeredLocales = Optional.ofNullable(offered);
        p.pseudoLocaleEnabled = false;
        return p;
    }

    private static ElicitI18NProvider curated() {
        return provider(CURATED, null);
    }

    @Test
    void unknownLanguage_fallsBackToEnglish() {
        // "fr" is not mounted: exact tag misses, language-only misses (same tag), so this
        // exercises the final tier landing on English.
        assertEquals("About", curated().getTranslation("sideNav.about", FR));
    }

    @Test
    void countryLocale_fallsBackToLanguageBundle() {
        // "ar-EG" has no file of its own; it must fall back to translations_ar.properties
        // rather than skipping straight to English.
        assertEquals("حول", curated().getTranslation("sideNav.about", AR_EG));
    }

    @Test
    void unknownKey_rendersVisibleMarker() {
        ElicitI18NProvider p = curated();

        assertEquals("!no.such.key!", p.getTranslation("no.such.key", Locale.ENGLISH));
        assertEquals("!no.such.key!", p.getTranslation("no.such.key", AR));
    }

    @Test
    void parameters_areFormatted_onlyWhenGiven() {
        ElicitI18NProvider p = curated();

        assertEquals("Survey: Elicit Demo", p.getTranslation("reviewView.surveyLabel", Locale.ENGLISH, "Elicit Demo"));
        assertEquals("Failed to generate PDF: {0}", p.getTranslation("reportView.error.pdf", Locale.ENGLISH),
                "no params given: the value is returned as-is, not run through MessageFormat");
    }

    @Test
    void getAllTranslations_mergesLanguageOverEnglish() {
        ElicitI18NProvider p = curated();

        assertEquals("About", p.getAllTranslations(Locale.ENGLISH).get("sideNav.about"),
                "the default locale itself gets no overlay");
        assertEquals("حول", p.getAllTranslations(AR).get("sideNav.about"),
                "a non-default locale overlays its bundle onto the English base");
    }

    @Test
    void pseudoLocale_marksEveryKnownKey() {
        ElicitI18NProvider p = curated();
        p.pseudoLocaleEnabled = true;

        assertTrue(p.getProvidedLocales().contains(ElicitI18NProvider.PSEUDO_LOCALE));
        assertEquals("⟦mainView.btnLogin⟧", p.getTranslation("mainView.btnLogin", ElicitI18NProvider.PSEUDO_LOCALE));
        assertEquals("!no.such.key!", p.getTranslation("no.such.key", ElicitI18NProvider.PSEUDO_LOCALE));
        assertTrue(p.getAllTranslations(ElicitI18NProvider.PSEUDO_LOCALE).values().stream()
                .allMatch(v -> v.startsWith("⟦")));
    }

    @Test
    void curatedMount_offersEveryMountedLanguage() {
        ElicitI18NProvider p = curated();

        List<Locale> locales = p.getProvidedLocales();
        assertEquals(List.of(Locale.ENGLISH, AR, ES_419), locales, "English first, then the mounted languages");
        assertEquals("حول", p.getTranslation("sideNav.about", AR));
        assertEquals("Acerca de", p.getTranslation("sideNav.about", ES_419));
    }

    @Test
    void nothingMounted_offersEnglishOnly(@TempDir Path empty) {
        ElicitI18NProvider p = provider(empty.toString(), null);

        assertEquals(List.of(Locale.ENGLISH), p.getProvidedLocales());
        assertEquals("About", p.getTranslation("sideNav.about", AR),
                "the image carries no Arabic: with nothing mounted it falls back to English");
    }

    @Test
    void theImageCarriesEnglishOnly() {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        assertNotNull(cl.getResource("vaadin-i18n/translations.properties"), "English is in the image");
        assertNull(cl.getResource("vaadin-i18n/translations_ar.properties"), "Arabic must come from the mount");
        assertNull(cl.getResource("vaadin-i18n/translations_es_419.properties"), "Spanish must come from the mount");
    }

    @Test
    void offeredLocales_narrowsTheMountedSet() {
        // i18n.offered.locales hides a mounted language from the selector and from ?lang=.
        ElicitI18NProvider p = provider(CURATED, "en,es-419");

        assertEquals(List.of(Locale.ENGLISH, ES_419), p.getProvidedLocales());
    }

    @Test
    void offeredLocales_cannotOfferWhatIsNotMounted() {
        // fr is listed but has no file: it is not offered (and a warning is logged).
        ElicitI18NProvider p = provider(CURATED, "en,fr,ar");

        assertEquals(List.of(Locale.ENGLISH, AR), p.getProvidedLocales());
    }

    @Test
    void offeredLocales_blank_offersEverythingMounted() {
        assertEquals(curated().getProvidedLocales(), provider(CURATED, "  ").getProvidedLocales());
    }

    @Test
    void englishIsOffered_evenWhenTheFilterLeavesItOut() {
        // English is the fallback for every key, so it cannot be switched off.
        assertEquals(List.of(Locale.ENGLISH, AR), provider(CURATED, "ar").getProvidedLocales());
    }

    @Test
    void aMountedFile_overlaysEnglishKeyByKey(@TempDir Path mount) throws IOException {
        // A partial file overrides only the keys it holds; the rest fall back to English, and the
        // file alone is enough to offer its language.
        Path dir = Files.createDirectories(mount.resolve("survey"));
        Files.writeString(dir.resolve("translations_fr.properties"), "sideNav.about=À propos\n",
                StandardCharsets.UTF_8);
        ElicitI18NProvider p = provider(mount.toString(), null);

        assertEquals(List.of(Locale.ENGLISH, FR), p.getProvidedLocales());
        assertEquals("À propos", p.getTranslation("sideNav.about", FR));
        assertEquals("Survey: X", p.getTranslation("reviewView.surveyLabel", FR, "X"), "unmounted key falls back");
    }

    @Test
    void eachApplicationReadsItsOwnSubDirectory(@TempDir Path mount) throws IOException {
        // A file in another app's directory is not this app's language.
        Path admin = Files.createDirectories(mount.resolve("admin"));
        Files.writeString(admin.resolve("translations_fr.properties"), "sideNav.about=À propos\n",
                StandardCharsets.UTF_8);

        assertEquals(List.of(Locale.ENGLISH), provider(mount.toString(), null).getProvidedLocales());
    }

    @Test
    void appName_mayNotClimbOutOfTheMount() {
        ElicitI18NProvider p = curated();
        p.appName = "../admin";

        assertThrows(IllegalArgumentException.class, p::mountDirectory);
    }

    @Test
    void fromConfig_readsTheConfiguredMount() {
        // A provider built outside CDI (Translations off a Vaadin thread in a plain test) must not
        // fall back to the default /i18n: the %test profile points it at the curated set.
        assertEquals(List.of(Locale.ENGLISH, AR, ES_419), ElicitI18NProvider.fromConfig().getProvidedLocales());
    }
}
