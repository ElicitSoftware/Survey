package com.elicitsoftware.flow;

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

import com.vaadin.flow.server.AppShellSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * AppConfig.configurePage() is AppShellConfigurator's single public entry point, and it takes
 * an AppShellSettings that Vaadin itself normally supplies. AppShellSettings has a public
 * no-arg constructor so it's constructible here, but its own accumulation methods
 * (getHeadElements/getInlineElements) are package-private to com.vaadin.flow.server, so this
 * test can't assert on the resulting HTML head content -- only that every brand-detection
 * branch (external mount / local directory / embedded fallback / missing entirely, with and
 * without metadata) runs without throwing. brandFileSystemPath/brandLocalPath are
 * package-private @ConfigProperty fields, settable directly; init() (also package-private)
 * must be called manually since there's no CDI container running @PostConstruct here.
 */
class AppConfigTest {

    private static AppConfig configFor(Path externalDir, Path localDir) {
        AppConfig config = new AppConfig();
        config.brandFileSystemPath = externalDir.toString();
        config.brandLocalPath = localDir.toString();
        config.init();
        return config;
    }

    private static void write(Path dir, String relativePath, String content) throws IOException {
        Path file = dir.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    // Neither external mount nor local brand directory exists at all -- falls through to
    // the embedded META-INF/brand/ resources shipped with the application (the common case
    // for a deployment that hasn't mounted any custom brand).
    @Test
    void configurePage_noBrandDirectoriesAtAll_fallsBackToEmbeddedResourcesWithoutThrowing(@TempDir Path root) {
        AppConfig config = configFor(root.resolve("no-external"), root.resolve("no-local"));

        assertDoesNotThrow(() -> config.configurePage(new AppShellSettings()));
    }

    // External brand mount exists with a full set of metadata and CSS files.
    @Test
    void configurePage_externalBrandFullyConfigured_doesNotThrow(@TempDir Path root) throws IOException {
        Path external = root.resolve("external");
        Files.createDirectories(external);
        write(external, "brand-config.json", "{\"name\": \"Acme\", \"favicon\": \"custom.ico\"}");
        write(external, "brand-info.json", "{\"name\": \"Acme\", \"version\": \"2.0\", \"organization\": \"Acme Corp\"}");
        write(external, "colors/brand-colors.css", ":root { --brand-color: blue; }");
        write(external, "typography/brand-typography.css", "body { font-family: sans-serif; }");
        write(external, "theme.css", "body { background: white; }");

        AppConfig config = configFor(external, root.resolve("unused-local"));

        assertDoesNotThrow(() -> config.configurePage(new AppShellSettings()));
    }

    // Local brand directory (no external mount) with metadata missing the "name" key --
    // exercises detectBrandInfo()'s file-listing fallback branch instead of the formatted-name one.
    @Test
    void configurePage_localBrandWithoutNameKey_fallsBackToFileListingWithoutThrowing(@TempDir Path root) throws IOException {
        Path local = root.resolve("local");
        Files.createDirectories(local);
        write(local, "brand-config.json", "{\"other\": \"stuff\"}");
        write(local, "colors/brand-colors.css", ":root {}");
        write(local, "theme.css", "body {}");
        // typography deliberately absent

        AppConfig config = configFor(root.resolve("no-external"), local);

        assertDoesNotThrow(() -> config.configurePage(new AppShellSettings()));
    }

    // A directory literally named "brand-config.json" (not a file) makes Files.exists() true
    // but Files.readString() throw -- exercises detectBrandInfo()'s catch-and-report branch.
    @Test
    void configurePage_metadataPathIsADirectoryNotAFile_isCaughtWithoutThrowing(@TempDir Path root) throws IOException {
        Path local = root.resolve("local");
        Files.createDirectories(local.resolve("brand-config.json"));

        AppConfig config = configFor(root.resolve("no-external"), local);

        assertDoesNotThrow(() -> config.configurePage(new AppShellSettings()));
    }

    // Malformed CSS-adjacent setup: external mount directory exists but is otherwise empty,
    // so every Files.exists() check inside configurePage's CSS-loading calls is false and it
    // must fall through to the embedded-resource branch for every stylesheet.
    @Test
    void configurePage_externalMountExistsButEmpty_fallsBackToEmbeddedCss(@TempDir Path root) throws IOException {
        Path external = root.resolve("external");
        Files.createDirectories(external);

        AppConfig config = configFor(external, root.resolve("no-local"));

        assertDoesNotThrow(() -> config.configurePage(new AppShellSettings()));
    }
}
