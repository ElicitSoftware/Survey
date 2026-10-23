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

import com.elicitsoftware.i18n.PlainVaadin.PlainUi;
import com.elicitsoftware.i18n.PlainVaadin.StubSession;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.Location;
import com.vaadin.flow.router.NavigationTrigger;
import com.vaadin.flow.router.QueryParameters;
import com.vaadin.flow.router.Router;
import com.vaadin.flow.server.RouteRegistry;
import com.vaadin.flow.server.ServiceInitEvent;
import com.vaadin.flow.server.VaadinService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UC-009: Complete the Survey in My Language (BR-003, BR-004) -- every new UI takes the language
 * remembered in its session and has its layout applied, and a {@code ?lang=} parameter selects a
 * language before the view is built.
 * <p>
 * Plain: the UI-init listener is fired on a {@link PlainUi} by an unstarted service, and the
 * before-enter listener it registers is invoked with a hand-built event, so no route is needed.
 */
class LocaleInitializerTest {

    private static final Locale ARABIC = Locale.forLanguageTag("ar");

    /** A router over a registry that is never consulted: the listener reads only the location. */
    private static final Router ROUTER = new Router((RouteRegistry) Proxy.newProxyInstance(
            RouteRegistry.class.getClassLoader(), new Class<?>[]{RouteRegistry.class}, (proxy, method, args) -> null));

    private final List<Locale> laidOut = new ArrayList<>();
    private final List<Locale> applied = new ArrayList<>();
    private LocaleInitializer initializer;
    private PlainUi ui;

    @BeforeEach
    void setUp() {
        ElicitI18NProvider provider = new ElicitI18NProvider();
        provider.bundledLocales = "en,es-419,ar";
        LocaleSelection selection = new LocaleSelection() {
            @Override
            public void apply(UI target, Locale locale) {
                applied.add(locale);
            }
        };
        selection.provider = provider;
        initializer = new LocaleInitializer();
        initializer.selection = selection;
        initializer.layout = new LocaleLayout() {
            @Override
            public void apply(UI target, Locale locale) {
                laidOut.add(locale);
            }
        };
        ui = PlainUi.of(Locale.ENGLISH, new StubSession());
    }

    @AfterEach
    void tearDown() {
        PlainVaadin.clear();
    }

    /** Registers the initializer on the running service and fires its UI-init listener on {@code ui}. */
    private void initUi() {
        VaadinService service = PlainVaadin.service(null);
        initializer.serviceInit(new ServiceInitEvent(service));
        service.fireUIInitListeners(ui);
        assertEquals(1, ui.beforeEnter.size(),
                "the initializer registers exactly one before-enter listener");
    }

    private void enter(QueryParameters parameters) {
        ui.beforeEnter.getFirst().beforeEnter(new BeforeEnterEvent(ROUTER, NavigationTrigger.PROGRAMMATIC,
                new Location("login", parameters), Div.class, ui, List.of()));
    }

    @Test
    void given_sessionWithRememberedLanguage_when_uiCreated_then_thatLanguageAndItsLayout() {
        ui.getSession().setAttribute(LocaleSelection.SESSION_ATTRIBUTE, ARABIC);

        initUi();

        assertEquals(ARABIC, ui.getLocale());
        assertEquals(List.of(ARABIC), laidOut);
    }

    @Test
    void given_sessionWithoutChoice_when_uiCreated_then_negotiatedLanguageLaidOut() {
        initUi();

        assertEquals(Locale.ENGLISH, ui.getLocale());
        assertEquals(List.of(Locale.ENGLISH), laidOut);
    }

    @Test
    void given_langParameter_when_entering_then_languageSelected() {
        initUi();

        enter(QueryParameters.of(LocaleInitializer.LANG_PARAMETER, "ar"));

        assertEquals(List.of(ARABIC), applied);
    }

    @Test
    void given_langParameterForLanguageInForce_when_entering_then_nothingReapplied() {
        initUi();

        enter(QueryParameters.of(LocaleInitializer.LANG_PARAMETER, "en"));

        assertTrue(applied.isEmpty());
    }

    @Test
    void given_unofferedOrMissingLangParameter_when_entering_then_nothingApplied() {
        initUi();

        enter(QueryParameters.of(LocaleInitializer.LANG_PARAMETER, "fr"));
        enter(QueryParameters.empty());

        assertTrue(applied.isEmpty());
    }
}
