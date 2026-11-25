package com.elicitsoftware.flow;

/*-
 * ***LICENSE_START***
 * Elicit Survey
 * %%
 * Copyright (C) 2025 - 2026 Elicit Software LLC
 * %%
 * PolyForm Noncommercial License 1.0.0
 * <https://polyformproject.org/licenses/noncommercial/1.0.0>
 * ***LICENSE_END***
 */

import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.component.page.Inline;
import com.vaadin.flow.server.AppShellSettings;
import com.vaadin.flow.component.dependency.StyleSheet;
import com.vaadin.flow.theme.lumo.Lumo;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.elicitsoftware.util.BrandUtil;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import io.quarkus.logging.Log;
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
// Load the Lumo theme (and its utility classes) via @StyleSheet before the
// application stylesheet. Theme stylesheets must come first so application
// overrides in styles.css win the cascade. Using the Lumo constants is the
// supported Vaadin 25 mechanism; CSS @import of the theme is an anti-pattern.
@StyleSheet(Lumo.STYLESHEET)
@StyleSheet(Lumo.UTILITY_STYLESHEET)
@StyleSheet("context://styles.css")
@ApplicationScoped
@Startup
public class AppConfig implements AppShellConfigurator {

    @ConfigProperty(name = "brand.file.system.path", defaultValue = "/brand")
    String brandFileSystemPath;

    @ConfigProperty(name = "brand.local.path", defaultValue = "brand")
    String brandLocalPath;

    /** Resolves the logo files the brand configuration names (umbrella UC-014 BR-008). */
    @Inject
    BrandUtil brandUtil;

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

        // The favicon the brand configuration names, else the default brand's (umbrella UC-014 BR-008)
        String faviconPath = "/" + brandUtil.getLogoUrl(BrandUtil.Logo.FAVICON);
        settings.addLink("shortcut icon", faviconPath);
        settings.addFavIcon("icon", faviconPath, "32x32");

        // Add brand CSS files in specific order - colors first, then typography, then theme
        // This ensures proper CSS cascade and avoids @import issues

        // 1. Load brand colors first (defines CSS variables)
        String brandColorsContent = loadBrandCssContent("colors/brand-colors.css");
        if (brandColorsContent != null) {
            settings.addInlineWithContents(withBrandUrls(brandColorsContent, "colors/brand-colors.css"), Inline.Wrapping.STYLESHEET);
        } else if (Files.exists(Paths.get(getLocalBrandPath(), "colors/brand-colors.css"))) {
            settings.addLink("stylesheet", "/brand/colors/brand-colors.css");
        }

        // 2. Load brand typography second (may depend on color variables)
        String brandTypographyContent = loadBrandCssContent("typography/brand-typography.css");
        if (brandTypographyContent != null) {
            settings.addInlineWithContents(withBrandUrls(brandTypographyContent, "typography/brand-typography.css"), Inline.Wrapping.STYLESHEET);
        } else if (Files.exists(Paths.get(getLocalBrandPath(), "typography/brand-typography.css"))) {
            settings.addLink("stylesheet", "/brand/typography/brand-typography.css");
        }

        // 3. Load theme CSS last (overrides Lumo theme and integrates brand)
        // Use inline loading for theme as well to avoid any @import issues
        String brandThemeContent = loadBrandCssContent("theme.css");
        if (brandThemeContent != null) {
            settings.addInlineWithContents(withBrandUrls(withoutInlinedImports(brandThemeContent), "theme.css"), Inline.Wrapping.STYLESHEET);
        } else {
            if (Files.exists(Paths.get(getBrandPath(), "theme.css")) ||
                   Files.exists(Paths.get(getLocalBrandPath(), "theme.css"))) {
                settings.addLink("stylesheet", "/brand/theme.css");
            }
        }
    }

    /**
     * Strips the {@code @import} rules for the color and typography sheets from an inlined theme.
     * Both sheets are inlined ahead of the theme already; left in, the browser would fetch and
     * apply each of them a second time.
     */
    static String withoutInlinedImports(String themeCss) {
        return themeCss.replaceAll(
                "(?m)^[ \\t]*@import\\s+(?:url\\(\\s*)?['\"]?\\.?/?(?:colors/brand-colors|typography/brand-typography)\\.css['\"]?\\s*\\)?\\s*;[ \\t]*\\r?\\n?",
                "");
    }

    /** Where BrandResourceHandler serves the brand's files. */
    static final String BRAND_URL = "/brand/";

    /** A CSS {@code url(...)} value, with or without quotes around its target. */
    private static final java.util.regex.Pattern CSS_URL =
            java.util.regex.Pattern.compile("url\\(\\s*(['\"]?)([^'\")]+)\\1\\s*\\)");

    /**
     * Resolves the relative {@code url(...)} values of a brand stylesheet against the brand
     * (NFR-021, umbrella UC-014 BR-007). The stylesheet is inlined into the page, where a
     * relative URL would resolve against the page's address and reach nothing; here it becomes the
     * address the file has when
     * BrandResourceHandler serves it, so {@code url("fonts/Face.woff2")} in
     * {@code typography/brand-typography.css} is fetched from
     * {@code /brand/typography/fonts/Face.woff2}. That is what lets a brand carry its own fonts.
     * A URL that is absolute, rooted, a fragment or a {@code data:} value is left as written, and
     * so is one that would climb out of the brand.
     *
     * @param css     the stylesheet's content
     * @param cssPath the stylesheet's path within the brand, such as {@code typography/brand-typography.css}
     * @return the content with its relative URLs resolved
     */
    static String withBrandUrls(String css, String cssPath) {
        int slash = cssPath.lastIndexOf('/');
        String directory = slash < 0 ? "" : cssPath.substring(0, slash + 1);
        java.util.regex.Matcher matcher = CSS_URL.matcher(css);
        StringBuilder resolved = new StringBuilder();
        while (matcher.find()) {
            String path = pathInBrand(directory, matcher.group(2).trim());
            String url = path == null ? matcher.group() : "url(\"" + BRAND_URL + path + "\")";
            matcher.appendReplacement(resolved, java.util.regex.Matcher.quoteReplacement(url));
        }
        matcher.appendTail(resolved);
        return resolved.toString();
    }

    /** The brand-relative path a stylesheet's URL names, or null when the URL is not relative to the brand. */
    private static String pathInBrand(String directory, String target) {
        if (target.isEmpty() || target.startsWith("/") || target.startsWith("#")
                || target.matches("^[a-zA-Z][a-zA-Z0-9+.-]*:.*")) {
            return null;
        }
        java.util.ArrayDeque<String> segments = new java.util.ArrayDeque<>();
        for (String segment : (directory + target).split("/")) {
            if (segment.isEmpty() || segment.equals(".")) {
                continue;
            }
            if (segment.equals("..")) {
                if (segments.isEmpty()) {
                    return null;
                }
                segments.removeLast();
            } else {
                segments.addLast(segment);
            }
        }
        return String.join("/", segments);
    }

    /**
     * Adds brand information as a meta tag for debugging and identification purposes.
     * Reads brand metadata from the external brand directory if available.
     *
     * @param settings The AppShellSettings to add the brand info to
     */
    private void addBrandInfoComment(AppShellSettings settings) {
        java.util.Locale requestLocale = settings.getRequest() != null ? settings.getRequest().getLocale() : java.util.Locale.ENGLISH;
        String brandInfo = detectBrandInfo(requestLocale);
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
    /**
     * Reads the {@code localized} block of a brand JSON file (top level or under {@code brand})
     * for the given locale: exact tag first, then language only. Returns an empty map when the
     * brand has no variants or the JSON cannot be parsed.
     */
    static java.util.Map<String, String> localizedBrandText(String json, java.util.Locale locale) {
        java.util.Map<String, String> result = new java.util.HashMap<>();
        if (json == null || locale == null) {
            return result;
        }
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper()
                    .enable(com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_COMMENTS);
            com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(json);
            com.fasterxml.jackson.databind.JsonNode brand = root.has("brand") ? root.get("brand") : root;
            com.fasterxml.jackson.databind.JsonNode localized = brand.get("localized");
            if (localized == null && root.has("localized")) {
                localized = root.get("localized");
            }
            if (localized == null || !localized.isObject()) {
                return result;
            }
            com.fasterxml.jackson.databind.JsonNode entry = null;
            java.util.Iterator<java.util.Map.Entry<String, com.fasterxml.jackson.databind.JsonNode>> it = localized.fields();
            String tag = locale.toLanguageTag().toLowerCase(java.util.Locale.ROOT);
            String language = locale.getLanguage().toLowerCase(java.util.Locale.ROOT);
            com.fasterxml.jackson.databind.JsonNode byLanguage = null;
            while (it.hasNext()) {
                var e = it.next();
                String key = e.getKey().toLowerCase(java.util.Locale.ROOT);
                if (key.equals(tag)) {
                    entry = e.getValue();
                } else if (key.equals(language)) {
                    byLanguage = e.getValue();
                }
            }
            if (entry == null) {
                entry = byLanguage;
            }
            if (entry != null) {
                for (String field : new String[] {"name", "organization", "description"}) {
                    if (entry.hasNonNull(field)) {
                        result.put(field, entry.get(field).asText());
                    }
                }
            }
        } catch (Exception e) {
            Log.debug("No localized brand text available", e);
        }
        return result;
    }

    private String detectBrandInfo(java.util.Locale locale) {
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
                    String description = null;
                    // Per-language variants from the brand's "localized" block (UC-009 BR-006)
                    java.util.Map<String, String> localized = localizedBrandText(content, locale);
                    brandName = localized.getOrDefault("name", brandName);
                    organization = localized.getOrDefault("organization", organization);
                    description = localized.getOrDefault("description", extractJsonValue(content, "description"));

                    if (brandName != null) {
                        String brandType = "external mount".equals(brandSource) ? "External" : "Default";
                        return String.format("%s Brand: %s (v%s) - %s%s [from %s]",
                            brandType,
                            brandName,
                            version != null ? version : "unknown",
                            organization != null ? organization : "",
                            description != null && !description.isBlank() ? " - " + description : "",
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
            Log.error("Error loading brand CSS from " + cssPath, e);
        }

        return null;
    }
}
