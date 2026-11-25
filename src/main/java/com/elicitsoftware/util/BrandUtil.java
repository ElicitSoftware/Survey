package com.elicitsoftware.util;

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

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Utility class for managing brand information across the application.
 * Detects brand based on what's mounted at the configured brand directory.
 * 
 * @since 1.0
 * @author Elicit Software
 */
@ApplicationScoped
public class BrandUtil {
    
    /** Cached brand information */
    private BrandInfo cachedBrandInfo = null;

    /** Cached logo resolution, cleared with the brand (umbrella UC-014 BR-006). */
    private Map<Logo, LogoFile> cachedLogos = null;

    /** Names the mounted configuration gives for files the mount does not hold, cached with the logos. */
    private List<String> cachedMissingLogoFiles = List.of();

    /**
     * The three logo files a brand names in {@code brand-config.json} (umbrella UC-014 BR-008):
     * {@code logos.horizontal}, {@code logos.icon} and the top-level {@code favicon}. The default
     * file is the last resort when even the embedded configuration names nothing.
     */
    public enum Logo {
        /** The lockup, the mark with its wording. */
        HORIZONTAL("horizontal", "HorizontalLogo.png"),
        /** The mark alone, shown in the header on the primary color. */
        ICON("icon", "icon-white.png"),
        /** The browser tab's icon. */
        FAVICON(null, "favicon.ico");

        private final String logosField;
        private final String defaultFile;

        Logo(String logosField, String defaultFile) {
            this.logosField = logosField;
            this.defaultFile = defaultFile;
        }

        /**
         * The configuration key, as the documentation writes it.
         *
         * @return {@code favicon}, or {@code logos.} followed by the logo's field
         */
        public String configKey() {
            return logosField == null ? "favicon" : "logos." + logosField;
        }

        String nameIn(JsonNode config) {
            if (config == null) {
                return null;
            }
            JsonNode node = logosField == null ? config.get("favicon")
                    : config.has("logos") ? config.get("logos").get(logosField) : null;
            return node != null && node.isTextual() && !node.asText().isBlank() ? node.asText().trim() : null;
        }
    }

    /**
     * A resolved logo file.
     *
     * @param file    the file name under the brand's {@code images/}
     * @param mounted whether the mounted brand supplies it; otherwise it is the default brand's
     */
    public record LogoFile(String file, boolean mounted) {
    }

    @ConfigProperty(name = "brand.file.system.path", defaultValue = "/brand")
    String brandFileSystemPath;
    
    /**
     * Data class representing brand information.
     */
    public static class BrandInfo {
        public static final String DEFAULT_BRAND_KEY = "default-brand";

        private final String brandKey;
        private final String displayName;
        private final String logoPath;
        private final String cssClass;
        /** Display name per language tag (lower-case), from the brand's {@code localized} block. */
        private final Map<String, String> localizedNames;
        
        /**
         * A brand with no localized display names.
         *
         * @param brandKey    the brand's key, {@value #DEFAULT_BRAND_KEY} for the default brand
         * @param displayName the brand's display name
         * @param logoPath    the path of the brand's logo
         * @param cssClass    the CSS class name added to the header for this brand
         */
        public BrandInfo(String brandKey, String displayName, String logoPath, String cssClass) {
            this(brandKey, displayName, logoPath, cssClass, Collections.emptyMap());
        }

        /**
         * A brand with display names per language.
         *
         * @param brandKey       the brand's key, {@value #DEFAULT_BRAND_KEY} for the default brand
         * @param displayName    the brand's base display name
         * @param logoPath       the path of the brand's logo
         * @param cssClass       the CSS class name added to the header for this brand
         * @param localizedNames display name per lower-case language tag; copied, and
         *                       {@code null} is taken as none
         */
        public BrandInfo(String brandKey, String displayName, String logoPath, String cssClass,
                         Map<String, String> localizedNames) {
            this.brandKey = brandKey;
            this.displayName = displayName;
            this.logoPath = logoPath;
            this.cssClass = cssClass;
            this.localizedNames = localizedNames == null ? Collections.emptyMap() : Map.copyOf(localizedNames);
        }
        
        /**
         * Returns the brand's key.
         *
         * @return the brand key
         */
        public String getBrandKey() { return brandKey; }
        /**
         * Returns the brand's base display name, untranslated.
         *
         * @return the base display name
         */
        public String getDisplayName() { return displayName; }
        /**
         * Returns the path of the brand's logo.
         *
         * @return the logo path
         */
        public String getLogoPath() { return logoPath; }
        /**
         * Returns the CSS class name added to the header for this brand.
         *
         * @return the CSS class name
         */
        public String getCssClass() { return cssClass; }
        /**
         * Whether this is the default brand.
         *
         * @return {@code true} when the brand key is {@value #DEFAULT_BRAND_KEY}
         */
        public boolean isDefaultBrand() { return DEFAULT_BRAND_KEY.equals(brandKey); }

        /**
         * The display name for a locale (UC-009 BR-006): the brand's variant for the exact
         * language tag, then for the language alone, then for any regional variant of the
         * language, then the base name.
         *
         * @param locale the locale, or {@code null} for the base name
         * @return the display name to show
         */
        public String getDisplayName(Locale locale) {
            if (locale == null || localizedNames.isEmpty()) {
                return displayName;
            }
            String byTag = localizedNames.get(locale.toLanguageTag().toLowerCase(Locale.ROOT));
            if (byTag != null) {
                return byTag;
            }
            String language = locale.getLanguage().toLowerCase(Locale.ROOT);
            String byLanguage = localizedNames.get(language);
            if (byLanguage != null) {
                return byLanguage;
            }
            // Any variant of the same language (es-GT -> es-419) before the base name
            return localizedNames.entrySet().stream()
                    .filter(e -> e.getKey().startsWith(language + "-"))
                    .map(Map.Entry::getValue)
                    .findFirst()
                    .orElse(displayName);
        }
    }
    
    /**
     * Gets the current brand information.
     * Detects brand based on mounted directory at the configured brand path.
     * 
     * @return BrandInfo object containing brand configuration details
     */
    public BrandInfo detectCurrentBrand() {
        if (cachedBrandInfo != null) {
            return cachedBrandInfo;
        }
        
        // Check for brand config file at the configured path
        Path brandConfigPath = Paths.get(brandFileSystemPath, "brand-config.json");
        
        try {
            String content;
            
            if (Files.exists(brandConfigPath)) {
                // External brand mounted at filesystem path
                content = new String(Files.readAllBytes(brandConfigPath));
            } else {
                // Try to load from classpath resources
                var resource = getClass().getResourceAsStream("/" + brandFileSystemPath + "/brand-config.json");
                if (resource != null) {
                    content = new String(resource.readAllBytes());
                } else {
                    // Ultimate fallback
                    cachedBrandInfo = getDefaultBrand();
                    return cachedBrandInfo;
                }
            }
            
            cachedBrandInfo = parseBrandConfig(content);
        } catch (Exception e) {
            // Fall back to default
            cachedBrandInfo = getDefaultBrand();
        }
        
        return cachedBrandInfo;
    }

    /**
     * Parses brand configuration JSON and creates BrandInfo.
     */
    private BrandInfo parseBrandConfig(String content) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode config = mapper.readTree(content);
        
        // Extract brand information from JSON configuration
        String brandName = extractBrandName(config);
        String organization = extractOrganization(config);
        String logoPath = extractLogoPath(config);
        
        // Generate brand key from the name
        String brandKey = generateBrandKey(brandName);
        
        return new BrandInfo(
            brandKey,
            organization != null ? organization : brandName,
            logoPath,
            brandKey,
            extractLocalizedNames(config)
        );
    }

    /**
     * Extracts the brand name from the configuration, handling different JSON structures.
     */
    private String extractBrandName(JsonNode config) {
        // Try different possible locations for brand name
        if (config.has("name")) {
            return config.get("name").asText();
        }
        if (config.has("brand") && config.get("brand").has("name")) {
            return config.get("brand").get("name").asText();
        }
        return "External Brand";
    }
    
    /**
     * Extracts the organization name from the configuration.
     */
    private String extractOrganization(JsonNode config) {
        if (config.has("organization")) {
            return config.get("organization").asText();
        }
        return null;
    }
    
    /**
     * Reads the optional {@code localized} block: {@code {"localized": {"es-419": {"name": ..,
     * "organization": ..}, "ar": {..}}}}. The organization wins over the name, mirroring the
     * base display name.
     */
    static Map<String, String> extractLocalizedNames(JsonNode config) {
        Map<String, String> names = new HashMap<>();
        JsonNode localized = config.get("localized");
        if (localized == null || !localized.isObject()) {
            return names;
        }
        localized.fields().forEachRemaining(entry -> {
            JsonNode value = entry.getValue();
            String name = value.hasNonNull("organization") ? value.get("organization").asText()
                    : value.hasNonNull("name") ? value.get("name").asText() : null;
            if (name != null && !name.isBlank()) {
                names.put(entry.getKey().toLowerCase(Locale.ROOT), name);
            }
        });
        return names;
    }

    /**
     * Extracts the logo path from the configuration.
     */
    private String extractLogoPath(JsonNode config) {
        if (config.has("logos")) {
            JsonNode logos = config.get("logos");
            String logoFile = null;
            
            // Prefer horizontal logo, then primary, then any available
            if (logos.has("horizontal")) {
                logoFile = logos.get("horizontal").asText();
            } else if (logos.has("primary")) {
                logoFile = logos.get("primary").asText();
            } else {
                // Get first available logo
                var fieldNames = logos.fieldNames();
                if (fieldNames.hasNext()) {
                    String firstField = fieldNames.next();
                    logoFile = logos.get(firstField).asText();
                }
            }
            
            if (logoFile != null) {
                return "brand/images/" + logoFile;
            }
        }
        
        // Default logo path
        return "brand/images/HorizontalLogo.png";
    }
    
    /**
     * Generates a brand key from the brand name for CSS class usage.
     */
    private String generateBrandKey(String brandName) {
        return brandName.toLowerCase()
            .replaceAll("[^a-z0-9\\s-]", "") // Remove special characters
            .replaceAll("\\s+", "-") // Replace spaces with hyphens
            .replaceAll("-+", "-") // Collapse multiple hyphens
            .replaceAll("^-|-$", ""); // Remove leading/trailing hyphens
    }
    
    /**
     * Gets the default brand information.
     */
    private BrandInfo getDefaultBrand() {
        return new BrandInfo(
            "default-brand",
            "Elicit",
            "brand/images/HorizontalLogo.png",
            "default-brand"
        );
    }

    /**
     * The URL of the brand's lockup ({@code logos.horizontal}, umbrella UC-014 BR-008).
     *
     * @param brandInfo the brand information (unused; the lockup comes from the configuration)
     * @return resource path suitable for Vaadin Image component
     */
    public String getLogoResourcePath(BrandInfo brandInfo) {
        return getLogoUrl(Logo.HORIZONTAL);
    }

    /**
     * The URL of the brand's mark for the nav header ({@code logos.icon}, umbrella UC-014
     * BR-008), where the full horizontal lockup's wordmark is illegible and its dark artwork
     * disappears against the dark header background.
     *
     * @param brandInfo the brand information (unused; the icon comes from the configuration)
     * @return resource path suitable for Vaadin Image component
     */
    public String getIconResourcePath(BrandInfo brandInfo) {
        return getLogoUrl(Logo.ICON);
    }

    /**
     * The URL a logo is served at by {@code BrandResourceHandler}, relative to the application
     * root as the header's {@code Image} uses it.
     *
     * @param logo which logo
     * @return the {@code brand/images/} URL of the resolved file
     */
    public String getLogoUrl(Logo logo) {
        return "brand/images/" + getLogoFile(logo).file();
    }

    /**
     * Resolves one logo file (umbrella UC-014 BR-008): the file the mounted configuration names
     * when the mount holds it; otherwise the file the embedded configuration names, which counts
     * as mounted when the mount holds a file of that name (per-file fallback, BR-002).
     *
     * @param logo which logo
     * @return the file name under {@code images/} and whether the mount supplies it
     */
    public LogoFile getLogoFile(Logo logo) {
        return logos().get(logo);
    }

    /**
     * Whether the mounted brand brings its own favicon but no header icon of its own (umbrella
     * UC-014 BR-009), so the header shows the default brand's mark beside the site's favicon.
     *
     * @return true when the favicon is mounted and the icon is not
     */
    public boolean isIconMissingBesideFavicon() {
        return getLogoFile(Logo.FAVICON).mounted() && !getLogoFile(Logo.ICON).mounted();
    }

    /**
     * The logo files the mounted configuration names but the mount does not hold (umbrella
     * UC-014 BR-008); each is served from the default brand instead.
     *
     * @return {@code images/<file>} for each, in configuration order
     */
    public List<String> getMissingLogoFiles() {
        logos();
        return cachedMissingLogoFiles;
    }

    private synchronized Map<Logo, LogoFile> logos() {
        if (cachedLogos != null) {
            return cachedLogos;
        }
        Path mount = Paths.get(brandFileSystemPath);
        JsonNode mounted = readConfig(mount.resolve("brand-config.json"));
        JsonNode embedded = readEmbeddedConfig();
        Map<Logo, LogoFile> logos = new EnumMap<>(Logo.class);
        List<String> missing = new ArrayList<>();
        for (Logo logo : Logo.values()) {
            String embeddedName = logo.nameIn(embedded) != null ? logo.nameIn(embedded) : logo.defaultFile;
            String named = logo.nameIn(mounted);
            if (named != null && isImage(mount, named)) {
                logos.put(logo, new LogoFile(named, true));
                continue;
            }
            if (named != null) {
                missing.add("images/" + named);
            }
            logos.put(logo, new LogoFile(embeddedName, isImage(mount, embeddedName)));
        }
        cachedMissingLogoFiles = List.copyOf(missing);
        cachedLogos = logos;
        return logos;
    }

    private static boolean isImage(Path mount, String file) {
        Path images = mount.resolve("images").normalize();
        Path candidate = images.resolve(file).normalize();
        return candidate.startsWith(images) && Files.isRegularFile(candidate);
    }

    private static JsonNode readConfig(Path file) {
        try {
            return Files.isRegularFile(file) ? new ObjectMapper().readTree(Files.readString(file)) : null;
        } catch (Exception e) {
            return null;
        }
    }

    private JsonNode readEmbeddedConfig() {
        try (InputStream in = getClass().getResourceAsStream("/META-INF/brand/brand-config.json")) {
            return in != null ? new ObjectMapper().readTree(in) : null;
        } catch (Exception e) {
            return null;
        }
    }


    /**
     * Gets the brand-specific application title.
     * 
     * @param brandInfo the brand information
     * @param appType the application type ("Survey" or "Admin")
     * @return formatted application title
     */
    public String getApplicationTitle(BrandInfo brandInfo, String appType) {
        // Use the brand's display name if available, otherwise fall back to default
        String brandName = brandInfo.getDisplayName();
        
        if ("default-brand".equals(brandInfo.getBrandKey())) {
            return "Elicit " + appType;
        }
        
        // For external brands, use their display name
        return brandName + " " + appType;
    }
    
    /**
     * Clears the cached brand information to force re-detection.
     * Useful for development or when brand configuration changes at runtime.
     */
    public void clearCache() {
        cachedBrandInfo = null;
        cachedLogos = null;
    }
}