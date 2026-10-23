package com.elicitsoftware.i18n;

/*-
 * ***LICENSE_START***
 * Elicit Survey
 * %%
 * Copyright (C) 2025 - 2026 The Regents of the University of Michigan - Rogel Cancer Center
 * %%
 * PolyForm Noncommercial License 1.0.0
 * <https://polyformproject.org/licenses/noncommercial/1.0.0>
 * ***LICENSE_END***
 */

import com.elicitsoftware.model.Question;
import com.elicitsoftware.model.Relationship;
import com.elicitsoftware.model.ReportDefinition;
import com.elicitsoftware.model.Section;
import com.elicitsoftware.model.SelectItem;
import com.elicitsoftware.model.Step;
import com.elicitsoftware.model.Survey;
import com.elicitsoftware.model.Translation;
import com.vaadin.flow.component.UI;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UC-009: Complete the Survey in My Language -- when {@link ContentTranslator} serves a translation
 * and when it falls back to the base text (BR-005, BR-008, BR-009, BR-010), without a database.
 * <p>
 * The translator's snapshot cache is seeded directly, so the language resolution, the half-open
 * effective window, the staleness check and every typed accessor run as plain code. The language
 * comes from the current UI's locale, set here on a bare {@link UI}; the end-to-end path through the
 * database is covered by {@code ContentTranslatorTest}.
 */
class ContentTranslatorUnitTest {

    private static final String SPANISH = "es-419";
    private static final OffsetDateTime T0 = OffsetDateTime.parse("2026-01-01T00:00:00Z");
    private static final OffsetDateTime T1 = OffsetDateTime.parse("2026-06-01T00:00:00Z");
    private static final OffsetDateTime T2 = OffsetDateTime.parse("2026-09-01T00:00:00Z");

    private ContentTranslator translator;
    private ElicitI18NProvider provider;
    private Survey survey;
    private final Map<Object, List<Object>> rows = new HashMap<>();

    @BeforeEach
    void setUp() {
        provider = new ElicitI18NProvider();
        provider.bundledLocales = "en,es-419,ar";
        translator = new ContentTranslator();
        translator.provider = provider;
        translator.ttl = Duration.ofMinutes(5);
        survey = new Survey();
        survey.id = 42;
        survey.surveyKey = UUID.randomUUID();
        survey.baseLanguage = "en";
        survey.contentLanguages = "en," + SPANISH + ",ar";
        uiLocale(Locale.forLanguageTag(SPANISH));
    }

    @AfterEach
    void tearDown() {
        UI.setCurrent(null);
    }

    private static void uiLocale(Locale locale) {
        UI ui = new UI();
        ui.setLocale(locale);
        UI.setCurrent(ui);
    }

    // ------------------------------------------------------------------ reflection helpers

    private static Class<?> nested(String simpleName) throws ClassNotFoundException {
        return Class.forName(ContentTranslator.class.getName() + "$" + simpleName);
    }

    private static Object construct(String simpleName, Object... args) throws Exception {
        Constructor<?> constructor = nested(simpleName).getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        return constructor.newInstance(args);
    }

    private void row(UUID key, String field, String language, String value, String baseText,
                     OffsetDateTime from, OffsetDateTime to) throws Exception {
        Object target = construct("Target", key, field, language);
        rows.computeIfAbsent(target, k -> new ArrayList<>())
                .add(construct("Row", value, ContentHash.of(baseText), from, to));
    }

    private void current(UUID key, String field, String value, String baseText) throws Exception {
        row(key, field, SPANISH, value, baseText, T0, Translation.SENTINEL);
    }

    @SuppressWarnings("unchecked")
    private Map<Integer, Object> cache() throws Exception {
        Field field = ContentTranslator.class.getDeclaredField("cache");
        field.setAccessible(true);
        return (Map<Integer, Object>) field.get(translator);
    }

    /** Installs the seeded rows as the survey's snapshot, loaded at {@code loadedAt}. */
    private void install(OffsetDateTime loadedAt) throws Exception {
        cache().put(survey.id, construct("Snapshot", loadedAt, rows));
    }

    private void install() throws Exception {
        install(OffsetDateTime.now());
    }

    // ------------------------------------------------------------------ language(Survey)

    @Test
    void given_noSurvey_when_resolvingLanguage_then_baseLanguage() {
        assertNull(translator.language(null));
    }

    @Test
    void given_surveyWithoutContentLanguages_when_resolvingLanguage_then_baseLanguage() {
        survey.contentLanguages = null;
        assertNull(translator.language(survey));
        survey.contentLanguages = "   ";
        assertNull(translator.language(survey));
    }

    @Test
    void given_exactPublishedAndOfferedTag_when_resolvingLanguage_then_thatTag() {
        assertEquals(SPANISH, translator.language(survey));
    }

    @Test
    void given_sameLanguageDifferentRegion_when_resolvingLanguage_then_publishedTag() {
        uiLocale(Locale.forLanguageTag("es-MX"));
        assertEquals(SPANISH, translator.language(survey));
    }

    @Test
    void given_laterPublishedTag_when_resolvingLanguage_then_loopReachesIt() {
        uiLocale(Locale.forLanguageTag("ar"));
        assertEquals("ar", translator.language(survey));
    }

    @Test
    void given_paddedAndEmptyEntries_when_resolvingLanguage_then_trimmedTagMatches() {
        survey.contentLanguages = " , " + SPANISH + " ,";
        assertEquals(SPANISH, translator.language(survey));
    }

    @Test
    void given_sessionInBaseLanguage_when_resolvingLanguage_then_baseLanguage() {
        uiLocale(Locale.ENGLISH);
        assertNull(translator.language(survey), "the base language is never served as a translation");
    }

    @Test
    void given_nullBaseLanguage_when_resolvingLanguage_then_englishIsTreatedAsBase() {
        survey.baseLanguage = null;
        survey.contentLanguages = "en";
        uiLocale(Locale.ENGLISH);
        assertNull(translator.language(survey), "a survey without a base language is an English survey");
    }

    @Test
    void given_publishedButNotOfferedBySite_when_resolvingLanguage_then_baseLanguage() {
        provider.bundledLocales = "en,ar";
        assertNull(translator.language(survey), "BR-009: a language the site does not offer is not served");
    }

    @Test
    void given_sessionLanguageNotPublished_when_resolvingLanguage_then_baseLanguage() {
        uiLocale(Locale.FRENCH);
        provider.bundledLocales = "en,es-419,ar,fr";
        assertNull(translator.language(survey));
    }

    @Test
    void given_privateUseTagWithoutLanguage_when_resolvingLanguage_then_skipped() {
        survey.contentLanguages = "x-private";
        assertNull(translator.language(survey));
    }

    // ------------------------------------------------------------------ get(...)

    @Test
    void given_currentFaithfulTranslation_when_getting_then_served() throws Exception {
        UUID key = UUID.randomUUID();
        current(key, "text", "Hola", "Hello");
        install();

        assertEquals(Optional.of("Hola"), translator.get(survey, key, "text", "Hello", T1));
    }

    @Test
    void given_noContentLanguage_when_getting_then_empty() throws Exception {
        UUID key = UUID.randomUUID();
        current(key, "text", "Hola", "Hello");
        install();
        uiLocale(Locale.ENGLISH);

        assertEquals(Optional.empty(), translator.get(survey, key, "text", "Hello", T1));
    }

    @Test
    void given_missingArguments_when_getting_then_empty() throws Exception {
        UUID key = UUID.randomUUID();
        current(key, "text", "Hola", "Hello");
        install();

        assertEquals(Optional.empty(), translator.get(survey, null, "text", "Hello", T1));
        assertEquals(Optional.empty(), translator.get(survey, key, null, "Hello", T1));
        assertEquals(Optional.empty(), translator.get(survey, key, "text", "Hello", null));
    }

    @Test
    void given_rowsKeyedOnNulls_when_gettingWithNullArguments_then_neverLookedUp() throws Exception {
        UUID key = UUID.randomUUID();
        current(null, "text", "null key", "Hello");
        current(key, null, "null field", "Hello");
        row(key, "text", null, "null language", "Hello", T0, Translation.SENTINEL);
        install();

        assertEquals(Optional.empty(), translator.get(survey, null, null, "text", "Hello", T1));
        assertEquals(Optional.empty(), translator.get(survey, SPANISH, null, "text", "Hello", T1));
        assertEquals(Optional.empty(), translator.get(survey, SPANISH, key, null, "Hello", T1));
        assertEquals(Optional.empty(), translator.get(survey, SPANISH, key, "text", "Hello", null));
    }

    @Test
    void given_noRowsForTarget_when_getting_then_empty() throws Exception {
        install();
        assertEquals(Optional.empty(), translator.get(survey, SPANISH, UUID.randomUUID(), "text", "Hello", T1));
    }

    @Test
    void given_versionedRows_when_gettingAsOf_then_rowEffectiveAtThatInstantIsServed() throws Exception {
        UUID key = UUID.randomUUID();
        row(key, "text", SPANISH, "Primera", "Hello", T0, T1);
        row(key, "text", SPANISH, "Segunda", "Hello", T1, Translation.SENTINEL);
        install();

        assertEquals(Optional.of("Primera"), translator.get(survey, SPANISH, key, "text", "Hello", T0),
                "BR-010: effective_from is inclusive");
        assertEquals(Optional.of("Segunda"), translator.get(survey, SPANISH, key, "text", "Hello", T1),
                "BR-010: effective_to is exclusive, so the instant belongs to the next version");
        assertEquals(Optional.of("Segunda"), translator.get(survey, SPANISH, key, "text", "Hello", T2));
    }

    @Test
    void given_onlyFutureOrExpiredRows_when_getting_then_empty() throws Exception {
        UUID key = UUID.randomUUID();
        row(key, "text", SPANISH, "Futura", "Hello", T1, T2);
        install();

        assertEquals(Optional.empty(), translator.get(survey, SPANISH, key, "text", "Hello", T0));
        assertEquals(Optional.empty(), translator.get(survey, SPANISH, key, "text", "Hello", T2));
    }

    @Test
    void given_staleTranslation_when_getting_then_notServed() throws Exception {
        UUID key = UUID.randomUUID();
        current(key, "text", "Hola", "Hello");
        install();

        assertEquals(Optional.empty(), translator.get(survey, SPANISH, key, "text", "Hello, rewritten", T1),
                "BR-008: a translation of different base text is not shown");
    }

    @Test
    void given_staleTranslationAndContinuityPolicy_when_getting_then_served() throws Exception {
        translator.serveStale = true;
        UUID key = UUID.randomUUID();
        current(key, "text", "Hola", "Hello");
        install();

        assertEquals(Optional.of("Hola"), translator.get(survey, SPANISH, key, "text", "Hello, rewritten", T1));
    }

    // ------------------------------------------------------------------ cache

    @Test
    void given_freshSnapshot_when_getting_then_notReloaded() throws Exception {
        UUID key = UUID.randomUUID();
        current(key, "text", "Hola", "Hello");
        install(OffsetDateTime.now().minusMinutes(1));
        Object held = cache().get(survey.id);

        assertEquals(Optional.of("Hola"), translator.get(survey, SPANISH, key, "text", "Hello", T1));
        assertSame(held, cache().get(survey.id));
    }

    /**
     * A reload goes to the database, which a plain test does not have: the attempt itself is the
     * evidence that the cached snapshot was not used.
     */
    @Test
    void given_expiredSnapshot_when_getting_then_reloaded() throws Exception {
        UUID key = UUID.randomUUID();
        current(key, "text", "Hola", "Hello");
        install(OffsetDateTime.now().minusMinutes(6));

        assertThrows(RuntimeException.class,
                () -> translator.get(survey, SPANISH, key, "text", "Hello", T1));
    }

    @Test
    void given_invalidatedSurvey_when_getting_then_reloaded() throws Exception {
        UUID key = UUID.randomUUID();
        current(key, "text", "Hola", "Hello");
        install();
        translator.invalidate(survey.id);

        assertTrue(cache().isEmpty());
        assertThrows(RuntimeException.class,
                () -> translator.get(survey, SPANISH, key, "text", "Hello", T1));
    }

    @Test
    void given_schema_when_askingForSentinel_then_currentRowEnd() {
        assertEquals(OffsetDateTime.parse("9999-12-31T23:59:59Z"), ContentTranslator.sentinel());
    }

    // ------------------------------------------------------------------ typed accessors

    @Test
    void given_nullElements_when_usingTypedAccessors_then_null() {
        assertNull(translator.text(survey, null, T1));
        assertNull(translator.shortText(survey, null, T1));
        assertNull(translator.toolTip(survey, null, T1));
        assertNull(translator.placeholder(survey, null, T1));
        assertNull(translator.validationText(survey, null, T1));
        assertNull(translator.displayText(survey, null, T1));
        assertNull(translator.name(survey, (Step) null, T1));
        assertNull(translator.name(survey, (Section) null, T1));
        assertNull(translator.name(survey, (ReportDefinition) null, T1));
        assertNull(translator.title(null, T1));
        assertNull(translator.description(null, T1));
        assertNull(translator.defaultUpstreamValue(survey, null, T1));
    }

    private Question question() {
        Question q = new Question();
        q.questionKey = UUID.randomUUID();
        q.text = "Text";
        q.shortText = "Short";
        q.toolTip = "Tip";
        q.placeholder = "Hint";
        q.validationText = "Invalid";
        return q;
    }

    @Test
    void given_translatedQuestion_when_usingQuestionAccessors_then_translationsServed() throws Exception {
        Question q = question();
        current(q.questionKey, "text", "ES text", q.text);
        current(q.questionKey, "short_text", "ES short", q.shortText);
        current(q.questionKey, "tool_tip", "ES tip", q.toolTip);
        current(q.questionKey, "placeholder", "ES hint", q.placeholder);
        current(q.questionKey, "validation_text", "ES invalid", q.validationText);
        install();

        assertEquals("ES text", translator.text(survey, q, T1));
        assertEquals("ES short", translator.shortText(survey, q, T1));
        assertEquals("ES tip", translator.toolTip(survey, q, T1));
        assertEquals("ES hint", translator.placeholder(survey, q, T1));
        assertEquals("ES invalid", translator.validationText(survey, q, T1));
    }

    @Test
    void given_untranslatedQuestion_when_usingQuestionAccessors_then_baseTextServed() throws Exception {
        Question q = question();
        install();

        assertEquals("Text", translator.text(survey, q, T1));
        assertEquals("Short", translator.shortText(survey, q, T1));
        assertEquals("Tip", translator.toolTip(survey, q, T1));
        assertEquals("Hint", translator.placeholder(survey, q, T1));
        assertEquals("Invalid", translator.validationText(survey, q, T1));
    }

    @Test
    void given_translatedStructure_when_usingElementAccessors_then_translationsServed() throws Exception {
        SelectItem item = new SelectItem();
        item.selectItemKey = UUID.randomUUID();
        item.displayText = "Yes";
        Step step = new Step();
        step.stepKey = UUID.randomUUID();
        step.name = "Step";
        Section section = new Section();
        section.sectionKey = UUID.randomUUID();
        section.name = "Section";
        ReportDefinition report = new ReportDefinition();
        report.reportKey = UUID.randomUUID();
        report.name = "Report";
        Relationship relationship = new Relationship();
        relationship.relationshipKey = UUID.randomUUID();
        relationship.defaultUpstreamValue = "your relative";
        survey.title = "Title";
        survey.description = "Description";

        current(item.selectItemKey, "display_text", "Sí", item.displayText);
        current(step.stepKey, "name", "Paso", step.name);
        current(section.sectionKey, "name", "Sección", section.name);
        current(report.reportKey, "name", "Informe", report.name);
        current(relationship.relationshipKey, "default_upstream_value", "su familiar",
                relationship.defaultUpstreamValue);
        current(survey.surveyKey, "title", "Título", survey.title);
        current(survey.surveyKey, "description", "Descripción", survey.description);
        install();

        assertEquals("Sí", translator.displayText(survey, item, T1));
        assertEquals("Paso", translator.name(survey, step, T1));
        assertEquals("Sección", translator.name(survey, section, T1));
        assertEquals("Informe", translator.name(survey, report, T1));
        assertEquals("su familiar", translator.defaultUpstreamValue(survey, relationship, T1));
        assertEquals("Título", translator.title(survey, T1));
        assertEquals("Descripción", translator.description(survey, T1));
    }

    @Test
    void given_untranslatedStructure_when_usingElementAccessors_then_baseTextServed() throws Exception {
        SelectItem item = new SelectItem();
        item.selectItemKey = UUID.randomUUID();
        item.displayText = "Yes";
        Step step = new Step();
        step.stepKey = UUID.randomUUID();
        step.name = "Step";
        Section section = new Section();
        section.sectionKey = UUID.randomUUID();
        section.name = "Section";
        ReportDefinition report = new ReportDefinition();
        report.reportKey = UUID.randomUUID();
        report.name = "Report";
        Relationship relationship = new Relationship();
        relationship.relationshipKey = UUID.randomUUID();
        relationship.defaultUpstreamValue = "your relative";
        survey.title = "Title";
        survey.description = "Description";
        install();

        assertEquals("Yes", translator.displayText(survey, item, T1));
        assertEquals("Step", translator.name(survey, step, T1));
        assertEquals("Section", translator.name(survey, section, T1));
        assertEquals("Report", translator.name(survey, report, T1));
        assertEquals("your relative", translator.defaultUpstreamValue(survey, relationship, T1));
        assertEquals("Title", translator.title(survey, T1));
        assertEquals("Description", translator.description(survey, T1));
    }
}
