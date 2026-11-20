package com.elicitsoftware.etl;

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

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * The naming rule for a survey's reporting schema (UC-008 BR-006).
 * <p>
 * A name is {@code report_} followed by the survey's name in lower case, every run of characters
 * outside {@code a-z} and {@code 0-9} replaced by one {@code _}, the ends trimmed of {@code _},
 * truncated so the whole fits PostgreSQL's 63-byte identifier limit, and suffixed {@code _2},
 * {@code _3}... while another survey holds it. The same rule validates a name an administrator
 * chooses (UC-010 BR-004): it must look like an unquoted lower-case identifier and must not be
 * one of the schemas Elicit or PostgreSQL already use.
 */
public final class ReportSchemaNames {

    /** What a schema name must match: an unquoted lower-case PostgreSQL identifier. */
    public static final Pattern SCHEMA_PATTERN = Pattern.compile("^[a-z_][a-z0-9_]{0,62}$");

    /** Names a survey schema may never take. */
    public static final Set<String> RESERVED = Set.of("survey", "surveyreport", "public", "information_schema");

    static final String PREFIX = "report_";
    static final int MAX_BYTES = 63;

    private ReportSchemaNames() {
    }

    /**
     * Why a candidate name is not acceptable, or {@code null} when it is.
     *
     * @param name the candidate
     * @return the objection, fit for an operator, or {@code null}
     */
    public static String objection(String name) {
        if (name == null || name.isBlank()) {
            return "A schema name is required.";
        }
        if (!SCHEMA_PATTERN.matcher(name).matches()) {
            return "A schema name must be 1 to 63 lower-case letters, digits and underscores, not starting with a digit: " + name;
        }
        if (RESERVED.contains(name) || name.startsWith("pg_")) {
            return "The name " + name + " is reserved.";
        }
        return null;
    }

    /**
     * The name a survey's schema gets the first time it is built.
     *
     * @param surveyName the survey's name
     * @param taken      whether a candidate is already in use (another survey's name, or a schema
     *                   present in the database)
     * @return a free name obeying the rule
     */
    public static String derive(String surveyName, Predicate<String> taken) {
        String base = slug(surveyName);
        if (!taken.test(base)) {
            return base;
        }
        for (int n = 2; ; n++) {
            String suffix = "_" + n;
            String candidate = truncate(base, MAX_BYTES - suffix.length()) + suffix;
            if (!taken.test(candidate)) {
                return candidate;
            }
        }
    }

    /** The rule without the uniqueness suffix: {@code report_} + the slug, fitted to 63 bytes. */
    static String slug(String surveyName) {
        String lowered = surveyName == null ? "" : surveyName.toLowerCase(Locale.ROOT);
        String slug = lowered.replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
        if (slug.isEmpty()) {
            slug = "survey";
        }
        return truncate(PREFIX + slug, MAX_BYTES);
    }

    /** Cuts to at most {@code maxBytes} bytes (the slug is ASCII, so bytes are characters) and trims a trailing {@code _}. */
    private static String truncate(String name, int maxBytes) {
        String cut = name;
        while (cut.getBytes(StandardCharsets.UTF_8).length > maxBytes) {
            cut = cut.substring(0, cut.length() - 1);
        }
        return cut.replaceAll("_+$", "");
    }
}
