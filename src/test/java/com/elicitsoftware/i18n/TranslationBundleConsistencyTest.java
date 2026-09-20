package com.elicitsoftware.i18n;

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

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UC-007 / NFR-009: the shipped translation bundles must agree with each other and with the
 * source code. Checks: every locale file carries exactly the default keys; every key referenced
 * from {@code getTranslation("...")} / {@code translate(..., "...")} exists; every default key is
 * referenced (unless flagged {@code dynamic} in the context sidecar); {@code {n}} placeholders
 * match across locales; parameterised values contain no lone apostrophe; non-English values differ
 * from English unless allow-listed or flagged {@code identical} in the sidecar; every key has a
 * context entry for the translation handoff.
 */
class TranslationBundleConsistencyTest {

    static final Path BUNDLE_DIR = Path.of("src/main/resources/vaadin-i18n");
    static final List<String> LOCALES = List.of("es_419", "ar");
    private static final Pattern KEY_REF = Pattern.compile(
            "(?:getTranslation|translate|Translations\\.get)\\s*\\((?:[^\"()]*,\\s*)?\"([A-Za-z0-9_.\\-]+)\"");
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{(\\d+)");

    @Test
    void bundlesAgreeWithEachOtherAndWithTheSource() throws IOException {
        Path root = DisplayedStringsCoverageTest.moduleRoot();
        Path dir = root.resolve(BUNDLE_DIR);
        List<String> problems = new ArrayList<>();

        Map<String, String> defaults = load(dir.resolve("translations.properties"), problems);
        Map<String, String> context = load(dir.resolve("translations.context.properties"), problems);
        Map<String, Map<String, String>> locales = new LinkedHashMap<>();
        for (String tag : LOCALES) {
            locales.put(tag, load(dir.resolve("translations_" + tag + ".properties"), problems));
        }
        if (!problems.isEmpty()) {
            assertTrue(problems.isEmpty(), String.join("\n", problems));
        }

        Set<String> identicalAllowed = DisplayedStringsCoverageTest.readAllowlist(
                root.resolve("src/test/resources/i18n/identical-allowlist.txt"));

        // 1. key sets
        for (var e : locales.entrySet()) {
            Set<String> missing = new TreeSet<>(defaults.keySet());
            missing.removeAll(e.getValue().keySet());
            Set<String> extra = new TreeSet<>(e.getValue().keySet());
            extra.removeAll(defaults.keySet());
            missing.forEach(k -> problems.add(e.getKey() + ": missing key " + k));
            extra.forEach(k -> problems.add(e.getKey() + ": key not in default bundle " + k));
        }

        // 2. source references
        Set<String> referenced = referencedKeys(root.resolve("src/main/java"));
        referenced.stream().filter(k -> !k.endsWith(".")).filter(k -> !defaults.containsKey(k))
                .forEach(k -> problems.add("source references unknown key " + k));
        referenced.stream().filter(k -> k.endsWith("."))
                .filter(prefix -> defaults.keySet().stream().noneMatch(k -> k.startsWith(prefix)))
                .forEach(prefix -> problems.add("source references dynamic key prefix with no keys " + prefix));
        for (String key : new TreeSet<>(defaults.keySet())) {
            boolean dynamic = context.getOrDefault(key, "").contains("dynamic");
            if (!referenced.contains(key) && !dynamic && !isDynamicPrefix(key, referenced)) {
                problems.add("orphan key (not referenced from source, not flagged dynamic): " + key);
            }
            if (!context.containsKey(key)) {
                problems.add("no translation context for key " + key + " (translations.context.properties)");
            }
        }
        context.keySet().stream().filter(k -> !defaults.containsKey(k))
                .forEach(k -> problems.add("context entry for unknown key " + k));

        // 3. placeholders and quoting
        for (var e : defaults.entrySet()) {
            Set<String> expected = placeholders(e.getValue());
            if (!expected.isEmpty() && hasLoneApostrophe(e.getValue())) {
                problems.add("default: lone apostrophe in parameterised value " + e.getKey());
            }
            for (var loc : locales.entrySet()) {
                String v = loc.getValue().get(e.getKey());
                if (v == null) continue;
                if (!placeholders(v).equals(expected)) {
                    problems.add(loc.getKey() + ": placeholders " + placeholders(v) + " != " + expected + " for " + e.getKey());
                }
                if (!expected.isEmpty() && hasLoneApostrophe(v)) {
                    problems.add(loc.getKey() + ": lone apostrophe in parameterised value " + e.getKey());
                }
                boolean identicalOk = identicalAllowed.contains(v.strip())
                        || context.getOrDefault(e.getKey(), "").contains("identical");
                if (v.strip().equals(e.getValue().strip()) && !identicalOk) {
                    problems.add(loc.getKey() + ": untranslated (identical to English) " + e.getKey());
                }
            }
        }

        assertTrue(problems.isEmpty(), problems.size() + " translation bundle problem(s):\n  "
                + String.join("\n  ", problems));
    }

    /** Keys built at runtime share a prefix flagged dynamic, e.g. {@code sectionView.error.} */
    private static boolean isDynamicPrefix(String key, Set<String> referenced) {
        return referenced.stream().anyMatch(r -> r.endsWith(".") && key.startsWith(r));
    }

    private static Set<String> referencedKeys(Path srcRoot) throws IOException {
        Set<String> keys = new TreeSet<>();
        try (Stream<Path> walk = Files.walk(srcRoot)) {
            for (Path p : walk.filter(f -> f.toString().endsWith(".java")).toList()) {
                Matcher m = KEY_REF.matcher(Files.readString(p, StandardCharsets.UTF_8));
                while (m.find()) {
                    keys.add(m.group(1));
                }
            }
        }
        return keys;
    }

    static Map<String, String> load(Path file, List<String> problems) throws IOException {
        Map<String, String> map = new TreeMap<>();
        if (!Files.exists(file)) {
            problems.add("missing bundle file " + file);
            return map;
        }
        Properties props = new Properties();
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            props.load(r);
        }
        props.forEach((k, v) -> map.put(k.toString(), v.toString()));
        return map;
    }

    private static Set<String> placeholders(String value) {
        Set<String> found = new TreeSet<>();
        Matcher m = PLACEHOLDER.matcher(value);
        while (m.find()) {
            found.add(m.group(1));
        }
        return found;
    }

    private static boolean hasLoneApostrophe(String value) {
        return value.replace("''", "").contains("'");
    }
}
