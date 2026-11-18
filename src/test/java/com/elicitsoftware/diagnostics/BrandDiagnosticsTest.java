package com.elicitsoftware.diagnostics;

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

import com.elicitsoftware.util.BrandUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Brand assets are resolved the same three-tier way the pages resolve them, one at a time.
 *
 * <p>Traceability: UC-007 step 4, A6, A11, BR-008.</p>
 */
class BrandDiagnosticsTest {

    private static BrandDiagnostics diagnostics(Path external, Path local) {
        BrandDiagnostics diagnostics = new BrandDiagnostics();
        diagnostics.brandFileSystemPath = external.toString();
        diagnostics.brandLocalPath = local.toString();
        // The real detector reads the configured mount; the report only needs a display name.
        diagnostics.brandUtil = new BrandUtil() {
            @Override
            public BrandInfo detectCurrentBrand() {
                return new BrandInfo("test-brand", "Test Brand", "brand/images/HorizontalLogo.png", "test-brand");
            }
        };
        setBrandPath(diagnostics.brandUtil, external);
        return diagnostics;
    }

    /** The logos are resolved by the real {@link BrandUtil} against the test's mount. */
    private static void setBrandPath(BrandUtil util, Path path) {
        try {
            Field field = BrandUtil.class.getDeclaredField("brandFileSystemPath");
            field.setAccessible(true);
            field.set(util, path.toString());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void write(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    /** UC-007 step 4: with no brand directory at all, every asset is the embedded default. */
    @Test
    void noBrandDirectoryResolvesEverythingEmbedded(@TempDir Path dir) {
        BrandDiagnostics.BrandReport report = diagnostics(dir.resolve("missing"), dir.resolve("also-missing")).report();

        assertFalse(report.externalExists());
        assertFalse(report.localExists());
        assertTrue(report.summary().startsWith("embedded default brand"), report.summary());
        assertFalse(report.hasProblem());
        assertTrue(report.assets().stream().allMatch(a -> a.source() == BrandDiagnostics.Source.EMBEDDED), report.toString());
    }

    /** UC-007 A6: a partial mount is reported asset by asset, external where present and embedded elsewhere. */
    @Test
    void partialMountIsReportedPerAsset(@TempDir Path dir) throws IOException {
        Path mount = dir.resolve("brand");
        Files.createDirectories(mount.resolve("colors"));
        Files.writeString(mount.resolve("colors/brand-colors.css"), ":root { --brand-primary: #00274c; }");

        BrandDiagnostics.BrandReport report = diagnostics(mount, dir.resolve("missing")).report();

        assertTrue(report.externalExists());
        assertTrue(report.summary().startsWith("mounted brand"), report.summary());
        BrandDiagnostics.AssetReport colors = report.assets().stream()
                .filter(a -> a.path().equals("colors/brand-colors.css")).findFirst().orElseThrow();
        BrandDiagnostics.AssetReport theme = report.assets().stream()
                .filter(a -> a.path().equals("theme.css")).findFirst().orElseThrow();
        assertEquals(BrandDiagnostics.Source.EXTERNAL, colors.source());
        assertEquals(BrandDiagnostics.Source.EMBEDDED, theme.source());
        assertFalse(report.hasProblem());
    }

    /** UC-007 step 4: the local directory is the second tier. */
    @Test
    void localDirectoryIsUsedWhenNothingIsMounted(@TempDir Path dir) throws IOException {
        Path local = dir.resolve("local");
        Files.createDirectories(local);
        Files.writeString(local.resolve("theme.css"), "/* local */");

        BrandDiagnostics.BrandReport report = diagnostics(dir.resolve("missing"), local).report();

        assertTrue(report.localExists());
        assertTrue(report.summary().startsWith("local brand directory"), report.summary());
        BrandDiagnostics.AssetReport theme = report.assets().stream()
                .filter(a -> a.path().equals("theme.css")).findFirst().orElseThrow();
        assertEquals(BrandDiagnostics.Source.LOCAL, theme.source());
    }

    /** UC-007 BR-008, umbrella UC-014 BR-008: the logos are looked for under their configured names. */
    @Test
    void logosAreReportedUnderTheirConfiguredNames(@TempDir Path dir) throws IOException {
        Path mount = dir.resolve("brand");
        write(mount.resolve("brand-config.json"), "{\"name\": \"Acme\", \"logos\": {\"horizontal\": \"Wide.svg\","
                + " \"icon\": \"mark.svg\"}, \"favicon\": \"tab.svg\"}");
        write(mount.resolve("images/Wide.svg"), "<svg/>");
        write(mount.resolve("images/mark.svg"), "<svg/>");
        write(mount.resolve("images/tab.svg"), "<svg/>");

        BrandDiagnostics.BrandReport report = diagnostics(mount, dir.resolve("missing")).report();

        List<BrandDiagnostics.AssetReport> logos = report.assets().subList(3, 6);
        assertEquals(List.of("images/Wide.svg", "images/mark.svg", "images/tab.svg"),
                logos.stream().map(BrandDiagnostics.AssetReport::path).toList());
        logos.forEach(logo -> assertEquals(BrandDiagnostics.Source.EXTERNAL, logo.source(), logo.path()));
        assertEquals(List.of(), report.warnings());
        assertFalse(report.hasProblem());
    }

    /** UC-007 A11, BR-008, umbrella UC-014 BR-009: a favicon of its own with no icon of its own is a warning. */
    @Test
    void mountedFaviconWithoutAnIconIsAWarning(@TempDir Path dir) throws IOException {
        Path mount = dir.resolve("brand");
        write(mount.resolve("brand-config.json"), "{\"name\": \"Acme\", \"favicon\": \"favicon.svg\"}");
        write(mount.resolve("images/favicon.svg"), "<svg/>");

        BrandDiagnostics.BrandReport report = diagnostics(mount, dir.resolve("missing")).report();

        assertEquals(new BrandDiagnostics.AssetReport("images/icon-white.png", "header icon",
                BrandDiagnostics.Source.EMBEDDED, "META-INF/brand/images/icon-white.png"), report.assets().get(4));
        assertEquals(1, report.warnings().size(), report.warnings().toString());
        assertTrue(report.warnings().get(0).contains("logos.icon"), report.warnings().get(0));
        assertTrue(report.hasProblem());
    }

    /** UC-007 A11, BR-008: a logo the configuration names but the mount lacks is a warning, and falls back. */
    @Test
    void namedLogoTheMountLacksIsAWarning(@TempDir Path dir) throws IOException {
        Path mount = dir.resolve("brand");
        write(mount.resolve("brand-config.json"), "{\"name\": \"Acme\", \"logos\": {\"icon\": \"mark.svg\"}}");

        BrandDiagnostics.BrandReport report = diagnostics(mount, dir.resolve("missing")).report();

        assertEquals("images/icon-white.png", report.assets().get(4).path());
        assertEquals(List.of("brand-config.json names images/mark.svg, which the mounted brand does not hold;"
                + " the file of the default brand is shown instead"), report.warnings());
    }

    /** UC-007 BR-008: a mount that names no logos and supplies none warns about nothing. */
    @Test
    void mountWithoutLogosWarnsAboutNothing(@TempDir Path dir) throws IOException {
        Path mount = dir.resolve("brand");
        write(mount.resolve("colors/brand-colors.css"), ":root{}");

        assertEquals(List.of(), diagnostics(mount, dir.resolve("missing")).report().warnings());
    }
}
