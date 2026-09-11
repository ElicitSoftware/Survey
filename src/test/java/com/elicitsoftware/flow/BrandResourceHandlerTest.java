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

import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * BrandResourceHandler.brandFileSystemPath is package-private and settable directly from a
 * test in the same package, exercising the tier-1 (external mount) path with a real @TempDir.
 * Tier 3 (embedded resource) is exercised against theme.css, a real file already shipped
 * under src/main/resources/META-INF/brand/ -- no test-only fixture needed. Tier 2 (a hardcoded
 * relative ./brand/ directory) is not exercised: it isn't configurable and creating files
 * relative to the process's working directory during a test would be an unwanted side effect.
 */
class BrandResourceHandlerTest {

    private static BrandResourceHandler handler(Path mountDir) {
        BrandResourceHandler handler = new BrandResourceHandler();
        handler.brandFileSystemPath = mountDir.toString();
        return handler;
    }

    @Test
    void getBrandFile_pathContainsDotDot_returnsBadRequest(@TempDir Path mountDir) {
        Response response = handler(mountDir).getBrandFile("../../etc/passwd");
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
    }

    @Test
    void getBrandFile_pathContainsDoubleSlash_returnsBadRequest(@TempDir Path mountDir) {
        Response response = handler(mountDir).getBrandFile("images//logo.png");
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
    }

    @Test
    void getBrandFile_externalMountHasFile_servesItWithCacheHeader(@TempDir Path mountDir) throws IOException {
        Files.writeString(mountDir.resolve("theme.css"), "body { color: red; }");

        Response response = handler(mountDir).getBrandFile("theme.css");

        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        assertEquals("body { color: red; }", new String((byte[]) response.getEntity()));
        assertEquals("text/css", response.getMediaType().toString());
        assertEquals("public, max-age=3600", response.getHeaderString("Cache-Control"));
    }

    @Test
    void getBrandFile_noExternalMountFile_fallsBackToEmbeddedResource(@TempDir Path emptyMountDir) {
        // theme.css ships under src/main/resources/META-INF/brand/ - real embedded content.
        Response response = handler(emptyMountDir).getBrandFile("theme.css");

        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        assertNotNull(response.getEntity());
        assertTrue(((byte[]) response.getEntity()).length > 0);
    }

    @Test
    void getBrandFile_notFoundAnywhere_returnsNotFound(@TempDir Path emptyMountDir) {
        Response response = handler(emptyMountDir).getBrandFile("does-not-exist-anywhere.xyz");
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());
    }

    @Test
    void getBrandFile_unreadableExternalFile_returnsInternalServerError(@TempDir Path mountDir) throws IOException {
        Path file = mountDir.resolve("theme.css");
        Files.writeString(file, "body {}");
        File asFile = file.toFile();
        boolean permissionChangeSupported = asFile.setReadable(false);

        try {
            Response response = handler(mountDir).getBrandFile("theme.css");
            if (permissionChangeSupported && !asFile.canRead()) {
                assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
            }
            // On platforms/users where the permission change has no effect (e.g. running as
            // root), the file remains readable and this scenario can't be forced - skip silently.
        } finally {
            asFile.setReadable(true);
        }
    }

    @Test
    void getBrandFile_svgExtension_mapsToSvgMediaType(@TempDir Path mountDir) throws IOException {
        Files.writeString(mountDir.resolve("icon.svg"), "<svg></svg>");
        Response response = handler(mountDir).getBrandFile("icon.svg");
        assertEquals("image/svg+xml", response.getMediaType().toString());
    }

    @Test
    void getBrandFile_woff2Extension_mapsToFontMediaType(@TempDir Path mountDir) throws IOException {
        Files.write(mountDir.resolve("font.woff2"), new byte[]{1, 2, 3});
        Response response = handler(mountDir).getBrandFile("font.woff2");
        assertEquals("font/woff2", response.getMediaType().toString());
    }

    @Test
    void getBrandFile_unknownExtension_mapsToOctetStream(@TempDir Path mountDir) throws IOException {
        Files.writeString(mountDir.resolve("data.bin"), "x");
        Response response = handler(mountDir).getBrandFile("data.bin");
        assertEquals("application/octet-stream", response.getMediaType().toString());
    }

    @Test
    void getBrandFile_noExtension_mapsToOctetStream(@TempDir Path mountDir) throws IOException {
        Files.writeString(mountDir.resolve("noext"), "x");
        Response response = handler(mountDir).getBrandFile("noext");
        assertEquals("application/octet-stream", response.getMediaType().toString());
    }
}
