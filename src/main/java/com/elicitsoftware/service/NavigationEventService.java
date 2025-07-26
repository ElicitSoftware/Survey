package com.elicitsoftware.service;

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

import com.elicitsoftware.event.SectionNavigationUpdateEvent;
import com.vaadin.quarkus.annotation.NormalUIScoped;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Service for managing section navigation update events.
 * <p>
 * This service allows components to register listeners for navigation updates
 * and provides a mechanism to fire events when navigation data changes.
 */
@NormalUIScoped
public class NavigationEventService {
    
    private final List<Consumer<SectionNavigationUpdateEvent>> listeners = new ArrayList<>();
    
    /**
     * Registers a listener for section navigation update events.
     *
     * @param listener the listener to register
     */
    public void addNavigationUpdateListener(Consumer<SectionNavigationUpdateEvent> listener) {
        listeners.add(listener);
    }
    
    /**
     * Removes a listener for section navigation update events.
     *
     * @param listener the listener to remove
     */
    public void removeNavigationUpdateListener(Consumer<SectionNavigationUpdateEvent> listener) {
        listeners.remove(listener);
    }
    
    /**
     * Fires a section navigation update event to all registered listeners.
     *
     * @param respondentId the ID of the respondent whose navigation data was updated
     */
    public void fireNavigationUpdateEvent(Integer respondentId) {
        SectionNavigationUpdateEvent event = new SectionNavigationUpdateEvent(respondentId);
        listeners.forEach(listener -> listener.accept(event));
    }
    
    /**
     * Clears all registered listeners.
     */
    public void clearListeners() {
        listeners.clear();
    }
}
