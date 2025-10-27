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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * JAX-RS resource handler for serving external brand files from the /brand mount point.
 * This allows CSS, fonts, images, and other brand assets to be served as static files
 * when an external brand directory is mounted at /brand.
 */
@Path("/brand")
public class BrandResourceHandler {

    /**
     * Serves brand files from the external brand directory.
     * 
     * @param filePath The relative path to the brand file
     * @return HTTP response with the file content and appropriate media type
     */
    @GET
    @Path("/{filePath:.+}")
    public Response getBrandFile(@PathParam("filePath") String filePath) {
        try {
            java.nio.file.Path brandFile = Paths.get("/brand", filePath);
            
            // Security check: ensure the path is within the brand directory
            if (!brandFile.normalize().startsWith(Paths.get("/brand"))) {
                return Response.status(Response.Status.FORBIDDEN).build();
            }
            
            // Check if file exists
            if (!Files.exists(brandFile) || !Files.isRegularFile(brandFile)) {
                return Response.status(Response.Status.NOT_FOUND).build();
            }
            
            // Read file content
            byte[] content = Files.readAllBytes(brandFile);
            
            // Determine media type based on file extension
            String mediaType = getMediaType(filePath);
            
            return Response.ok(content)
                    .type(mediaType)
                    .header("Cache-Control", "public, max-age=3600") // Cache for 1 hour
                    .build();
                    
        } catch (IOException e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
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