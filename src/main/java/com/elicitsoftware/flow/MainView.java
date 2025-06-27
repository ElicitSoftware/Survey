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

import com.elicitsoftware.QuestionService;
import com.elicitsoftware.TokenService;
import com.elicitsoftware.UISessionDataService;
import com.elicitsoftware.model.Respondent;
import com.elicitsoftware.model.Survey;
import com.elicitsoftware.response.NavResponse;
import com.vaadin.flow.component.Direction;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;
import io.quarkus.logging.Log;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;

import java.util.List;

/**
 * The `MainView` class represents the main view of the application, providing the user interface
 * for login functionality and handling survey selection. It is the default landing page of the
 * application and dynamically updates its title based on the current locale.
 * <p>
 * This class extends `VerticalLayout` and implements `HasDynamicTitle` to define the main layout
 * and its dynamic page title. It utilizes dependency injection for `TokenService` and
 * `QuestionService` to interact with business logic related to user authentication and survey
 * initialization.
 * <p>
 * The view dynamically adjusts its layout based on the user’s locale, supporting both left-to-right
 * (LTR) and right-to-left (RTL) layouts. It incorporates Vaadin components such as
 * `TextField`, `Button`, and `ComboBox` for user interaction.
 * <p>
 * Key Features:
 * - Language-sensitive layout direction (LTR/RTL).
 * - Login functionality using a token supplied by the user.
 * - Survey selection with support for multiple or single available surveys.
 * - Navigation to different views (`section` or `report`) depending on the respondent's state.
 * - Accessibility and enhanced user experience with theme variants, tooltips, and keyboard shortcuts.
 */
@Route(value = "", layout = MainLayout.class)
@Dependent
public class MainView extends VerticalLayout implements HasDynamicTitle {

    @Inject
    TokenService tokenService;

    @Inject
    QuestionService questionService;

    @Inject
    UISessionDataService sessionDataService;

    private final User user = new User();
    private final Binder<User> binder = new Binder<>(User.class);

    /**
     * Default constructor for MainView.
     * The actual initialization is performed in the init() method
     * which is called after dependency injection is complete.
     */
    public MainView() {

    }

    /**
     * Initializes the main view of the application. This method sets up the user interface components and
     * behavior necessary for the login process and survey selection. It is automatically invoked after
     * dependency injections are completed, marked with {@code @PostConstruct}.
     * <p>
     * Key features of the initialization include:
     * <p>
     * 1. Localization and UI Direction Setup:
     * Configures the user interface direction based on the current locale's language.
     * For Arabic, the interface is set to right-to-left; otherwise, it defaults to left-to-right.
     * <p>
     * 2. Token Input Field:
     * Creates a text field for entering the survey token. This field is required and includes:
     * - A tooltip for guidance.
     * - A bordered style theme.
     * - Autofocus enabled for user convenience.
     * <p>
     * 3. Login Button Configuration:
     * Defines the behavior for the login button, which attempts to authenticate the respondent
     * using the provided token. On successful authentication:
     * - Respondent and navigation data are set in the session.
     * - Navigation to the appropriate view is triggered, either the survey section or the report view,
     * based on the respondent's active status.
     * The button is styled as a primary action and has an Enter key shortcut for quick access.
     * <p>
     * 4. Survey Selection:
     * Manages surveys available for the user:
     * - Displays a message if no surveys are available.
     * - If only one survey exists, automatically associates it with the session.
     * - For multiple surveys, provides a dropdown combo box for selection. Users can choose a survey,
     * which updates the session with the selected survey's ID.
     * <p>
     * 5. UI Styling:
     * Applies custom CSS class styling for layout adjustments in the view.
     * <p>
     * The method leverages the following application components:
     * - {@code tokenService} for retrieving available surveys.
     * - {@code questionService} for initializing the respondent's survey navigation.
     * - {@code session} for managing session attributes.
     */
    @PostConstruct
    public void init() {
        Log.info("MainView init");
        //Set up the I18n
        final UI ui = UI.getCurrent();
        if (ui.getLocale().getLanguage().equals("ar")) {
            ui.setDirection(Direction.RIGHT_TO_LEFT);
        } else {
            ui.setDirection(Direction.LEFT_TO_RIGHT);
        }

        this.setAlignItems(Alignment.CENTER);

        //Add istruction for auto register testing.
        if (tokenService.isAutoRegister()) {
            Div autoRegisterInstructions = new Div();
            autoRegisterInstructions.getElement().setProperty("innerHTML", "To test enter any value between 5 and 12 charaters. <br/>Tokens are case sensitive");
            this.add(autoRegisterInstructions);
        }

        //Create a layout for the textbox and buttons.
        VerticalLayout loginLayout = new VerticalLayout();
        loginLayout.setAlignItems(Alignment.CENTER);

        // Use TextField for standard text input
        TextField txtToken = new TextField(getTranslation("mainView.txtToken"));
        txtToken.setTooltipText(getTranslation("mainView.txtToken.tooltip"));
        txtToken.addThemeName("bordered");
        txtToken.setAutofocus(true);

        // Bind and add validation
        binder.forField(txtToken)
                .asRequired("Token cannot be empty")
                .withValidator(token -> token.length() >= 5, "Token must be at least 5 characters")
                .withValidator(token -> token.length() <= 12, "Token must be at most 12 characters")
                .bind(User::getToken, User::setToken);


        // Button click listeners can be defined as lambda expressions
        Button btnLogin = new Button(getTranslation("mainView.btnLogin"), e -> {
            if (binder.validate().isOk()) {

                Respondent respondent = login(sessionDataService.getSurveyId(), txtToken.getValue());
                if (respondent != null) {
                    sessionDataService.setRespondent(respondent);
                    NavResponse navResponse = questionService.init(respondent.id, respondent.survey.initialDisplayKey);
                    sessionDataService.setNavResponse(navResponse);
                    //if active they are still taking the survey
                    if (respondent.active) {
                        ui.navigate("section");
                    }
                    // If in active take them to the reports page.
                    else {
                        ui.navigate("report");
                    }
                } else {
                    Log.info("Respondent is null");
                }
            } else {
                Notification.show("Please correct the errors before logging in.");
            }
        });

        // Theme variants give you predefined extra styles for components.
        // Example: Primary button is more prominent look.
        btnLogin.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        // You can specify keyboard shortcuts for buttons.
        // Example: Pressing enter in this view clicks the Button.
        btnLogin.addClickShortcut(Key.ENTER);

        List<Survey> surveys = tokenService.getSurveys().get("Surveys");
        if (surveys.isEmpty()) {
            loginLayout.add(new Paragraph(getTranslation("mainView.surveys.isEmpty")));
        } else if (surveys.size() == 1) {
            sessionDataService.setSurveyId(surveys.get(0).id.intValue());
        } else {
            ComboBox<Survey> comboBox = new ComboBox<>(getTranslation("mainView.comboBox"));
            comboBox.setItems(tokenService.getSurveys().get("Surveys"));
            comboBox.setItemLabelGenerator(survey -> survey.name);
            comboBox.onEnabledStateChanged(true);
            comboBox.addValueChangeListener(e -> {
                sessionDataService.setSurveyId(e.getValue().id);
            });
            loginLayout.add(comboBox);
        }
        loginLayout.add(txtToken, btnLogin);

        this.add(loginLayout);

        //Add the auto register WARNING
        if (tokenService.isAutoRegister()) {
            Div autoRegisterWarning = new Div();
            autoRegisterWarning.getElement().setProperty("innerHTML", "<h5>Warning: the property token.autoRegister is set to true.<br/>This is for testng only and should be removed for production.</h5>");
            this.add(autoRegisterWarning);
        }
    }

    /**
     * Logs in a respondent using the provided survey ID and token.
     *
     * @param surveyId the ID of the survey the respondent is attempting to access
     * @param token    the authentication token for the respondent
     * @return the {@link Respondent} object representing the logged-in user
     */
    private Respondent login(int surveyId, String token) {
        return tokenService.login(surveyId, token);
    }

    /**
     * Retrieves the title of the page.
     * The title is obtained by translating the key "mainView.pageTitle".
     *
     * @return The translated page title as a {@code String}.
     */
    @Override
    public String getPageTitle() {
        return getTranslation("mainView.pageTitle");
    }
}
