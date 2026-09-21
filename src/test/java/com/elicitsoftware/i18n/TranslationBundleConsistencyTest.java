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
 * UC-008 / NFR-011: the English bundle and the deployment's translation files must agree with each other and with the
 * source code. Checks: every locale file carries exactly the default keys; every key referenced
 * from {@code getTranslation("...")} / {@code translate(..., "...")} exists; every default key is
 * referenced (unless flagged {@code dynamic} in the context sidecar); {@code {n}} placeholders
 * match across locales; parameterised values contain no lone apostrophe; non-English values differ
 * from English unless allow-listed or flagged {@code identical} in the sidecar; every key has a
 * context entry for the translation handoff.
 */
class TranslationBundleConsistencyTest {

    static final Path BUNDLE_DIR = Path.of("src/main/resources/vaadin-i18n");
    /** The deployment translations directory the test profile mounts ({@code %test.i18n.file.system.path}). */
    static final Path MOUNT_DIR = Path.of("../elicit-i18n/survey");
    private static final Pattern LOCALE_FILE = Pattern.compile("translations_([A-Za-z0-9_]+)\\.properties");
    /** Keys passed straight to the translation API; these must exist. */
    private static final Pattern STRICT_REF = Pattern.compile(
            "(?:getTranslation|translate|Translations\\.get)\\s*\\((?:[^\"()]*,\\s*)?\"([A-Za-z0-9_.\\-]+)\"");
    /** Every key-like string literal (incl. ternaries and prefixes such as {@code "view.prefix."}); used for orphan detection. */
    private static final Pattern KEY_REF = Pattern.compile("\"([a-z][A-Za-z0-9_]*(?:(?:\\.[A-Za-z0-9_\\-]+)+\\.?|\\.))\"");
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{(\\d+)");

    @Test
    void bundlesAgreeWithEachOtherAndWithTheSource() throws IOException {
        Path root = DisplayedStringsCoverageTest.moduleRoot();
        Path dir = root.resolve(BUNDLE_DIR);
        List<String> problems = new ArrayList<>();

        Map<String, String> defaults = load(dir.resolve("translations.properties"), problems);
        Map<String, String> context = load(dir.resolve("translations.context.properties"), problems);
        Map<String, Map<String, String>> locales = new LinkedHashMap<>();
        collectLocales(dir, locales, problems);
        Path mount = root.resolve(MOUNT_DIR).normalize();
        if (!Files.isDirectory(mount)) {
            problems.add("deployment translations not found at " + mount
                    + " (the module tests run inside the Elicit umbrella checkout, which provides elicit-i18n)");
        } else {
            Map<String, String> mountedDefaults = load(mount.resolve("translations.properties"), problems);
            if (!mountedDefaults.equals(defaults)) {
                problems.add("the mount's copy of translations.properties differs from the application's English file: "
                        + mount.resolve("translations.properties"));
            }
            collectLocales(mount, locales, problems);
            if (locales.isEmpty()) {
                problems.add("no translations_<tag>.properties found in " + mount);
            }
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
        Set<String> referenced = referencedKeys(root.resolve("src/main/java"), KEY_REF);
        Set<String> strict = referencedKeys(root.resolve("src/main/java"), STRICT_REF);
        strict.stream().filter(k -> !k.endsWith(".")).filter(k -> !defaults.containsKey(k))
                .forEach(k -> problems.add("source references unknown key " + k));
        strict.stream().filter(k -> k.endsWith("."))
                .filter(prefix -> defaults.keySet().stream().noneMatch(k -> k.startsWith(prefix)))
                .forEach(prefix -> problems.add("source references dynamic key prefix with no keys " + prefix));
        for (String key : new TreeSet<>(defaults.keySet())) {
            boolean dynamic = context.getOrDefault(key, "").contains("dynamic");
            if (!referenced.contains(key) && !dynamic && !isDynamicPrefix(key, referenced)
                    && !isSuffixed(key, referenced)) {
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

    /** Keys completed with a suffix in code, e.g. {@code getTranslation(prefix + ".term")}. */
    private static boolean isSuffixed(String key, Set<String> referenced) {
        int dot = key.lastIndexOf('.');
        return dot > 0 && referenced.contains(key.substring(0, dot));
    }

    /** Keys built at runtime share a prefix flagged dynamic, e.g. {@code sectionView.error.} */
    private static boolean isDynamicPrefix(String key, Set<String> referenced) {
        return referenced.stream().anyMatch(r -> r.endsWith(".") && key.startsWith(r));
    }

    private static Set<String> referencedKeys(Path srcRoot, Pattern pattern) throws IOException {
        Set<String> keys = new TreeSet<>();
        try (Stream<Path> walk = Files.walk(srcRoot)) {
            for (Path p : walk.filter(f -> f.toString().endsWith(".java")).toList()) {
                Matcher m = pattern.matcher(Files.readString(p, StandardCharsets.UTF_8));
                while (m.find()) {
                    keys.add(m.group(1));
                }
            }
        }
        return keys;
    }

    /** Every {@code translations_<tag>.properties} in {@code dir}; a tag present in two directories keeps the first. */
    private static void collectLocales(Path dir, Map<String, Map<String, String>> locales, List<String> problems)
            throws IOException {
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (Stream<Path> files = Files.list(dir)) {
            for (Path f : files.sorted().toList()) {
                Matcher m = LOCALE_FILE.matcher(f.getFileName().toString());
                if (m.matches() && !locales.containsKey(m.group(1))) {
                    locales.put(m.group(1), load(f, problems));
                }
            }
        }
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
