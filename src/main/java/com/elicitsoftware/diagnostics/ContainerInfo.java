package com.elicitsoftware.diagnostics;

/*-
 * ***LICENSE_START***
 * Elicit Survey
 * %%
 * Copyright (C) 2025 - 2026 The Regents of the University of Michigan - Rogel Cancer Center
 * %%
 * PolyForm Noncommercial License 1.0.0
 * <https://polyformproject.org/licenses/noncommercial/1.0.0>
 * ***LICENSE_END***
 */

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * What the version page shows about the container the application runs in: the date its image
 * was created. Nothing here changes while the container lives, so the commands that find it run
 * once, on first use, and every later page view reads the remembered answer. Each command is
 * given {@link #COMMAND_TIMEOUT} and is abandoned when it has not finished by then.
 */
@ApplicationScoped
public class ContainerInfo {

    /** How long one command may take before it is abandoned. */
    static final Duration COMMAND_TIMEOUT = Duration.ofSeconds(2);

    /** Runs a command and answers the first line it printed. */
    @FunctionalInterface
    interface CommandRunner {
        /**
         * @param command the program and its arguments
         * @return the first line of output, or null when there was none, the command failed or
         *         it did not finish in time
         */
        String firstLine(String... command);
    }

    private final CommandRunner runner;
    private boolean resolved;
    private String imageCreationDate;

    /** Runs the commands as real processes, each with {@link #COMMAND_TIMEOUT}. */
    public ContainerInfo() {
        this(command -> run(COMMAND_TIMEOUT, command));
    }

    ContainerInfo(CommandRunner runner) {
        this.runner = runner;
    }

    /**
     * The image's creation date as the first source that knows it reports it, unformatted; null
     * when none does. Found once and remembered.
     *
     * @return the raw date text, or null
     */
    public synchronized String imageCreationDate() {
        if (!resolved) {
            imageCreationDate = findImageCreationDate();
            resolved = true;
        }
        return imageCreationDate;
    }

    /**
     * Runs one command, bounded by the timeout. The process is destroyed when it has not
     * finished in time, so a command that hangs never holds the caller.
     */
    static String run(Duration timeout, String... command) {
        Process process = null;
        try {
            process = new ProcessBuilder(command)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            process.getOutputStream().close();
            // The commands print a line or two, far less than the pipe holds, so waiting before
            // reading cannot block the command.
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                return null;
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line = reader.readLine();
                return line == null || line.trim().isEmpty() ? null : line.trim();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            Log.debug("Could not run " + command[0], e);
            return null;
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    /** The container id: the hostname when it looks like one, else the id in the mount table. */
    private String containerId() {
        String hostname = runner.firstLine("hostname");
        if (hostname != null && hostname.matches("^[a-f0-9]{12,64}$")) {
            return hostname;
        }
        return runner.firstLine("sh", "-c",
                "cat /proc/self/mountinfo 2>/dev/null | grep -o -E '/docker/containers/[a-f0-9]{64}' | grep -o -E '[a-f0-9]{64}' | head -1");
    }

    /** The image's name or tags: from the environment, else from docker inspect, else from its labels. */
    private String imageTags(String containerId) {
        for (String variable : new String[]{"DOCKER_IMAGE_TAG", "IMAGE_TAG", "DOCKER_TAG", "IMAGE_NAME"}) {
            String value = System.getenv(variable);
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        String line = runner.firstLine("sh", "-c",
                "cat /proc/1/environ 2>/dev/null | tr '\\0' '\\n' | grep -E '(IMAGE_TAG|DOCKER_TAG|IMAGE_NAME)' | head -1");
        if (line != null && line.contains("=")) {
            String value = line.split("=", 2)[1];
            if (!value.trim().isEmpty()) {
                return value.trim();
            }
        }
        if (containerId == null) {
            return null;
        }
        String imageName = runner.firstLine("sh", "-c",
                "docker inspect " + containerId + " --format '{{.Config.Image}}' 2>/dev/null");
        if (imageName != null) {
            return imageName;
        }
        String repoTags = runner.firstLine("sh", "-c",
                "docker inspect " + containerId + " --format '{{range .RepoTags}}{{.}} {{end}}' 2>/dev/null");
        if (repoTags != null) {
            return repoTags;
        }
        return runner.firstLine("sh", "-c",
                "docker inspect " + containerId + " --format '{{range $key, $value := .Config.Labels}}{{$key}}={{$value}} {{end}}' 2>/dev/null | grep -i image");
    }

    private String findImageCreationDate() {
        try {
            String containerId = containerId();
            String imageTags = imageTags(containerId);

            String[] commands = {
                    // docker inspect of the container
                    containerId != null ? "docker inspect " + containerId + " --format '{{.Created}}' 2>/dev/null" : null,

                    // docker images, by the image's tag
                    imageTags != null ? "docker images --format 'table {{.Repository}}:{{.Tag}}\\t{{.CreatedAt}}' " + imageTags + " 2>/dev/null | tail -n +2" : null,

                    // docker inspect of the image
                    imageTags != null ? "docker inspect " + imageTags + " --format '{{.Created}}' 2>/dev/null" : null,

                    // the container's own configuration file
                    containerId != null ? "find /var/lib/docker/containers/" + containerId + "/ -name 'config.v2.json' -exec grep -o '\"Created\":\"[^\"]*\"' {} \\; 2>/dev/null | cut -d'\"' -f4" : null,

                    // timestamps in the file system
                    "stat -c %y /.dockerenv 2>/dev/null",

                    // the container's start, as an approximation
                    "stat -c %y /proc/1 2>/dev/null",

                    // a build date in the environment
                    "cat /proc/1/environ 2>/dev/null | tr '\\0' '\\n' | grep -E '(BUILD_DATE|CREATED|IMAGE_DATE)' | head -1",

                    // a date among the labels
                    containerId != null ? "docker inspect " + containerId + " --format '{{range $key, $value := .Config.Labels}}{{$key}}={{$value}}{{\"\\n\"}}{{end}}' 2>/dev/null | grep -i -E '(date|created|build)'" : null
            };

            for (String command : commands) {
                if (command == null) {
                    continue;
                }
                String result = runner.firstLine("sh", "-c", command);
                if (result != null && looksLikeADate(result)) {
                    return result;
                }
            }
        } catch (RuntimeException e) {
            Log.debug("Could not determine the image creation date", e);
        }
        return null;
    }

    static boolean looksLikeADate(String text) {
        return text.matches(".*\\d{4}-\\d{2}-\\d{2}.*")
                || text.matches(".*\\d{4}/\\d{2}/\\d{2}.*")
                || text.contains("T")
                || text.contains("UTC")
                || text.contains("GMT");
    }
}
