package com.elicitsoftware.report;

/*-
 * ***LICENSE_START***
 * Elicit Survey
 * %%
 * Copyright (C) 2026 The Regents of the University of Michigan - Rogel Cancer Center
 * %%
 * PolyForm Noncommercial License 1.0.0
 * <https://polyformproject.org/licenses/noncommercial/1.0.0>
 * ***LICENSE_END***
 */

import org.apache.batik.bridge.ExternalResourceSecurity;
import org.apache.batik.bridge.NoLoadExternalResourceSecurity;
import org.apache.batik.bridge.NoLoadScriptSecurity;
import org.apache.batik.bridge.ScriptSecurity;
import org.apache.batik.bridge.UserAgentAdapter;
import org.apache.batik.util.ParsedURL;

/**
 * The Batik user agent for every SVG this module renders into a PDF. The SVG comes from a report
 * service, so it is not trusted: Batik's default agent follows {@code xlink:href} to images,
 * fonts, stylesheets and scripts by URL, which lets an SVG make the server fetch any address it
 * names (CVE-2022-44729, CVE-2022-44730). This agent refuses every external resource and every
 * script; references inside the document itself ({@code #id}) still resolve.
 */
public class NoExternalResourcesUserAgent extends UserAgentAdapter {

    @Override
    public ExternalResourceSecurity getExternalResourceSecurity(ParsedURL resourceURL, ParsedURL docURL) {
        return new NoLoadExternalResourceSecurity();
    }

    @Override
    public ScriptSecurity getScriptSecurity(String scriptType, ParsedURL scriptURL, ParsedURL docURL) {
        return new NoLoadScriptSecurity(scriptType);
    }
}
