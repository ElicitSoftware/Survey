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

/**
 * Utility class for managing brand information across the application.
 * Detects brand based on what's mounted at /brand directory.
 * 
 * @since 1.0
 * @author Elicit Software
 */
public class BrandUtil {
    
    /** Cached brand information */
    private static BrandInfo CACHED_BRAND_INFO = null;
    
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
     * Detects brand based on mounted directory at /brand.
     * 
     * @return BrandInfo object containing brand configuration details
     */
    public static BrandInfo detectCurrentBrand() {
        if (CACHED_BRAND_INFO != null) {
            return CACHED_BRAND_INFO;
        }
        
        // Check if there's a brand config file to identify the brand
        Path brandConfigPath = Paths.get("/brand/brand-config.json");
        
        if (Files.exists(brandConfigPath)) {
            try {
                String content = new String(Files.readAllBytes(brandConfigPath));
                
                // Simple brand detection based on config content
                if (content.contains("University of Michigan") || content.contains("Michigan Medicine")) {
                    CACHED_BRAND_INFO = new BrandInfo(
                        "um-brand",
                        "University of Michigan",
                        "/brand/images/HorizontalLogo.png",
                        "um-brand"
                    );
                } else if (content.contains("test-brand") || content.contains("Healthcare")) {
                    CACHED_BRAND_INFO = new BrandInfo(
                        "test-brand",
                        "Healthcare System",
                        "/brand/images/HorizontalLogo.png",
                        "test-brand"
                    );
                } else {
                    // External brand mounted but unknown type - use generic
                    CACHED_BRAND_INFO = new BrandInfo(
                        "external-brand",
                        "External Brand",
                        "/brand/images/HorizontalLogo.png",
                        "external-brand"
                    );
                }
            } catch (Exception e) {
                // Fall back to default
                CACHED_BRAND_INFO = getDefaultBrand();
            }
        } else {
            // No external brand mounted, use default
            CACHED_BRAND_INFO = getDefaultBrand();
        }
        
        return CACHED_BRAND_INFO;
    }
    
    /**
     * Gets the default brand information.
     */
    private static BrandInfo getDefaultBrand() {
        return new BrandInfo(
            "default-brand",
            "Elicit",
            "images/default-horizontal-logo.png",
            "default-brand"
        );
    }

    /**
     * Gets the appropriate logo resource path for Vaadin Flow applications.
     * 
     * @param brandInfo the brand information
     * @return resource path suitable for Vaadin Image component
     */
    public static String getLogoResourcePath(BrandInfo brandInfo) {
        // For external brands, serve via the brand resource handler
        if (brandInfo.getLogoPath().startsWith("/brand/")) {
            return "brand/images/HorizontalLogo.png";
        }
        // For default brand, use static resources
        return "images/HorizontalLogo.png";
    }
    
    /**
     * Gets the brand-specific application title.
     * 
     * @param brandInfo the brand information
     * @param appType the application type ("Survey" or "Admin")
     * @return formatted application title
     */
    public static String getApplicationTitle(BrandInfo brandInfo, String appType) {
        return switch (brandInfo.getBrandKey()) {
            case "um-brand" -> "Michigan Medicine " + appType;
            case "test-brand" -> "Healthcare " + appType + " System";
            case "external-brand" -> brandInfo.getDisplayName() + " " + appType;
            default -> "Elicit " + appType;
        };
    }
}