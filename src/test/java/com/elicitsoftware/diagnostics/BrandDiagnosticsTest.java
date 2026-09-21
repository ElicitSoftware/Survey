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
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Brand assets are resolved the same three-tier way the pages resolve them, one at a time.
 *
 * <p>Traceability: UC-007 step 4, A6.</p>
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
        return diagnostics;
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
}
