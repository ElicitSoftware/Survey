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

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * JAX-RS resource handler for serving brand files with intelligent three-tier fallback logic.
 * This handler serves CSS, fonts, images, and other brand assets as static files with
 * the same fallback behavior as BrandStaticFileFilter.
 * <p>
 * Three-tier brand resource resolution system:
 * 1. External brand mount (brandFileSystemPath) - Docker volume mounts for runtime branding
 * 2. Local brand directory (brand/) - Development or embedded default brand
 * 3. Embedded resources (META-INF/brand/) - Final fallback for missing resources
 * <p>
 * Security Features:
 * - Path traversal protection (blocks ".." and "//" in paths)
 * - File existence validation
 * - Proper MIME type detection for various brand asset types
 * - HTTP caching headers for optimal performance
 */
@Path("/brand")
public class BrandResourceHandler {

    @ConfigProperty(name = "brand.file.system.path", defaultValue = "/brand")
    String brandFileSystemPath;

    /**
     * Serves brand files with three-tier fallback logic matching BrandStaticFileFilter:
     * 1. External brand mount (brandFileSystemPath)
     * 2. Local brand directory (brand/)
     * 3. Embedded resources (META-INF/brand/)
     * 
     * @param filePath The relative path to the brand file
     * @return HTTP response with the file content and appropriate media type, or 404 if not found
     */
    @GET
    @Path("/{filePath:.+}")
    public Response getBrandFile(@PathParam("filePath") String filePath) {
        // Security check: prevent directory traversal attacks
        if (filePath.contains("..") || filePath.contains("//")) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
        
        try {
            byte[] content = null;
            String mediaType = getMediaType(filePath);
            
            // 1. Try external brand mount first
            java.nio.file.Path externalBrandFile = Paths.get(brandFileSystemPath, filePath);
            if (Files.exists(externalBrandFile) && Files.isRegularFile(externalBrandFile)) {
                content = Files.readAllBytes(externalBrandFile);
            } 
            // 2. Try local brand directory (brand/) - NEW: matches BrandStaticFileFilter
            else {
                java.nio.file.Path localBrandFile = Paths.get("brand", filePath);
                if (Files.exists(localBrandFile) && Files.isRegularFile(localBrandFile)) {
                    content = Files.readAllBytes(localBrandFile);
                } 
                // 3. Try embedded resources as final fallback
                else {
                    content = readEmbeddedResource(filePath);
                }
            }
            
            if (content == null) {
                return Response.status(Response.Status.NOT_FOUND).build();
            }
            
            return Response.ok(content)
                    .type(mediaType)
                    .header("Cache-Control", "public, max-age=3600") // Cache for 1 hour
                    .build();
                    
        } catch (IOException e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Attempts to read a resource from the embedded META-INF/resources/ directory.
     * 
     * @param filePath The relative path to the resource
     * @return The file content as byte array, or null if not found
     */
    private byte[] readEmbeddedResource(String filePath) {
        try {
            // Try to load the resource from META-INF/brand/
            String resourcePath = "/META-INF/brand/" + filePath;
            InputStream inputStream = getClass().getResourceAsStream(resourcePath);
            
            if (inputStream != null) {
                try (inputStream) {
                    return inputStream.readAllBytes();
                }
            }
            
            return null;
        } catch (IOException e) {
            return null;
        }
    }
    
    /**
     * Determines the appropriate media type for a file based on its extension.
     * 
     * @param filePath The file path
     * @return The media type string
     */
    private String getMediaType(String filePath) {
        String extension = getFileExtension(filePath);
        
        return switch (extension.toLowerCase()) {
            case "css" -> "text/css";
            case "js" -> "application/javascript";
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "svg" -> "image/svg+xml";
            case "ico" -> "image/x-icon";
            case "woff" -> "font/woff";
            case "woff2" -> "font/woff2";
            case "ttf" -> "font/ttf";
            case "otf" -> "font/otf";
            case "json" -> MediaType.APPLICATION_JSON;
            default -> MediaType.APPLICATION_OCTET_STREAM;
        };
    }
    
    /**
     * Extracts the file extension from a file path.
     * 
     * @param filePath The file path
     * @return The file extension (without the dot)
     */
    private String getFileExtension(String filePath) {
        int lastDotIndex = filePath.lastIndexOf('.');
        if (lastDotIndex > 0 && lastDotIndex < filePath.length() - 1) {
            return filePath.substring(lastDotIndex + 1);
        }
        return "";
    }
}