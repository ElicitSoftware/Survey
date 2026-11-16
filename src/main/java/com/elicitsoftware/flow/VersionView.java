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

import com.elicitsoftware.diagnostics.ContainerInfo;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;
import com.vaadin.quarkus.annotation.NormalUIScoped;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;

import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * VersionView displays the container's current time and the creation date of its image. The
 * time comes from the JVM's clock and the date from {@link ContainerInfo}, which finds it once
 * per application start, so viewing the page starts no process.
 */
@Route(value = "version", layout = MainLayout.class)
@NormalUIScoped
public class VersionView extends VerticalLayout {

    @Inject
    ContainerInfo containerInfo;
    
    public VersionView() {
        super();
    }
    
    @PostConstruct
    public void init() {
        setSpacing(true);
        setPadding(true);
        
        // Add title
        H2 title = new H2(getTranslation("versionView.title"));
        add(title);
        
        // Add Docker container ID section
        Div containerSection = createContainerIdSection();
        add(containerSection);
    }
    
    /**
     * Creates a section displaying the Docker image creation date.
     * 
     * @return Div containing the image creation date information
     */
    private Div createContainerIdSection() {
        Div section = new Div();
        
        H2 sectionTitle = new H2(getTranslation("versionView.container"));
        section.add(sectionTitle);
        
        // Get and display container time
        String containerTime = getContainerTime();
        if (containerTime != null && !containerTime.isEmpty()) {
            Paragraph containerTimeLabel = new Paragraph(getTranslation("versionView.containerTime"));
            containerTimeLabel.getStyle().set("font-weight", "bold");
            
            Paragraph containerTimeValue = new Paragraph(containerTime);
            containerTimeValue.getElement().setAttribute("data-i18n-content", "");
            containerTimeValue.getStyle().set("font-family", "monospace");
            containerTimeValue.getStyle().set("background-color", "var(--lumo-primary-color-10pct)");
            containerTimeValue.getStyle().set("padding", "var(--lumo-space-s)");
            containerTimeValue.getStyle().set("border", "1px solid var(--lumo-primary-color-50pct)");
            containerTimeValue.getStyle().set("border-radius", "var(--lumo-border-radius-m)");
            containerTimeValue.getStyle().set("margin-bottom", "var(--lumo-space-m)");
            
            section.add(containerTimeLabel, containerTimeValue);
        }
        
        // Get image creation date
        String creationDate = getImageCreationDate();
        
        // Display image creation date if available
        if (creationDate != null && !creationDate.isEmpty()) {
            Paragraph creationDateLabel = new Paragraph(getTranslation("versionView.imageCreated"));
            creationDateLabel.getStyle().set("font-weight", "bold");
            
            Paragraph creationDateValue = new Paragraph(creationDate);
            creationDateValue.getElement().setAttribute("data-i18n-content", "");
            creationDateValue.getStyle().set("font-family", "monospace");
            creationDateValue.getStyle().set("background-color", "var(--lumo-success-color-10pct)");
            creationDateValue.getStyle().set("padding", "var(--lumo-space-s)");
            creationDateValue.getStyle().set("border", "1px solid var(--lumo-success-color-50pct)");
            creationDateValue.getStyle().set("border-radius", "var(--lumo-border-radius-m)");
            
            section.add(creationDateLabel, creationDateValue);
        } else {
            Paragraph noImageInfo = new Paragraph(getTranslation("versionView.imageCreatedUnavailable"));
            noImageInfo.getStyle().set("font-style", "italic");
            noImageInfo.getStyle().set("color", "var(--lumo-secondary-text-color)");
            section.add(noImageInfo);
        }
        
        return section;
    }
    
    /**
     * Gets the current time in the container with timezone information
     * 
     * @return The current container time with timezone info
     */
    private String getContainerTime() {
        return LocalDateTime.now().atZone(ZoneId.systemDefault()).toString();
    }
    
    /**
     * The image's creation date, formatted for the page. The date is found once per application
     * start ({@link ContainerInfo}); viewing the page starts no process.
     */
    private String getImageCreationDate() {
        String raw = containerInfo.imageCreationDate();
        if (raw == null) {
            return null;
        }
        String formattedDate = formatImageCreationDate(raw);
        return formattedDate != null ? formattedDate : raw;
    }
    
    /**
     * Formats the image creation date into raw format using container timezone
     */
    // Package-private (not private) so VersionViewFormatImageCreationDateTest can exercise
    // this pure date-formatting logic directly. Same visibility-for-testability precedent as
    // PostSurveyActionCaller.timeout.
    String formatImageCreationDate(String rawDate) {
        try {
            // Handle different date formats that might be returned
            
            // Format: "2025-07-24 15:54:44.626850013 +0000"
            if (rawDate.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d+ \\+\\d{4}")) {
                // Parse the datetime and convert to container timezone
                String[] parts = rawDate.split(" ");
                String datePart = parts[0];
                String timePart = parts[1].split("\\.")[0]; // Remove nanoseconds
                
                try {
                    // Parse as UTC first
                    String utcDateTimeString = datePart + "T" + timePart + "Z";
                    ZonedDateTime utcDateTime = ZonedDateTime.parse(utcDateTimeString);
                    
                    // Convert to container's timezone (same as system time)
                    ZoneId containerZone = ZoneId.systemDefault();
                    ZonedDateTime containerDateTime = utcDateTime.withZoneSameInstant(containerZone);
                    
                    // Return raw format
                    return containerDateTime.toString();
                    
                } catch (Exception e) {
                    // Fall back to original format if parsing fails
                    return rawDate;
                }
            }
            
            // Format: "2025-07-24T15:54:44.626850013Z" (ISO format)
            else if (rawDate.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}.*Z")) {
                try {
                    ZonedDateTime utcDateTime = ZonedDateTime.parse(rawDate);
                    
                    // Convert to container's timezone (same as system time)
                    ZoneId containerZone = ZoneId.systemDefault();
                    ZonedDateTime containerDateTime = utcDateTime.withZoneSameInstant(containerZone);
                    
                    // Return raw format
                    return containerDateTime.toString();
                    
                } catch (DateTimeParseException e) {
                    // Fall back to manual parsing
                    String[] parts = rawDate.replace("T", " ").replace("Z", "").split(" ");
                    if (parts.length >= 2) {
                        return formatImageCreationDate(parts[0] + " " + parts[1] + " +0000");
                    }
                }
            }
            
            // Format: "2025-07-24T15:54:44Z" (shorter ISO format)
            else if (rawDate.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z")) {
                try {
                    ZonedDateTime utcDateTime = ZonedDateTime.parse(rawDate);
                    
                    // Convert to container's timezone (same as system time)
                    ZoneId containerZone = ZoneId.systemDefault();
                    ZonedDateTime containerDateTime = utcDateTime.withZoneSameInstant(containerZone);
                    
                    // Return raw format
                    return containerDateTime.toString();
                    
                } catch (Exception e) {
                    String[] parts = rawDate.replace("T", " ").replace("Z", "").split(" ");
                    if (parts.length >= 2) {
                        return formatImageCreationDate(parts[0] + " " + parts[1] + " +0000");
                    }
                }
            }
            
            // If we can't parse it, return the original
            return rawDate;
            
        } catch (Exception e) {
            // If formatting fails, return the original date
            return rawDate;
        }
    }
}