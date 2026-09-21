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

import static org.junit.jupiter.api.Assertions.*;

/**
 * UC-008 BR-001/BR-002: the provider's three-tier resolution against a real mounted directory.
 * The application ships English only; every other language comes from a mount. The provider's path fields are package-private (the BrandUtilTest pattern) so a @TempDir can
 * stand in for /opt/i18n without CDI.
 */
class ElicitI18NProviderMountTest {

    private static final Locale ES_419 = Locale.forLanguageTag("es-419");
    private static final Locale AR = Locale.forLanguageTag("ar");
    private static final Locale FR = Locale.FRENCH;

    private static ElicitI18NProvider provider(Path mount) {
        ElicitI18NProvider p = new ElicitI18NProvider();
        p.fileSystemPath = mount.toString();
        p.localPath = mount.resolve("no-local-dir").toString();
        p.appName = "survey";
        p.bundledLocales = "en";
        p.pseudoLocaleEnabled = false;
        return p;
    }

    private static void write(Path mount, String file, String content) throws IOException {
        Path dir = mount.resolve("survey");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve(file), content, StandardCharsets.UTF_8);
    }

    @Test
    void providedLocales_defaultFirst_thenBundled_thenMounted(@TempDir Path mount) throws IOException {
        write(mount, "translations_fr.properties", "sideNav.about=À propos\n");
        write(mount, "translations_es_419.properties", "sideNav.about=Acerca de\n");

        List<Locale> locales = provider(mount).getProvidedLocales();

        assertEquals(Locale.ENGLISH, locales.get(0));
        assertTrue(locales.contains(ES_419), "mounted locale must be offered: " + locales);
        assertTrue(locales.contains(FR), "mount-only locale must be offered: " + locales);
        assertFalse(locales.contains(AR), "a locale that is neither bundled nor mounted is not offered: " + locales);
        assertFalse(locales.contains(ElicitI18NProvider.PSEUDO_LOCALE));
    }

    @Test
    void mountedFile_overridesOnlyTheKeysItContains(@TempDir Path mount) throws IOException {
        write(mount, "translations_es_419.properties", "mainView.btnLogin=Entrar ahora\n");
        ElicitI18NProvider mounted = provider(mount);
        ElicitI18NProvider plain = provider(mount.resolve("empty"));

        assertEquals("Entrar ahora", mounted.getTranslation("mainView.btnLogin", ES_419));
        assertEquals(plain.getTranslation("sideNav.about", ES_419), mounted.getTranslation("sideNav.about", ES_419),
                "keys absent from the mounted file keep their fallback value");
    }

    @Test
    void missingKeyInLocale_fallsBackToEnglish(@TempDir Path mount) throws IOException {
        write(mount, "translations_fr.properties", "sideNav.about=À propos\n");
        ElicitI18NProvider p = provider(mount);

        assertEquals("À propos", p.getTranslation("sideNav.about", FR));
        assertEquals(p.getTranslation("mainView.btnLogin", Locale.ENGLISH), p.getTranslation("mainView.btnLogin", FR));
    }

    @Test
    void countryLocale_fallsBackToLanguageBundle(@TempDir Path mount) throws IOException {
        write(mount, "translations_es.properties", "sideNav.about=Acerca de\n");

        assertEquals("Acerca de", provider(mount).getTranslation("sideNav.about", Locale.forLanguageTag("es-GT")));
    }

    @Test
    void unknownKey_rendersVisibleMarker(@TempDir Path mount) {
        assertEquals("!no.such.key!", provider(mount).getTranslation("no.such.key", Locale.ENGLISH));
        assertEquals("!no.such.key!", provider(mount).getTranslation("no.such.key", AR));
    }

    @Test
    void parameters_areFormatted_onlyWhenGiven(@TempDir Path mount) throws IOException {
        write(mount, "translations.properties", "test.greeting=Hello, {0}!\ntest.plain=Don't format {0}\n");
        ElicitI18NProvider p = provider(mount);

        assertEquals("Hello, Ana!", p.getTranslation("test.greeting", Locale.ENGLISH, "Ana"));
        assertEquals("Don't format {0}", p.getTranslation("test.plain", Locale.ENGLISH));
    }

    @Test
    void pseudoLocale_marksEveryKnownKey(@TempDir Path mount) {
        ElicitI18NProvider p = provider(mount);
        p.pseudoLocaleEnabled = true;

        assertTrue(p.getProvidedLocales().contains(ElicitI18NProvider.PSEUDO_LOCALE));
        assertEquals("⟦mainView.btnLogin⟧", p.getTranslation("mainView.btnLogin", ElicitI18NProvider.PSEUDO_LOCALE));
        assertEquals("!no.such.key!", p.getTranslation("no.such.key", ElicitI18NProvider.PSEUDO_LOCALE));
        assertTrue(p.getAllTranslations(ElicitI18NProvider.PSEUDO_LOCALE).values().stream()
                .allMatch(v -> v.startsWith("⟦")));
    }

    @Test
    void appName_withPathTraversal_isRejected(@TempDir Path mount) {
        ElicitI18NProvider p = provider(mount);
        p.appName = "../etc";

        assertThrows(IllegalArgumentException.class, p::getProvidedLocales);
    }

    @Test
    void clearCache_picksUpFileChanges(@TempDir Path mount) throws IOException {
        ElicitI18NProvider p = provider(mount);
        String before = p.getTranslation("mainView.btnLogin", ES_419);
        write(mount, "translations_es_419.properties", "mainView.btnLogin=Cambiado\n");

        assertEquals(before, p.getTranslation("mainView.btnLogin", ES_419), "cached until cleared");
        p.clearCache();
        assertEquals("Cambiado", p.getTranslation("mainView.btnLogin", ES_419));
    }
}
