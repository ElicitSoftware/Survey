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
 * JAX-RS resource handler for serving external brand files from the /brand mount point.
 * This allows CSS, fonts, images, and other brand assets to be served as static files
 * when an external brand directory is mounted at /brand.
 */
@Path("/brand")
public class BrandResourceHandler {

    @ConfigProperty(name = "brand.file.system.path", defaultValue = "/brand")
    String brandFileSystemPath;

    /**
     * Serves brand files from the external brand directory with fallback to embedded resources.
     * If a file is not found in the external brand directory, it will redirect to
     * the default version served by Quarkus from META-INF/resources/.
     * 
     * @param filePath The relative path to the brand file
     * @return HTTP response with the file content and appropriate media type, or redirect to fallback
     */
    @GET
    @Path("/{filePath:.+}")
    public Response getBrandFile(@PathParam("filePath") String filePath) {
        try {
            java.nio.file.Path brandFile = Paths.get(brandFileSystemPath, filePath);
            
            // Security check: ensure the path is within the brand directory
            if (!brandFile.normalize().startsWith(Paths.get(brandFileSystemPath))) {
                return Response.status(Response.Status.FORBIDDEN).build();
            }
            
            // Try to read from external brand directory first
            if (Files.exists(brandFile) && Files.isRegularFile(brandFile)) {
                byte[] content = Files.readAllBytes(brandFile);
                String mediaType = getMediaType(filePath);
                
                return Response.ok(content)
                        .type(mediaType)
                        .header("Cache-Control", "public, max-age=3600") // Cache for 1 hour
                        .build();
            } else {
                // Fallback: serve content directly from embedded resources
                byte[] fallbackContent = readEmbeddedResource(filePath);
                if (fallbackContent != null) {
                    String mediaType = getMediaType(filePath);
                    return Response.ok(fallbackContent)
                            .type(mediaType)
                            .header("Cache-Control", "public, max-age=3600") // Cache for 1 hour
                            .build();
                } else {
                    return Response.status(Response.Status.NOT_FOUND).build();
                }
            }
                    
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