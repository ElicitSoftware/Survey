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
        settings.addLink("shortcut icon", "icons/favicon.ico");
        settings.addFavIcon("icon", "/icons/favicon-32x32.png", "32x32");
    }
}
