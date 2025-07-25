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
import com.elicitsoftware.UISessionDataService;
import com.elicitsoftware.flow.input.*;
import com.elicitsoftware.model.Answer;
import com.elicitsoftware.model.Respondent;
import com.elicitsoftware.model.SelectItem;
import com.elicitsoftware.response.NavResponse;
import com.elicitsoftware.service.NavigationEventService;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.binder.BinderValidationStatus;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.quarkus.annotation.NormalUIScoped;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;


/**
 * Represents a dynamic view for a specific section within the survey application.
 * <p>
 * This class is responsible for rendering questions dynamically based on the navigation response
 * and respondent data stored in the session. It supports various question types such as text fields,
 * combo boxes, checkboxes, date pickers, radio buttons, and more.
 * <p>
 * Key Features:
 * <ul>
 *   <li>Dynamic question rendering based on the {@link NavResponse} and {@link Respondent} data.</li>
 *   <li>Maintains a display map to track and synchronize UI components during updates.</li>
 *   <li>Handles user interactions and saves responses using the service layer.</li>
 *   <li>Provides navigation controls to move between sections or review responses.</li>
 * </ul>
 * <p>
 * This class integrates with the {@link MainLayout} and initializes its components after dependency injection.
 * This view is UI-scoped to prevent data leakage between browser tabs.
 */
@Route(value = "section", layout = MainLayout.class)
@NormalUIScoped
public class SectionView extends VerticalLayout implements HasDynamicTitle {
    final UI ui = UI.getCurrent();
    @Inject
    QuestionService service;

    @Inject
    UISessionDataService sessionDataService;
    
    @Inject
    NavigationEventService navigationEventService;

    // TODO make a HasMap that holds the ElicitComponents and HTML
    // Then you can replace some of these and only generate new components.
    LinkedHashMap<String, Component> displayMap = new LinkedHashMap<>();
    LinkedHashMap<String, Component> oldDisplayMap = new LinkedHashMap<>();
    LinkedHashMap<String, Binder<?>> binders = new LinkedHashMap<>();

    Button btnPrevious = null;
    Button btnNext = null;

    NavResponse navResponse = null;
    Respondent respondent;

    private String pageTitle = "";

    private boolean flash;

    public SectionView() {
    }

    @Override
    public String getPageTitle() {
        return pageTitle;
    }

    /**
     * Initializes the section view after dependency injection.
     * <p>
     * This method retrieves the navigation response and respondent data from the UI-scoped session service.
     * If session data is missing (e.g., after browser refresh), it attempts to restore from HTTP session.
     * Finally, it builds the UI components for the current section.
     */
    @PostConstruct
    public void init() {
        navResponse = sessionDataService.getNavResponse();
        respondent = sessionDataService.getRespondent();

        // If session data is missing, try to restore from HTTP session (browser refresh scenario)
        if (respondent == null || navResponse == null) {
            boolean restored = sessionDataService.restoreFromSession();
            if (restored) {
                navResponse = sessionDataService.getNavResponse();
                respondent = sessionDataService.getRespondent();
                // Log successful restoration
                if (navResponse != null && navResponse.getCurrentNavItem() != null) {
                    String currentPath = navResponse.getCurrentNavItem().getPath();
                    System.out.println("Session restored successfully to path: " + currentPath);
                }
            }
        }

        // Add null checks and handle missing data gracefully
        if (respondent == null) {
            Notification.show("Session expired. Please login again.", 3000, Notification.Position.MIDDLE);
            UI.getCurrent().navigate("");
            return;
        }

        if (navResponse == null) {
            // Try to reinitialize if we have a respondent
            try {
                navResponse = service.init(respondent.id, respondent.survey.initialDisplayKey);
                sessionDataService.setNavResponse(navResponse);
            } catch (Exception e) {
                Notification.show("Error loading survey. Please try again.", 3000, Notification.Position.MIDDLE);
                UI.getCurrent().navigate("");
                return;
            }
        }

        buildQuestions();
    }

    /**
     * Dynamically builds and updates the UI components for the current section.
     * <p>
     * This method processes the answers from the navigation response, generates the
     * appropriate UI components based on question types, and synchronizes the display map.
     * It also removes obsolete components and adds new ones to the layout.
     * <p>
     * Key Operations:
     * <ul>
     *   <li>Backs up the current display map and clears it for regeneration.</li>
     *   <li>Generates components for each answer and associates them with the display map.</li>
     *   <li>Removes components no longer relevant and adds new components to the layout.</li>
     *   <li>Updates navigation buttons for the current section.</li>
     * </ul>
     */
    private void buildQuestions() {
        System.out.println("Starting buildQuestions() method");
        
        //Save a copy of the display map
        oldDisplayMap = getDisplayComponents();

        //Make a new display map
        displayMap.clear();

        if (navResponse != null) {
            System.out.println("Processing " + navResponse.getAnswers().size() + " answers in navResponse");
            for (Answer answer : navResponse.getAnswers()) {
                if (answer.question == null && answer.sectionInstance == 0) {
                    // this is a section title.
                    pageTitle = answer.displayText;
                } else {
                    System.out.println("Processing question: " + answer.getDisplayKey() + ", type: " + answer.question.questionType.name + ", text: " + answer.displayText);
                    switch (answer.question.questionType.name) {
                        case GlobalStrings.QUESTION_TYPE_CHECKBOX:
                            ElicitCheckbox checkbox = new ElicitCheckbox(answer);
                            checkbox.component.addValueChangeListener(e -> {
                                saveAnswer(answer, e.getValue().toString());
                            });
                            displayMap.put(answer.getDisplayKey(), checkbox.component);
                            if (!binders.containsKey(answer.getDisplayKey())) {
                                binders.put(answer.getDisplayKey(), checkbox.getBinder());
                            }

                            break;
                        case GlobalStrings.QUESTION_TYPE_DATE_PICKER:
                            ElcitDatePicker datePicker = new ElcitDatePicker(answer);
                            datePicker.component.addValueChangeListener(e -> {
                                saveAnswer(answer, e.getValue().toString());
                            });
                            displayMap.put(answer.getDisplayKey(), datePicker.component);
                            if (!binders.containsKey(answer.getDisplayKey())) {
                                binders.put(answer.getDisplayKey(), datePicker.getBinder());
                            }
                            break;
                        case GlobalStrings.QUESTION_TYPE_COMBOBOX:
                            ElicitComboBox comboBox = new ElicitComboBox(answer);
                            comboBox.component.addValueChangeListener(e -> {
                                saveAnswer(answer, e.getValue().toString());
                            });
                            displayMap.put(answer.getDisplayKey(), comboBox.component);
                            if (!binders.containsKey(answer.getDisplayKey())) {
                                binders.put(answer.getDisplayKey(), comboBox.getBinder());
                            }
                            break;
                        case GlobalStrings.QUESTION_TYPE_HTML:
                            System.out.println("Processing HTML question: " + answer.getDisplayKey() + " with text: " + answer.displayText);
                            ElicitHtml htmlComponent = new ElicitHtml(answer);
                            displayMap.put(answer.getDisplayKey(), htmlComponent);
                            System.out.println("Added HTML component to displayMap with key: " + answer.getDisplayKey());
                            break;
                        case GlobalStrings.QUESTION_TYPE_INTEGER:
                            ElicitIntegerField integerField = new ElicitIntegerField(answer);
                            integerField.component.setValueChangeMode(ValueChangeMode.LAZY);
                            integerField.component.setValueChangeTimeout(300);
                            integerField.component.addValueChangeListener(e -> {
                                Integer newValue = e.getValue();
                                saveAnswer(answer, String.valueOf(newValue));
                            });
                            displayMap.put(answer.getDisplayKey(), integerField.component);
                            if (!binders.containsKey(answer.getDisplayKey())) {
                                binders.put(answer.getDisplayKey(), integerField.getBinder());
                            }
                            break;
                        case GlobalStrings.QUESTION_TYPE_MODAL:
                            //TODO
                            displayMap.put(answer.getDisplayKey(), new Paragraph(GlobalStrings.QUESTION_TYPE_MODAL));
                            break;
                        case GlobalStrings.QUESTION_TYPE_DOUBLE:
                            ElicitDoubleField numberField = new ElicitDoubleField(answer);
                            numberField.component.setValueChangeMode(ValueChangeMode.LAZY);
                            numberField.component.setValueChangeTimeout(300);
                            numberField.component.addValueChangeListener(e -> {
                                saveAnswer(answer, e.getValue().toString());
                            });
                            displayMap.put(answer.getDisplayKey(), numberField.component);
                            if (!binders.containsKey(answer.getDisplayKey())) {
                                binders.put(answer.getDisplayKey(), numberField.getBinder());
                            }
                            break;
                        case GlobalStrings.QUESTION_TYPE_RADIO:
                            ElicitRadioButtonGroup radio = new ElicitRadioButtonGroup(answer);
                            radio.component.addValueChangeListener(e -> {
                                saveAnswer(answer, e.getValue().codedValue);
                            });
                            displayMap.put(answer.getDisplayKey(), radio.component);
                            if (!binders.containsKey(answer.getDisplayKey())) {
                                binders.put(answer.getDisplayKey(), radio.getBinder());
                            }
                            break;
                        case GlobalStrings.QUESTION_TYPE_TEXT:
                            ElicitTextField text = new ElicitTextField(answer);
                            text.component.setValueChangeMode(ValueChangeMode.LAZY);
                            text.component.setValueChangeTimeout(300);
                            text.component.addValueChangeListener(e -> {
                                saveAnswer(answer, e.getValue());
                            });

                            displayMap.put(answer.getDisplayKey(), text.component);
                            if (!binders.containsKey(answer.getDisplayKey())) {
                                binders.put(answer.getDisplayKey(), text.getBinder());
                            }
                            break;
                        case GlobalStrings.QUESTION_TYPE_TEXTAREA:
                            ElicitTextArea textArea = new ElicitTextArea(answer);
                            textArea.component.setValueChangeMode(ValueChangeMode.LAZY);
                            textArea.component.setValueChangeTimeout(300);
                            textArea.component.addValueChangeListener(e -> {
                                saveAnswer(answer, e.getValue());
                            });
                            displayMap.put(answer.getDisplayKey(), textArea.component);
                            if (!binders.containsKey(answer.getDisplayKey())) {
                                binders.put(answer.getDisplayKey(), textArea.getBinder());
                            }
                            break;
                        case GlobalStrings.QUESTION_TYPE_MULTI_SELECT:
                            ElicitMultiSelectComboBox multiSelect = new ElicitMultiSelectComboBox(answer);
                            multiSelect.component.addValueChangeListener(e -> {
                                StringBuilder val = new StringBuilder();
                                for (SelectItem item : e.getValue()) {
                                    val.append(item.codedValue).append(",");
                                }
                                if (val.toString().contains(",")) {
                                    val = new StringBuilder(val.substring(0, val.length() - 1));
                                }
                                saveAnswer(answer, val.toString());
                            });
                            displayMap.put(answer.getDisplayKey(), multiSelect.component);
                            if (!binders.containsKey(answer.getDisplayKey())) {
                                binders.put(answer.getDisplayKey(), multiSelect.getBinder());
                            }
                            break;
                        case GlobalStrings.QUESTIION_TYPE_CHECKBOX_GROUP:
                            ElicitCheckboxGroup checkboxGroup = new ElicitCheckboxGroup(answer);
                            checkboxGroup.component.addValueChangeListener(e -> {
                                StringBuilder val = new StringBuilder();
                                for (SelectItem item : e.getValue()) {
                                    val.append(item.codedValue).append(",");
                                }
                                if (val.toString().contains(",")) {
                                    val = new StringBuilder(val.substring(0, val.length() - 1));
                                }
                                saveAnswer(answer, val.toString());
                            });
                            displayMap.put(answer.getDisplayKey(), checkboxGroup.component);
                            if (!binders.containsKey(answer.getDisplayKey())) {
                                binders.put(answer.getDisplayKey(), checkboxGroup.getBinder());
                            }
                            break;
                        case GlobalStrings.QUESTIION_TYPE_DATE_TIME_PICKER:
                            ElicitDateTimePicker dateTimePicker = new ElicitDateTimePicker(answer);
                            dateTimePicker.component.addValueChangeListener(e -> {
                                saveAnswer(answer, e.getValue().toString());
                            });
                            displayMap.put(answer.getDisplayKey(), dateTimePicker.component);
                            if (!binders.containsKey(answer.getDisplayKey())) {
                                binders.put(answer.getDisplayKey(), dateTimePicker.getBinder());
                            }
                            break;
                        case GlobalStrings.QUESTIION_TYPE_EMAIL:
                            ElicitEmailField email = new ElicitEmailField(answer);
                            email.component.setValueChangeMode(ValueChangeMode.LAZY);
                            email.component.setValueChangeTimeout(300);
                            email.component.addValueChangeListener(e -> {
                                saveAnswer(answer, e.getValue());
                            });
                            displayMap.put(answer.getDisplayKey(), email.component);
                            if (!binders.containsKey(answer.getDisplayKey())) {
                                binders.put(answer.getDisplayKey(), email.getBinder());
                            }
                            break;
                        case GlobalStrings.QUESTIION_TYPE_MULTI_SELECT_COMBOBOX:
                            ElicitMultiSelectComboBox multiSelectComboBox = new ElicitMultiSelectComboBox(answer);
                            multiSelectComboBox.component.addValueChangeListener(e -> {
                                saveAnswer(answer, e.getValue().toString());
                            });
                            displayMap.put(answer.getDisplayKey(), multiSelectComboBox.component);
                            if (!binders.containsKey(answer.getDisplayKey())) {
                                binders.put(answer.getDisplayKey(), multiSelectComboBox.getBinder());
                            }
                            break;
                        case GlobalStrings.QUESTIION_TYPE_PASSWORD:
                            ElicitPasswordField password = new ElicitPasswordField(answer);
                            password.component.addValueChangeListener(e -> {
                                saveAnswer(answer, e.getValue());
                            });
                            displayMap.put(answer.getDisplayKey(), password.component);
                            if (!binders.containsKey(answer.getDisplayKey())) {
                                binders.put(answer.getDisplayKey(), password.getBinder());
                            }
                            break;
                        case GlobalStrings.QUESTIION_TYPE_TIME_PICKER:
                            ElicitTimePicker timePicker = new ElicitTimePicker(answer);
                            timePicker.component.addValueChangeListener(e -> {
                                saveAnswer(answer, e.getValue().toString());
                            });
                            displayMap.put(answer.getDisplayKey(), timePicker.component);
                            if (!binders.containsKey(answer.getDisplayKey())) {
                                binders.put(answer.getDisplayKey(), timePicker.getBinder());
                            }
                            break;
                    }
                }
            }
        }
        //find the removed items
        HashMap<String, Component> removedMap = map1MinusMap2(oldDisplayMap, displayMap);
        for (Component component : removedMap.values()) {
            this.remove(component);
            if (component.getId().isPresent()) {
                binders.remove(component.getId().get());
            }
        }

        //Components to add
        HashMap<String, Component> addMap = map1MinusMap2(displayMap, oldDisplayMap);
        int index = 0;
        System.out.println("Adding " + addMap.size() + " new components to layout");
        for (Component component : displayMap.values()) {
            if (addMap.containsValue(component)) {
                System.out.println("Adding component at index " + index + " with ID: " + (component.getId().isPresent() ? component.getId().get() : "NO_ID") + " of type: " + component.getClass().getSimpleName());
                // Add the flash class for the effect only if this is not the first time adding components
                if (flash && !oldDisplayMap.isEmpty()) {
                    component.addClassName("flash");
                }
                this.addComponentAtIndex(index, component);
            }
            index++;
        }
        addButtons();
        System.out.println("Completed buildQuestions() method successfully");
    }

    /**
     * Retrieves the current display components from the layout.
     * <p>
     * This method iterates through the child components of the layout and maps them
     * by their unique IDs.
     *
     * @return a LinkedHashMap containing the current display components, keyed by their IDs
     */
    private LinkedHashMap<String, Component> getDisplayComponents() {
        LinkedHashMap<String, Component> componentsMap = new LinkedHashMap<>();
        for (Component child : this.getChildren().toList()) {
            if (child.getId().isPresent()) {
                componentsMap.put(child.getId().get(), child);
            }
        }
        return componentsMap;
    }

    /**
     * Updates the navigation buttons (Previous and Next/Review) based on the current navigation state.
     * <p>
     * This method dynamically creates and configures the navigation buttons:
     * <ul>
     *   <li>The "Previous" button navigates to the previous section if available.</li>
     *   <li>The "Next" button navigates to the next section if available, with input validation on mouseover.</li>
     *   <li>If no next section exists, the "Next" button is replaced with a "Review" button to navigate to the review section.</li>
     * </ul>
     */
    private void addButtons() {
        // Add null checks for navResponse and getCurrentNavItem
        if (navResponse == null || navResponse.getCurrentNavItem() == null) {
            // If navigation data is not available, don't add buttons
            return;
        }

        Button btnNewPrevious = new Button(getTranslation("sectionView.btnPrevious"));
        btnNewPrevious.setDisableOnClick(true);
        btnNewPrevious.setEnabled(navResponse.getCurrentNavItem() != null && navResponse.getCurrentNavItem().getPrevious() != null);
        btnNewPrevious.addClickListener(e -> {
            flash = false;
            previousSection();
        });

        if (btnPrevious != null) {
            this.replace(btnPrevious, btnNewPrevious);
        } else {
            this.add(btnNewPrevious);
        }
        btnPrevious = btnNewPrevious;

        Button btnNewNext = new Button();
        if (navResponse.getCurrentNavItem() != null && navResponse.getCurrentNavItem().getNext() != null) {
            btnNewNext.setText(getTranslation("sectionView.btnNext"));
            btnNewNext.setDisableOnClick(true);
            btnNewNext.setEnabled(navResponse.getCurrentNavItem().getNext() != null);
            btnNewNext.getElement().addEventListener("mouseover", event -> {
                // Handle the mouseover event
            });
            btnNewNext.addClickListener(e -> {
                        flash = false;
                        if (validateSection()) {
                            nextSection();
                        } else {
                            btnNewNext.setEnabled(true);
                        }
                    }
            );
        } else if (navResponse.getCurrentNavItem() != null && navResponse.getCurrentNavItem().getNext() == null) {
            btnNewNext.setText(getTranslation("sectionView.btnReview"));
            btnNewNext.setDisableOnClick(true);
            btnNewNext.addClickListener(e -> {
                flash = false;
                if (validateSection()) {
                    review();
                } else {
                    btnNewNext.setEnabled(true);
                }
            });
        }
        if (btnNext != null) {
            this.replace(btnNext, btnNewNext);
        } else {
            this.add(btnNewNext);
        }
        this.btnNext = btnNewNext;
    }

    /**
     * Validates all binders in the current section to ensure they meet the required criteria.
     *
     * <p>This method iterates through all binders and triggers their validation process.
     * If any binder fails validation, the section is considered invalid. The method
     * returns {@code true} if all binders are valid, otherwise {@code false}.</p>
     *
     * @return {@code true} if all binders are valid, {@code false} otherwise.
     */
    private boolean validateSection() {
        boolean valid = true;
        Component firstInvalidComponent = null;

        // Iterate through displayMap in order to find the topmost invalid component
        for (Map.Entry<String, Component> displayEntry : displayMap.entrySet()) {
            String componentKey = displayEntry.getKey();
            Component component = displayEntry.getValue();
            
            // Check if this component has a corresponding binder
            if (binders.containsKey(componentKey)) {
                Binder<?> binder = binders.get(componentKey);
                // Trigger validation and retrieve status
                BinderValidationStatus<?> status = binder.validate();
                // Check if the validation failed
                if (!status.isOk()) {
                    valid = false;
                    // Capture the first (topmost) invalid component
                    if (firstInvalidComponent == null) {
                        firstInvalidComponent = component;
                    }
                }
            }
        }
        
        if (!valid) {
            // Make the component reference final for use in lambda
            final Component componentToScrollTo = firstInvalidComponent;
            if (componentToScrollTo != null) {
                // Schedule the scroll operation to happen after the current server round-trip
                UI.getCurrent().getPage().executeJs(
                    "setTimeout(() => { " +
                    "const element = document.getElementById($0); " +
                    "if (element) { " +
                    "console.log('Scrolling to validation error element:', element.id); " +
                    "element.scrollIntoView({behavior: 'smooth', block: 'start', inline: 'nearest'}); " +
                    "} else { " +
                    "console.log('Element not found for scrolling:', $0); " +
                    "} " +
                    "}, 200);", 
                    componentToScrollTo.getId().orElse("unknown")
                );
            }
            Notification.show("Please fix validation errors", 3000, Notification.Position.MIDDLE);
        }
        return valid;
    }

    /**
     * Saves the provided answer and updates the UI components.
     * <p>
     * This method attempts to save the given answer using the service layer. If the save
     * operation is successful, it rebuilds the UI components based on the updated navigation response.
     * It also fires a navigation update event to refresh the section navigation tree grid.
     * <p>
     * In case of an exception, the error is logged for troubleshooting.
     *
     * @param answer the answer object representing the user's response to a question
     */
    private void saveAnswer(Answer answer, String value) {
        flash = true;
        //check if this is the first time and textValue is null
        // Or if it has changed.
        if ((value != null && answer.getTextValue() == null) ||
                (value != null && !answer.getTextValue().equals(value))) {
            answer.setTextValue(value);
            try {
                NavResponse newNavResponse = service.saveAnswer(answer);
                if (newNavResponse != null) {
                    navResponse = newNavResponse;
                    sessionDataService.setNavResponse(navResponse);
                    buildQuestions();
                    
                    // Fire navigation update event to refresh section tree grid
                    if (respondent != null) {
                        navigationEventService.fireNavigationUpdateEvent(respondent.id);
                    }
                }
            } catch (Exception e) {
                Notification.show("Error saving answer. Please try again.", 3000, Notification.Position.MIDDLE);
            }
        }
    }

    /**
     * Navigates to the next section of the survey and updates the UI.
     * <p>
     * This method initializes the navigation response for the next section using the respondent's ID
     * and the "next" property of the current navigation item. It updates the session service with the new
     * navigation response and navigates directly to the section view.
     */
    private void nextSection() {
        // Add null checks before accessing navigation data
        if (navResponse == null || navResponse.getCurrentNavItem() == null) {
            Notification.show("Navigation data not available. Please refresh the page.", 3000, Notification.Position.MIDDLE);
            return;
        }

        if (respondent == null) {
            Notification.show("Session expired. Please login again.", 3000, Notification.Position.MIDDLE);
            UI.getCurrent().navigate("");
            return;
        }

        String nextKey = navResponse.getCurrentNavItem().getNext();
        if (nextKey == null) {
            Notification.show("No next section available.", 3000, Notification.Position.MIDDLE);
            return;
        }

        try {
            System.out.println("Attempting to navigate to next section with key: " + nextKey + " for respondent: " + respondent.id);
            NavResponse newNavResponse = service.init(respondent.id, nextKey);
            if (newNavResponse != null) {
                System.out.println("Successfully loaded next section data");
                navResponse = newNavResponse;
                sessionDataService.setNavResponse(newNavResponse);
                // Rebuild the questions in place instead of navigating
                buildQuestions();
            } else {
                System.out.println("Navigation service returned null response for key: " + nextKey);
                Notification.show("Error loading next section data.", 3000, Notification.Position.MIDDLE);
                if (btnNext != null) {
                    btnNext.setEnabled(true);
                }
            }
        } catch (Exception e) {
            System.out.println("Exception during navigation to next section: " + e.getMessage());
            e.printStackTrace();
            Notification.show("Error navigating to next section. Please try again.", 3000, Notification.Position.MIDDLE);
            // Re-enable the button if navigation fails
            if (btnNext != null) {
                btnNext.setEnabled(true);
            }
        }
    }

    /**
     * Navigates to the previous section of the survey and refreshes the UI.
     * <p>
     * This method initializes the navigation response for the previous section using the respondent's ID
     * and the "previous" property of the current navigation item. It updates the session service with the new
     * navigation response and navigates directly to the section view.
     */
    private void previousSection() {
        // Add null checks before accessing navigation data
        if (navResponse == null || navResponse.getCurrentNavItem() == null) {
            Notification.show("Navigation data not available. Please refresh the page.", 3000, Notification.Position.MIDDLE);
            return;
        }

        if (respondent == null) {
            Notification.show("Session expired. Please login again.", 3000, Notification.Position.MIDDLE);
            UI.getCurrent().navigate("");
            return;
        }

        String previousKey = navResponse.getCurrentNavItem().getPrevious();
        if (previousKey == null) {
            Notification.show("No previous section available.", 3000, Notification.Position.MIDDLE);
            return;
        }

        try {
            System.out.println("Attempting to navigate to previous section with key: " + previousKey + " for respondent: " + respondent.id);
            NavResponse newNavResponse = service.init(respondent.id, previousKey);
            if (newNavResponse != null) {
                System.out.println("Successfully loaded previous section data");
                navResponse = newNavResponse;
                sessionDataService.setNavResponse(newNavResponse);
                // Rebuild the questions in place instead of navigating
                buildQuestions();
            } else {
                System.out.println("Navigation service returned null response for key: " + previousKey);
                Notification.show("Error loading previous section data.", 3000, Notification.Position.MIDDLE);
                if (btnPrevious != null) {
                    btnPrevious.setEnabled(true);
                }
            }
        } catch (Exception e) {
            System.out.println("Exception during navigation to previous section: " + e.getMessage());
            e.printStackTrace();
            Notification.show("Error navigating to previous section. Please try again.", 3000, Notification.Position.MIDDLE);
            // Re-enable the button if navigation fails
            if (btnPrevious != null) {
                btnPrevious.setEnabled(true);
            }
        }
    }

    /**
     * Navigates to the review section of the survey.
     * <p>
     * This method directs the user to the review section by updating the UI navigation state.
     */
    private void review() {
        ui.navigate("review");
    }

    /**
     * Computes the difference between two LinkedHashMaps.
     * <p>
     * This method returns a new map containing all key-value pairs from the first map
     * that are not present in the second map.
     *
     * @param map1 the first LinkedHashMap to subtract from
     * @param map2 the second LinkedHashMap to subtract
     * @return a LinkedHashMap containing the difference between the two maps
     */
    private LinkedHashMap<String, Component> map1MinusMap2(LinkedHashMap<String, Component> map1, LinkedHashMap<String, Component> map2) {
        // Check keys in map1 that are not in map2
        LinkedHashMap<String, Component> result = new LinkedHashMap<>();
        for (String key : map1.keySet()) {
            if (!map2.containsKey(key)) {
                result.put(key, map1.get(key));
            }
        }
        return result;
    }
}
