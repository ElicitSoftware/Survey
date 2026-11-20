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

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UC-005 BR-021: a report's content is cleaned before it reaches the page. What the reports draw
 * with is kept as it was sent; anything that could run or load script is removed.
 */
class ReportHtmlTest {

    private static String pedigree() throws IOException {
        try (InputStream in = ReportHtmlTest.class.getResourceAsStream("/report/pedigree-sample.svg")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** The markup as a browser would see it: element and attribute names, text, in order. */
    private static String shape(String html) {
        Document doc = Jsoup.parseBodyFragment(html);
        StringBuilder out = new StringBuilder();
        for (Element e : doc.body().getAllElements()) {
            out.append(e.tagName()).append(e.attributes().asList().stream()
                    .filter(a -> !a.getKey().startsWith("xmlns") && !a.getKey().equals("version"))
                    .map(a -> a.getKey() + "=" + a.getValue()).toList()).append('\n');
        }
        return out.toString();
    }

    @Test
    void given_aPedigreeDrawing_when_cleaned_then_everyElementAndAttributeIsKept() throws IOException {
        String drawing = pedigree();

        String clean = ReportHtml.sanitize(drawing);

        assertEquals(shape(drawing), shape(clean));
        assertEquals(156, Jsoup.parseBodyFragment(clean).select("use[xlink:href^=#glyph]").size());
    }

    @Test
    void given_theFamilyHistoryCards_when_cleaned_then_theyAreUnchanged() {
        String cards = "<div class=\"card\"><div class=\"card--header\"><p>Mother</p></div><div class=\"card--content\">"
                + "<span class=\"card--content--label\">Cancer</span><span class=\"card--content--response\">: Breast &amp; ovary</span><br>"
                + "<br><hr></div></div>";

        assertEquals(cards, ReportHtml.sanitize(cards));
    }

    @Test
    void given_theRespondentSummary_when_cleaned_then_itIsUnchanged() {
        String summary = "<div class=\"respondent-summary\" dir=\"rtl\"><p class=\"respondent-summary-meta\">Submitted</p>"
                + "<h5>Step</h5><dl><dt>Question</dt><dd><strong>Answer</strong></dd></dl></div>";

        assertEquals(summary, ReportHtml.sanitize(summary));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "<script>alert(1)</script>",
            "<img src=\"https://x/y.png\" onerror=\"alert(1)\">",
            "<svg onload=\"alert(1)\"><rect/></svg>",
            "<a href=\"javascript:alert(1)\">x</a>",
            "<iframe src=\"https://x\"></iframe>",
            "<svg><foreignObject><div onmouseover=\"alert(1)\">x</div></foreignObject></svg>",
            "<svg><script>alert(1)</script></svg>",
            "<svg><animate attributeName=\"href\" values=\"javascript:alert(1)\"/></svg>",
            "<svg><set attributeName=\"onload\" to=\"alert(1)\"/></svg>",
            "<svg><a xlink:href=\"javascript:alert(1)\"><text>x</text></a></svg>",
            "<style>body{background:url(https://x)}</style>",
            "<form action=\"https://x\"><input name=\"code\"></form>",
            "<object data=\"https://x\"></object><embed src=\"https://x\">",
            "<p style=\"x\" onclick=\"alert(1)\">x</p>"
    })
    void given_markupThatCouldRunScript_when_cleaned_then_nothingOfItRemains(String hostile) {
        String clean = ReportHtml.sanitize(hostile).toLowerCase();

        for (String bad : new String[]{"<script", "alert", "javascript:", "<iframe", "<foreignobject", "<animate",
                "<set", "<style", "<form", "<input", "<object", "<embed", "onerror", "onload", "onclick", "onmouseover"}) {
            assertFalse(clean.contains(bad), () -> bad + " survived in " + clean);
        }
    }

    @Test
    void given_aUseThatPointsOutsideTheDrawing_when_cleaned_then_itsReferenceIsDropped() {
        String clean = ReportHtml.sanitize("<svg><use xlink:href=\"https://x/sprite.svg#a\"/><use href=\"data:image/svg+xml,x\"/>"
                + "<use xlink:href=\"#glyph-0-0\" x=\"1\"/></svg>");

        Document doc = Jsoup.parseBodyFragment(clean);
        assertEquals(1, doc.select("use[xlink:href]").size());
        assertEquals("#glyph-0-0", doc.select("use[xlink:href]").attr("xlink:href"));
        assertTrue(doc.select("use[href]").isEmpty());
    }

    @Test
    void given_noContent_when_cleaned_then_theResultIsEmpty() {
        assertEquals("", ReportHtml.sanitize(null));
        assertEquals("", ReportHtml.sanitize(""));
    }
}
