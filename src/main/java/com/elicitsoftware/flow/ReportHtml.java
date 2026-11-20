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

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Safelist;

/**
 * Cleans a report's HTML before it is set on the page (UC-005 BR-021). An external report service's
 * address comes from the survey definition, so what it answers is not trusted: only the HTML a
 * report writes and the SVG a drawing is made of are kept, with no script, event handler, frame,
 * form or {@code foreignObject}.
 * <p>
 * The allowlist names are matched without regard to case, and jsoup keeps an SVG name as it was
 * written ({@code clipPath}, {@code viewBox}), so a drawing comes out as it went in.
 */
final class ReportHtml {

    /** The SVG elements a drawing is made of; none of them runs script or embeds HTML. */
    private static final String[] SVG_ELEMENTS = {
            "svg", "g", "defs", "symbol", "use", "title", "desc",
            "path", "rect", "circle", "ellipse", "line", "polyline", "polygon",
            "text", "tspan", "clippath", "mask", "pattern", "marker",
            "lineargradient", "radialgradient", "stop"
    };

    /** Geometry and presentation attributes of those elements. No event handler is among them. */
    private static final String[] SVG_ATTRIBUTES = {
            "id", "xmlns", "xmlns:xlink", "version", "viewbox", "preserveaspectratio",
            "x", "y", "x1", "y1", "x2", "y2", "cx", "cy", "r", "rx", "ry", "width", "height",
            "d", "points", "transform", "pathlength",
            "fill", "fill-opacity", "fill-rule", "stroke", "stroke-width", "stroke-opacity",
            "stroke-linecap", "stroke-linejoin", "stroke-miterlimit", "stroke-dasharray",
            "stroke-dashoffset", "opacity", "color", "visibility", "display",
            "clip-path", "clip-rule", "clippathunits", "mask", "marker-start", "marker-mid", "marker-end",
            "markerwidth", "markerheight", "refx", "refy", "orient", "markerunits",
            "patternunits", "patterncontentunits", "patterntransform",
            "gradientunits", "gradienttransform", "offset", "stop-color", "stop-opacity",
            "font-family", "font-size", "font-weight", "font-style", "text-anchor",
            "dominant-baseline", "dx", "dy", "textlength", "lengthadjust"
    };

    private static final Safelist REPORT = Safelist.relaxed()
            .addTags("hr")
            .addAttributes(":all", "class", "style", "dir", "lang")
            .addTags(SVG_ELEMENTS)
            .addAttributes(":all", SVG_ATTRIBUTES)
            .addAttributes("use", "href", "xlink:href");

    private ReportHtml() {
    }

    /**
     * Returns the report's HTML with everything outside the allowlist removed.
     *
     * @param html the content a report answered; {@code null} gives the empty string
     * @return the cleaned content, ready to set as {@code innerHTML}
     */
    static String sanitize(String html) {
        if (html == null) {
            return "";
        }
        Document clean = SafeHtml.clean(html, REPORT);
        // A use element may only point into the drawing it is part of (#id), never at another document.
        for (Element use : clean.select("use")) {
            for (String key : new String[]{"href", "xlink:href"}) {
                if (use.hasAttr(key) && !use.attr(key).startsWith("#")) {
                    use.removeAttr(key);
                }
            }
        }
        clean.outputSettings().prettyPrint(false);
        return clean.body().html();
    }
}
