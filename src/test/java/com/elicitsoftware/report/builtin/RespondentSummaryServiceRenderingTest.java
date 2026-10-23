package com.elicitsoftware.report.builtin;

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

import com.elicitsoftware.i18n.ContentTranslator;
import com.elicitsoftware.model.ReportItem;
import com.elicitsoftware.model.SelectItem;
import com.elicitsoftware.model.StepsSections;
import com.elicitsoftware.model.Survey;
import com.elicitsoftware.report.ReportResponse;
import com.elicitsoftware.report.pdf.Content;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The built-in respondent report (UC-014) without a database: the language it renders in
 * (BR-002), which mark covers an answer row (BR-003), how each value type is shown or left out
 * (BR-005, BR-006), the mark's label, the escaping (BR-005) and the two renderings of one
 * content (BR-007). The private steps of {@link RespondentSummaryService#summary} are invoked
 * reflectively with rows built here; the native queries go to a hand-written
 * {@link EntityManager}. Reading the marks, the placements and the option lists is Panache and
 * is covered by {@code RespondentSummaryServiceTest} under Quarkus.
 */
class RespondentSummaryServiceRenderingTest {

    private static final OffsetDateTime ANCHOR = OffsetDateTime.parse("2026-05-01T00:00:00Z");
    private static final UUID MARK_KEY = UUID.fromString("cccccccc-0000-0000-0000-000000000003");

    private final RespondentSummaryService service = new RespondentSummaryService();
    private final Survey survey = new Survey();

    /** Records native queries and answers {@code getResultList()} with {@link #results}. */
    private final List<String> queries = new ArrayList<>();
    private final Map<String, Object> parameters = new LinkedHashMap<>();
    private List<?> results = List.of();

    /** What the stub translator answers, and what it was asked. */
    private Optional<String> translation = Optional.empty();
    private final List<String> translated = new ArrayList<>();

    RespondentSummaryServiceRenderingTest() {
        survey.id = 4;
        survey.title = "Family History";
        service.em = (EntityManager) Proxy.newProxyInstance(EntityManager.class.getClassLoader(),
                new Class<?>[]{EntityManager.class}, (p, method, args) -> {
                    if (method.getName().equals("createNativeQuery")) {
                        queries.add((String) args[0]);
                        return query();
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
        service.translator = new ContentTranslator() {
            @Override
            public Optional<String> get(Survey s, String language, UUID targetKey, String field, String baseText, OffsetDateTime asOf) {
                translated.add(language + "|" + targetKey + "|" + field + "|" + baseText + "|" + asOf);
                return translation;
            }
        };
    }

    private Query query() {
        return (Query) Proxy.newProxyInstance(Query.class.getClassLoader(), new Class<?>[]{Query.class},
                (p, method, args) -> switch (method.getName()) {
                    case "setParameter" -> {
                        parameters.put((String) args[0], args[1]);
                        yield p;
                    }
                    case "getResultList" -> results;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private static Object call(Object target, String name, Object... args) throws Exception {
        for (Method method : RespondentSummaryService.class.getDeclaredMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == args.length) {
                method.setAccessible(true);
                try {
                    return method.invoke(target, args);
                } catch (InvocationTargetException e) {
                    throw (Exception) e.getCause();
                }
            }
        }
        throw new NoSuchMethodException(name);
    }

    private static RespondentSummaryService.Row row(String type, String value) {
        return new RespondentSummaryService.Row("0001-0001-0001", BigDecimal.ONE, BigDecimal.ONE, 0, 0,
                "How tall are you?", value, 10, 20, type, null);
    }

    private String render(String type, String value, Locale locale) throws Exception {
        return (String) call(service, "renderValue", row(type, value), survey, ANCHOR, locale.toLanguageTag(), locale, new HashMap<>());
    }

    // ── language (BR-002) ──────────────────────────────────────────────────────────────────────

    @Test
    void given_aRequestedLanguage_when_resolveLanguage_then_itWinsTrimmedWithoutAQuery() {
        assertEquals("es-419", service.resolveLanguage(7, "  es-419 ", survey));
        assertTrue(queries.isEmpty());
    }

    @Test
    void given_noRequestedLanguage_when_resolveLanguage_then_theRespondentsLatestDisplayLanguage() {
        results = List.of("ar");

        assertEquals("ar", service.resolveLanguage(7, "  ", survey));
        assertEquals(1, queries.size());
        assertTrue(queries.get(0).contains("FROM survey.answers"), queries.get(0));
        assertEquals(Map.of("respondentId", 7), parameters);
    }

    @Test
    void given_noLanguageOnAnyAnswer_when_resolveLanguage_then_theSurveysBaseLanguage() {
        survey.baseLanguage = "fr";

        assertEquals("fr", service.resolveLanguage(7, null, survey));
    }

    @Test
    void given_aNullLatestLanguage_when_resolveLanguage_then_theSurveysBaseLanguage() {
        survey.baseLanguage = "fr";
        List<Object> latest = new ArrayList<>();
        latest.add(null);
        results = latest;

        assertEquals("fr", service.resolveLanguage(7, null, survey));
    }

    @Test
    void given_noBaseLanguage_when_resolveLanguage_then_english() {
        survey.baseLanguage = " ";
        assertEquals("en", service.resolveLanguage(7, null, survey));
        survey.baseLanguage = null;
        assertEquals("en", service.resolveLanguage(7, null, survey));
    }

    // ── answer rows ────────────────────────────────────────────────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void given_rawAnswerRows_when_rows_then_eachColumnIsConverted() throws Exception {
        results = List.<Object[]>of(
                new Object[]{"0001-0002", new BigDecimal("2.5"), 3, 1, 2L, "Height", 180, 10L, 20, "INTEGER", 30},
                new Object[]{"0001-0003", null, null, null, null, null, null, null, null, null, null});

        List<RespondentSummaryService.Row> rows = (List<RespondentSummaryService.Row>) call(service, "rows", 7, "en");

        assertEquals(new RespondentSummaryService.Row("0001-0002", new BigDecimal("2.5"), new BigDecimal("3"), 1, 2,
                "Height", "180", 10, 20, "INTEGER", 30), rows.get(0));
        assertEquals(new RespondentSummaryService.Row("0001-0003", null, null, 0, 0, "", null, null, null, null, null), rows.get(1));
        assertEquals(Map.of("respondentId", 7, "language", "en"), parameters);
        assertTrue(queries.get(0).contains("a.deleted = false"), "deleted rows are never read (BR-003)");
    }

    @Test
    void given_rows_when_askedWhatTheyAre_then_aHeaderHasNoQuestionAndASectionHeaderAlsoASection() {
        RespondentSummaryService.Row question = row("TEXT", "x");
        RespondentSummaryService.Row stepHeader = new RespondentSummaryService.Row("1", BigDecimal.ONE, BigDecimal.ZERO, 0, 0, "Step", null, null, null, null, null);
        RespondentSummaryService.Row noSection = new RespondentSummaryService.Row("1", BigDecimal.ONE, null, 0, 0, "Step", null, null, null, null, null);
        RespondentSummaryService.Row sectionHeader = new RespondentSummaryService.Row("1", BigDecimal.ONE, BigDecimal.TEN, 0, 0, "Section", null, null, null, null, null);

        assertEquals(List.of(false, true, true, true), List.of(question.isHeader(), stepHeader.isHeader(), noSection.isHeader(), sectionHeader.isHeader()));
        assertEquals(List.of(false, false, false, true),
                List.of(question.isSectionHeader(), stepHeader.isSectionHeader(), noSection.isSectionHeader(), sectionHeader.isSectionHeader()));
    }

    // ── covering marks (BR-003) ────────────────────────────────────────────────────────────────

    private static ReportItem mark(int id, Integer questionId, Integer sectionsQuestionId, Integer stepsSectionsId) {
        ReportItem mark = new ReportItem();
        mark.id = id;
        mark.questionId = questionId;
        mark.sectionsQuestionId = sectionsQuestionId;
        mark.stepsSectionsId = stepsSectionsId;
        return mark;
    }

    private static StepsSections placement(String step, String section) {
        StepsSections placement = new StepsSections();
        placement.stepDisplayOrder = step == null ? null : new BigDecimal(step);
        placement.sectionDisplayOrder = section == null ? null : new BigDecimal(section);
        return placement;
    }

    private static ReportItem covering(RespondentSummaryService.Row row, List<ReportItem> marks, Map<Integer, StepsSections> placements)
            throws Exception {
        return (ReportItem) call(null, "coveringMark", row, marks, placements);
    }

    @Test
    void given_aQuestionMark_when_coveringMark_then_itCoversRowsOfThatQuestionOnly() throws Exception {
        ReportItem other = mark(1, 99, null, null);
        ReportItem mine = mark(2, 10, null, null);

        assertSame(mine, covering(row("TEXT", "x"), List.of(other, mine), Map.of()));
        assertNull(covering(row("TEXT", "x"), List.of(other), Map.of()));
    }

    @Test
    void given_aPlacementMark_when_coveringMark_then_itCoversRowsOfThatPlacement() throws Exception {
        assertNotNullSame(mark(3, null, 20, null), row("TEXT", "x"));
        assertNull(covering(row("TEXT", "x"), List.of(mark(3, null, 21, null)), Map.of()));
    }

    private void assertNotNullSame(ReportItem mark, RespondentSummaryService.Row row) throws Exception {
        assertSame(mark, covering(row, List.of(mark), Map.of()));
    }

    @Test
    void given_aSectionMark_when_coveringMark_then_itCoversRowsMountedAtItsPlacementByValueNotScale() throws Exception {
        ReportItem section = mark(5, null, null, 50);
        RespondentSummaryService.Row row = new RespondentSummaryService.Row("1", new BigDecimal("1.0"), new BigDecimal("2.00"), 0, 0,
                "Q", "v", 10, 20, "TEXT", null);

        assertSame(section, covering(row, List.of(section), Map.of(5, placement("1", "2"))));
        assertNull(covering(row, List.of(section), Map.of(5, placement("1", "3"))), "another section");
        assertNull(covering(row, List.of(section), Map.of(5, placement("2", "2"))), "another step");
        assertNull(covering(row, List.of(section), Map.of()), "a placement not current at the anchor covers nothing");
        assertNull(covering(row, List.of(section), Map.of(5, placement(null, "2"))));
        RespondentSummaryService.Row unplaced = new RespondentSummaryService.Row("1", null, new BigDecimal("2"), 0, 0, "Q", "v", 10, 20, "TEXT", null);
        assertNull(covering(unplaced, List.of(section), Map.of(5, placement("1", "2"))));
    }

    @Test
    void given_aMarkWithoutElement_when_coveringMark_then_itIsSkippedAndTheNextOneDecides() throws Exception {
        ReportItem empty = mark(1, null, null, null);
        ReportItem mine = mark(2, 10, null, null);

        assertSame(mine, covering(row("TEXT", "x"), List.of(empty, mine), Map.of()));
    }

    // ── values (BR-005, BR-006) ────────────────────────────────────────────────────────────────

    @Test
    void given_aCheckbox_when_renderValue_then_yesWhenTickedAndNoOtherwiseEvenUntouched() throws Exception {
        assertEquals("Yes", render("CHECKBOX", " true ", Locale.ENGLISH));
        assertEquals("No", render("CHECKBOX", "false", Locale.ENGLISH));
        assertEquals("No", render("CHECKBOX", null, Locale.ENGLISH));
    }

    @Test
    void given_noValue_when_renderValue_then_theItemIsLeftOut() throws Exception {
        assertNull(render("TEXT", null, Locale.ENGLISH));
        assertNull(render("TEXT", "   ", Locale.ENGLISH));
        assertNull(render(null, "", Locale.ENGLISH));
    }

    @Test
    void given_textAndUnknownTypes_when_renderValue_then_shownTrimmedAsTyped() throws Exception {
        assertEquals("<b>me</b>", render("TEXT", "  <b>me</b> ", Locale.ENGLISH));
        assertEquals("x", render(null, "x", Locale.ENGLISH));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "INTEGER|1234|en|1,234",
            "INTEGER|1234|de|1.234",
            "DOUBLE|1234.50|en|1,234.5",
            "DOUBLE|0.125|en|0.125",
            "DOUBLE|0.12345|en|0.12345",
            "DOUBLE|1.5E+3|en|1,500",
            "INTEGER|12a|en|12a",
            "DATE_PICKER|2026-03-05|en|March 5, 2026",
            "DATE_PICKER|05/03/2026|en|05/03/2026",
            "TIME_PICKER|14:30|en-US|2:30 PM",
            "TIME_PICKER|2pm|en|2pm",
            "DATE_TIME_PICKER|2026-03-05T14:30|en-US|March 5, 2026, 2:30 PM",
            "DATE_TIME_PICKER|yesterday|en|yesterday"})
    void given_aTypedValue_when_renderValue_then_formattedForTheLanguageOrShownAsStored(String type, String raw, String tag,
                                                                                         String expected) throws Exception {
        assertEquals(expected, render(type, raw, Locale.forLanguageTag(tag)));
    }

    @Test
    void given_optionsWithoutAList_when_renderValue_then_theCodesAreShown() throws Exception {
        assertEquals("R", render("RADIO", "R", Locale.ENGLISH));
        assertEquals("R", render("COMBOBOX", "R", Locale.ENGLISH));
        assertEquals("R, G, B", render("MULTI_SELECT", "R, ,G,B,", Locale.ENGLISH));
        assertEquals("R, G", render("CHECKBOX_GROUP", "R,G", Locale.ENGLISH));
        assertEquals("R", render("MULTI_SELECT_COMBOBOX", "R", Locale.ENGLISH));
    }

    private static SelectItem option(String code, String text, UUID key) {
        SelectItem option = new SelectItem();
        option.codedValue = code;
        option.displayText = text;
        option.selectItemKey = key;
        return option;
    }

    private String renderOptions(String type, String value) throws Exception {
        RespondentSummaryService.Row row = new RespondentSummaryService.Row("0001", BigDecimal.ONE, BigDecimal.ONE, 0, 0,
                "Colour?", value, 10, 20, type, 30);
        Map<String, List<SelectItem>> options = new HashMap<>();
        // The option list as of the anchor, already read for this group, as the cache holds it.
        options.put("30@" + ANCHOR, List.of(option("R", "Red", MARK_KEY), option("G", "Green", null)));
        return (String) call(service, "renderValue", row, survey, ANCHOR, "es", Locale.ENGLISH, options);
    }

    @Test
    void given_optionsOfAList_when_renderValue_then_theChosenOptionsTextNeverItsCode() throws Exception {
        assertEquals("Red", renderOptions("RADIO", "R"));
        assertEquals(List.of("es|" + MARK_KEY + "|display_text|Red|" + ANCHOR), translated);
        assertEquals("Red, Green, X", renderOptions("MULTI_SELECT", "R,G,X"), "an unknown code is shown as stored");
    }

    @Test
    void given_aTranslatedOption_when_renderValue_then_theTranslation() throws Exception {
        translation = Optional.of("Rojo");

        assertEquals("Rojo", renderOptions("COMBOBOX", "R"));
    }

    // ── labels (BR-002) ────────────────────────────────────────────────────────────────────────

    private String label(String markLabel) throws Exception {
        ReportItem mark = mark(1, 10, null, null);
        mark.label = markLabel;
        mark.reportItemKey = MARK_KEY;
        return (String) call(service, "labelOf", mark, row("TEXT", "x"), survey, "es", ANCHOR);
    }

    @Test
    void given_aMarkWithoutLabel_when_labelOf_then_theQuestionAsShown() throws Exception {
        assertEquals("How tall are you?", label(null));
        assertEquals("How tall are you?", label("  "));
        assertTrue(translated.isEmpty());
    }

    @Test
    void given_aLabelledMark_when_labelOf_then_itsTranslationOrItsLabel() throws Exception {
        assertEquals("Height", label("Height"));
        assertEquals(List.of("es|" + MARK_KEY + "|label|Height|" + ANCHOR), translated);

        translation = Optional.of("Estatura");
        assertEquals("Estatura", label("Height"));
    }

    // ── renderings (BR-005, BR-007) ────────────────────────────────────────────────────────────

    @Test
    void given_text_when_escape_then_markupCharactersAreEntities() {
        assertEquals("&lt;b&gt;Tom &amp; &quot;Jerry&quot;&lt;/b&gt; it's", RespondentSummaryService.escape("<b>Tom & \"Jerry\"</b> it's"));
        assertEquals("", RespondentSummaryService.escape(null));
        assertEquals("", RespondentSummaryService.escape(""));
    }

    private static RespondentSummaryService.Summary summary(String completed, boolean rtl) {
        return new RespondentSummaryService.Summary("Your answers", "Family <History>", completed, "en", rtl, List.of(
                new RespondentSummaryService.Group("About you › Height", List.of(
                        new RespondentSummaryService.Item("Height", "180 & up", 1, "0001"),
                        new RespondentSummaryService.Item("Weight", "80", 2, "0002"))),
                new RespondentSummaryService.Group("Family", List.of(
                        new RespondentSummaryService.Item("Mother", "Yes", 0, "0003")))));
    }

    @Test
    void given_aCompletedSummary_when_toResponse_then_theCardCarriesEveryItemEscapedInOrder() {
        ReportResponse response = service.toResponse(summary("March 5, 2026", false));

        assertEquals("Your answers", response.title);
        assertEquals("<div class=\"respondent-summary\"><p class=\"respondent-summary-meta\"><strong>Family &lt;History&gt;</strong>"
                + " · Completed March 5, 2026</p>"
                + "<h5>About you › Height</h5><dl><dt>Height</dt><dd>180 &amp; up</dd><dt>Weight</dt><dd>80</dd></dl>"
                + "<h5>Family</h5><dl><dt>Mother</dt><dd>Yes</dd></dl></div>", response.innerHTML);
    }

    @Test
    void given_aCompletedSummary_when_toResponse_then_thePdfCarriesTheSameContentUnderTheReportsName() {
        ReportResponse response = service.toResponse(summary("March 5, 2026", false));

        assertEquals("Your answers", response.pdf.title);
        Content[] content = response.pdf.content;
        assertEquals(5, content.length);
        assertEquals("Family <History> · Completed March 5, 2026", content[0].text);
        assertEquals("About you › Height", content[1].text);
        assertNull(content[1].table);
        assertArrayEquals(new String[]{"Question", "Answer"}, content[2].table.headers);
        assertArrayEquals(new float[]{250f, 250f}, content[2].table.widths);
        assertArrayEquals(new String[][]{{"Height", "180 & up"}, {"Weight", "80"}}, content[2].table.body);
        assertEquals("Family", content[3].text);
        assertArrayEquals(new String[][]{{"Mother", "Yes"}}, content[4].table.body);
    }

    @Test
    void given_anUnfinishedRightToLeftSummary_when_toResponse_then_markedRtlAndSaysNotFinished() {
        ReportResponse response = service.toResponse(summary(null, true));

        assertTrue(response.innerHTML.startsWith("<div class=\"respondent-summary\" dir=\"rtl\"><p class=\"respondent-summary-meta\">"
                + "<strong>Family &lt;History&gt;</strong> · Not finished yet</p>"), response.innerHTML);
        assertEquals("Family <History> · Not finished yet", response.pdf.content[0].text);
    }

    @Test
    void given_aSummaryWithoutGroups_when_toResponse_then_onlyTheMetaLine() {
        ReportResponse response = service.toResponse(new RespondentSummaryService.Summary("R", "T", null, "en", false, List.of()));

        assertEquals("<div class=\"respondent-summary\"><p class=\"respondent-summary-meta\"><strong>T</strong> · Not finished yet</p></div>",
                response.innerHTML);
        assertEquals(1, response.pdf.content.length);
    }

    @Test
    void given_theService_when_path_then_theAddressAuthorSeeds() {
        assertEquals("/api/reports/respondent-summary", service.path());
    }
}
