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
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;

/**
 * The MainLayout class serves as the primary layout structure for the application,
 * managing the organization of headers, navigation bars, and main content.
 * <p>
 * This class extends {@code AppLayout} and implements {@code AfterNavigationListener}
 * to provide a container framework for the application's UI and handle navigation events
 * for improved user experience.
 */
public class MainLayout extends AppLayout implements AfterNavigationListener {

    @Inject
    MainView mainView;

    @Inject
    UISessionDataService sessionDataService;

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
     * - Configures and sets the main content area using the {@code mainView} field.
     */
    @PostConstruct
    public void init() {
        createHeader();
        createNavBar();
        setContent(mainView);
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
        Anchor title = new Anchor("/", getTranslation("software.name"));
        title.getStyle().set("font-size", "var(--lumo-font-size-l)")
                .set("margin", "0");
        addToNavbar(toggle, title);
    }

    /**
     * Creates and adds a navigation bar to the drawer section of the layout.
     * <p>
     * This method initializes a {@code SideNav} component using the {@code getsideNav} method,
     * wraps it with a {@code Scroller} for scrollable functionality, and applies a small padding
     * style using {@code LumoUtility.Padding.SMALL}. The resulting scroller is then added to the
     * layout drawer.
     */
    private void createNavBar() {
        SideNav nav = getsideNav();
        Scroller navScroller = new Scroller(nav);
        navScroller.setClassName(LumoUtility.Padding.SMALL);
        addToDrawer(navScroller);
    }

    /**
     * Creates and returns a {@code SideNav} component configured with navigation items
     * and potentially a label based on session attributes.
     * <p>
     * The method retrieves the current respondent from the UI-scoped session service.
     * If a respondent exists, the {@code SideNav}'s label is set to the respondent's token value.
     * <p>
     * The following navigation items are added to the {@code SideNav}:
     * - A "Login" link with a user icon and URL "/".
     * - An "About" link with an info icon and URL "/about".
     * - A "Logout" link with an unlink icon and no target URL.
     *
     * @return A {@code SideNav} component populated with navigation items and an
     * optional label derived from the respondent details in the session service.
     */
    private SideNav getsideNav() {
        SideNav sideNav = new SideNav();
        Respondent respondent = sessionDataService.getRespondent();
        if (respondent != null) {
            sideNav.setLabel(respondent.token);
        }
        sideNav.addItem(
                new SideNavItem(getTranslation("sideNav.login"), "/",
                        VaadinIcon.USER.create()),
                new SideNavItem(getTranslation("sideNav.about"), "/about", VaadinIcon.INFO.create()),
                // TODO remove respondent form the session.
                new SideNavItem(getTranslation("sideNav.logout"), "",
                        VaadinIcon.UNLINK.create())
        );
        return sideNav;
    }

    //These two functions were added to make sure the top of the page is in view after navigation
    @Override
    protected void onAttach(AttachEvent attachEvent) {
        getUI().ifPresent(ui -> ui.addAfterNavigationListener(this));
    }

    @Override
    public void afterNavigation(AfterNavigationEvent event) {
        getContent().scrollIntoView();
    }

}
