package com.elicitsoftware.flow;

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

import com.elicitsoftware.AccessCodeService;
import com.elicitsoftware.QuestionService;
import com.elicitsoftware.UISessionDataService;
import com.elicitsoftware.i18n.ContentTranslator;
import com.elicitsoftware.model.ReportDefinition;
import com.elicitsoftware.model.Respondent;
import com.elicitsoftware.model.Survey;
import com.elicitsoftware.response.NavResponse;
import com.elicitsoftware.response.ReviewResponse;
import com.vaadin.browserless.BrowserlessTest;
import com.vaadin.browserless.internal.Routes;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.router.RouteConfiguration;
import org.junit.jupiter.api.BeforeEach;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.time.OffsetDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Queue;
import java.util.Set;
import java.util.function.BiFunction;

/**
 * Base for the plain browserless view tests: hand-written stand-ins for the CDI beans the
 * {@code flow} views inject, plus placeholder routes, so that a view can be built and driven in a
 * plain {@code BrowserlessTest} with no Quarkus, no CDI and no database (UC-001, UC-002, UC-005,
 * UC-006). Nothing here reaches a Panache static
 * method: every service method a view calls is overridden.
 */
abstract class PlainViewTestSupport extends BrowserlessTest {

    static {
        // Copilot, a development tool, scans the whole classpath each time a mock VaadinService
        // starts -- about a second per test, which is what Pitest would pay per mutant. Nothing
        // under test depends on it.
        System.setProperty("vaadin.copilot.enable", "false");
    }

    private static final Properties ENGLISH = new Properties();

    static {
        try (InputStream in = PlainViewTestSupport.class.getResourceAsStream("/vaadin-i18n/translations.properties");
             Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            ENGLISH.load(reader);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    protected Routes discoverRoutes() {
        return new Routes(Set.of(), Set.of(), Set.of(), true);
    }

    /**
     * Registers the placeholders by path. They carry no {@code @Route}: the booted tests' build
     * indexes the test classes too, and an annotated placeholder would claim the real views' paths.
     */
    @BeforeEach
    void registerPlaceholderRoutes() {
        RouteConfiguration routes = RouteConfiguration.forApplicationScope();
        routes.setRoute("", HomeStub.class);
        routes.setRoute("section", SectionStub.class);
        routes.setRoute("report", ReportStub.class);
        routes.setRoute("review", ReviewStub.class);
    }

    /** The English bundle's text for a key, formatted as the views format it. */
    static String t(String key, Object... params) {
        String value = ENGLISH.getProperty(key);
        if (value == null) {
            throw new IllegalArgumentException("No English text for " + key);
        }
        return params.length == 0 ? value : new MessageFormat(value, Locale.ENGLISH).format(params);
    }


    /** The path of the view the current UI last navigated to. */
    static String activePath() {
        return UI.getCurrent().getInternals().getActiveViewLocation().getPath();
    }

    static Survey survey(int id, String name) {
        Survey survey = new Survey();
        survey.id = id;
        survey.name = name;
        survey.title = name + " title";
        survey.initialDisplayKey = "0001-0001";
        return survey;
    }

    static Respondent respondent(int id, Survey survey, boolean active) {
        Respondent respondent = new Respondent();
        respondent.id = id;
        respondent.survey = survey;
        respondent.active = active;
        respondent.accessCode = "code" + id;
        return respondent;
    }

    static ReportDefinition report(String name, String url) {
        ReportDefinition rpt = new ReportDefinition();
        rpt.name = name;
        rpt.url = url;
        return rpt;
    }

    /** Placeholder route for the landing page. */
    public static class HomeStub extends Div {
    }

    /** Placeholder route for the section page. */
    public static class SectionStub extends Div {
    }

    /** Placeholder route for the report page. */
    public static class ReportStub extends Div {
    }

    /** Placeholder route for the review page. */
    public static class ReviewStub extends Div {
    }

    /**
     * A session holder without the HTTP-session persistence behind it. {@link #restoreFromSession()}
     * answers with whatever {@link #restorable} holds; {@link #clear()} only resets the fields.
     */
    static class StubSession extends UISessionDataService {
        StubSession restorable;
        int restoreCalls;
        int clearCalls;

        @Override
        public boolean restoreFromSession() {
            restoreCalls++;
            if (restorable == null) {
                return false;
            }
            setSurveyId(restorable.getSurveyId());
            setRespondent(restorable.getRespondent());
            setNavResponse(restorable.getNavResponse());
            return true;
        }

        @Override
        public void clear() {
            clearCalls++;
            setSurveyId(null);
            setRespondent(null);
            setNavResponse(null);
        }
    }

    /** An access-code service with fixed surveys and a scripted login. */
    static class StubAccessCodeService extends AccessCodeService {
        List<Survey> surveys = new ArrayList<>();
        boolean autoRegisterFlag;
        BiFunction<Integer, String, Respondent> loginAnswer = (s, c) -> null;
        final List<String> logins = new ArrayList<>();

        @Override
        public HashMap<String, List<Survey>> getSurveys() {
            HashMap<String, List<Survey>> map = new HashMap<>();
            map.put("Surveys", surveys);
            return map;
        }

        @Override
        public boolean isAutoRegister() {
            return autoRegisterFlag;
        }

        @Override
        public Respondent login(int surveyId, String accessCode) {
            logins.add(surveyId + ":" + accessCode);
            return loginAnswer.apply(surveyId, accessCode);
        }
    }

    /** A question service whose {@code init} answers from a queue and records each call. */
    static class StubQuestionService extends QuestionService {
        final Queue<Object> initAnswers = new ArrayDeque<>();
        final List<String> initCalls = new ArrayList<>();
        final List<Integer> finalized = new ArrayList<>();
        ReviewResponse reviewAnswer;

        @Override
        public NavResponse init(int respondentId, String displaykey) {
            initCalls.add(respondentId + ":" + displaykey);
            Object next = initAnswers.poll();
            if (next instanceof RuntimeException e) {
                throw e;
            }
            return (NavResponse) next;
        }

        @Override
        public ReviewResponse review(int respondentId) {
            return reviewAnswer;
        }

        @Override
        public void finalize(int respondentId) {
            finalized.add(respondentId);
        }
    }

    /** A translator that serves the base text and a fixed content language. */
    static class StubTranslator extends ContentTranslator {
        String languageTag;

        @Override
        public String language(Survey survey) {
            return languageTag;
        }

        @Override
        public String name(Survey survey, ReportDefinition report, OffsetDateTime asOf) {
            return report.name;
        }

        @Override
        public String title(Survey survey, OffsetDateTime asOf) {
            return survey.title;
        }

        @Override
        public String description(Survey survey, OffsetDateTime asOf) {
            return survey.description;
        }
    }

    /** Finds the first descendant of a component with the given id. */
    static <T extends Component> T byId(Component root, String id, Class<T> type) {
        if (root.getId().filter(id::equals).isPresent() && type.isInstance(root)) {
            return type.cast(root);
        }
        for (Component child : root.getChildren().toList()) {
            T found = byId(child, id, type);
            if (found != null) {
                return found;
            }
        }
        return null;
    }
}
