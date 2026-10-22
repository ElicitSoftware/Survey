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
package com.elicitsoftware.report.builtin;

import com.elicitsoftware.i18n.ContentTranslator;
import com.elicitsoftware.i18n.LocaleConfig;
import com.elicitsoftware.i18n.Translations;
import com.elicitsoftware.model.ReportDefinition;
import com.elicitsoftware.model.ReportItem;
import com.elicitsoftware.model.Respondent;
import com.elicitsoftware.model.SelectItem;
import com.elicitsoftware.model.Step;
import com.elicitsoftware.model.StepsSections;
import com.elicitsoftware.model.Survey;
import com.elicitsoftware.report.ReportRequest;
import com.elicitsoftware.report.ReportResponse;
import com.elicitsoftware.report.pdf.Content;
import com.elicitsoftware.report.pdf.PDFDocument;
import com.elicitsoftware.report.pdf.Table;
import io.quarkus.logging.Log;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * The built-in respondent report (UC-014): for one respondent, the answers the survey's author
 * marked {@code RESPONDENT} (UC-013), grouped by step and section in survey order, in the
 * respondent's language, as a card and as a part of the merged PDF.
 */
@ApplicationScoped
public class RespondentSummaryService implements BuiltInReports.Service {

    /** The address Author seeds into every new survey (Author UC-048 BR-001). */
    public static final String PATH = "/api/reports/respondent-summary";

    /** UC-014 BR-003: question types with no value to show back. */
    static final Set<String> VALUELESS_TYPES = Set.of("HTML", "MODAL", "PASSWORD");

    private static final String ROWS_SQL = """
            SELECT a.display_key, a.step, a.section, a.section_instance, a.question_instance,
                   CASE WHEN a.display_language = :language THEN a.display_text_local ELSE a.display_text END AS display_text,
                   a.text_value, q.question_id, sq.sections_question_id, t.name AS question_type, q.select_group_id
            FROM survey.answers a
            LEFT JOIN survey.questions q ON a.question_id = q.id
            LEFT JOIN survey.sections_questions sq ON a.section_question_id = sq.id
            LEFT JOIN survey.question_types t ON q.type_id = t.id
            WHERE a.respondent_id = :respondentId AND a.deleted = false
            ORDER BY a.display_key
            """;

    private static final String LANGUAGE_SQL = """
            SELECT display_language FROM survey.answers
            WHERE respondent_id = :respondentId AND display_language IS NOT NULL
            ORDER BY saved_dt DESC NULLS LAST, id DESC LIMIT 1
            """;

    /** One answer row as the report reads it. */
    record Row(String displayKey, BigDecimal step, BigDecimal section, int sectionInstance, int questionInstance,
               String text, String value, Integer questionId, Integer sectionsQuestionId, String type, Integer selectGroupId) {

        boolean isHeader() {
            return questionId == null;
        }

        boolean isSectionHeader() {
            return isHeader() && section != null && section.signum() != 0;
        }
    }

    /** One item of the report: a marked answer under its label. */
    record Item(String label, String value, int markOrder, String displayKey) {
    }

    /** One group of items: a section as the respondent met it, with its own header. */
    record Group(String heading, List<Item> items) {
    }

    /** What a rendering produced, for tests and the two renderings (BR-007). */
    public record Summary(String reportName, String surveyTitle, String completed, String language, boolean rightToLeft,
                          List<Group> groups) {
    }

    @Inject
    EntityManager em;

    @Inject
    ContentTranslator translator;

    @Inject
    LocaleConfig localeConfig;

    @Override
    public String path() {
        return PATH;
    }

    @Override
    @Transactional
    public Optional<ReportResponse> render(ReportRequest request, String caller) {
        return summary(request.getId(), request.getLanguage(), caller).map(this::toResponse);
    }

    /** The report's content before rendering; empty when the respondent does not exist. */
    @Transactional
    public Optional<Summary> summary(int respondentId, String requestedLanguage, String caller) {
        Respondent respondent = Respondent.findById(respondentId);
        if (respondent == null || respondent.survey == null) {
            return Optional.empty();
        }
        Survey survey = respondent.survey;
        OffsetDateTime anchor = Respondent.snapshotAnchor(respondentId);
        String language = resolveLanguage(respondentId, requestedLanguage, survey);
        Locale locale = Locale.forLanguageTag(language);

        List<ReportItem> marks = ReportItem.list("surveyId = ?1 and reportType = ?2",
                Sort.by("displayOrder").and("id"), survey.id, ReportItem.RESPONDENT);
        Map<Integer, StepsSections> sectionMarks = new HashMap<>();
        for (ReportItem mark : marks) {
            if (mark.elementRef() == ReportItem.ElementRef.STEPS_SECTIONS) {
                StepsSections placement = StepsSections.findAsOf(mark.stepsSectionsId, anchor);
                if (placement != null) {
                    sectionMarks.put(mark.id, placement);
                }
            }
        }

        List<Row> rows = rows(respondentId, language);
        Map<String, Group> groups = new LinkedHashMap<>();
        Map<String, String> sectionHeaders = new HashMap<>();
        Map<String, List<SelectItem>> optionsByGroup = new HashMap<>();
        for (Row row : rows) {
            String groupKey = row.step() + ":" + row.section() + ":" + row.sectionInstance();
            if (row.isSectionHeader()) {
                sectionHeaders.put(groupKey, row.text());
                continue;
            }
            if (row.isHeader() || VALUELESS_TYPES.contains(row.type())) {
                continue;
            }
            ReportItem mark = coveringMark(row, marks, sectionMarks);
            if (mark == null) {
                continue;
            }
            String value = renderValue(row, survey, anchor, language, locale, optionsByGroup);
            if (value == null) {
                continue;
            }
            String label = labelOf(mark, row, survey, language, anchor);
            Group group = groups.computeIfAbsent(groupKey, k -> new Group(
                    heading(row, sectionHeaders.get(k), survey, language, anchor), new ArrayList<>()));
            group.items().add(new Item(label, value, mark.displayOrder == null ? 0 : mark.displayOrder, row.displayKey()));
        }
        for (Group group : groups.values()) {
            group.items().sort((a, b) -> a.markOrder() != b.markOrder()
                    ? Integer.compare(a.markOrder(), b.markOrder()) : a.displayKey().compareTo(b.displayKey()));
        }

        String reportName = reportName(survey, anchor, language);
        String title = translator.get(survey, language, survey.surveyKey, "title", survey.title, anchor).orElse(survey.title);
        String completed = respondent.finalizedDt == null ? null
                : DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale).format(respondent.finalizedDt);
        Log.infof("Respondent report rendered: survey %s, respondent %d, language %s, via %s (UC-014 BR-008)",
                survey.surveyKey, respondentId, language, caller);
        return Optional.of(new Summary(reportName, title, completed, language, localeConfig.isRightToLeft(locale),
                List.copyOf(groups.values())));
    }

    // ---- content --------------------------------------------------------------------------------

    /** UC-014 BR-002: the request's language, then the respondent's, then the survey's. */
    String resolveLanguage(int respondentId, String requested, Survey survey) {
        if (requested != null && !requested.isBlank()) {
            return requested.trim();
        }
        List<?> latest = em.createNativeQuery(LANGUAGE_SQL).setParameter("respondentId", respondentId).getResultList();
        if (!latest.isEmpty() && latest.get(0) != null) {
            return String.valueOf(latest.get(0));
        }
        return survey.baseLanguage == null || survey.baseLanguage.isBlank() ? "en" : survey.baseLanguage;
    }

    @SuppressWarnings("unchecked")
    private List<Row> rows(int respondentId, String language) {
        List<Object[]> raw = em.createNativeQuery(ROWS_SQL)
                .setParameter("respondentId", respondentId)
                .setParameter("language", language)
                .getResultList();
        List<Row> rows = new ArrayList<>(raw.size());
        for (Object[] r : raw) {
            rows.add(new Row(String.valueOf(r[0]), decimal(r[1]), decimal(r[2]), integer(r[3]), integer(r[4]),
                    r[5] == null ? "" : String.valueOf(r[5]), r[6] == null ? null : String.valueOf(r[6]),
                    r[7] == null ? null : ((Number) r[7]).intValue(), r[8] == null ? null : ((Number) r[8]).intValue(),
                    r[9] == null ? null : String.valueOf(r[9]), r[10] == null ? null : ((Number) r[10]).intValue()));
        }
        return rows;
    }

    /** UC-014 BR-003: the first mark, in the author's order, that covers this answer row. */
    private static ReportItem coveringMark(Row row, List<ReportItem> marks, Map<Integer, StepsSections> sectionMarks) {
        for (ReportItem mark : marks) {
            ReportItem.ElementRef ref = mark.elementRef();
            if (ref == null) {
                continue;
            }
            boolean covers = switch (ref) {
                case QUESTION -> Objects.equals(mark.questionId, row.questionId());
                case SECTIONS_QUESTION -> Objects.equals(mark.sectionsQuestionId, row.sectionsQuestionId());
                case STEPS_SECTIONS -> {
                    StepsSections placement = sectionMarks.get(mark.id);
                    yield placement != null && same(placement.stepDisplayOrder, row.step()) && same(placement.sectionDisplayOrder, row.section());
                }
            };
            if (covers) {
                return mark;
            }
        }
        return null;
    }

    private static boolean same(BigDecimal a, BigDecimal b) {
        return a != null && b != null && a.compareTo(b) == 0;
    }

    /** UC-014 BR-005 and BR-006: the value as the report shows it, or {@code null} to leave the item out. */
    private String renderValue(Row row, Survey survey, OffsetDateTime anchor, String language, Locale locale,
                               Map<String, List<SelectItem>> optionsByGroup) {
        String type = row.type() == null ? "" : row.type();
        String raw = row.value() == null ? null : row.value().trim();
        if ("CHECKBOX".equals(type)) {
            return Boolean.parseBoolean(raw)
                    ? Translations.get(locale, "respondentSummary.yes")
                    : Translations.get(locale, "respondentSummary.no");
        }
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        return switch (type) {
            case "RADIO", "COMBOBOX" -> optionText(raw, row, survey, anchor, language, optionsByGroup);
            case "MULTI_SELECT", "CHECKBOX_GROUP", "MULTI_SELECT_COMBOBOX" -> {
                List<String> texts = new ArrayList<>();
                for (String code : raw.split(",")) {
                    String code1 = code.trim();
                    if (!code1.isEmpty()) {
                        texts.add(optionText(code1, row, survey, anchor, language, optionsByGroup));
                    }
                }
                yield String.join(", ", texts);
            }
            case "INTEGER", "DOUBLE" -> number(raw, locale);
            case "DATE_PICKER" -> date(raw, locale);
            case "DATE_TIME_PICKER" -> dateTime(raw, locale);
            case "TIME_PICKER" -> time(raw, locale);
            default -> raw;
        };
    }

    private String optionText(String code, Row row, Survey survey, OffsetDateTime anchor, String language,
                              Map<String, List<SelectItem>> optionsByGroup) {
        if (row.selectGroupId() == null) {
            return code;
        }
        List<SelectItem> options = optionsByGroup.computeIfAbsent(row.selectGroupId() + "@" + anchor,
                k -> SelectItem.findByGroupAsOf(row.selectGroupId(), anchor));
        for (SelectItem option : options) {
            if (code.equals(option.codedValue)) {
                return translator.get(survey, language, option.selectItemKey, "display_text", option.displayText, anchor)
                        .orElse(option.displayText);
            }
        }
        return code;
    }

    private static String number(String raw, Locale locale) {
        try {
            BigDecimal n = new BigDecimal(raw);
            NumberFormat format = NumberFormat.getNumberInstance(locale);
            format.setMaximumFractionDigits(Math.max(0, n.scale()));
            return format.format(n);
        } catch (NumberFormatException e) {
            return raw;
        }
    }

    private static String date(String raw, Locale locale) {
        try {
            return DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale).format(LocalDate.parse(raw));
        } catch (DateTimeParseException e) {
            return raw;
        }
    }

    private static String dateTime(String raw, Locale locale) {
        try {
            return DateTimeFormatter.ofLocalizedDateTime(FormatStyle.LONG, FormatStyle.SHORT).withLocale(locale)
                    .format(LocalDateTime.parse(raw));
        } catch (DateTimeParseException e) {
            return raw;
        }
    }

    private static String time(String raw, Locale locale) {
        try {
            return DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).format(LocalTime.parse(raw));
        } catch (DateTimeParseException e) {
            return raw;
        }
    }

    /** UC-014 BR-002: the mark's label, translated, or the question's text as it was shown. */
    private String labelOf(ReportItem mark, Row row, Survey survey, String language, OffsetDateTime anchor) {
        if (mark.label != null && !mark.label.isBlank()) {
            return translator.get(survey, language, mark.reportItemKey, "label", mark.label, anchor).orElse(mark.label);
        }
        return row.text();
    }

    /** "Step › Section header": the step's name translated, the section's header row as shown. */
    private String heading(Row row, String sectionHeader, Survey survey, String language, OffsetDateTime anchor) {
        String stepName = null;
        for (StepsSections placement : StepsSections.findBySurveyIdAsOf(survey.id, anchor)) {
            if (same(placement.stepDisplayOrder, row.step()) && same(placement.sectionDisplayOrder, row.section())) {
                Step step = Step.findAsOf(placement.stepId, anchor);
                if (step != null) {
                    stepName = translator.get(survey, language, step.stepKey, "name", step.name, anchor).orElse(step.name);
                }
                break;
            }
        }
        String section = sectionHeader == null || sectionHeader.isBlank() ? "" : sectionHeader;
        if (stepName == null || stepName.isBlank()) {
            return section;
        }
        return section.isEmpty() ? stepName : stepName + " › " + section;
    }

    private String reportName(Survey survey, OffsetDateTime anchor, String language) {
        for (ReportDefinition report : ReportDefinition.<ReportDefinition>list("survey.id = ?1 and url = ?2", survey.id, PATH)) {
            return translator.get(survey, language, report.reportKey, "name", report.name, anchor).orElse(report.name);
        }
        return Translations.get(Locale.forLanguageTag(language), "respondentSummary.title");
    }

    // ---- the two renderings (BR-007) ------------------------------------------------------------

    ReportResponse toResponse(Summary s) {
        Locale locale = Locale.forLanguageTag(s.language());
        String questionHeader = Translations.get(locale, "respondentSummary.question");
        String answerHeader = Translations.get(locale, "respondentSummary.answer");
        String completedLine = s.completed() == null
                ? Translations.get(locale, "respondentSummary.inProgress")
                : Translations.get(locale, "respondentSummary.completed", s.completed());

        StringBuilder html = new StringBuilder();
        html.append("<div class=\"respondent-summary\"").append(s.rightToLeft() ? " dir=\"rtl\"" : "").append(">");
        html.append("<p class=\"respondent-summary-meta\"><strong>").append(escape(s.surveyTitle())).append("</strong> · ")
                .append(escape(completedLine)).append("</p>");
        List<Content> contents = new ArrayList<>();
        for (Group group : s.groups()) {
            html.append("<h5>").append(escape(group.heading())).append("</h5><dl>");
            String[][] body = new String[group.items().size()][];
            int i = 0;
            for (Item item : group.items()) {
                html.append("<dt>").append(escape(item.label())).append("</dt><dd>").append(escape(item.value())).append("</dd>");
                body[i++] = new String[]{item.label(), item.value()};
            }
            html.append("</dl>");
            Content heading = new Content();
            heading.text = group.heading();
            contents.add(heading);
            Table table = new Table();
            table.headers = new String[]{questionHeader, answerHeader};
            table.widths = new float[]{250f, 250f};
            table.body = body;
            contents.add(new Content(table));
        }
        html.append("</div>");

        PDFDocument pdf = new PDFDocument();
        pdf.title = s.reportName();
        Content meta = new Content();
        meta.text = s.surveyTitle() + " · " + completedLine;
        contents.add(0, meta);
        pdf.content = contents.toArray(new Content[0]);
        return new ReportResponse(s.reportName(), html.toString(), pdf);
    }

    static String escape(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(text.length() + 16);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '&' -> out.append("&amp;");
                case '"' -> out.append("&quot;");
                default -> out.append(c);
            }
        }
        return out.toString();
    }

    private static BigDecimal decimal(Object o) {
        if (o == null) {
            return null;
        }
        return o instanceof BigDecimal d ? d : new BigDecimal(String.valueOf(o));
    }

    private static int integer(Object o) {
        return o == null ? 0 : ((Number) o).intValue();
    }
}
