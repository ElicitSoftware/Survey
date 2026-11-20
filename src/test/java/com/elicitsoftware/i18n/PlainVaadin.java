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

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.page.Page;
import com.vaadin.flow.component.page.PendingJavaScriptResult;
import com.vaadin.flow.di.DefaultInstantiator;
import com.vaadin.flow.di.Instantiator;
import com.vaadin.flow.i18n.I18NProvider;
import com.vaadin.flow.internal.CurrentInstance;
import com.vaadin.flow.router.BeforeEnterListener;
import com.vaadin.flow.server.VaadinServletService;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.shared.Registration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Hand-written Vaadin stand-ins for plain tests of the i18n package: a UI whose page records the
 * JavaScript it is asked to run and whose session is a lock-free stub, and a service that hands
 * out a chosen {@link I18NProvider}. Pitest can mutate code exercised through these, which it
 * cannot do through a running Vaadin servlet, and they cost nothing to set up per test.
 */
final class PlainVaadin {

    private PlainVaadin() {
    }

    /** A session that needs no service and considers its lock always held. */
    static final class StubSession extends VaadinSession {
        StubSession() {
            super(null);
        }

        @Override
        public boolean hasLock() {
            return true;
        }
    }

    /** One {@code executeJs} call. */
    record Js(String expression, List<Object> parameters) {
    }

    /** A page that records instead of sending. */
    static final class RecordingPage extends Page {
        final List<Js> executed = new ArrayList<>();

        RecordingPage(UI ui) {
            super(ui);
        }

        @Override
        public PendingJavaScriptResult executeJs(String expression, Object... parameters) {
            executed.add(new Js(expression, Arrays.asList(parameters)));
            return null;
        }
    }

    /**
     * A UI with a recording page and an optional stub session, made the current UI. Before-enter
     * listeners are kept here rather than in the UI internals, which need an attached session.
     */
    static final class PlainUi extends UI {
        final RecordingPage page = new RecordingPage(this);
        final List<BeforeEnterListener> beforeEnter = new ArrayList<>();
        VaadinSession session;

        /**
         * No-argument on purpose: the booted tests' build indexes the test classes and makes every
         * {@code UI} subclass a CDI bean, which a constructor with parameters would leave
         * unsatisfiable. Tests build one with {@link #of}.
         */
        PlainUi() {
        }

        static PlainUi of(Locale locale, VaadinSession session) {
            PlainUi ui = new PlainUi();
            ui.session = session;
            ui.setLocale(locale);
            UI.setCurrent(ui);
            return ui;
        }

        @Override
        public Page getPage() {
            return page;
        }

        @Override
        public VaadinSession getSession() {
            return session;
        }

        @Override
        public Registration addBeforeEnterListener(BeforeEnterListener listener) {
            beforeEnter.add(listener);
            return () -> beforeEnter.remove(listener);
        }
    }

    /** A service, not started, whose instantiator answers with {@code provider}. */
    static VaadinServletService service(I18NProvider provider) {
        return new VaadinServletService(null, null) {
            private final Instantiator instantiator = new DefaultInstantiator(this) {
                @Override
                public I18NProvider getI18NProvider() {
                    return provider;
                }
            };

            @Override
            public Instantiator getInstantiator() {
                return instantiator;
            }
        };
    }

    /** Forgets every current UI, session and service on this thread. */
    static void clear() {
        CurrentInstance.clearAll();
    }
}
