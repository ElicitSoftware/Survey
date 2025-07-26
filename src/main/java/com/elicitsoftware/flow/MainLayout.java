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
import com.elicitsoftware.flow.component.SectionNavigationTreeGrid;
import com.elicitsoftware.model.Respondent;
import com.elicitsoftware.service.NavigationEventService;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.Scroller;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.sidenav.SideNav;
import com.vaadin.flow.component.sidenav.SideNavItem;
import com.vaadin.flow.router.AfterNavigationEvent;
import com.vaadin.flow.router.AfterNavigationListener;
import com.vaadin.flow.router.RouterLayout;
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
@CssImport("./css/section-navigation.css")
@CssImport("./themes/starter-theme/section-navigation-tree-grid.css")
@NormalUIScoped
public class MainLayout extends VerticalLayout implements AfterNavigationListener, RouterLayout {

    /** The UI-scoped session data service for managing respondent session information. */
    @Inject
    UISessionDataService sessionDataService;
    
    /** The section navigation tree grid for displaying section hierarchy. */
    @Inject
    SectionNavigationTreeGrid sectionNavigationTreeGrid;
    
    /** The navigation event service for coordinating navigation updates. */
    @Inject
    NavigationEventService navigationEventService;
    
    /** The current side navigation component displayed in the drawer. */
    private SideNav currentSideNav;
    
    /** The scroller component that wraps the side navigation for scrollable functionality. */
    private Scroller navScroller;
    
    /** The container for the main navigation and section tree grid. */
    private VerticalLayout drawerContent;
    
    /** The header layout containing the navigation toggle and title. */
    private VerticalLayout headerLayout;
    
    /** The sidebar layout containing navigation components. */
    private VerticalLayout sidebarLayout;
    
    /** The main content area where pages are displayed. */
    private Div contentArea;

    /**
     * Default constructor for MainLayout.
     * The actual initialization is performed in the init() method
     * which is called after dependency injection is complete.
     */
    public MainLayout() {
        setSizeFull();
        setPadding(false);
        setSpacing(false);
        getStyle().set("background", "white");
        getStyle().set("background-color", "white");
    }

    /**
     * Initializes the main layout components after the construction of the class.
     * <p>
     * This method is annotated with {@code @PostConstruct}, ensuring it is called
     * automatically after the dependency injection is completed. It performs the following actions:
     * - Creates and configures the header section of the layout by calling {@code createHeader()}.
     * - Creates and adds a navigation bar to the sidebar section of the layout using {@code createNavBar()}.
     * - Sets up event listeners for navigation updates.
     * - Configures the split layout with adjustable sidebar width.
     */
    @PostConstruct
    public void init() {
        createSplitLayout();
        createHeader();
        createNavBar();
        setupNavigationEventListeners();
    }

    /**
     * Creates the split layout structure with header, sidebar, and content area.
     * <p>
     * This method sets up the main layout structure using a vertical layout containing
     * a header and a horizontal layout with an adjustable sidebar.
     */
    private void createSplitLayout() {
        // Create main container
        VerticalLayout mainContainer = new VerticalLayout();
        mainContainer.setSizeFull();
        mainContainer.setPadding(false);
        mainContainer.setSpacing(false);
        mainContainer.getStyle().set("background", "white");
        
        // Create header layout
        headerLayout = new VerticalLayout();
        headerLayout.setPadding(false);
        headerLayout.setSpacing(false);
        headerLayout.setWidthFull();
        headerLayout.addClassName("header-layout");
        headerLayout.getStyle().set("min-height", "var(--lumo-size-l)");
        
        // Create a horizontal layout for sidebar and content
        HorizontalLayout horizontalContainer = new HorizontalLayout();
        horizontalContainer.setSizeFull();
        horizontalContainer.setPadding(false);
        horizontalContainer.setSpacing(false);
        horizontalContainer.addClassName("horizontal-container");
        horizontalContainer.getStyle().set("background", "white");
        
        // Create sidebar layout
        sidebarLayout = new VerticalLayout();
        sidebarLayout.setPadding(false);
        sidebarLayout.setSpacing(true);
        sidebarLayout.setSizeFull(); // Make sidebar fill available space
        sidebarLayout.addClassName("sidebar-layout");
        sidebarLayout.setWidth("300px"); // Fixed width for now, we'll make it resizable with JavaScript
        sidebarLayout.getStyle().set("min-width", "200px");
        sidebarLayout.getStyle().set("max-width", "50%");
        sidebarLayout.getStyle().set("resize", "horizontal");
        sidebarLayout.getStyle().set("overflow", "auto");
        
        // Create content area
        contentArea = new Div();
        contentArea.setSizeFull();
        contentArea.addClassName("content-area");
        contentArea.getStyle().set("background", "white");
        contentArea.getStyle().set("background-color", "white");
        
        // Add components to horizontal layout
        horizontalContainer.add(sidebarLayout);
        horizontalContainer.addAndExpand(contentArea);
        
        // Add header and horizontal layout to main container
        mainContainer.add(headerLayout);
        mainContainer.addAndExpand(horizontalContainer);
        
        // Add main container to this layout
        add(mainContainer);
        expand(mainContainer);
        
        // Add JavaScript to make sidebar resizable
        addResizableFeature();
    }

    /**
     * Adds JavaScript to make the sidebar resizable with constraints.
     */
    private void addResizableFeature() {
        getElement().executeJs(
            "var style = document.createElement('style');" +
            "style.id = 'custom-resize-styles';" +
            "style.textContent = `" +
            "  vaadin-split-layout > div[part='splitter'] { display: none !important; }" +
            "  .sidebar-layout { " +
            "    position: relative !important; " +
            "  }" +
            "  .custom-resize-handle { " +
            "    position: absolute !important; " +
            "    top: 0 !important; " +
            "    right: -3px !important; " +
            "    width: 6px !important; " +
            "    height: 100% !important; " +
            "    background: transparent !important; " +
            "    cursor: col-resize !important; " +
            "    z-index: 1000 !important; " +
            "    pointer-events: auto !important; " +
            "  }" +
            "  .custom-resize-handle:hover { " +
            "    background: rgba(0, 120, 212, 0.2) !important; " +
            "  }" +
            "`;" +
            "if (document.getElementById('custom-resize-styles')) {" +
            "  document.getElementById('custom-resize-styles').remove();" +
            "}" +
            "document.head.appendChild(style);" +
            
            "function createResizeHandle() {" +
            "  var sidebar = document.querySelector('.sidebar-layout');" +
            "  if (!sidebar) return false;" +
            
            "  var existing = sidebar.querySelectorAll('.custom-resize-handle');" +
            "  existing.forEach(function(h) { h.remove(); });" +
            
            "  var handle = document.createElement('div');" +
            "  handle.className = 'custom-resize-handle';" +
            "  handle.title = 'Drag to resize';" +
            
            "  var isResizing = false;" +
            "  var startX = 0;" +
            "  var startWidth = 0;" +
            
            "  handle.addEventListener('mousedown', function(e) {" +
            "    e.preventDefault();" +
            "    isResizing = true;" +
            "    startX = e.clientX;" +
            "    startWidth = sidebar.offsetWidth;" +
            "    document.body.style.cursor = 'col-resize';" +
            "    document.body.style.userSelect = 'none';" +
            "  });" +
            
            "  document.addEventListener('mousemove', function(e) {" +
            "    if (!isResizing) return;" +
            "    var newWidth = startWidth + (e.clientX - startX);" +
            "    if (newWidth >= 200 && newWidth <= window.innerWidth * 0.6) {" +
            "      sidebar.style.width = newWidth + 'px';" +
            "    }" +
            "  });" +
            
            "  document.addEventListener('mouseup', function() {" +
            "    if (isResizing) {" +
            "      isResizing = false;" +
            "      document.body.style.cursor = 'default';" +
            "      document.body.style.userSelect = 'auto';" +
            "    }" +
            "  });" +
            
            "  sidebar.appendChild(handle);" +
            "  return true;" +
            "}" +
            
            "var attempts = 0;" +
            "function tryCreate() {" +
            "  attempts++;" +
            "  if (createResizeHandle()) {" +
            "    return;" +
            "  } else if (attempts < 5) {" +
            "    setTimeout(tryCreate, 100);" +
            "  }" +
            "}" +
            "tryCreate();"
        );
    }

    /**
     * Creates and adds a header section in the layout.
     * <p>
     * This method initializes a drawer toggle and a title anchor that links
     * to the application's root path ("/") and displays the software name.
     * The title is styled with a larger font size and zero-margin spacing.
     * Both the toggle and title are arranged horizontally in the header layout.
     */
    private void createHeader() {
        DrawerToggle toggle = new DrawerToggle();
        toggle.addClickListener(e -> toggleSidebar());
        
        Anchor title = new Anchor("/", getTranslation("software.name"));
        title.getStyle().set("font-size", "var(--lumo-font-size-l)")
                .set("margin", "0")
                .set("text-decoration", "none")
                .set("color", "var(--lumo-header-text-color)")
                .set("white-space", "nowrap")
                .set("flex-shrink", "0");
        
        // Create horizontal layout for header content (toggle and title side by side)
        HorizontalLayout headerContent = new HorizontalLayout();
        headerContent.addClassName("header-content");
        headerContent.setWidthFull();
        headerContent.setPadding(true);
        headerContent.setSpacing(true);
        headerContent.setAlignItems(FlexComponent.Alignment.CENTER);
        headerContent.getStyle().set("flex-wrap", "nowrap");
        
        // Add toggle and title to header horizontally
        headerContent.add(toggle, title);
        headerLayout.add(headerContent);
    }

    /**
     * Toggles the visibility of the sidebar.
     */
    private void toggleSidebar() {
        if (sidebarLayout.isVisible()) {
            sidebarLayout.setVisible(false);
        } else {
            sidebarLayout.setVisible(true);
        }
    }

    /**
     * Creates and adds a navigation bar to the sidebar section of the layout.
     * <p>
     * This method initializes a {@code SideNav} component using the {@code getSideNav} method,
     * wraps it with a {@code Scroller} for scrollable functionality, and applies a small padding
     * style using {@code LumoUtility.Padding.SMALL}. The resulting scroller is then added to the
     * sidebar layout along with the section navigation tree grid.
     */
    private void createNavBar() {
        currentSideNav = getSideNav();
        navScroller = new Scroller(currentSideNav);
        navScroller.setClassName(LumoUtility.Padding.SMALL);
        
        // Create container for drawer content
        drawerContent = new VerticalLayout();
        drawerContent.setPadding(true);
        drawerContent.setSpacing(true);
        drawerContent.addClassName("drawer-content");
        drawerContent.setDefaultHorizontalComponentAlignment(FlexComponent.Alignment.STRETCH);
        drawerContent.setSizeFull(); // Make drawer content fill the sidebar
        
        // Add main navigation (this should not expand)
        drawerContent.add(navScroller);
        
        // Add section navigation tree grid for logged-in users
        if (shouldShowSectionNavigation()) {
            // Make the tree grid expand to fill remaining space
            drawerContent.addAndExpand(sectionNavigationTreeGrid);
        }
        
        // Add drawer content to sidebar
        sidebarLayout.add(drawerContent);
    }
    
    /**
     * Refreshes the navigation bar based on the current session state.
     * This method is called when the session state changes (e.g., after login/logout).
     */
    private void refreshNavBar() {
        if (drawerContent != null) {
            sidebarLayout.remove(drawerContent);
        }
        createNavBar();
    }
    
    /**
     * Sets up event listeners for navigation updates.
     * This method registers a listener to refresh the section navigation tree grid
     * when navigation data is updated.
     */
    private void setupNavigationEventListeners() {
        navigationEventService.addNavigationUpdateListener(event -> {
            if (sectionNavigationTreeGrid != null) {
                sectionNavigationTreeGrid.refreshData();
                // Also update the current section highlighting after refreshing data
                sectionNavigationTreeGrid.updateCurrentSectionFromSession();
            }
        });
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
        sideNav.addItem(new SideNavItem(getTranslation("sideNav.about"), "/about", VaadinIcon.INFO.create()));
        
        if (respondent != null) {
            // User is logged in - show logout option
            sideNav.addItem(new SideNavItem(getTranslation("sideNav.logout"), "/logout", VaadinIcon.UNLINK.create()));
        } else {
            // User is not logged in - show login option
            sideNav.addItem(new SideNavItem(getTranslation("sideNav.login"), "/", VaadinIcon.USER.create()));
        }
        
        return sideNav;
    }
    
    /**
     * Checks if the section navigation tree grid should be shown.
     * The tree grid is only shown when a user is logged in.
     *
     * @return true if the tree grid should be shown, false otherwise
     */
    private boolean shouldShowSectionNavigation() {
        Respondent respondent = sessionDataService.getRespondent();
        return respondent != null;
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
        
        // Add resize functionality when component is attached
        addResizableFeatureOnAttach();
    }

    /**
     * Adds resize functionality when the component is attached to ensure DOM is ready.
     */
    private void addResizableFeatureOnAttach() {
        getElement().executeJs(
            "var sidebar = document.querySelector('.sidebar-layout');" +
            "if (sidebar) {" +
            "  sidebar.style.position = 'relative';" +
            
            "  var handle = document.createElement('div');" +
            "  handle.style.position = 'absolute';" +
            "  handle.style.top = '0';" +
            "  handle.style.right = '-3px';" +
            "  handle.style.width = '6px';" +
            "  handle.style.height = '100%';" +
            "  handle.style.cursor = 'col-resize';" +
            "  handle.style.zIndex = '1000';" +
            "  handle.style.backgroundColor = 'transparent';" +
            "  handle.title = 'Drag to resize sidebar';" +
            
            "  var isResizing = false;" +
            "  var startX = 0;" +
            "  var startWidth = 0;" +
            
            "  handle.addEventListener('mousedown', function(e) {" +
            "    isResizing = true;" +
            "    startX = e.clientX;" +
            "    startWidth = sidebar.offsetWidth;" +
            "    document.body.style.cursor = 'col-resize';" +
            "    document.body.style.userSelect = 'none';" +
            "    e.preventDefault();" +
            "  });" +
            
            "  document.addEventListener('mousemove', function(e) {" +
            "    if (!isResizing) return;" +
            "    var newWidth = startWidth + (e.clientX - startX);" +
            "    if (newWidth >= 200 && newWidth <= window.innerWidth * 0.6) {" +
            "      sidebar.style.width = newWidth + 'px';" +
            "    }" +
            "  });" +
            
            "  document.addEventListener('mouseup', function() {" +
            "    if (isResizing) {" +
            "      isResizing = false;" +
            "      document.body.style.cursor = 'default';" +
            "      document.body.style.userSelect = 'auto';" +
            "    }" +
            "  });" +
            
            "  sidebar.appendChild(handle);" +
            "}"
        );
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
        if (contentArea != null) {
            contentArea.scrollIntoView();
        }
        // Refresh the navigation bar to reflect current session state
        refreshNavBar();
        
        // Update current section selection in the tree grid
        updateCurrentSectionInTreeGrid(event);
    }
    
    /**
     * Updates the current section selection in the tree grid based on navigation.
     *
     * @param event the navigation event
     */
    private void updateCurrentSectionInTreeGrid(AfterNavigationEvent event) {
        if (sectionNavigationTreeGrid == null) {
            return;
        }
        
        // Extract the current section information from the URL or route parameters
        String location = event.getLocation().getPath();
        
        // Check if this is a section view - use session data instead of URL parsing
        if (location.startsWith("section") || location.equals("")) {
            // For section views, get current section from session data
            sectionNavigationTreeGrid.updateCurrentSectionFromSession();
        } else if (location.equals("review")) {
            // Highlight the Review item in the tree
            sectionNavigationTreeGrid.updateCurrentSectionForSpecialPage("review");
        } else if (location.equals("report")) {
            // Highlight the Report item in the tree
            sectionNavigationTreeGrid.updateCurrentSectionForSpecialPage("report");
        } else {
            // Clear selection if not on a section, review, or report page
            sectionNavigationTreeGrid.updateCurrentSection(null);
        }
    }

    @Override
    public void showRouterLayoutContent(com.vaadin.flow.component.HasElement content) {
        if (contentArea != null) {
            contentArea.removeAll();
            if (content != null) {
                contentArea.getElement().appendChild(content.getElement());
            }
        }
    }

}
