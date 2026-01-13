package com.elicitsoftware.flow;

/*-
 * ***LICENSE_START***
 * Elicit Admin
 * %%
 * Copyright (C) 2025 The Regents of the University of Michigan - Rogel Cancer Center
 * %%
 * PolyForm Noncommercial License 1.0.0
 * <https://polyformproject.org/licenses/noncommercial/1.0.0>
 * ***LICENSE_END***
 */

import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.component.page.Inline;
import com.vaadin.flow.server.AppShellSettings;
import com.vaadin.flow.component.dependency.StyleSheet;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import io.quarkus.runtime.Startup;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * The AppConfig class configures application-wide settings for the Vaadin Flow application.
 * It loads the master stylesheet and implements the AppShellConfigurator interface,
 * which allows customization of the app shell configuration.
 * <p>
 * This class is primarily used to define global UI-related settings and is automatically
 * picked up by Vaadin during the application runtime.
 * <p>
 * Key Features:
 * - Theme Configuration: Loads the master stylesheet from META-INF/resources/styles.css
 * - Brand Integration: Configures favicon and CSS links based on mounted brand directories
 * - Fallback System: Implements three-tier brand fallback (external → local → default)
 * - Meta Tags: Adds brand identification information for debugging
 * <p>
 * Brand Directory Precedence:
 * 1. External brand mount (/brand) - Docker volume mounts for runtime branding
 * 2. Local brand directory (brand/) - Development or embedded default brand
 * 3. Application defaults (icons/) - Fallback when no brand is available
 */
@StyleSheet("context://styles.css")
@ApplicationScoped
@Startup
public class AppConfig implements AppShellConfigurator {

    @ConfigProperty(name = "brand.file.system.path", defaultValue = "/brand")
    String brandFileSystemPath;

    @ConfigProperty(name = "brand.local.path", defaultValue = "brand")
    String brandLocalPath;

    private String resolvedBrandPath;
    private String resolvedLocalBrandPath;

    @PostConstruct
    void init() {
        // Initialize the resolved brand paths after CDI injection is complete
        // These values are guaranteed to be available due to @Startup and @PostConstruct
        resolvedBrandPath = brandFileSystemPath;
        resolvedLocalBrandPath = brandLocalPath;
    }

    /**
     * Gets the resolved brand file system path.
     * CDI injection is guaranteed to be complete due to @Startup.
     */
    private String getBrandPath() {
        return resolvedBrandPath;
    }

    /**
     * Gets the resolved local brand path.
     * CDI injection is guaranteed to be complete due to @Startup.
     */
    private String getLocalBrandPath() {
        return resolvedLocalBrandPath;
    }

    /**
     * Configures the application shell settings including favicons, CSS links, and brand metadata.
     * This method is automatically called by Vaadin during application startup to customize
     * the HTML head section of the application.
     *
     * @param settings The AppShellSettings to configure with brand-specific resources
     */
    @Override
    public void configurePage(AppShellSettings settings) {
        // Add brand information as HTML comment for debugging/identification
        addBrandInfoComment(settings);

        // Favicon logic: Use Elicit favicon for default brand, allow external brands to override
        boolean externalBrandMounted = Files.exists(Paths.get(getBrandPath()));

        if (externalBrandMounted) {
            // External brand is mounted - check favicon from brand config
            String faviconPath = getFaviconPathFromBrand();

            if (faviconPath != null) {
                settings.addLink("shortcut icon", faviconPath);
                settings.addFavIcon("icon", faviconPath, "32x32");
            }
        } else {
            // No external brand mounted - check for embedded brand favicon
            String faviconPath = getFaviconPathFromBrand();

            if (faviconPath != null) {
                settings.addLink("shortcut icon", faviconPath);
                settings.addFavIcon("icon", faviconPath, "32x32");
            }
        }

        // Add brand CSS files in specific order - colors first, then typography, then theme
        // This ensures proper CSS cascade and avoids @import issues

        // 1. Load brand colors first (defines CSS variables)
        String brandColorsContent = loadBrandCssContent("colors/brand-colors.css");
        if (brandColorsContent != null) {
            settings.addInlineWithContents(brandColorsContent, Inline.Wrapping.STYLESHEET);
        } else if (Files.exists(Paths.get(getLocalBrandPath(), "colors/brand-colors.css"))) {
            settings.addLink("stylesheet", "/brand/colors/brand-colors.css");
        }

        // 2. Load brand typography second (may depend on color variables)
        String brandTypographyContent = loadBrandCssContent("typography/brand-typography.css");
        if (brandTypographyContent != null) {
            settings.addInlineWithContents(brandTypographyContent, Inline.Wrapping.STYLESHEET);
        } else if (Files.exists(Paths.get(getLocalBrandPath(), "typography/brand-typography.css"))) {
            settings.addLink("stylesheet", "/brand/typography/brand-typography.css");
        }

        // 3. Load theme CSS last (overrides Lumo theme and integrates brand)
        // Use inline loading for theme as well to avoid any @import issues
        String brandThemeContent = loadBrandCssContent("theme.css");
        if (brandThemeContent != null) {
            settings.addInlineWithContents(brandThemeContent, Inline.Wrapping.STYLESHEET);
        } else {
            if (Files.exists(Paths.get(getBrandPath(), "theme.css")) ||
                   Files.exists(Paths.get(getLocalBrandPath(), "theme.css"))) {
                settings.addLink("stylesheet", "/brand/theme.css");
            }
        }
    }

    /**
     * Adds brand information as a meta tag for debugging and identification purposes.
     * Reads brand metadata from the external brand directory if available.
     *
     * @param settings The AppShellSettings to add the brand info to
     */
    private void addBrandInfoComment(AppShellSettings settings) {
        String brandInfo = detectBrandInfo();
        if (brandInfo != null && !brandInfo.isEmpty()) {
            // Add brand info as a meta tag for easy identification in page source
            settings.addMetaTag("brand-info", brandInfo);
        }
    }

    /**
     * Detects and reads brand information from various sources for debugging and identification.
     * Implements the brand directory precedence: external mount (/brand) → local directory (brand/).
     * Attempts to read metadata from brand-info.json first, then brand-config.json as fallback.
     *
     * @return A formatted string containing brand information, or a fallback message if no brand is detected.
     *         Format: "{External|Default} Brand: {name} (v{version}) - {organization} [from {filename}]"
     */
    private String detectBrandInfo() {
        // Check for external brand directory first, then local brand
        String brandPath = null;
        String brandSource = null;

        if (Files.exists(Paths.get(getBrandPath()))) {
            brandPath = getBrandPath();
            brandSource = "external mount";
        } else if (Files.exists(Paths.get(getLocalBrandPath()))) {
            brandPath = getLocalBrandPath();
            brandSource = "local directory";
        }

        if (brandPath != null) {
            // Try to read brand-info.json first, then brand-config.json as fallback
            try {
                java.nio.file.Path brandInfoPath = Paths.get(brandPath, "brand-info.json");
                java.nio.file.Path brandConfigPath = Paths.get(brandPath, "brand-config.json");

                java.nio.file.Path metadataFile = null;
                if (Files.exists(brandInfoPath)) {
                    metadataFile = brandInfoPath;
                } else if (Files.exists(brandConfigPath)) {
                    metadataFile = brandConfigPath;
                }

                if (metadataFile != null) {
                    String content = Files.readString(metadataFile);
                    // Extract basic info from JSON (simple parsing)
                    String brandName = extractJsonValue(content, "name");
                    String version = extractJsonValue(content, "version");
                    String organization = extractJsonValue(content, "organization");

                    if (brandName != null) {
                        String brandType = "external mount".equals(brandSource) ? "External" : "Default";
                        return String.format("%s Brand: %s (v%s) - %s [from %s]",
                            brandType,
                            brandName,
                            version != null ? version : "unknown",
                            organization != null ? organization : "",
                            metadataFile.getFileName());
                    }
                }

                // Fallback: list available brand files
                String fileListPrefix = "external mount".equals(brandSource) ? "External Brand Files: " : "Default Brand Files: ";
                StringBuilder fileList = new StringBuilder(fileListPrefix);
                if (Files.exists(Paths.get(brandPath, "colors/brand-colors.css"))) {
                    fileList.append("colors ");
                }
                if (Files.exists(Paths.get(brandPath, "typography/brand-typography.css"))) {
                    fileList.append("typography ");
                }
                if (Files.exists(Paths.get(brandPath, "theme.css"))) {
                    fileList.append("theme ");
                }

                return fileList.toString().trim();

            } catch (Exception e) {
                String errorType = "external mount".equals(brandSource) ? "External" : "Default";
                return errorType + " Brand Directory Detected (error reading metadata: " + e.getMessage() + ")";
            }
        }

        return "Default Theme (no brand directory found)";
    }

    /**
     * Simple JSON value extraction (avoiding full JSON parsing dependency).
     *
     * @param json The JSON content
     * @param key The key to extract
     * @return The value or null if not found
     */
    private String extractJsonValue(String json, String key) {
        try {
            String pattern = "\"" + key + "\"\\s*:\\s*\"([^\"]+)\"";
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
            java.util.regex.Matcher m = p.matcher(json);
            if (m.find()) {
                return m.group(1);
            }
        } catch (Exception e) {
            // Silently handle JSON parsing errors
        }
        return null;
    }

    /**
     * Gets the favicon path from brand configuration.
     * Reads the favicon filename from brand-config.json and constructs the appropriate URL.
     *
     * @return The favicon URL path, or null if no favicon is configured
     */
    private String getFaviconPathFromBrand() {
        try {
            // First try to read from external brand
            if (Files.exists(Paths.get(getBrandPath(), "brand-config.json"))) {
                String content = Files.readString(Paths.get(getBrandPath(), "brand-config.json"));
                String faviconFile = extractJsonValue(content, "favicon");
                if (faviconFile != null) {
                    return "/brand/images/" + faviconFile;
                }
            }

            // Then try embedded brand
            try (var inputStream = getClass().getResourceAsStream("/META-INF/brand/brand-config.json")) {
                if (inputStream != null) {
                    String content = new String(inputStream.readAllBytes());
                    String faviconFile = extractJsonValue(content, "favicon");
                    if (faviconFile != null) {
                        return "/brand/images/" + faviconFile;
                    }
                }
            }

            // Fallback to common favicon files
            return getBrandResourcePath("images/favicon.ico",
                                      getBrandResourcePath("images/favicon.svg",
                                      getBrandResourcePath("images/favicon.png", null)));

        } catch (Exception e) {
            // Fallback to common favicon files
            return getBrandResourcePath("images/favicon.ico",
                                      getBrandResourcePath("images/favicon.svg",
                                      getBrandResourcePath("images/favicon.png", null)));
        }
    }

    /**
     * Implements three-tier brand resource fallback logic for favicon and asset resolution.
     * Checks for brand resources in order of precedence and returns the appropriate URL path.
     *
     * Precedence Order:
     * 1. External brand mount (/brand/{brandPath}) - Docker volume mounts
     * 2. Local brand directory (brand/{brandPath}) - Development or embedded brand
     * 3. Default application path (fallbackPath) - Application defaults
     *
     * @param brandPath The relative path within the brand directory (e.g., "visual-assets/icons/favicon.svg")
     * @param fallbackPath The default application path to use if no brand resource is found (can be null)
     * @return The resolved URL path for the resource, or the fallbackPath if no brand resource exists
     */
    private String getBrandResourcePath(String brandPath, String fallbackPath) {
        // Check if external brand directory exists (for Docker volume mounts)
        if (Files.exists(Paths.get(getBrandPath(), brandPath))) {
            return "/brand/" + brandPath;
        }
        // Check if local brand directory exists (for development or embedded brand)
        if (Files.exists(Paths.get(getLocalBrandPath(), brandPath))) {
            return "/brand/" + brandPath; // Use /brand/ URL path for Survey (served by BrandStaticFileFilter)
        }
        // Use default path
        return fallbackPath;
    }

    /**
     * Loads brand CSS content from external mount or local directory.
     * This method reads CSS files directly without processing @import statements.
     *
     * @param cssPath The relative path to the CSS file within the brand directory
     * @return The CSS content as a string, or null if the file doesn't exist
     */
    private String loadBrandCssContent(String cssPath) {
        try {
            Path externalPath = Paths.get(getBrandPath(), cssPath);
            Path localPath = Paths.get(getLocalBrandPath(), cssPath);

            // Check external brand first, then local brand
            if (Files.exists(externalPath)) {
                return Files.readString(externalPath);
            } else if (Files.exists(localPath)) {
                return Files.readString(localPath);
            } else {
                // Fallback: try to load from embedded resources
                String resourcePath = "/META-INF/brand/" + cssPath;
                try (var inputStream = getClass().getResourceAsStream(resourcePath)) {
                    if (inputStream != null) {
                        return new String(inputStream.readAllBytes());
                    }
                }
            }

        } catch (Exception e) {
            System.err.println("Error loading brand CSS from " + cssPath + ": " + e.getMessage());
        }

        return null;
    }
}
