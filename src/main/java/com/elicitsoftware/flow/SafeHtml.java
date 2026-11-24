package com.elicitsoftware.flow;

/*-
 * ***LICENSE_START***
 * Elicit Survey
 * %%
 * Copyright (C) 2025 - 2026 Elicit Software LLC
 * %%
 * PolyForm Noncommercial License 1.0.0
 * <https://polyformproject.org/licenses/noncommercial/1.0.0>
 * ***LICENSE_END***
 */

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Entities;
import org.jsoup.safety.Cleaner;
import org.jsoup.safety.Safelist;

/**
 * Cleans HTML from the survey definition before it is set on the page as {@code innerHTML}
 * (UC-002 BR-015). The definition is written in Author, may arrive by import or from an AI draft,
 * and is shown to every respondent, so only the formatting survey content is written with is
 * kept: no script, event handler, {@code javascript:} address, frame or form.
 * <p>
 * A report's content is cleaned by {@link ReportHtml}, which adds the SVG a drawing is made of.
 */
public final class SafeHtml {

    /** Text, headings, lists, tables, links and images ({@link Safelist#relaxed()}), plus rules. */
    private static final Safelist CONTENT = Safelist.relaxed()
            .addTags("hr")
            .addAttributes(":all", "class", "style", "dir", "lang");

    private SafeHtml() {
    }

    /**
     * Returns authored content HTML with everything outside the allowlist removed. A respondent's
     * answer substituted into the text was escaped beforehand (UC-002 BR-011) and stays literal.
     *
     * @param html the text of an HTML or MODAL question, or a survey's description; {@code null}
     *             gives the empty string
     * @return the cleaned content, ready to set as {@code innerHTML}
     */
    public static String content(String html) {
        return html == null ? "" : clean(html, CONTENT).body().html();
    }

    /**
     * Returns plain text escaped for use inside HTML, for a value that is shown as text, never as
     * markup (UC-002 BR-015: the survey's title on the About page).
     *
     * @param text the text; {@code null} gives the empty string
     * @return the text with {@code <}, {@code >}, {@code &} and quotes escaped
     */
    public static String text(String text) {
        return text == null ? "" : Entities.escape(text);
    }

    /**
     * Parses {@code html} as a body fragment and keeps only what {@code safelist} allows, with the
     * output left unindented so the markup reads as it was written.
     */
    static Document clean(String html, Safelist safelist) {
        Document clean = new Cleaner(safelist).clean(Jsoup.parseBodyFragment(html));
        clean.outputSettings().prettyPrint(false);
        return clean;
    }
}
