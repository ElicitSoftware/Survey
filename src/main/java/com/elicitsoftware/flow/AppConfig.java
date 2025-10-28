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

import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.server.AppShellSettings;
import com.vaadin.flow.theme.Theme;

import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * The AppConfig class configures application-wide settings for the Vaadin Flow application.
 * It sets the theme of the application to "starter-theme" and implements the AppShellConfigurator
 * interface, which allows customization of the app shell configuration.
 * <p>
 * This class is primarily used to define global UI-related settings and is automatically
 * picked up by Vaadin during the application runtime.
 * <p>
 * Key Features:
 * - Theme Configuration: Sets the application-wide theme to "starter-theme"
 * - Brand Integration: Configures favicon and CSS links based on mounted brand directories
 * - Fallback System: Implements three-tier brand fallback (external → local → default)
 * - Meta Tags: Adds brand identification information for debugging
 * <p>
 * Brand Directory Precedence:
 * 1. External brand mount (/brand) - Docker volume mounts for runtime branding
 * 2. Local brand directory (brand/) - Development or embedded default brand
 * 3. Application defaults (icons/) - Fallback when no brand is available
 */
@Theme("starter-theme")
public class AppConfig implements AppShellConfigurator {
    
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
        boolean externalBrandMounted = Files.exists(Paths.get("/brand"));
        
        if (externalBrandMounted) {
            // External brand is mounted - use brand favicons if available, otherwise fall back to Elicit
            String faviconPath = getBrandResourcePath("visual-assets/icons/favicon.ico", "icons/favicon.ico");
            String favicon32Path = getBrandResourcePath("visual-assets/icons/favicon-32x32.png", "/icons/favicon-32x32.png");
            String faviconSvgPath = getBrandResourcePath("visual-assets/icons/favicon.svg", null);
            
            // Set favicon (prefer ICO, fallback to SVG if available)
            if (faviconSvgPath != null && faviconPath.equals("icons/favicon.ico")) {
                // Use SVG favicon if no ICO available but SVG exists
                settings.addLink("icon", faviconSvgPath);
            } else {
                settings.addLink("shortcut icon", faviconPath);
            }
            
            // Set 32x32 favicon (prefer PNG, fallback to SVG if available)  
            if (faviconSvgPath != null && favicon32Path.equals("/icons/favicon-32x32.png")) {
                // Use SVG as 32x32 icon if no PNG available but SVG exists
                settings.addFavIcon("icon", faviconSvgPath, "32x32");
            } else {
                settings.addFavIcon("icon", favicon32Path, "32x32");
            }
        } else {
            // Default brand - always use Elicit favicons
            settings.addLink("shortcut icon", "icons/favicon.ico");
            settings.addFavIcon("icon", "/icons/favicon-32x32.png", "32x32");
        }
        
        // Add brand CSS files - check external mount first, fall back to local brand
        if (Files.exists(Paths.get("/brand/colors/brand-colors.css")) || 
            Files.exists(Paths.get("brand/colors/brand-colors.css"))) {
            settings.addLink("stylesheet", "/brand/colors/brand-colors.css");
        }
        if (Files.exists(Paths.get("/brand/typography/brand-typography.css")) || 
            Files.exists(Paths.get("brand/typography/brand-typography.css"))) {
            settings.addLink("stylesheet", "/brand/typography/brand-typography.css");
        }
        if (Files.exists(Paths.get("/brand/theme.css")) || 
            Files.exists(Paths.get("brand/theme.css"))) {
            settings.addLink("stylesheet", "/brand/theme.css");
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
        
        if (Files.exists(Paths.get("/brand"))) {
            brandPath = "/brand";
            brandSource = "external mount";
        } else if (Files.exists(Paths.get("brand"))) {
            brandPath = "brand";
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
            // Ignore parsing errors
        }
        return null;
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
        if (Files.exists(Paths.get("/brand", brandPath))) {
            return "/brand/" + brandPath;
        }
        // Check if local brand directory exists (for development or Docker image default)
        if (Files.exists(Paths.get("brand", brandPath))) {
            return "/brand/" + brandPath; // Still use /brand/ URL path (served by BrandStaticFileFilter)
        }
        // Use default path
        return fallbackPath;
    }
}
