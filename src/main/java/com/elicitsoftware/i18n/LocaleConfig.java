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
import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Per-locale presentation settings (UC-009 BR-004, BR-011): text direction and font scale.
 * <p>
 * Right-to-left is inferred from the language for the well-known RTL scripts and the font scale
 * defaults to {@code 1.0}. The image states what it ships for its own languages in
 * {@code META-INF/i18n/i18n-config.json} on the classpath:
 * <pre>{"locales": [{"tag": "ar", "direction": "rtl", "fontScale": 1.15}]}</pre>
 * Both properties are optional per entry, and an entry that declares neither usably is ignored.
 * <p>
 * A deployment overrides either with a config property naming the tag, {@code i18n.direction.<tag>}
 * and {@code i18n.font-scale.<tag>}, which wins over the shipped file:
 * <pre>i18n.font-scale.ar=1.2</pre>
 * Properties rather than a file, because there is no longer a mounted directory to put a file in;
 * without them a site could not adjust typography at all short of a new build. They are read for
 * the tag being asked about rather than enumerated, so an operator can name any tag without the
 * lookup having to discover which keys exist.
 * <p>
 * The scale is per language but page-wide: it multiplies the root font size, so an Arabic page
 * grows entirely, spacing and Latin text on it included, while other languages stay at 1.0.
 */
@ApplicationScoped
public class LocaleConfig {

    private static final Logger LOG = Logger.getLogger(LocaleConfig.class);
    static final String CONFIG_FILE = "i18n-config.json";
    static final String DIRECTION_PROPERTY = "i18n.direction.";
    static final String FONT_SCALE_PROPERTY = "i18n.font-scale.";
    static final Set<String> RTL_LANGUAGES = Set.of("ar", "he", "fa", "ur", "ps", "sd", "ug", "yi", "dv", "ckb");

    /** Neither shrunk nor blown up far enough to break a layout; outside this a scale is refused. */
    static final double MIN_FONT_SCALE = 0.75;
    static final double MAX_FONT_SCALE = 2.0;
    public static final double DEFAULT_FONT_SCALE = 1.0;

    /**
     * How a deployment's configuration is read, as a seam the unit tests replace.
     * <p>
     * Not an injected {@code Config}: this bean is also constructed directly in tests, where an
     * injected field would be null. Looked up per call rather than enumerated, so an operator can
     * name any tag without this having to discover which keys exist -- which is the part of
     * SmallRye's property enumeration that does not survive environment variables.
     */
    Function<String, Optional<String>> propertyLookup =
            key -> ConfigProvider.getConfig().getOptionalValue(key, String.class);

    private volatile Map<String, Entry> entries;

    /** One {@code locales} entry; either property may be absent, which means "not declared here". */
    record Entry(Boolean rightToLeft, Double fontScale) {
    }

    public boolean isRightToLeft(Locale locale) {
        if (locale == null) {
            return false;
        }
        Boolean override = overriddenDirection(locale);
        if (override != null) {
            return override;
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
        Double override = overriddenFontScale(locale);
        if (override != null) {
            return override;
        }
        return declared(locale, Entry::fontScale, DEFAULT_FONT_SCALE);
    }

    /** The exact tag first, then the language alone, as the shipped file is also matched. */
    private static List<String> keysFor(Locale locale, String prefix) {
        String tag = locale.toLanguageTag();
        String language = locale.getLanguage();
        return tag.equalsIgnoreCase(language)
                ? List.of(prefix + language)
                : List.of(prefix + tag, prefix + language);
    }

    private Boolean overriddenDirection(Locale locale) {
        for (String key : keysFor(locale, DIRECTION_PROPERTY)) {
            Optional<String> configured = read(key);
            if (configured.isEmpty()) {
                continue;
            }
            String direction = configured.get().trim();
            if ("rtl".equalsIgnoreCase(direction) || "ltr".equalsIgnoreCase(direction)) {
                return "rtl".equalsIgnoreCase(direction);
            }
            LOG.warnf("%s is \"%s\"; expected \"rtl\" or \"ltr\", so it is ignored", key, direction);
        }
        return null;
    }

    private Double overriddenFontScale(Locale locale) {
        for (String key : keysFor(locale, FONT_SCALE_PROPERTY)) {
            Optional<String> configured = read(key);
            if (configured.isEmpty()) {
                continue;
            }
            try {
                double scale = Double.parseDouble(configured.get().trim());
                if (scale >= MIN_FONT_SCALE && scale <= MAX_FONT_SCALE) {
                    return scale;
                }
                LOG.warnf("%s is %s, outside %s..%s, so it is ignored",
                        key, scale, MIN_FONT_SCALE, MAX_FONT_SCALE);
            } catch (NumberFormatException e) {
                LOG.warnf("%s is \"%s\", which is not a number, so it is ignored",
                        key, configured.get());
            }
        }
        return null;
    }

    /** A lookup must never take the page down over a misconfigured property. */
    private Optional<String> read(String key) {
        try {
            Optional<String> value = propertyLookup.apply(key);
            return value == null || value.isEmpty() || value.get().isBlank()
                    ? Optional.empty() : value;
        } catch (RuntimeException e) {
            LOG.warnf(e, "Cannot read %s", key);
            return Optional.empty();
        }
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

    /** The shipped manifest, or no entries when the image carries none or it cannot be read. */
    private Map<String, Entry> load() {
        try (InputStream in = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream("META-INF/i18n/" + CONFIG_FILE)) {
            return in == null
                    ? Collections.emptyMap()
                    : parse(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
        } catch (IOException e) {
            LOG.warn("Cannot read classpath i18n-config.json", e);
            return Collections.emptyMap();
        }
    }

    /**
     * The {@code locales} entries of a manifest, by lower-cased tag.
     * <p>
     * Separate from {@link #load()} so the parsing has a seam. The manifest is a fixed classpath
     * resource now, so there is no longer any way to feed this a malformed or surprising document
     * through the public API -- and it still runs at every startup, where a bad one must leave the
     * built-in defaults standing rather than take the application down.
     *
     * @param json the manifest's contents
     * @return the usable entries, empty when the document is malformed or declares none
     */
    static Map<String, Entry> parse(String json) {
        if (json == null || json.isBlank()) {
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
