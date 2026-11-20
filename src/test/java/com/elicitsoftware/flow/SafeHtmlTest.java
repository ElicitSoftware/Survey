package com.elicitsoftware.flow;

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

import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UC-002 BR-015: the text of an HTML or MODAL question and a survey's description keep the
 * formatting survey content is written with and lose anything that could run script; the title on
 * the About page is text.
 */
class SafeHtmlTest {

    @Test
    void given_scriptAndEventHandlers_when_cleaned_then_theyAreRemoved() {
        String html = SafeHtml.content("<p onclick=\"steal()\">Hi</p><script>steal()</script>"
                + "<img src=x onerror=steal()><svg onload=steal()></svg>");

        assertFalse(html.contains("script"), html);
        assertFalse(html.contains("steal"), html);
        assertFalse(html.contains("<svg"), html);
        assertTrue(html.startsWith("<p>Hi</p>"), html);
    }

    @Test
    void given_javascriptLinkFrameAndForm_when_cleaned_then_onlyTheirTextRemains() {
        String html = SafeHtml.content("<a href=\"javascript:steal()\">go</a>"
                + "<iframe src=\"https://evil.example\"></iframe>"
                + "<form action=\"https://evil.example\"><input name=code></form>");

        assertEquals("<a>go</a>", html);
    }

    @Test
    void given_formatting_when_cleaned_then_itIsKept() {
        String html = "<h2 class=\"intro\" dir=\"rtl\" lang=\"ar\">T</h2><hr><ul><li><b>a</b></li></ul>"
                + "<dl><dt>x</dt><dd><code>y</code></dd></dl><a href=\"https://example.org\">link</a>"
                + "<p style=\"color: red\"><em>e</em><i>i</i><br></p>";

        assertEquals(html, SafeHtml.content(html));
    }

    @Test
    void given_anEscapedRespondentAnswer_when_cleaned_then_itStaysLiteral() {
        // UC-002 BR-011: the answer "<b>Bob</b>" was escaped before it was spliced in.
        String html = "<p>Hello &lt;b&gt;Bob&lt;/b&gt; &amp; O'Brien</p>";

        assertEquals(html, SafeHtml.content(html));
    }

    @Test
    void given_null_when_cleaned_then_empty() {
        assertEquals("", SafeHtml.content(null));
        assertEquals("", SafeHtml.text(null));
    }

    @Test
    void given_aTitleWithMarkup_when_escapedAsText_then_itCannotBecomeMarkup() {
        assertEquals("&lt;img src=x onerror=steal()&gt; &amp; Co", SafeHtml.text("<img src=x onerror=steal()> & Co"));
    }

    /**
     * Every HTML and MODAL question text and survey description in the definitions this platform
     * ships, with its placeholders resolved to their defaults as the runtime falls back to them,
     * renders as it was written: the same document once parsed.
     */
    @ParameterizedTest
    @MethodSource("shippedContent")
    void given_shippedContent_when_cleaned_then_itRendersUnchanged(String authored) {
        String rendered = authored.replaceAll("\\{[^{}|]*\\|([^{}]*)}", "$1");

        assertEquals(asParsed(rendered), SafeHtml.content(rendered));
    }

    static Stream<String> shippedContent() throws IOException {
        try (InputStream in = Objects.requireNonNull(
                SafeHtmlTest.class.getResourceAsStream("/content/definition-content.txt"))) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).lines()
                    .filter(line -> !line.isBlank() && !line.startsWith("#"))
                    .toList().stream();
        }
    }

    private static String asParsed(String html) {
        var document = Jsoup.parseBodyFragment(html);
        document.outputSettings().prettyPrint(false);
        return document.body().html();
    }
}
