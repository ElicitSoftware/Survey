package com.elicitsoftware.i18n;

/*-
 * ***LICENSE_START***
 * Elicit Survey
 * %%
 * Copyright (C) 2025 - 2026 The Regents of the University of Michigan - Rogel Cancer Center
 * %%
 * PolyForm Noncommercial License 1.0.0
 * ***LICENSE_END***
 */

import com.vaadin.quarkus.annotation.VaadinServiceEnabled;
import com.elicitsoftware.model.Question;
import com.elicitsoftware.model.Relationship;
import com.elicitsoftware.model.ReportDefinition;
import com.elicitsoftware.model.Section;
import com.elicitsoftware.model.SelectItem;
import com.elicitsoftware.model.Step;
import com.elicitsoftware.model.Survey;
import com.elicitsoftware.model.Translation;
import io.quarkus.logging.Log;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Serves a survey's content in the respondent's language (docs/research/i18n_survey.md section 4.1;
 * Survey UC-009 BR-005, BR-008, BR-009, BR-010).
 * <p>
 * This is the only place the session locale is consulted for content. Everything it returns is
 * either a translation that applies or the base text; nothing else in the runtime has to know which
 * it got. Three conditions must all hold before a translation is served:
 * <ol>
 *   <li>the language applies -- it is one of the survey's {@code content_languages} <em>and</em> is
 *       mounted for the chrome at this site, so no respondent reads translated questions between
 *       base-language buttons (BR-009);</li>
 *   <li>a row is effective at the respondent's as-of instant, the same half-open test the
 *       structural finders use, so a respondent keeps the wording they started under (BR-010);</li>
 *   <li>its {@code source_hash} still matches the element's base text. A rewritten question shown
 *       with its pre-rewrite translation is worse than showing English, so a stale translation is
 *       not served (BR-008) unless a deployment opts into continuity instead.</li>
 * </ol>
 * <p>
 * Deliberately a service and not a {@code @PostLoad} with transient fields. Overwriting a mapped
 * field on a managed entity inside {@code QuestionService.init}'s transaction would flush
 * translated text back into {@code questions.text}; transient projections avoid that but still cost
 * a lazy select per entity, bake the load-time language into entities that outlive a language
 * switch, and put UI-thread logic into classes the ETL and the migrator share. Here the entities
 * stay pure, the whole survey loads in one query, and the class is testable without Vaadin.
 * <p>
 * Off the UI thread -- the ETL, the migrator, the test suite -- {@code currentLocale()} is English,
 * {@link #language} is {@code null}, and every accessor returns base text. Those callers see no
 * change at all.
 */
@ApplicationScoped
public class ContentTranslator {

    /** Element types, as {@code survey.translations.element_type} spells them. */
    public static final String SURVEYS = "surveys";
    public static final String STEPS = "steps";
    public static final String SECTIONS = "sections";
    public static final String QUESTIONS = "questions";
    public static final String SELECT_ITEMS = "select_items";
    public static final String RELATIONSHIPS = "relationships";
    public static final String REPORTS = "reports";

    @Inject
    @VaadinServiceEnabled
    ElicitI18NProvider provider;

    /**
     * Whether to serve a translation whose base text has since been edited. False by default: a
     * question that was rewritten and shown with its old translation asks a different question in
     * that language, which is worse than falling back to the base text. A deployment that prefers
     * continuity can turn it on.
     */
    @ConfigProperty(name = "elicit.content.serve-stale-translations", defaultValue = "false")
    boolean serveStale;

    /**
     * How long a survey's translations are held before being reloaded. An Admin publish therefore
     * surfaces within this window without a restart.
     */
    @ConfigProperty(name = "elicit.content.translations-ttl", defaultValue = "PT5M")
    Duration ttl;

    private final Map<Integer, Snapshot> cache = new ConcurrentHashMap<>();

    /** {@code effective_to} of a current row, as the schema writes it. */
    public static OffsetDateTime sentinel() {
        return com.elicitsoftware.model.Translation.SENTINEL;
    }

    /** One survey's translations, every language and every version, as loaded. */
    private record Snapshot(OffsetDateTime loadedAt, Map<Target, List<Row>> rows) {
    }

    private record Target(UUID targetKey, String field, String language) {
    }

    private record Row(String value, String sourceHash, OffsetDateTime from, OffsetDateTime to) {
        boolean covers(OffsetDateTime asOf) {
            return !from.isAfter(asOf) && to.isAfter(asOf);
        }
    }

    @PostConstruct
    void logPolicy() {
        Log.debugf("Content translations: ttl=%s, serve-stale=%s", ttl, serveStale);
    }

    /**
     * The content language in force for this survey and this session, or {@code null} when content
     * must be shown in the survey's base language.
     * <p>
     * Resolves the session locale against the intersection of the survey's published set and the
     * languages this site mounts for its own texts, exact tag first and then same-language, the
     * same chain {@code LocaleSelection.resolve} uses for the chrome.
     *
     * @param survey the survey being answered
     * @return the BCP-47 tag to serve content in, or {@code null} for the base language
     */
    public String language(Survey survey) {
        if (survey == null || survey.contentLanguages == null || survey.contentLanguages.isBlank()) {
            return null;
        }
        Locale current = Translations.currentLocale();
        if (current == null) {
            return null;
        }
        String base = survey.baseLanguage == null ? "en" : survey.baseLanguage;
        List<String> published = Arrays.stream(survey.contentLanguages.split(","))
                .map(String::trim).filter(t -> !t.isEmpty()).toList();
        for (String tag : published) {
            if (tag.equalsIgnoreCase(base)) {
                continue;
            }
            Locale candidate = Locale.forLanguageTag(tag);
            if (candidate.getLanguage().isEmpty() || !provider.isProvided(candidate)) {
                // Published for the survey but not mounted for the chrome here: this site has not
                // adopted the language, so it holds the rows without ever serving them (BR-009).
                continue;
            }
            if (candidate.equals(current) || candidate.getLanguage().equals(current.getLanguage())) {
                return tag;
            }
        }
        return null;
    }

    /**
     * The translated text for one field of one element, or empty when the base text must be shown.
     *
     * @param survey    the survey being answered
     * @param targetKey the element key of the element being translated
     * @param field     the base column's name
     * @param baseText  the element's base text, against which staleness is decided
     * @param asOf      the respondent's snapshot anchor; required, never optional
     * @return the translation to show, or empty
     */
    public Optional<String> get(Survey survey, UUID targetKey, String field, String baseText, OffsetDateTime asOf) {
        String language = language(survey);
        if (language == null || targetKey == null || field == null || asOf == null) {
            return Optional.empty();
        }
        return get(survey, language, targetKey, field, baseText, asOf);
    }

    /** As {@link #get}, with the language already resolved -- the bulk path through a page draw. */
    public Optional<String> get(Survey survey, String language, UUID targetKey, String field,
                                String baseText, OffsetDateTime asOf) {
        if (language == null || targetKey == null || field == null || asOf == null) {
            return Optional.empty();
        }
        List<Row> candidates = snapshot(survey.id).rows().get(new Target(targetKey, field, language));
        if (candidates == null) {
            return Optional.empty();
        }
        for (Row row : candidates) {
            if (!row.covers(asOf)) {
                continue;
            }
            if (!serveStale && !ContentHash.matches(row.sourceHash(), baseText)) {
                return Optional.empty();
            }
            return Optional.of(row.value());
        }
        return Optional.empty();
    }

    /** Drops a survey's cached translations, so the next read reloads them. */
    public void invalidate(Integer surveyId) {
        cache.remove(surveyId);
    }

    private Snapshot snapshot(Integer surveyId) {
        Snapshot held = cache.get(surveyId);
        if (held != null && held.loadedAt().plus(ttl).isAfter(OffsetDateTime.now())) {
            return held;
        }
        Snapshot loaded = load(surveyId);
        cache.put(surveyId, loaded);
        return loaded;
    }

    @Transactional
    Snapshot load(Integer surveyId) {
        Map<Target, List<Row>> rows = new HashMap<>();
        for (Translation t : Translation.findBySurvey(surveyId)) {
            rows.computeIfAbsent(new Target(t.targetKey, t.field, t.language), k -> new ArrayList<>())
                    .add(new Row(t.value, t.sourceHash, t.effectiveFrom, t.effectiveTo));
        }
        return new Snapshot(OffsetDateTime.now(), rows);
    }

    // ---------------------------------------------------------------------
    // Typed accessors. Each returns the base field when nothing applies, so a
    // caller never has to ask whether a translation existed.
    // ---------------------------------------------------------------------

    public String text(Survey survey, Question question, OffsetDateTime asOf) {
        return question == null ? null
                : or(get(survey, question.questionKey, "text", question.text, asOf), question.text);
    }

    public String shortText(Survey survey, Question question, OffsetDateTime asOf) {
        return question == null ? null
                : or(get(survey, question.questionKey, "short_text", question.shortText, asOf), question.shortText);
    }

    public String toolTip(Survey survey, Question question, OffsetDateTime asOf) {
        return question == null ? null
                : or(get(survey, question.questionKey, "tool_tip", question.toolTip, asOf), question.toolTip);
    }

    public String placeholder(Survey survey, Question question, OffsetDateTime asOf) {
        return question == null ? null
                : or(get(survey, question.questionKey, "placeholder", question.placeholder, asOf), question.placeholder);
    }

    public String validationText(Survey survey, Question question, OffsetDateTime asOf) {
        return question == null ? null
                : or(get(survey, question.questionKey, "validation_text", question.validationText, asOf),
                        question.validationText);
    }

    public String displayText(Survey survey, SelectItem item, OffsetDateTime asOf) {
        return item == null ? null
                : or(get(survey, item.selectItemKey, "display_text", item.displayText, asOf), item.displayText);
    }

    public String name(Survey survey, Step step, OffsetDateTime asOf) {
        return step == null ? null : or(get(survey, step.stepKey, "name", step.name, asOf), step.name);
    }

    public String name(Survey survey, Section section, OffsetDateTime asOf) {
        return section == null ? null : or(get(survey, section.sectionKey, "name", section.name, asOf), section.name);
    }

    public String title(Survey survey, OffsetDateTime asOf) {
        return survey == null ? null : or(get(survey, survey.surveyKey, "title", survey.title, asOf), survey.title);
    }

    public String description(Survey survey, OffsetDateTime asOf) {
        return survey == null ? null
                : or(get(survey, survey.surveyKey, "description", survey.description, asOf), survey.description);
    }

    public String name(Survey survey, ReportDefinition report, OffsetDateTime asOf) {
        return report == null ? null : or(get(survey, report.reportKey, "name", report.name, asOf), report.name);
    }

    public String defaultUpstreamValue(Survey survey, Relationship relationship, OffsetDateTime asOf) {
        return relationship == null ? null
                : or(get(survey, relationship.relationshipKey, "default_upstream_value",
                        relationship.defaultUpstreamValue, asOf), relationship.defaultUpstreamValue);
    }

    private static String or(Optional<String> translated, String base) {
        return translated.orElse(base);
    }
}
