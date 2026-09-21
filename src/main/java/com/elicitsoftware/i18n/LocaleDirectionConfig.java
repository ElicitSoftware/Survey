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

/**
 * Text direction per locale (UC-008 BR-004). Right-to-left is inferred from the language for the
 * well-known RTL scripts; a deployment can override or extend that for a mounted locale through an
 * optional {@code i18n-config.json} at the translations mount root (falling back to the local
 * directory and then to {@code META-INF/i18n/i18n-config.json} on the classpath):
 * <pre>{"locales": [{"tag": "ar", "direction": "rtl"}]}</pre>
 */
@ApplicationScoped
public class LocaleDirectionConfig {

    private static final Logger LOG = Logger.getLogger(LocaleDirectionConfig.class);
    static final String CONFIG_FILE = "i18n-config.json";
    static final Set<String> RTL_LANGUAGES = Set.of("ar", "he", "fa", "ur", "ps", "sd", "ug", "yi", "dv", "ckb");

    @ConfigProperty(name = "i18n.file.system.path", defaultValue = "/i18n")
    String fileSystemPath = "/i18n";

    @ConfigProperty(name = "i18n.local.path", defaultValue = "i18n")
    String localPath = "i18n";

    private volatile Map<String, Boolean> overrides;

    public boolean isRightToLeft(Locale locale) {
        if (locale == null) {
            return false;
        }
        Map<String, Boolean> map = overrides();
        Boolean byTag = map.get(locale.toLanguageTag().toLowerCase(Locale.ROOT));
        if (byTag != null) {
            return byTag;
        }
        Boolean byLanguage = map.get(locale.getLanguage().toLowerCase(Locale.ROOT));
        if (byLanguage != null) {
            return byLanguage;
        }
        return RTL_LANGUAGES.contains(locale.getLanguage());
    }

    public void clearCache() {
        overrides = null;
    }

    private Map<String, Boolean> overrides() {
        Map<String, Boolean> map = overrides;
        if (map == null) {
            map = load();
            overrides = map;
        }
        return map;
    }

    private Map<String, Boolean> load() {
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
        Map<String, Boolean> map = new HashMap<>();
        try {
            JsonNode root = new ObjectMapper().readTree(json);
            JsonNode locales = root.path("locales");
            for (JsonNode entry : locales) {
                String tag = entry.path("tag").asText("");
                String direction = entry.path("direction").asText("");
                if (!tag.isEmpty() && ("rtl".equalsIgnoreCase(direction) || "ltr".equalsIgnoreCase(direction))) {
                    map.put(tag.toLowerCase(Locale.ROOT), "rtl".equalsIgnoreCase(direction));
                }
            }
        } catch (IOException e) {
            LOG.warn("Malformed i18n-config.json; using built-in direction defaults", e);
        }
        return Collections.unmodifiableMap(map);
    }
}
