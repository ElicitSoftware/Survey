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

import com.vaadin.flow.i18n.I18NProvider;
import com.vaadin.quarkus.annotation.VaadinServiceEnabled;
import io.quarkus.arc.Unremovable;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Translation provider for the application chrome (UC-009).
 * <p>
 * Translations live in Vaadin's standard layout — {@code vaadin-i18n/translations[_tag].properties}
 * on the classpath — so the Copilot internationalization tooling keeps working, and are overlaid
 * per key by the same three-tier scheme the brand system uses: an externally mounted directory
 * ({@code i18n.file.system.path}, {@code /opt/i18n} in Docker) wins over a local development
 * directory ({@code i18n.local.path}), which wins over the classpath. Each application reads its
 * own sub-directory ({@code <mount>/<i18n.app.name>/}) so one host directory can serve every module.
 * <p>
 * Lookup order for a key: exact locale, language-only locale, default (English) bundle, then the
 * visible marker {@code !key!}. A locale that exists only on the mount is offered as well. The
 * pseudo-locale {@code zxx} (enabled in tests) renders every known key as {@code ⟦key⟧} so a
 * browserless sweep can spot text that bypasses the provider.
 */
@VaadinServiceEnabled
@ApplicationScoped
@Unremovable
public class ElicitI18NProvider implements I18NProvider {

    private static final Logger LOG = Logger.getLogger(ElicitI18NProvider.class);

    public static final String BUNDLE_FOLDER = "vaadin-i18n";
    public static final String BUNDLE_PREFIX = "translations";
    public static final Locale DEFAULT_LOCALE = Locale.ENGLISH;
    /** ISO 639-2 "no linguistic content": the pseudo-locale used by the displayed-string sweep. */
    public static final Locale PSEUDO_LOCALE = Locale.forLanguageTag("zxx");
    public static final String PSEUDO_OPEN = "⟦";
    public static final String PSEUDO_CLOSE = "⟧";

    private static final Pattern LOCALE_FILE = Pattern.compile(BUNDLE_PREFIX + "_([A-Za-z0-9_]+)\\.properties");

    @ConfigProperty(name = "i18n.file.system.path", defaultValue = "/i18n")
    String fileSystemPath = "/i18n";

    @ConfigProperty(name = "i18n.local.path", defaultValue = "i18n")
    String localPath = "i18n";

    @ConfigProperty(name = "i18n.app.name", defaultValue = "survey")
    String appName = "survey";

    @ConfigProperty(name = "i18n.bundled.locales", defaultValue = "en")
    String bundledLocales = "en";

    @ConfigProperty(name = "i18n.pseudo-locale.enabled", defaultValue = "false")
    boolean pseudoLocaleEnabled;

    private final Map<Locale, Map<String, String>> bundles = new ConcurrentHashMap<>();
    private final Set<String> reportedMissing = ConcurrentHashMap.newKeySet();
    private volatile List<Locale> providedLocales;

    @Override
    public List<Locale> getProvidedLocales() {
        List<Locale> locales = providedLocales;
        if (locales == null) {
            locales = discoverLocales();
            providedLocales = locales;
        }
        return locales;
    }

    @Override
    public Locale getDefaultLocale() {
        return DEFAULT_LOCALE;
    }

    @Override
    public String getTranslation(String key, Locale locale, Object... params) {
        if (key == null) {
            LOG.warn("Translation requested for a null key");
            return "";
        }
        Locale effective = locale == null ? DEFAULT_LOCALE : locale;
        if (PSEUDO_LOCALE.getLanguage().equals(effective.getLanguage())) {
            return bundle(DEFAULT_LOCALE).containsKey(key) ? PSEUDO_OPEN + key + PSEUDO_CLOSE : missing(key);
        }
        String value = lookup(key, effective);
        if (value == null) {
            return missing(key);
        }
        if (params != null && params.length > 0) {
            return new MessageFormat(value, effective).format(params);
        }
        return value;
    }

    @Override
    public Map<String, String> getAllTranslations(Locale locale) {
        Locale effective = locale == null ? DEFAULT_LOCALE : locale;
        Map<String, String> all = new LinkedHashMap<>(bundle(DEFAULT_LOCALE));
        if (PSEUDO_LOCALE.getLanguage().equals(effective.getLanguage())) {
            all.replaceAll((k, v) -> PSEUDO_OPEN + k + PSEUDO_CLOSE);
            return all;
        }
        Locale languageOnly = languageOnly(effective);
        if (!languageOnly.equals(effective)) {
            all.putAll(bundle(languageOnly));
        }
        if (!effective.equals(DEFAULT_LOCALE)) {
            all.putAll(bundle(effective));
        }
        return all;
    }

    /** Forgets every loaded bundle and the discovered locale list; the next lookup reloads. */
    public void clearCache() {
        bundles.clear();
        providedLocales = null;
        reportedMissing.clear();
    }

    /** True when the locale (or its language) has a translation file on any tier. */
    public boolean isProvided(Locale locale) {
        return getProvidedLocales().contains(locale);
    }

    private String lookup(String key, Locale locale) {
        String value = bundle(locale).get(key);
        if (value == null) {
            Locale languageOnly = languageOnly(locale);
            if (!languageOnly.equals(locale)) {
                value = bundle(languageOnly).get(key);
            }
        }
        if (value == null && !DEFAULT_LOCALE.equals(languageOnly(locale))) {
            value = bundle(DEFAULT_LOCALE).get(key);
        }
        return value;
    }

    private String missing(String key) {
        if (reportedMissing.add(key)) {
            LOG.warnf("No translation for key '%s' in any bundle", key);
        }
        return "!" + key + "!";
    }

    private static Locale languageOnly(Locale locale) {
        return Locale.forLanguageTag(locale.getLanguage());
    }

    private Map<String, String> bundle(Locale locale) {
        return bundles.computeIfAbsent(locale, this::loadBundle);
    }

    /** Classpath, then local directory, then external mount — later tiers override per key. */
    private Map<String, String> loadBundle(Locale locale) {
        String fileName = fileNameFor(locale);
        Map<String, String> merged = new LinkedHashMap<>();
        try (InputStream in = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream(BUNDLE_FOLDER + "/" + fileName)) {
            if (in != null) {
                merged.putAll(read(new InputStreamReader(in, StandardCharsets.UTF_8)));
            }
        } catch (IOException e) {
            LOG.warnf(e, "Cannot read classpath bundle %s", fileName);
        }
        for (Path dir : tierDirectories()) {
            Path file = dir.resolve(fileName);
            if (Files.isRegularFile(file)) {
                try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    merged.putAll(read(r));
                    LOG.infof("Loaded %d translation(s) for '%s' from %s", merged.size(), locale.toLanguageTag(), file);
                } catch (IOException e) {
                    LOG.warnf(e, "Cannot read translation file %s", file);
                }
            }
        }
        return Collections.unmodifiableMap(merged);
    }

    private static Map<String, String> read(Reader reader) throws IOException {
        Properties props = new Properties();
        props.load(reader);
        Map<String, String> map = new LinkedHashMap<>();
        props.forEach((k, v) -> map.put(k.toString(), v.toString()));
        return map;
    }

    static String fileNameFor(Locale locale) {
        if (locale == null || locale.equals(DEFAULT_LOCALE) || locale.getLanguage().isEmpty()) {
            return BUNDLE_PREFIX + ".properties";
        }
        return BUNDLE_PREFIX + "_" + locale.toLanguageTag().replace('-', '_') + ".properties";
    }

    /** Local directory first, external mount last, so the mount has the final word. */
    private List<Path> tierDirectories() {
        String app = appName == null ? "" : appName.trim();
        if (app.isEmpty() || app.contains("..") || app.contains("/") || app.contains("\\")) {
            throw new IllegalArgumentException("i18n.app.name must be a plain directory name, got '" + app + "'");
        }
        List<Path> dirs = new ArrayList<>(2);
        if (localPath != null && !localPath.isBlank()) {
            dirs.add(Paths.get(localPath, app));
        }
        if (fileSystemPath != null && !fileSystemPath.isBlank()) {
            dirs.add(Paths.get(fileSystemPath, app));
        }
        return dirs;
    }

    private List<Locale> discoverLocales() {
        Set<Locale> found = new LinkedHashSet<>();
        found.add(DEFAULT_LOCALE);
        if (bundledLocales != null) {
            Arrays.stream(bundledLocales.split(","))
                    .map(String::trim)
                    .filter(t -> !t.isEmpty())
                    .map(Locale::forLanguageTag)
                    .filter(l -> !l.getLanguage().isEmpty())
                    .forEach(found::add);
        }
        for (Path dir : tierDirectories()) {
            if (!Files.isDirectory(dir)) {
                continue;
            }
            try (Stream<Path> files = Files.list(dir)) {
                files.map(p -> p.getFileName().toString())
                        .map(LOCALE_FILE::matcher)
                        .filter(Matcher::matches)
                        .map(m -> Locale.forLanguageTag(m.group(1).replace('_', '-')))
                        .filter(l -> !l.getLanguage().isEmpty())
                        .forEach(found::add);
            } catch (IOException e) {
                LOG.warnf(e, "Cannot list translation directory %s", dir);
            }
        }
        if (pseudoLocaleEnabled) {
            found.add(PSEUDO_LOCALE);
        }
        List<Locale> ordered = new ArrayList<>(found);
        ordered.sort((a, b) -> {
            if (a.equals(DEFAULT_LOCALE)) return -1;
            if (b.equals(DEFAULT_LOCALE)) return 1;
            return a.toLanguageTag().compareTo(b.toLanguageTag());
        });
        return Collections.unmodifiableList(ordered);
    }
}
