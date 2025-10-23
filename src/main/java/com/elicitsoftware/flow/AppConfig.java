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
 * - Theme Configuration: Sets the application-wide theme.
 * - Shell Customization: Provides a mechanism for customizing the app shell through the
 * AppShellConfigurator interface.
 */
@Theme("starter-theme")
public class AppConfig implements AppShellConfigurator {
    @Override
    public void configurePage(AppShellSettings settings) {
        // Add brand information as HTML comment for debugging/identification
        addBrandInfoComment(settings);
        
        // Use brand favicons if external brand directory is mounted, otherwise use defaults
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
        
        // Add external brand CSS files if they exist - these will load after the theme
        if (Files.exists(Paths.get("/brand/colors/brand-colors.css"))) {
            settings.addLink("stylesheet", "/brand/colors/brand-colors.css");
        }
        if (Files.exists(Paths.get("/brand/typography/brand-typography.css"))) {
            settings.addLink("stylesheet", "/brand/typography/brand-typography.css");
        }
        if (Files.exists(Paths.get("/brand/theme.css"))) {
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
     * Detects and reads brand information from various sources.
     * 
     * @return A string containing brand information or null if no brand is detected
     */
    private String detectBrandInfo() {
        // Check for external brand directory first
        if (Files.exists(Paths.get("/brand"))) {
            // Try to read brand-info.json first, then brand-config.json as fallback
            try {
                java.nio.file.Path brandInfoPath = Paths.get("/brand/brand-info.json");
                java.nio.file.Path brandConfigPath = Paths.get("/brand/brand-config.json");
                
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
                        return String.format("External Brand: %s (v%s) - %s [from %s]", 
                            brandName, 
                            version != null ? version : "unknown", 
                            organization != null ? organization : "",
                            metadataFile.getFileName());
                    }
                }
                
                // Fallback: list available brand files
                StringBuilder fileList = new StringBuilder("External Brand Files: ");
                if (Files.exists(Paths.get("/brand/colors/brand-colors.css"))) {
                    fileList.append("colors ");
                }
                if (Files.exists(Paths.get("/brand/typography/brand-typography.css"))) {
                    fileList.append("typography ");
                }
                if (Files.exists(Paths.get("/brand/theme.css"))) {
                    fileList.append("theme ");
                }
                
                return fileList.toString().trim();
                
            } catch (Exception e) {
                return "External Brand Directory Detected (error reading metadata: " + e.getMessage() + ")";
            }
        }
        
        return "Default Brand (no external brand mounted)";
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
     * Simple fallback logic: use brand path if external directory exists, otherwise use default.
     * @param brandPath Path within the brand directory
     * @param fallbackPath Default path if brand directory is not available
     * @return The resolved resource path
     */
    private String getBrandResourcePath(String brandPath, String fallbackPath) {
        // Check if external brand directory exists (for Docker volume mounts)
        if (Files.exists(Paths.get("/brand", brandPath))) {
            return "/brand/" + brandPath;
        }
        // Use default path
        return fallbackPath;
    }
}
