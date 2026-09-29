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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Per-locale presentation settings (UC-009 BR-004, BR-011): text direction and font scale.
 * <p>
 * Right-to-left is inferred from the language for the well-known RTL scripts and the font scale
 * defaults to {@code 1.0}; a deployment can override or extend either for a mounted locale through
 * an optional {@code i18n-config.json} at the translations mount root (falling back to the local
 * directory and then to {@code META-INF/i18n/i18n-config.json} on the classpath):
 * <pre>{"locales": [{"tag": "ar", "direction": "rtl", "fontScale": 1.15}]}</pre>
 * Both properties are optional per entry, and an entry that declares neither usably is ignored, so
 * a mount that carries only directions behaves exactly as it did before font scale existed.
 */
@ApplicationScoped
public class LocaleConfig {

    private static final Logger LOG = Logger.getLogger(LocaleConfig.class);
    static final String CONFIG_FILE = "i18n-config.json";
    static final Set<String> RTL_LANGUAGES = Set.of("ar", "he", "fa", "ur", "ps", "sd", "ug", "yi", "dv", "ckb");

    /** Neither shrunk nor blown up far enough to break a layout; outside this a scale is refused. */
    static final double MIN_FONT_SCALE = 0.75;
    static final double MAX_FONT_SCALE = 2.0;
    public static final double DEFAULT_FONT_SCALE = 1.0;

    @ConfigProperty(name = "i18n.file.system.path", defaultValue = "/i18n")
    String fileSystemPath = "/i18n";

    @ConfigProperty(name = "i18n.local.path", defaultValue = "i18n")
    String localPath = "i18n";

    private volatile Map<String, Entry> entries;

    /** One {@code locales} entry; either property may be absent, which means "not declared here". */
    record Entry(Boolean rightToLeft, Double fontScale) {
    }

    public boolean isRightToLeft(Locale locale) {
        if (locale == null) {
            return false;
        }
        return declared(locale, Entry::rightToLeft, RTL_LANGUAGES.contains(locale.getLanguage()));
    }

    /**
     * The multiplier to apply to the root font size for this locale, {@value #DEFAULT_FONT_SCALE}
     * unless the manifest declares another. A script whose apparent x-height is smaller than
     * Latin's -- Arabic is the case this exists for -- reads at the same size as English only when
     * it is set a little larger.
     */
    public double fontScale(Locale locale) {
        if (locale == null) {
            return DEFAULT_FONT_SCALE;
        }
        return declared(locale, Entry::fontScale, DEFAULT_FONT_SCALE);
    }

    public void clearCache() {
        entries = null;
    }

    /** The exact tag wins over the language alone; an entry silent on the property is skipped. */
    private <T> T declared(Locale locale, Function<Entry, T> property, T fallback) {
        Map<String, Entry> map = entries();
        for (String key : new String[] {
                locale.toLanguageTag().toLowerCase(Locale.ROOT),
                locale.getLanguage().toLowerCase(Locale.ROOT)}) {
            Entry entry = map.get(key);
            if (entry != null) {
                T value = property.apply(entry);
                if (value != null) {
                    return value;
                }
            }
        }
        return fallback;
    }

    private Map<String, Entry> entries() {
        Map<String, Entry> map = entries;
        if (map == null) {
            map = load();
            entries = map;
        }
        return map;
    }

    private Map<String, Entry> load() {
        String json = null;
        for (String base : new String[] {fileSystemPath, localPath}) {
            if (base == null || base.isBlank()) continue;
            Path file = Paths.get(base, CONFIG_FILE);
            if (Files.isRegularFile(file)) {
                try {
                    json = Files.readString(file);
                    break;
                } catch (IOException e) {
                    LOG.warnf(e, "Cannot read %s", file);
                }
            }
        }
        if (json == null) {
            try (InputStream in = Thread.currentThread().getContextClassLoader()
                    .getResourceAsStream("META-INF/i18n/" + CONFIG_FILE)) {
                if (in != null) {
                    json = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                }
            } catch (IOException e) {
                LOG.warn("Cannot read classpath i18n-config.json", e);
            }
        }
        if (json == null) {
            return Collections.emptyMap();
        }
        Map<String, Entry> map = new HashMap<>();
        try {
            JsonNode root = new ObjectMapper().readTree(json);
            JsonNode locales = root.path("locales");
            for (JsonNode entry : locales) {
                String tag = entry.path("tag").asText("");
                if (tag.isEmpty()) {
                    continue;
                }
                Boolean rightToLeft = direction(entry.path("direction"), tag);
                Double fontScale = fontScale(entry.path("fontScale"), tag);
                if (rightToLeft != null || fontScale != null) {
                    map.put(tag.toLowerCase(Locale.ROOT), new Entry(rightToLeft, fontScale));
                }
            }
        } catch (IOException e) {
            LOG.warn("Malformed i18n-config.json; using built-in locale defaults", e);
        }
        return Collections.unmodifiableMap(map);
    }

    /** {@code null} when the entry does not declare a usable direction, so the default stands. */
    private static Boolean direction(JsonNode node, String tag) {
        if (node.isMissingNode() || node.isNull()) {
            return null;
        }
        String direction = node.asText("");
        if ("rtl".equalsIgnoreCase(direction) || "ltr".equalsIgnoreCase(direction)) {
            return "rtl".equalsIgnoreCase(direction);
        }
        LOG.warnf("i18n-config.json: ignoring direction \"%s\" for %s; expected \"rtl\" or \"ltr\"",
                direction, tag);
        return null;
    }

    /** {@code null} when the entry does not declare a usable scale, so {@code 1.0} stands. */
    private static Double fontScale(JsonNode node, String tag) {
        if (node.isMissingNode() || node.isNull()) {
            return null;
        }
        double value;
        if (node.isNumber()) {
            value = node.doubleValue();
        } else {
            try {
                value = Double.parseDouble(node.asText("").trim());
            } catch (NumberFormatException e) {
                LOG.warnf("i18n-config.json: ignoring fontScale \"%s\" for %s; expected a number",
                        node.asText(""), tag);
                return null;
            }
        }
        if (!Double.isFinite(value) || value < MIN_FONT_SCALE || value > MAX_FONT_SCALE) {
            LOG.warnf("i18n-config.json: ignoring fontScale %s for %s; expected %s to %s",
                    value, tag, MIN_FONT_SCALE, MAX_FONT_SCALE);
            return null;
        }
        return value;
    }
}
