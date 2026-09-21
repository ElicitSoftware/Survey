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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** UC-008 BR-004: built-in right-to-left languages and the optional per-deployment override. */
class LocaleDirectionConfigTest {

    private static LocaleDirectionConfig config(Path mount) {
        LocaleDirectionConfig c = new LocaleDirectionConfig();
        c.fileSystemPath = mount.toString();
        c.localPath = mount.resolve("no-local").toString();
        return c;
    }

    @Test
    void builtInDefaults(@TempDir Path mount) {
        LocaleDirectionConfig c = config(mount);
        assertTrue(c.isRightToLeft(Locale.forLanguageTag("ar")));
        assertTrue(c.isRightToLeft(Locale.forLanguageTag("ar-EG")));
        assertTrue(c.isRightToLeft(Locale.forLanguageTag("he")));
        assertFalse(c.isRightToLeft(Locale.forLanguageTag("es-419")));
        assertFalse(c.isRightToLeft(Locale.ENGLISH));
        assertFalse(c.isRightToLeft(null));
    }

    @Test
    void manifestOverridesDirection(@TempDir Path mount) throws IOException {
        Files.writeString(mount.resolve("i18n-config.json"),
                "{\"locales\":[{\"tag\":\"fr\",\"direction\":\"rtl\"},{\"tag\":\"ar\",\"direction\":\"ltr\"}]}");
        LocaleDirectionConfig c = config(mount);

        assertTrue(c.isRightToLeft(Locale.FRENCH), "manifest can declare a mounted locale rtl");
        assertFalse(c.isRightToLeft(Locale.forLanguageTag("ar")), "manifest can override the built-in default");
    }

    @Test
    void malformedManifest_keepsBuiltInDefaults(@TempDir Path mount) throws IOException {
        Files.writeString(mount.resolve("i18n-config.json"), "{not json");
        assertTrue(config(mount).isRightToLeft(Locale.forLanguageTag("ar")));
    }
}
