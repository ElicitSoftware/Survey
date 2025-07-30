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
import com.elicitsoftware.model.Respondent;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.Scroller;
import com.vaadin.flow.component.sidenav.SideNav;
import com.vaadin.flow.component.sidenav.SideNavItem;
import com.vaadin.flow.router.AfterNavigationEvent;
import com.vaadin.flow.router.AfterNavigationListener;
import com.vaadin.flow.theme.lumo.LumoUtility;
import com.vaadin.quarkus.annotation.NormalUIScoped;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;

/**
 * The MainLayout class serves as the primary layout structure for the application,
 * managing the organization of headers, navigation bars, and main content.
 * <p>
 * This class extends {@code AppLayout} and implements {@code AfterNavigationListener}
 * to provide a container framework for the application's UI and handle navigation events
 * for improved user experience.
 * <p>
 * This layout is UI-scoped to prevent data leakage between browser tabs.
 */
@NormalUIScoped
public class MainLayout extends AppLayout implements AfterNavigationListener {

    /** The UI-scoped session data service for managing respondent session information. */
    @Inject
    UISessionDataService sessionDataService;
    
    /** The current side navigation component displayed in the drawer. */
    private SideNav currentSideNav;
    
    /** The scroller component that wraps the side navigation for scrollable functionality. */
    private Scroller navScroller;

    /**
     * Default constructor for MainLayout.
     * The actual initialization is performed in the init() method
     * which is called after dependency injection is complete.
     */
    public MainLayout() {
    }

    /**
     * Initializes the main layout components after the construction of the class.
     * <p>
     * This method is annotated with {@code @PostConstruct}, ensuring it is called
     * automatically after the dependency injection is completed. It performs the following actions:
     * - Creates and configures the header section of the layout by calling {@code createHeader()}.
     * - Creates and adds a navigation bar to the drawer section of the layout using {@code createNavBar()}.
     */
    @PostConstruct
    public void init() {
        createHeader();
        createNavBar();
    }

    /**
     * Creates and adds a header section in the layout.
     * <p>
     * This method initializes a drawer toggle and a title anchor that links
     * to the application's root path ("/") and displays the software name.
     * The title is styled with a larger font size and zero-margin spacing.
     * Both the toggle and title are added to the navigation bar.
     */
    private void createHeader() {
        DrawerToggle toggle = new DrawerToggle();
        Anchor title = new Anchor("/", "Elicit");
        title.getStyle().set("font-size", "var(--lumo-font-size-l)")
                .set("margin", "0");
        addToNavbar(toggle, title);
    }

    /**
     * Creates and adds a navigation bar to the drawer section of the layout.
     * <p>
     * This method initializes a {@code SideNav} component using the {@code getSideNav} method,
     * wraps it with a {@code Scroller} for scrollable functionality, and applies a small padding
     * style using {@code LumoUtility.Padding.SMALL}. The resulting scroller is then added to the
     * layout drawer.
     */
    private void createNavBar() {
        currentSideNav = getSideNav();
        navScroller = new Scroller(currentSideNav);
        navScroller.setClassName(LumoUtility.Padding.SMALL);
        addToDrawer(navScroller);
    }
    
    /**
     * Refreshes the navigation bar based on the current session state.
     * This method is called when the session state changes (e.g., after login/logout).
     */
    private void refreshNavBar() {
        if (navScroller != null) {
            remove(navScroller);
        }
        createNavBar();
    }

    /**
     * Creates and returns a {@code SideNav} component configured with navigation items
     * based on the current login state.
     * <p>
     * The method retrieves the current respondent from the UI-scoped session service.
     * If a respondent exists, it shows "About" and "Logout" options. If no respondent exists, 
     * it shows "About" and "Login" options.
     * <p>
     * The logout functionality clears the UI session data and redirects to the login page.
     *
     * @return A {@code SideNav} component populated with navigation items based on login state.
     */
    private SideNav getSideNav() {
        SideNav sideNav = new SideNav();
        Respondent respondent = sessionDataService.getRespondent();
        
        // Always add About item
        sideNav.addItem(new SideNavItem("About", "/about", VaadinIcon.INFO.create()));
        
        if (respondent != null) {
            // User is logged in - show logout option
            sideNav.addItem(new SideNavItem("Logout", "/logout", VaadinIcon.UNLINK.create()));
        } else {
            // User is not logged in - show login option
            sideNav.addItem(new SideNavItem("Login", "/", VaadinIcon.USER.create()));
        }
        
        return sideNav;
    }

    /**
     * Attaches an after-navigation listener to the UI when this layout is attached.
     * <p>
     * This method ensures that the top of the page is in view after navigation
     * and that the navigation bar is refreshed to reflect the current session state.
     *
     * @param attachEvent the event fired when this component is attached to the UI
     */
    @Override
    protected void onAttach(AttachEvent attachEvent) {
        getUI().ifPresent(ui -> ui.addAfterNavigationListener(this));
    }

    /**
     * Handles post-navigation actions to ensure proper page display and navigation state.
     * <p>
     * This method is called after every navigation event and performs two key actions:
     * <ul>
     * <li>Scrolls the page content to the top for better user experience</li>
     * <li>Refreshes the navigation bar to reflect the current session state (login/logout)</li>
     * </ul>
     *
     * @param event the navigation event containing information about the completed navigation
     */
    @Override
    public void afterNavigation(AfterNavigationEvent event) {
        getContent().scrollIntoView();
        // Refresh the navigation bar to reflect current session state
        refreshNavBar();
    }

}
