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
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Reports which brand directory resolved and where each brand asset came from (UC-007 step 4).
 * <p>
 * Mirrors the three-tier resolution {@code AppConfig} and {@code BrandResourceHandler} perform
 * when the pages render: the mounted directory ({@code brand.file.system.path}), then the
 * local directory ({@code brand.local.path}), then the embedded default under
 * {@code META-INF/brand}. Every asset is reported on its own because a partial mount falls
 * back silently and is otherwise invisible until someone notices the wrong logo.
 */
@ApplicationScoped
public class BrandDiagnostics {

    /** Where an asset resolved from. */
    public enum Source {
        /** The mounted brand directory. */
        EXTERNAL,
        /** The local brand directory. */
        LOCAL,
        /** The default packaged with the application. */
        EMBEDDED,
        /** Nowhere; the page renders without it. */
        ABSENT,
        /** A file exists but could not be read. */
        UNREADABLE
    }

    /**
     * One brand asset.
     *
     * @param path   the path relative to the brand directory
     * @param role   what the asset is for
     * @param source where it resolved from
     * @param detail the resolved location, or the read failure
     */
    public record AssetReport(String path, String role, Source source, String detail) {
    }

    /**
     * The whole brand picture.
     *
     * @param configuredPath {@code brand.file.system.path}
     * @param localPath      {@code brand.local.path}
     * @param externalExists whether the configured directory exists
     * @param localExists    whether the local directory exists
     * @param brandName      the display name of the brand the application resolved
     * @param assets         every expected asset and its source
     */
    public record BrandReport(String configuredPath, String localPath, boolean externalExists, boolean localExists,
                              String brandName, List<AssetReport> assets) {

        /** A one-line summary. */
        public String summary() {
            if (externalExists) {
                return "mounted brand \"" + brandName + "\" at " + configuredPath;
            }
            if (localExists) {
                return "local brand directory " + localPath + " (\"" + brandName + "\")";
            }
            return "embedded default brand; no brand directory at " + configuredPath;
        }

        /** Whether any asset is missing or unreadable. */
        public boolean hasProblem() {
            return assets.stream().anyMatch(a -> a.source() == Source.ABSENT || a.source() == Source.UNREADABLE);
        }
    }

    record Expected(String path, String role) {
    }

    static final List<Expected> EXPECTED = List.of(
            new Expected("colors/brand-colors.css", "colour stylesheet"),
            new Expected("typography/brand-typography.css", "typography stylesheet"),
            new Expected("theme.css", "theme stylesheet"),
            new Expected("images/HorizontalLogo.png", "horizontal logo"),
            new Expected("images/icon-white.png", "header icon"),
            new Expected("images/favicon.ico", "favicon"));

    @ConfigProperty(name = "brand.file.system.path", defaultValue = "/brand")
    String brandFileSystemPath;

    @ConfigProperty(name = "brand.local.path", defaultValue = "brand")
    String brandLocalPath;

    @Inject
    BrandUtil brandUtil;

    public BrandDiagnostics() {
        // CDI managed bean
    }

    public BrandReport report() {
        Path external = Paths.get(brandFileSystemPath);
        Path local = Paths.get(brandLocalPath);
        boolean externalExists = Files.isDirectory(external);
        boolean localExists = Files.isDirectory(local);

        List<AssetReport> assets = new ArrayList<>();
        for (Expected expected : EXPECTED) {
            assets.add(resolve(expected, external, local));
        }
        String brandName = brandUtil != null ? brandUtil.detectCurrentBrand().getDisplayName() : null;
        return new BrandReport(brandFileSystemPath, brandLocalPath, externalExists, localExists, brandName, assets);
    }

    private AssetReport resolve(Expected expected, Path external, Path local) {
        Path externalFile = external.resolve(expected.path());
        if (Files.exists(externalFile)) {
            return readable(expected, Source.EXTERNAL, externalFile);
        }
        Path localFile = local.resolve(expected.path());
        if (Files.exists(localFile)) {
            return readable(expected, Source.LOCAL, localFile);
        }
        try (InputStream embedded = getClass().getResourceAsStream("/META-INF/brand/" + expected.path())) {
            if (embedded != null) {
                return new AssetReport(expected.path(), expected.role(), Source.EMBEDDED, "META-INF/brand/" + expected.path());
            }
        } catch (IOException e) {
            return new AssetReport(expected.path(), expected.role(), Source.UNREADABLE, e.getMessage());
        }
        return new AssetReport(expected.path(), expected.role(), Source.ABSENT, "not found in any location");
    }

    private static AssetReport readable(Expected expected, Source source, Path file) {
        if (Files.isReadable(file) && Files.isRegularFile(file)) {
            return new AssetReport(expected.path(), expected.role(), source, file.toAbsolutePath().toString());
        }
        return new AssetReport(expected.path(), expected.role(), Source.UNREADABLE,
                file.toAbsolutePath() + " exists but cannot be read");
    }
}
