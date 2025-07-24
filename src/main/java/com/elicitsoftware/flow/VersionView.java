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

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;
import com.vaadin.quarkus.annotation.NormalUIScoped;
import io.quarkus.logging.Log;
import jakarta.annotation.PostConstruct;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * VersionView displays system and container information for the application.
 * This includes Docker container ID when running in a containerized environment.
 */
@Route(value = "version", layout = MainLayout.class)
@NormalUIScoped
public class VersionView extends VerticalLayout {
    
    public VersionView() {
        super();
    }
    
    @PostConstruct
    public void init() {
        setSpacing(true);
        setPadding(true);
        
        // Add title
        H2 title = new H2("Version Information");
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
        
        H2 sectionTitle = new H2("Container Information");
        section.add(sectionTitle);
        
        // Get and display container time
        String containerTime = getContainerTime();
        if (containerTime != null && !containerTime.isEmpty()) {
            Paragraph containerTimeLabel = new Paragraph("Container Current Time:");
            containerTimeLabel.getStyle().set("font-weight", "bold");
            
            Paragraph containerTimeValue = new Paragraph(containerTime);
            containerTimeValue.getStyle().set("font-family", "monospace");
            containerTimeValue.getStyle().set("background-color", "#e8f4fd");
            containerTimeValue.getStyle().set("padding", "8px");
            containerTimeValue.getStyle().set("border", "1px solid #bee5eb");
            containerTimeValue.getStyle().set("border-radius", "4px");
            containerTimeValue.getStyle().set("margin-bottom", "16px");
            
            section.add(containerTimeLabel, containerTimeValue);
        }
        
        // Get image creation date
        String creationDate = getImageCreationDate();
        
        // Display image creation date if available
        if (creationDate != null && !creationDate.isEmpty()) {
            Paragraph creationDateLabel = new Paragraph("Image Creation Date:");
            creationDateLabel.getStyle().set("font-weight", "bold");
            
            Paragraph creationDateValue = new Paragraph(creationDate);
            creationDateValue.getStyle().set("font-family", "monospace");
            creationDateValue.getStyle().set("background-color", "#e8f5e8");
            creationDateValue.getStyle().set("padding", "8px");
            creationDateValue.getStyle().set("border", "1px solid #c3e6c3");
            creationDateValue.getStyle().set("border-radius", "4px");
            
            section.add(creationDateLabel, creationDateValue);
        } else {
            Paragraph noImageInfo = new Paragraph("Image creation date not available.");
            noImageInfo.getStyle().set("font-style", "italic");
            noImageInfo.getStyle().set("color", "#6c757d");
            section.add(noImageInfo);
        }
        
        return section;
    }
    
    /**
     * Simple method to get container ID for docker inspect
     */
    private String getContainerIdSimple() {
        try {
            // Try hostname first (often container ID)
            String hostname = getHostname();
            if (hostname != null && hostname.matches("^[a-f0-9]{12,64}$")) {
                return hostname;
            }
            
            // Try from /proc/self/mountinfo
            ProcessBuilder processBuilder = new ProcessBuilder("sh", "-c", 
                "cat /proc/self/mountinfo 2>/dev/null | grep -o -E '/docker/containers/[a-f0-9]{64}' | grep -o -E '[a-f0-9]{64}' | head -1");
            
            Process process = processBuilder.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String containerId = reader.readLine();
            process.waitFor();
            
            if (containerId != null && !containerId.trim().isEmpty()) {
                return containerId.trim();
            }
            
        } catch (Exception e) {
            // Ignore exception
        }
        return null;
    }
    
    /**
     * Gets the current time in the container with timezone information
     * 
     * @return The current container time with timezone info or null if unavailable
     */
    private String getContainerTime() {
        try {
            // Get current time with Java's system time in raw format
            LocalDateTime now = LocalDateTime.now();
            ZoneId systemZone = ZoneId.systemDefault();
            ZonedDateTime zonedNow = now.atZone(systemZone);
            
            // Return raw format
            return zonedNow.toString();
            
        } catch (Exception e) {
            // If there's an error, try to get basic time info
            try {
                ProcessBuilder processBuilder = new ProcessBuilder("date");
                Process process = processBuilder.start();
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String result = reader.readLine();
                process.waitFor();
                
                if (result != null && !result.trim().isEmpty()) {
                    return result.trim();
                }
            } catch (Exception e2) {
                // Last resort - Java system time
                return LocalDateTime.now().toString();
            }
        }
        return null;
    }
    
    /**
     * Gets the system hostname which in Docker containers is often set to the container ID.
     * 
     * @return The system hostname or null if unavailable
     */
    private String getHostname() {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder("hostname");
            Process process = processBuilder.start();
            
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String hostname = reader.readLine();
            
            int exitCode = process.waitFor();
            
            if (exitCode == 0 && hostname != null && !hostname.trim().isEmpty()) {
                return hostname.trim();
            }
            
        } catch (Exception e) {
            Log.debug("Could not retrieve hostname", e);
        }
        
        return null;
    }
    
    /**
     * Executes methods to retrieve the Docker image tags.
     * 
     * @return The Docker image tags if found, null otherwise
     */
    private String getDockerImageTags() {
        try {
            // Method 1: Try to get from environment variables
            String imageTags = getImageTagsFromEnv();
            if (imageTags != null) {
                return imageTags;
            }
            
            // Method 2: Try to get from Docker inspect (if available)
            imageTags = getImageTagsFromDockerInspect();
            if (imageTags != null) {
                return imageTags;
            }
            
            // Method 3: Try to get from Docker labels
            imageTags = getImageTagsFromLabels();
            if (imageTags != null) {
                return imageTags;
            }
            
            return null;
            
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Tries to get image tags from environment variables
     */
    private String getImageTagsFromEnv() {
        try {
            // Check common environment variables that might contain image tag info
            String[] envVars = {"DOCKER_IMAGE_TAG", "IMAGE_TAG", "DOCKER_TAG", "IMAGE_NAME"};
            
            for (String envVar : envVars) {
                String value = System.getenv(envVar);
                if (value != null && !value.trim().isEmpty()) {
                    return value.trim();
                }
            }
            
            // Also try reading from /proc/1/environ
            ProcessBuilder processBuilder = new ProcessBuilder("sh", "-c", 
                "cat /proc/1/environ 2>/dev/null | tr '\\0' '\\n' | grep -E '(IMAGE_TAG|DOCKER_TAG|IMAGE_NAME)' | head -1");
            
            Process process = processBuilder.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line = reader.readLine();
            process.waitFor();
            
            if (line != null && line.contains("=")) {
                String value = line.split("=", 2)[1];
                if (!value.trim().isEmpty()) {
                    return value.trim();
                }
            }
            
        } catch (Exception e) {
            // Ignore exception
        }
        return null;
    }
    
    /**
     * Tries to get image tags from docker inspect command
     */
    private String getImageTagsFromDockerInspect() {
        try {
            // First get container ID
            String containerId = getContainerIdSimple();
            if (containerId == null) {
                return null;
            }
            
            // Try to use docker inspect to get image tags
            ProcessBuilder processBuilder = new ProcessBuilder("sh", "-c", 
                "docker inspect " + containerId + " --format '{{.Config.Image}}' 2>/dev/null");
            
            Process process = processBuilder.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String imageName = reader.readLine();
            process.waitFor();
            
            if (imageName != null && !imageName.trim().isEmpty()) {
                return imageName.trim();
            }
            
            // Alternative: try to get repository tags
            processBuilder = new ProcessBuilder("sh", "-c", 
                "docker inspect " + containerId + " --format '{{range .RepoTags}}{{.}} {{end}}' 2>/dev/null");
            
            process = processBuilder.start();
            reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String repoTags = reader.readLine();
            process.waitFor();
            
            if (repoTags != null && !repoTags.trim().isEmpty()) {
                return repoTags.trim();
            }
            
        } catch (Exception e) {
            // Ignore exception
        }
        return null;
    }
    
    /**
     * Tries to get image tags from Docker labels or metadata
     */
    private String getImageTagsFromLabels() {
        try {
            // First get container ID
            String containerId = getContainerIdSimple();
            if (containerId == null) {
                return null;
            }
            
            // Try to get labels that might contain image information
            ProcessBuilder processBuilder = new ProcessBuilder("sh", "-c", 
                "docker inspect " + containerId + " --format '{{range $key, $value := .Config.Labels}}{{$key}}={{$value}} {{end}}' 2>/dev/null | grep -i image");
            
            Process process = processBuilder.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String labels = reader.readLine();
            process.waitFor();
            
            if (labels != null && !labels.trim().isEmpty()) {
                return labels.trim();
            }
            
        } catch (Exception e) {
            // Ignore exception
        }
        return null;
    }
    
    /**
     * Tries to get the Docker image creation date/time
     */
    private String getImageCreationDate() {
        try {
            String containerId = getContainerIdSimple();
            String imageTags = getDockerImageTags();
            
            // Try multiple approaches to get image creation date
            String[] commands = {
                // Try docker inspect to get image creation date
                containerId != null ? "docker inspect " + containerId + " --format '{{.Created}}' 2>/dev/null" : null,
                
                // Try docker images to get creation date from image tag
                imageTags != null ? "docker images --format 'table {{.Repository}}:{{.Tag}}\\t{{.CreatedAt}}' " + imageTags + " 2>/dev/null | tail -n +2" : null,
                
                // Try docker inspect on the image directly
                imageTags != null ? "docker inspect " + imageTags + " --format '{{.Created}}' 2>/dev/null" : null,
                
                // Look for image metadata in container config
                containerId != null ? "find /var/lib/docker/containers/" + containerId + "/ -name 'config.v2.json' -exec grep -o '\"Created\":\"[^\"]*\"' {} \\; 2>/dev/null | cut -d'\"' -f4" : null,
                
                // Try to get from /proc filesystem timestamps
                "stat -c %y /.dockerenv 2>/dev/null",
                
                // Try to get container start time as approximation
                "stat -c %y /proc/1 2>/dev/null",
                
                // Look for Docker build date in environment
                "cat /proc/1/environ 2>/dev/null | tr '\\0' '\\n' | grep -E '(BUILD_DATE|CREATED|IMAGE_DATE)' | head -1",
                
                // Check for any date-related labels
                containerId != null ? "docker inspect " + containerId + " --format '{{range $key, $value := .Config.Labels}}{{$key}}={{$value}}{{\"\\n\"}}{{end}}' 2>/dev/null | grep -i -E '(date|created|build)'" : null
            };
            
            for (String command : commands) {
                if (command == null) continue;
                
                ProcessBuilder processBuilder = new ProcessBuilder("sh", "-c", command);
                Process process = processBuilder.start();
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String result = reader.readLine();
                process.waitFor();
                
                if (result != null && !result.trim().isEmpty()) {
                    String cleaned = result.trim();
                    // Try to parse and format the date if it looks like a valid date
                    if (cleaned.matches(".*\\d{4}-\\d{2}-\\d{2}.*") || 
                        cleaned.matches(".*\\d{4}/\\d{2}/\\d{2}.*") ||
                        cleaned.contains("T") ||
                        cleaned.contains("UTC") ||
                        cleaned.contains("GMT")) {
                        
                        // Try to format the date in a more user-friendly way
                        String formattedDate = formatImageCreationDate(cleaned);
                        return formattedDate != null ? formattedDate : cleaned;
                    }
                }
            }
            
        } catch (Exception e) {
            // Ignore exception
        }
        return null;
    }
    
    /**
     * Formats the image creation date into raw format using container timezone
     */
    private String formatImageCreationDate(String rawDate) {
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
