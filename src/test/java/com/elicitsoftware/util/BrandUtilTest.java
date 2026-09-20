package com.elicitsoftware.util;

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

import static org.junit.jupiter.api.Assertions.*;

/**
 * BrandUtil.brandFileSystemPath is package-private and settable directly from a test in the
 * same package, so the filesystem-mounted-brand path can be exercised with a real @TempDir
 * instead of the actual /brand mount point used in production.
 */
class BrandUtilTest {

    private static BrandUtil util(Path brandDir) {
        BrandUtil util = new BrandUtil();
        util.brandFileSystemPath = brandDir.toString();
        return util;
    }

    private static void writeConfig(Path dir, String json) throws IOException {
        Files.writeString(dir.resolve("brand-config.json"), json);
    }

    // Neither a filesystem mount nor a matching classpath resource exists.
    @Test
    void detectCurrentBrand_noMountAndNoClasspathResource_returnsDefaultBrand(@TempDir Path emptyDir) {
        BrandUtil.BrandInfo info = util(emptyDir).detectCurrentBrand();

        assertEquals("default-brand", info.getBrandKey());
        assertEquals("Elicit", info.getDisplayName());
        assertEquals("brand/images/HorizontalLogo.png", info.getLogoPath());
    }

    @Test
    void detectCurrentBrand_malformedJson_fallsBackToDefaultBrand(@TempDir Path brandDir) throws IOException {
        writeConfig(brandDir, "{not valid json");

        BrandUtil.BrandInfo info = util(brandDir).detectCurrentBrand();

        assertEquals("default-brand", info.getBrandKey());
    }

    @Test
    void detectCurrentBrand_topLevelName_usedAsBrandNameAndKey(@TempDir Path brandDir) throws IOException {
        writeConfig(brandDir, "{\"name\": \"My Cool  Brand!!\"}");

        BrandUtil.BrandInfo info = util(brandDir).detectCurrentBrand();

        assertEquals("my-cool-brand", info.getBrandKey());
        assertEquals("My Cool  Brand!!", info.getDisplayName());
    }

    @Test
    void detectCurrentBrand_nestedBrandName_usedWhenNoTopLevelName(@TempDir Path brandDir) throws IOException {
        writeConfig(brandDir, "{\"brand\": {\"name\": \"Nested Name\"}}");

        BrandUtil.BrandInfo info = util(brandDir).detectCurrentBrand();

        assertEquals("nested-name", info.getBrandKey());
    }

    @Test
    void detectCurrentBrand_noNameAnywhere_fallsBackToExternalBrand(@TempDir Path brandDir) throws IOException {
        writeConfig(brandDir, "{}");

        BrandUtil.BrandInfo info = util(brandDir).detectCurrentBrand();

        assertEquals("external-brand", info.getBrandKey());
    }

    @Test
    void detectCurrentBrand_organizationPresent_usedAsDisplayNameInsteadOfBrandName(@TempDir Path brandDir) throws IOException {
        writeConfig(brandDir, "{\"name\": \"Internal Name\", \"organization\": \"Public Org\"}");

        BrandUtil.BrandInfo info = util(brandDir).detectCurrentBrand();

        assertEquals("Public Org", info.getDisplayName());
    }

    @Test
    void detectCurrentBrand_horizontalLogoPreferred_overPrimary(@TempDir Path brandDir) throws IOException {
        writeConfig(brandDir, "{\"name\": \"Acme\", \"logos\": {\"horizontal\": \"h.png\", \"primary\": \"p.png\"}}");

        BrandUtil.BrandInfo info = util(brandDir).detectCurrentBrand();

        assertEquals("brand/images/h.png", info.getLogoPath());
    }

    @Test
    void detectCurrentBrand_primaryLogoUsed_whenNoHorizontal(@TempDir Path brandDir) throws IOException {
        writeConfig(brandDir, "{\"name\": \"Acme\", \"logos\": {\"primary\": \"p.png\"}}");

        BrandUtil.BrandInfo info = util(brandDir).detectCurrentBrand();

        assertEquals("brand/images/p.png", info.getLogoPath());
    }

    @Test
    void detectCurrentBrand_firstAvailableLogoUsed_whenNoHorizontalOrPrimary(@TempDir Path brandDir) throws IOException {
        writeConfig(brandDir, "{\"name\": \"Acme\", \"logos\": {\"square\": \"s.png\"}}");

        BrandUtil.BrandInfo info = util(brandDir).detectCurrentBrand();

        assertEquals("brand/images/s.png", info.getLogoPath());
    }

    @Test
    void detectCurrentBrand_noLogosKey_usesDefaultLogoPath(@TempDir Path brandDir) throws IOException {
        writeConfig(brandDir, "{\"name\": \"Acme\"}");

        BrandUtil.BrandInfo info = util(brandDir).detectCurrentBrand();

        assertEquals("brand/images/HorizontalLogo.png", info.getLogoPath());
    }

    @Test
    void detectCurrentBrand_secondCall_returnsCachedInstance(@TempDir Path brandDir) throws IOException {
        writeConfig(brandDir, "{\"name\": \"Acme\"}");
        BrandUtil util = util(brandDir);

        BrandUtil.BrandInfo first = util.detectCurrentBrand();
        BrandUtil.BrandInfo second = util.detectCurrentBrand();

        assertSame(first, second);
    }

    @Test
    void clearCache_forcesRedetectionOnNextCall(@TempDir Path brandDir) throws IOException {
        writeConfig(brandDir, "{\"name\": \"Acme\"}");
        BrandUtil util = util(brandDir);
        BrandUtil.BrandInfo first = util.detectCurrentBrand();

        util.clearCache();
        BrandUtil.BrandInfo second = util.detectCurrentBrand();

        assertNotSame(first, second);
        assertEquals(first.getBrandKey(), second.getBrandKey());
    }

    @Test
    void getLogoResourcePath_externalBrandUnderMountPath_rewrittenToServedPath(@TempDir Path brandDir) {
        BrandUtil util = util(brandDir);
        BrandUtil.BrandInfo external = new BrandUtil.BrandInfo("acme", "Acme", brandDir + "/images/logo.png", "acme");

        assertEquals("brand/images/HorizontalLogo.png", util.getLogoResourcePath(external));
    }

    @Test
    void getLogoResourcePath_defaultBrand_returnedAsIs(@TempDir Path brandDir) {
        BrandUtil util = util(brandDir);
        BrandUtil.BrandInfo defaultBrand = new BrandUtil.BrandInfo("default-brand", "Elicit", "brand/images/HorizontalLogo.png", "default-brand");

        assertEquals("brand/images/HorizontalLogo.png", util.getLogoResourcePath(defaultBrand));
    }

    @Test
    void getIconResourcePath_alwaysReturnsWhiteIcon(@TempDir Path brandDir) {
        BrandUtil.BrandInfo info = new BrandUtil.BrandInfo("acme", "Acme", "logo.png", "acme");
        assertEquals("brand/images/icon-white.png", util(brandDir).getIconResourcePath(info));
    }

    @Test
    void getApplicationTitle_defaultBrand_prefixedWithElicit(@TempDir Path brandDir) {
        BrandUtil.BrandInfo defaultBrand = new BrandUtil.BrandInfo("default-brand", "Elicit", "logo.png", "default-brand");
        assertEquals("Elicit Survey", util(brandDir).getApplicationTitle(defaultBrand, "Survey"));
    }

    @Test
    void getApplicationTitle_externalBrand_usesDisplayName(@TempDir Path brandDir) {
        BrandUtil.BrandInfo external = new BrandUtil.BrandInfo("acme", "Acme Corp", "logo.png", "acme");
        assertEquals("Acme Corp Survey", util(brandDir).getApplicationTitle(external, "Survey"));
    }

    // UC-007 BR-006: the brand's "localized" block supplies per-language display names.
    @Test
    void detectCurrentBrand_localizedBlock_resolvesTagThenLanguageThenBase(@TempDir Path brandDir) throws IOException {
        writeConfig(brandDir, "{\"name\": \"Health Test\", \"organization\": \"Health Test Organization\", "
                + "\"localized\": {\"es-419\": {\"organization\": \"Organizaci\u00f3n de prueba\"}, "
                + "\"ar\": {\"name\": \"\u0645\u0646\u0638\u0645\u0629\"}}}");

        BrandUtil.BrandInfo info = util(brandDir).detectCurrentBrand();

        assertEquals("Health Test Organization", info.getDisplayName());
        assertEquals("Organizaci\u00f3n de prueba", info.getDisplayName(Locale.forLanguageTag("es-419")));
        assertEquals("Organizaci\u00f3n de prueba", info.getDisplayName(Locale.forLanguageTag("es-GT")), "language-only fallback");
        assertEquals("\u0645\u0646\u0638\u0645\u0629", info.getDisplayName(Locale.forLanguageTag("ar")), "name is used when no organization variant");
        assertEquals("Health Test Organization", info.getDisplayName(Locale.FRENCH), "base name when the language has no variant");
        assertEquals("health-test", info.getBrandKey(), "the technical key always derives from the base name");
    }

    @Test
    void detectCurrentBrand_withoutLocalizedBlock_localeLookupReturnsBase(@TempDir Path brandDir) throws IOException {
        writeConfig(brandDir, "{\"name\": \"Plain Brand\"}");

        BrandUtil.BrandInfo info = util(brandDir).detectCurrentBrand();

        assertEquals("Plain Brand", info.getDisplayName(Locale.forLanguageTag("ar")));
        assertEquals("Plain Brand", info.getDisplayName(null));
    }
}
