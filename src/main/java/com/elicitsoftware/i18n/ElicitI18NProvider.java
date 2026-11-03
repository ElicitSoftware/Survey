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
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
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
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Translation provider for the application chrome (UC-009).
 * <p>
 * The image carries English only, in Vaadin's standard layout -- {@code vaadin-i18n/translations.properties}
 * on the classpath -- so the Copilot internationalization tooling keeps working. Every other language
 * is supplied by the deployment: {@code translations_<tag>.properties} files in this application's
 * sub-directory of the translations mount, {@code <i18n.file.system.path>/<i18n.app.name>/}
 * ({@code /i18n/survey/} by default). One host directory can serve every module that way.
 * <p>
 * A mounted file overlays the classpath key by key, so it may hold only the keys it changes, and a key
 * missing from a language falls back to English. The languages offered are English plus every language
 * mounted here; {@code i18n.offered.locales}, when set, narrows that to the tags it lists, and a tag it
 * lists that has no mounted file is not offered. {@code LocaleSelection.resolve} checks every requested
 * tag against {@link #getProvidedLocales()}, so a narrowed site cannot be talked past it with
 * {@code ?lang=}.
 * <p>
 * Lookup order for a key: exact locale, language-only locale, default (English) bundle, then the
 * visible marker {@code !key!}. The
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

    /** The translations mount; this application reads its own sub-directory of it. */
    @ConfigProperty(name = "i18n.file.system.path", defaultValue = "/i18n")
    String fileSystemPath = "/i18n";

    /** This application's sub-directory of the mount. */
    @ConfigProperty(name = "i18n.app.name", defaultValue = "survey")
    String appName = "survey";

    /**
     * The languages this site offers, when it wants fewer than are mounted: a comma-separated list
     * of tags. Unset or blank offers every mounted language. English is always offered, because it
     * is the fallback for every key.
     */
    @ConfigProperty(name = "i18n.offered.locales")
    Optional<String> offeredLocales = Optional.empty();

    @ConfigProperty(name = "i18n.pseudo-locale.enabled", defaultValue = "false")
    boolean pseudoLocaleEnabled;

    private final Map<Locale, Map<String, String>> bundles = new ConcurrentHashMap<>();
    private final Set<String> reportedMissing = ConcurrentHashMap.newKeySet();
    private volatile List<Locale> providedLocales;

    /**
     * A provider built outside CDI that still honours the deployment's settings: the injected
     * fields are only set on the container's bean, so a bare {@code new} would read the default
     * mount whatever the site configured. A setting that cannot be read keeps its default.
     */
    static ElicitI18NProvider fromConfig() {
        ElicitI18NProvider p = new ElicitI18NProvider();
        try {
            Config config = ConfigProvider.getConfig();
            config.getOptionalValue("i18n.file.system.path", String.class).ifPresent(v -> p.fileSystemPath = v);
            config.getOptionalValue("i18n.app.name", String.class).ifPresent(v -> p.appName = v);
            p.offeredLocales = config.getOptionalValue("i18n.offered.locales", String.class);
        } catch (RuntimeException e) {
            LOG.warn("Cannot read the i18n settings; using the defaults", e);
        }
        return p;
    }

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

    /** True when this site offers the locale, or its language. */
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

    /** The classpath bundle for this locale, overlaid key by key by the mounted file of the same name. */
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
        Path file = mountDirectory().resolve(fileName);
        if (Files.isRegularFile(file)) {
            try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                merged.putAll(read(r));
                LOG.infof("Loaded %d translation(s) for '%s' from %s", merged.size(), locale.toLanguageTag(), file);
            } catch (IOException e) {
                LOG.warnf(e, "Cannot read translation file %s", file);
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

    /** {@code <i18n.file.system.path>/<i18n.app.name>}; the app name may not climb out of the mount. */
    Path mountDirectory() {
        String app = appName == null ? "" : appName.trim();
        if (app.isEmpty() || app.contains("..") || app.contains("/") || app.contains("\\")) {
            throw new IllegalArgumentException("i18n.app.name must be a plain directory name, got '" + app + "'");
        }
        String root = fileSystemPath == null || fileSystemPath.isBlank() ? "/i18n" : fileSystemPath.trim();
        return Paths.get(root, app);
    }

    /** The languages with a {@code translations_<tag>.properties} in the mount, in no particular order. */
    private Set<Locale> mountedLocales() {
        Set<Locale> mounted = new LinkedHashSet<>();
        Path dir = mountDirectory();
        if (!Files.isDirectory(dir)) {
            LOG.infof("No translations mounted at %s; offering English only", dir);
            return mounted;
        }
        try (Stream<Path> files = Files.list(dir)) {
            files.map(p -> p.getFileName().toString())
                    .map(LOCALE_FILE::matcher)
                    .filter(Matcher::matches)
                    .map(m -> Locale.forLanguageTag(m.group(1).replace('_', '-')))
                    .filter(l -> !l.getLanguage().isEmpty())
                    .forEach(mounted::add);
        } catch (IOException e) {
            LOG.warnf(e, "Cannot list translation directory %s", dir);
        }
        return mounted;
    }

    /** The tags {@code i18n.offered.locales} lists, or empty when it is unset or blank. */
    private Optional<Set<Locale>> offeredFilter() {
        if (offeredLocales == null || offeredLocales.isEmpty() || offeredLocales.get().isBlank()) {
            return Optional.empty();
        }
        return Optional.of(Arrays.stream(offeredLocales.get().split(","))
                .map(String::trim)
                .filter(t -> !t.isEmpty())
                .map(Locale::forLanguageTag)
                .filter(l -> !l.getLanguage().isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new)));
    }

    private List<Locale> discoverLocales() {
        Set<Locale> mounted = mountedLocales();
        Optional<Set<Locale>> filter = offeredFilter();
        Set<Locale> found = new LinkedHashSet<>();
        found.add(DEFAULT_LOCALE);
        for (Locale locale : mounted) {
            if (filter.isEmpty() || filter.get().contains(locale)) {
                found.add(locale);
            }
        }
        filter.ifPresent(wanted -> wanted.stream()
                .filter(l -> !l.equals(DEFAULT_LOCALE) && !mounted.contains(l))
                .forEach(l -> LOG.warnf("i18n.offered.locales lists %s, but %s holds no %s; it is not offered",
                        l.toLanguageTag(), mountDirectory(), fileNameFor(l))));
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
