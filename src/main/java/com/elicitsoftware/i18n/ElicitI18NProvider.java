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

/**
 * Translation provider for the application chrome (UC-009).
 * <p>
 * Translations live in Vaadin's standard layout — {@code vaadin-i18n/translations[_tag].properties}
 * on the classpath — so the Copilot internationalization tooling keeps working. The English bundle
 * is authored under {@code src/main/resources}; the translated ones are received from a translator,
 * kept in the module's {@code i18n/} directory beside the hand-off document that produced them, and
 * packaged to the same classpath location by the build.
 * <p>
 * The classpath is the only source. Languages are curated and arrive in a release, so a deployment
 * can neither add one nor patch one, and a released image renders the translation it was built and
 * tested with. What a site chooses is <em>which</em> of the shipped languages it offers, through
 * {@code i18n.bundled.locales}; the rest stay in the image unreachable.
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


    /**
     * The languages this image carries, and the ones this deployment offers.
     * <p>
     * Both, because classpath resources cannot be listed: nothing can discover which
     * {@code translations_*.properties} the jar holds, so the set has to be declared. A site that
     * wants fewer narrows this property, and a language left out of it is not offered even though
     * its bundle is still in the image. {@code LocaleSelection.resolve} checks every requested tag
     * against {@link #getProvidedLocales()}, so a narrowed site cannot be talked past it with
     * {@code ?lang=}.
     */
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
