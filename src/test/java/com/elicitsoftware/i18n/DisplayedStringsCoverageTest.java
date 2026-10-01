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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * UC-009 / NFR-011: 100 % displayed-string coverage gate.
 * <p>
 * Scans the UI source (every class under a {@code flow} package plus the explicit extras below)
 * for string literals that look like prose a user could see and are not routed through
 * {@code getTranslation}. The scan is deliberately broad: any literal with a letter that contains
 * whitespace, starts with an upper-case letter, or ends in sentence punctuation is a candidate,
 * unless the call it is passed to is a known non-display context (logging, exceptions, JavaScript,
 * CSS classes, string comparisons) or the literal is listed in
 * {@code src/test/resources/i18n/coverage-allowlist.txt}. A line ending in
 * {@code // i18n:ignore} is skipped, as is everything between {@code // i18n:ignore-start}
 * and {@code // i18n:ignore-end} (for blocks of shell commands and the like).
 * <p>
 * The test is red until every candidate is extracted, then stays green as the gate.
 */
class DisplayedStringsCoverageTest {

    /** Files outside a {@code flow} package that still render user-visible text. */
    private static final List<String> EXTRA_FILES = List.of(
            "src/main/java/com/elicitsoftware/report/PDFService.java",
            "src/main/java/com/elicitsoftware/report/PDFDownloadResource.java");

    /** Infrastructure classes under {@code flow} whose literals are not user-visible. */
    private static final Set<String> EXCLUDED_FILES = Set.of(
            "AppConfig.java", "BrandResourceHandler.java", "GlobalStrings.java");

    /** Enclosing call names whose string arguments never reach the screen. */
    private static final Pattern NON_DISPLAY_CONTEXT = Pattern.compile(
            "^(debug|info|warn|error|trace|debugf|infof|warnf|errorf|tracef|log|"
            + "\\w*Exception|\\w*Error|"
            + "executeJs|executeJavaScript|callJsFunction|"
            + "addClassName|addClassNames|setClassName|setClassNames|removeClassName|removeClassNames|"
            + "setId|setTestId|set|getStyle|"
            + "equals|equalsIgnoreCase|startsWith|endsWith|contains|matches|replace|replaceAll|replaceFirst|"
            + "split|indexOf|lastIndexOf|of|get|resolve|getResourceAsStream|getProperty|getenv|forName|"
            + "Route|RouteAlias|JsModule|StyleSheet|CssImport|ConfigProperty|Path|Produces|Consumes|"
            + "Column|Table|Entity|NamedQuery|QueryParam|PathParam|HeaderParam|Tag|"
            + "setSrc|setHref|setTarget|setName|setWidth|setHeight|setMaxWidth|setMinWidth|setMaxHeight|setMinHeight|"
            + "setPattern|setAllowedCharPattern|setAutocomplete|setType|"
            + "getResource|getLogger|valueOf|parse|ofPattern|header|type|status|ProcessBuilder|exec|command|"
            + "assertEquals|assertTrue|assertFalse|assertNotNull|if|while|switch|case)$");

    /** Keys, paths, CSS tokens and SHOUTING_CONSTANTS are never prose. */
    private static final Pattern IDENTIFIER_LIKE = Pattern.compile("^([a-z0-9_.:/#%\\-]+|[A-Z0-9_]+)$");
    /** Translation-key prefixes built at runtime, e.g. {@code "searchView.action."}. */
    private static final Pattern KEY_LIKE = Pattern.compile("^[a-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_\\-]+)*\\.?$");
    private static final Pattern PROSE_LIKE = Pattern.compile("^(?=.*\\p{L})(.*\\s.*|\\p{Lu}.*|.*[.:!?])$", Pattern.DOTALL);
    private static final Pattern STRING_LITERAL = Pattern.compile("\"((?:[^\"\\\\\\n]|\\\\.)*)\"");
    private static final Pattern IDENT_BEFORE_PAREN = Pattern.compile("([A-Za-z_][A-Za-z0-9_]*)\\s*(?:<[^<>]*>\\s*)?$");

    record Hit(Path file, int line, String context, String literal) {
        String render(Path root) {
            return root.relativize(file) + ":" + line + ": " + context + "(\"" + literal + "\")";
        }
    }

    @Test
    void everyDisplayedStringIsTranslated() throws IOException {
        Path root = moduleRoot();
        Set<String> allowlist = readAllowlist(root.resolve("src/test/resources/i18n/coverage-allowlist.txt"));
        List<Hit> hits = new ArrayList<>();
        for (Path file : sourceFiles(root)) {
            hits.addAll(scan(file, allowlist));
        }
        if (!hits.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            sb.append(hits.size()).append(" hard-coded user-visible literal(s) remain (UC-009 / NFR-011):\n");
            hits.forEach(h -> sb.append("  ").append(h.render(root)).append('\n'));
            sb.append("Route each through getTranslation(...) or, for a genuinely non-display literal, ")
              .append("append // i18n:ignore or add it to src/test/resources/i18n/coverage-allowlist.txt");
            fail(sb.toString());
        }
    }

    static Path moduleRoot() {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null && !Files.exists(dir.resolve("pom.xml"))) {
            dir = dir.getParent();
        }
        if (dir == null) {
            throw new IllegalStateException("pom.xml not found above " + Path.of("").toAbsolutePath());
        }
        return dir;
    }

    private static List<Path> sourceFiles(Path root) throws IOException {
        List<Path> files = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(root.resolve("src/main/java"))) {
            walk.filter(p -> p.toString().endsWith(".java"))
                .filter(p -> p.toString().contains("/flow/"))
                .filter(p -> !EXCLUDED_FILES.contains(p.getFileName().toString()))
                .sorted()
                .forEach(files::add);
        }
        for (String extra : EXTRA_FILES) {
            Path p = root.resolve(extra);
            if (Files.exists(p)) {
                files.add(p);
            }
        }
        return files;
    }

    static Set<String> readAllowlist(Path file) throws IOException {
        if (!Files.exists(file)) {
            return Set.of();
        }
        return Set.copyOf(Files.readAllLines(file, StandardCharsets.UTF_8).stream()
                .map(String::strip)
                .filter(l -> !l.isEmpty() && !l.startsWith("#"))
                .toList());
    }

    static List<Hit> scan(Path file, Set<String> allowlist) throws IOException {
        String raw = Files.readString(file, StandardCharsets.UTF_8);
        Set<Integer> ignoredLines = ignoredLines(raw);
        String src = blankComments(raw);
        List<Hit> hits = new ArrayList<>();
        Matcher m = STRING_LITERAL.matcher(src);
        List<Integer> openParens = new ArrayList<>();
        int cursor = 0;
        while (m.find()) {
            // Track paren nesting between the previous literal and this one.
            for (int i = cursor; i < m.start(); i++) {
                char c = src.charAt(i);
                if (c == '(') {
                    openParens.add(i);
                } else if (c == ')' && !openParens.isEmpty()) {
                    openParens.remove(openParens.size() - 1);
                }
            }
            cursor = m.end();
            String literal = m.group(1);
            int line = lineOf(src, m.start());
            if (ignoredLines.contains(line) || allowlist.contains(literal)) {
                continue;
            }
            if (IDENTIFIER_LIKE.matcher(literal).matches() || KEY_LIKE.matcher(literal).matches()
                    || !PROSE_LIKE.matcher(literal).matches()) {
                continue;
            }
            String context = openParens.isEmpty() ? "<field>" : identBefore(src, openParens.get(openParens.size() - 1));
            if (NON_DISPLAY_CONTEXT.matcher(context).matches()) {
                continue;
            }
            hits.add(new Hit(file, line, context, literal));
        }
        return hits;
    }

    private static String identBefore(String src, int parenIndex) {
        String before = src.substring(Math.max(0, parenIndex - 120), parenIndex);
        Matcher im = IDENT_BEFORE_PAREN.matcher(before);
        return im.find() ? im.group(1) : "<unknown>";
    }

    /** Lines carrying {@code i18n:ignore}, plus every line between {@code i18n:ignore-start} and {@code i18n:ignore-end}. */
    private static Set<Integer> ignoredLines(String raw) {
        Set<Integer> lines = new java.util.HashSet<>();
        String[] all = raw.split("\n", -1);
        boolean block = false;
        for (int i = 0; i < all.length; i++) {
            if (all[i].contains("i18n:ignore-start")) {
                block = true;
            }
            if (block || all[i].contains("i18n:ignore")) {
                lines.add(i + 1);
            }
            if (all[i].contains("i18n:ignore-end")) {
                block = false;
            }
        }
        return lines;
    }

    /** Replaces comment bodies with spaces, preserving newlines so line numbers stay intact. */
    static String blankComments(String src) {
        StringBuilder out = new StringBuilder(src.length());
        int i = 0;
        int n = src.length();
        while (i < n) {
            char c = src.charAt(i);
            if (c == '"') {
                int j = i + 1;
                while (j < n && src.charAt(j) != '"') {
                    if (src.charAt(j) == '\\') j++;
                    if (src.charAt(j) == '\n') break;
                    j++;
                }
                out.append(src, i, Math.min(j + 1, n));
                i = j + 1;
            } else if (c == '/' && i + 1 < n && src.charAt(i + 1) == '/') {
                while (i < n && src.charAt(i) != '\n') {
                    out.append(' ');
                    i++;
                }
            } else if (c == '/' && i + 1 < n && src.charAt(i + 1) == '*') {
                int end = src.indexOf("*/", i + 2);
                end = end < 0 ? n : end + 2;
                for (int k = i; k < end; k++) {
                    out.append(src.charAt(k) == '\n' ? '\n' : ' ');
                }
                i = end;
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    private static int lineOf(String src, int index) {
        int line = 1;
        for (int i = 0; i < index; i++) {
            if (src.charAt(i) == '\n') line++;
        }
        return line;
    }
}
