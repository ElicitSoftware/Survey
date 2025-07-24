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

import com.elicitsoftware.UISessionDataService;
import com.vaadin.quarkus.annotation.NormalUIScoped;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;
import jakarta.inject.Inject;

/**
 * The `LogoutView` class is responsible for managing the user logout functionality of the application.
 * It extends `VerticalLayout` and ensures that the user is securely logged out and redirected to the
 * application's default view.
 * <p>
 * Core Behavior:
 * - When the view is attached to the UI, it automatically triggers the logout process
 * - Clears the UI-scoped session data through UISessionDataService
 * - Redirects the user to the root view (login page) using JavaScript for a clean page reload
 * <p>
 * This approach avoids session invalidation timing issues and provides a smooth logout experience.
 */
@Route("logout")
@NormalUIScoped
public class LogoutView extends VerticalLayout {

    /** The UI-scoped session data service for clearing respondent session information during logout. */
    @Inject
    UISessionDataService sessionDataService;

    /**
     * Default constructor for LogoutView.
     * <p>
     * The constructor is intentionally empty as the logout logic is performed
     * in the {@link #onAttach(AttachEvent)} method to ensure proper UI attachment
     * before executing the logout process.
     */
    public LogoutView() {
        // Constructor intentionally empty - logout logic happens in onAttach
    }
    
    /**
     * Performs the logout process when the view is attached to the UI.
     * <p>
     * This method is called automatically when the LogoutView is attached to the UI
     * and executes the following logout sequence:
     * <ol>
     * <li>Clears the UI-scoped session data through {@code UISessionDataService}</li>
     * <li>Redirects the user to the root URL ("/") using JavaScript for a clean page reload</li>
     * </ol>
     * <p>
     * Using JavaScript redirection avoids session timing issues and provides a smooth
     * logout experience without "Session expired" error messages.
     *
     * @param attachEvent the event fired when this view is attached to the UI
     */
    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        
        // Clear UI-scoped session data first
        if (sessionDataService != null) {
            sessionDataService.clear();
        }
        
        // Use JavaScript to redirect to root with a full page reload
        // This avoids the "Session expired" message by not invalidating session during navigation
        getUI().ifPresent(ui -> {
            ui.getPage().executeJs("window.location.href = '/';");
        });
    }
}
