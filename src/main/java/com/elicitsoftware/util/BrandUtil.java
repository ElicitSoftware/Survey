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

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
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
    
    @ConfigProperty(name = "brand.file.system.path", defaultValue = "/brand")
    String brandFileSystemPath;
    
    /**
     * Data class representing brand information.
     */
    public static class BrandInfo {
        private final String brandKey;
        private final String displayName;
        private final String logoPath;
        private final String cssClass;
        
        public BrandInfo(String brandKey, String displayName, String logoPath, String cssClass) {
            this.brandKey = brandKey;
            this.displayName = displayName;
            this.logoPath = logoPath;
            this.cssClass = cssClass;
        }
        
        public String getBrandKey() { return brandKey; }
        public String getDisplayName() { return displayName; }
        public String getLogoPath() { return logoPath; }
        public String getCssClass() { return cssClass; }
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
            brandKey
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
     * Gets the appropriate logo resource path for Vaadin Flow applications.
     * 
     * @param brandInfo the brand information
     * @return resource path suitable for Vaadin Image component
     */
    public String getLogoResourcePath(BrandInfo brandInfo) {
        // For external brands, serve via the brand resource handler
        if (brandInfo.getLogoPath().startsWith(brandFileSystemPath + "/")) {
            return "brand/images/HorizontalLogo.png";
        }
        // For default brand, use the brand resource path (served by embedded resources)
        return brandInfo.getLogoPath();
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
    }
}