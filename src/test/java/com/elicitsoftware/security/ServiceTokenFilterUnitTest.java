package com.elicitsoftware.security;

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

import com.elicitsoftware.etl.ETLBuildResource;
import com.elicitsoftware.etl.ETLSchemaResource;
import com.elicitsoftware.flow.BrandResourceHandler;
import com.elicitsoftware.report.PDFDownloadResource;
import com.elicitsoftware.report.builtin.RespondentSummaryResource;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UC-016: the receiver's decisions without a running application. Which paths require the
 * platform service token (BR-001) and what a request is answered (main scenario, A1, A2).
 */
class ServiceTokenFilterUnitTest {

    private static final String TOKEN = TestServiceToken.VALUE;
    private final ServiceToken configured = ServiceToken.of(TOKEN, null);
    private final ServiceToken unset = ServiceToken.of(null, null);

    private static Method method(Class<?> resource, String name) {
        return Arrays.stream(resource.getMethods()).filter(m -> m.getName().equals(name)).findFirst().orElseThrow();
    }

    // ── BR-001: which paths are protected ─────────────────────────────────────

    @Test
    void uc016_br001_theResourcesUnderApiAreProtectedByTheirDeclaredPath() {
        String build = ServiceTokenFilter.declaredPath(ETLBuildResource.class, method(ETLBuildResource.class, "build"));
        String rename = ServiceTokenFilter.declaredPath(ETLSchemaResource.class, method(ETLSchemaResource.class, "rename"));
        String drop = ServiceTokenFilter.declaredPath(ETLSchemaResource.class, method(ETLSchemaResource.class, "drop"));
        String summary = ServiceTokenFilter.declaredPath(RespondentSummaryResource.class, method(RespondentSummaryResource.class, "render"));

        assertEquals("/api/etl/build", build);
        assertEquals("/api/etl/schema/{surveyKey}/rename", rename);
        assertEquals("/api/etl/schema/{surveyKey}", drop);
        assertEquals("/api/reports/respondent-summary", summary);
        for (String declared : new String[]{build, rename, drop, summary}) {
            // Whatever the request path looked like, the matched resource decides.
            assertTrue(ServiceTokenFilter.isProtected("/anything", declared), declared);
            assertTrue(ServiceTokenFilter.isProtected("/api/pdf/download", declared), declared);
        }
    }

    @Test
    void uc016_br001_thePdfDownloadIsTheOneOpenPathUnderApi() {
        String download = ServiceTokenFilter.declaredPath(PDFDownloadResource.class, method(PDFDownloadResource.class, "downloadPDF"));

        assertEquals("/api/pdf/download", download);
        assertFalse(ServiceTokenFilter.isProtected("/api/pdf/download", download));
        assertEquals(1, ServiceTokenFilter.OPEN.size(), "an allow-list of one");
    }

    @Test
    void uc016_br008_brandFilesNeedNoToken() {
        String brand = ServiceTokenFilter.declaredPath(BrandResourceHandler.class, method(BrandResourceHandler.class, "getBrandFile"));

        assertTrue(brand.startsWith("/brand/"), brand);
        assertFalse(ServiceTokenFilter.isProtected("/brand/theme.css", brand));
    }

    @Test
    void uc016_br001_aPathUnderApiIsProtectedUnlessListed() {
        for (String declared : new String[]{"/api/new", "/api/pdf", "/api/pdf/download/other", "/api/pdf/downloads",
                "/api/pdf/download/{key}", "/api", "/API/etl/build", "api/etl/build", "//api//etl/build", "/api/"}) {
            assertTrue(ServiceTokenFilter.isProtected("/x", declared), declared);
        }
        for (String requested : new String[]{"/api/etl/build", "api/etl/build", "/api", "//api/etl/build", "/Api/x"}) {
            assertTrue(ServiceTokenFilter.isProtected(requested, "/somewhere/else"), requested);
        }
        for (String open : new String[]{"/brand/logo.png", "/apiary", "/apis/x", "/", ""}) {
            assertFalse(ServiceTokenFilter.isProtected(open, open), open);
        }
        assertFalse(ServiceTokenFilter.isProtected(null, "/brand/{filePath:.+}"));
    }

    // ── main scenario, A1, A2: what a request is answered ─────────────────────

    @Test
    void uc016_main_theConfiguredTokenProceeds() {
        assertNull(ServiceTokenFilter.refusal(configured, "Bearer " + TOKEN));
        assertNull(ServiceTokenFilter.refusal(configured, "bearer " + TOKEN), "the scheme's case does not matter");
    }

    @Test
    void uc016_a1_missingMalformedAndWrongAreOneAnswer() {
        for (String header : new String[]{
                null,
                "",
                "Bearer",
                "Bearer ",
                "Bearer wrong",
                "Bearer " + TOKEN + "x",
                "Bearer " + TOKEN.substring(1),
                "Bearer  " + TOKEN,
                "Bearer " + TOKEN + " ",
                "Bearer " + TOKEN + "," + "Bearer " + TOKEN,
                TOKEN,
                "Basic " + TOKEN,
                "Token " + TOKEN,
                "Basic YWRtaW46YWRtaW4="}) {
            Response refusal = ServiceTokenFilter.refusal(configured, header);

            assertNotNull(refusal, String.valueOf(header));
            assertEquals(401, refusal.getStatus(), String.valueOf(header));
            assertEquals("Bearer", refusal.getHeaderString("WWW-Authenticate"));
            assertFalse(refusal.hasEntity(), "an empty body");
        }
    }

    @Test
    void uc016_a2_withNoTokenConfiguredEveryRequestIsRefusedAs503() {
        for (String header : new String[]{null, "Bearer " + TOKEN, "Bearer ", "Bearer " + ServiceToken.DEVELOPMENT_TOKEN}) {
            Response refusal = ServiceTokenFilter.refusal(unset, header);

            assertNotNull(refusal, "never open");
            assertEquals(503, refusal.getStatus());
            assertEquals("{\"error\":\"service token not configured\"}", refusal.getEntity());
            assertEquals("application/json", refusal.getMediaType().toString());
        }
    }
}
