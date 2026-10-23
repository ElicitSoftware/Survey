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

import com.elicitsoftware.flow.PlainViewTestSupport.StubSession;
import com.elicitsoftware.i18n.ElicitI18NProvider;
import com.elicitsoftware.i18n.LanguageSwitcher;
import com.elicitsoftware.i18n.LocaleSelection;
import com.elicitsoftware.util.BrandUtil;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.orderedlayout.Scroller;
import com.vaadin.flow.component.sidenav.SideNav;
import com.vaadin.flow.component.sidenav.SideNavItem;
import com.vaadin.flow.theme.lumo.LumoUtility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;

import static com.elicitsoftware.flow.PlainViewTestSupport.respondent;
import static com.elicitsoftware.flow.PlainViewTestSupport.survey;
import static com.elicitsoftware.flow.PlainViewTestSupport.t;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The application frame built by {@link MainLayout} in a plain browserless UI: the branded header
 * with its title, logo and language switcher (UC-009 BR-006), and the side navigation, which
 * offers Login before sign-in (UC-001) and Logout after it (UC-006) and is rebuilt after every
 * navigation.
 */
class MainLayoutHeaderNavTest extends PlainViewTestSupport {

    private StubSession session;
    private StubBrandUtil brands;
    private StubProvider provider;

    @BeforeEach
    void stubs() {
        session = new StubSession();
        brands = new StubBrandUtil();
        provider = new StubProvider();
    }

    private MainLayout build() {
        MainLayout layout = new MainLayout();
        layout.sessionDataService = session;
        layout.brandUtil = brands;
        layout.localeSelection = new LocaleSelection();
        layout.i18nProvider = provider;
        layout.init();
        return layout;
    }

    private static Div header(MainLayout layout) {
        return (Div) layout.getChildren()
                .filter(c -> c.hasClassName("branded-header"))
                .findFirst().orElseThrow();
    }

    private static Anchor title(MainLayout layout) {
        return header(layout).getChildren().filter(Anchor.class::isInstance).map(Anchor.class::cast)
                .findFirst().orElseThrow();
    }

    private static Image logo(MainLayout layout) {
        return header(layout).getChildren().filter(c -> c.hasClassName("logo-container"))
                .findFirst()
                .map(c -> (Image) c.getChildren().findFirst().orElseThrow())
                .orElse(null);
    }

    private static Scroller navScroller(MainLayout layout) {
        List<Scroller> scrollers = layout.getChildren().filter(Scroller.class::isInstance)
                .map(Scroller.class::cast).toList();
        assertEquals(1, scrollers.size(), "exactly one navigation scroller in the drawer");
        return scrollers.get(0);
    }

    private static List<String> navItems(MainLayout layout) {
        SideNav nav = (SideNav) navScroller(layout).getContent();
        return nav.getItems().stream().map(i -> i.getLabel() + "->" + i.getPath()).toList();
    }

    @Test
    void given_defaultBrand_when_init_then_headerShowsElicitTitleAndIcon() {
        brands.info = new BrandUtil.BrandInfo(BrandUtil.BrandInfo.DEFAULT_BRAND_KEY, "Elicit", "logo.png", "");

        MainLayout layout = build();

        Div header = header(layout);
        assertEquals(List.of("branded-header"), List.copyOf(header.getClassNames()),
                "an empty brand class is not added");
        assertTrue(header.getComponentAt(0) instanceof DrawerToggle);
        Anchor title = title(layout);
        assertEquals(t("common.appTitle.default", t("common.appType.survey")), title.getText());
        assertEquals("Elicit Survey", title.getText());
        assertEquals("/", title.getHref());
        assertTrue(title.hasClassName("brand-title"));
        Image logo = logo(layout);
        assertNotNull(logo);
        assertEquals("brand/images/icon-white.png", logo.getSrc());
        assertEquals(t("common.logoAlt", "Elicit"), logo.getAlt().orElseThrow());
        assertTrue(logo.hasClassName("logo"));
    }

    @Test
    void given_customBrand_when_init_then_headerCarriesBrandClassAndName() {
        brands.info = new BrandUtil.BrandInfo("acme", "Acme Health", "logo.png", "brand-acme");

        MainLayout layout = build();

        assertTrue(header(layout).hasClassName("brand-acme"));
        assertEquals(t("common.appTitle", "Acme Health", t("common.appType.survey")), title(layout).getText());
        assertEquals(t("common.logoAlt", "Acme Health"), logo(layout).getAlt().orElseThrow());
    }

    @Test
    void given_brandWithoutCssClass_when_init_then_onlyBaseClass() {
        brands.info = new BrandUtil.BrandInfo("acme", "Acme Health", "logo.png", null);

        MainLayout layout = build();

        assertEquals(1, header(layout).getClassNames().size());
    }

    @Test
    void given_noBrand_when_init_then_defaultTitleAndNoLogo() {
        brands.info = null;

        MainLayout layout = build();

        assertEquals("Elicit Survey", title(layout).getText());
        assertNull(logo(layout));
        assertEquals(1, header(layout).getClassNames().size());
    }

    @Test
    void given_iconPathUnavailable_when_init_then_headerIsBuiltWithoutLogo() {
        brands.info = new BrandUtil.BrandInfo("acme", "Acme Health", "logo.png", "brand-acme");
        brands.iconFailure = true;

        MainLayout layout = build();

        assertNull(logo(layout));
        assertEquals(t("common.appTitle", "Acme Health", t("common.appType.survey")), title(layout).getText());
    }

    @Test
    void given_twoLanguages_when_init_then_headerOffersVisibleSwitcher() {
        provider.locales = List.of(Locale.ENGLISH, Locale.forLanguageTag("es-419"));

        MainLayout layout = build();

        LanguageSwitcher switcher = header(layout).getChildren().filter(LanguageSwitcher.class::isInstance)
                .map(LanguageSwitcher.class::cast).findFirst().orElseThrow();
        assertTrue(switcher.isVisible());
    }

    @Test
    void given_noRespondent_when_init_then_navOffersAboutAndLogin() {
        MainLayout layout = build();

        assertEquals(List.of(t("sideNav.about") + "->about", t("sideNav.login") + "->"), navItems(layout));
        assertTrue(navScroller(layout).hasClassName(LumoUtility.Padding.SMALL));
    }

    @Test
    void given_signedInRespondent_when_init_then_navOffersAboutAndLogout() {
        session.setRespondent(respondent(3, survey(7, "Family"), true));

        MainLayout layout = build();

        assertEquals(List.of(t("sideNav.about") + "->about", t("sideNav.logout") + "->logout"), navItems(layout));
    }

    @Test
    void given_attachedLayout_when_respondentSignsInAndUiNavigates_then_navIsRebuiltWithLogout() {
        MainLayout layout = build();
        layout.setContent(new Div());
        UI.getCurrent().add(layout);
        Scroller before = navScroller(layout);
        session.setRespondent(respondent(3, survey(7, "Family"), true));

        UI.getCurrent().navigate("section");

        Scroller after = navScroller(layout);
        assertNotSame(before, after, "the drawer's navigation is replaced");
        assertFalse(before.getParent().isPresent(), "the old navigation is removed");
        assertEquals(List.of(t("sideNav.about") + "->about", t("sideNav.logout") + "->logout"), navItems(layout));
    }

    @Test
    void given_detachedLayout_when_uiNavigates_then_navIsNotRebuilt() {
        MainLayout layout = build();
        Scroller before = navScroller(layout);
        session.setRespondent(respondent(3, survey(7, "Family"), true));

        UI.getCurrent().navigate("section");

        assertTrue(before == navScroller(layout), "a layout that was never attached is not listening");
    }

    /** Answers with a fixed brand. */
    static class StubBrandUtil extends BrandUtil {
        BrandInfo info;
        boolean iconFailure;

        @Override
        public BrandInfo detectCurrentBrand() {
            return info;
        }

        @Override
        public String getIconResourcePath(BrandInfo brandInfo) {
            if (iconFailure) {
                throw new IllegalStateException("no icon");
            }
            return super.getIconResourcePath(brandInfo);
        }
    }

    /** Offers a fixed set of languages. */
    static class StubProvider extends ElicitI18NProvider {
        List<Locale> locales = List.of(Locale.ENGLISH);

        @Override
        public List<Locale> getProvidedLocales() {
            return locales;
        }
    }
}
