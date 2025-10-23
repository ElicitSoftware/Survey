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

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
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
        // Try to use external brand directory first, fallback to default icons
        String faviconPath = getBrandResourcePath("visual-assets/icons/favicon.ico", "icons/favicon.ico");
        String favicon32Path = getBrandResourcePath("visual-assets/icons/favicon-32x32.png", "/icons/favicon-32x32.png");
        
        settings.addLink("shortcut icon", faviconPath);
        settings.addFavIcon("icon", favicon32Path, "32x32");
    }
    
    /**
     * Gets the appropriate resource path for brand assets with fallback logic.
     * @param brandPath Path within the brand directory
     * @param fallbackPath Default path if brand directory is not available
     * @return The resolved resource path
     */
    private String getBrandResourcePath(String brandPath, String fallbackPath) {
        // Check if external brand directory is mounted and contains the resource
        Path externalBrandPath = Paths.get("/brand", brandPath);
        if (Files.exists(externalBrandPath)) {
            return "/brand/" + brandPath;
        }
        
        // Check if brand resource is available in static resources
        try (InputStream resourceStream = getClass().getClassLoader()
                .getResourceAsStream("META-INF/resources/brand/" + brandPath)) {
            if (resourceStream != null) {
                return "/brand/" + brandPath;
            }
        } catch (Exception e) {
            // Ignore and use fallback
        }
        
        // Use fallback path
        return fallbackPath;
    }
}
