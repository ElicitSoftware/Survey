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

import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.server.VaadinSession;
import jakarta.inject.Inject;

/**
 * The `LogoutView` class is responsible for managing the user logout functionality of the application.
 * It extends `VerticalLayout` and ensures that the user is securely logged out and redirected to the
 * application's default view.
 * <p>
 * Core Behavior:
 * - Upon construction, the `LogoutView` triggers the logout process automatically, invalidating
 * the user session and clearing relevant session data.
 * - After successfully logging out, it navigates the user to the root view of the application.
 * <p>
 * This class simplifies the process of securely logging out users by handling all session
 * management and cleaning up resources before redirecting.
 */
public class LogoutView extends VerticalLayout {

    VaadinSession session = VaadinSession.getCurrent();

    @Inject
    SessionMigrationService migrationService;

    /**
     * The `LogoutView` class is responsible for handling the user logout process in the application.
     * It extends `VerticalLayout` to provide a layout structure and automatically performs logout actions
     * and navigation upon initialization.
     * <p>
     * When constructed, this view:
     * 1. Invokes the `logout` method to invalidate the current session and performs cleanup.
     * 2. Navigates the user to the application's root view after successfully logging out.
     */
    public LogoutView() {
        logout();
        getUI().ifPresent(ui ->
                ui.navigate(""));

    }

    /**
     * Invalidates the current user session and clears both VaadinSession and UI-scoped session data.
     * <p>
     * This method is used to log out the current user by:
     * 1. Clearing all UI-scoped session data through the migration service.
     * 2. Invalidating the current HTTP session, ensuring all session attributes are cleared on the server.
     * 3. Setting the current session to null to remove the session reference in the application.
     * <p>
     * It ensures that session-related data is securely erased and prevents further user interaction
     * until a new session is established.
     */
    private void logout() {
        // Clear UI-scoped session data first
        if (migrationService != null) {
            migrationService.clearAll();
        }

        // Then invalidate the underlying session
        VaadinSession.getCurrent().getSession().invalidate();
        VaadinSession.setCurrent(null);
    }
}
