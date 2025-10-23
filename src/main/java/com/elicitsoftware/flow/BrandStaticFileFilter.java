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

import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Servlet filter that intercepts requests to /brand/* and serves them as static files
 * from the external brand directory mounted at /brand.
 * This filter runs before Vaadin's routing to handle brand resources directly.
 */
@WebFilter(urlPatterns = "/brand/*")
public class BrandStaticFileFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        
        String requestURI = httpRequest.getRequestURI();
        
        if (requestURI.startsWith("/brand/")) {
            // Remove /brand prefix to get relative path
            String relativePath = requestURI.substring("/brand/".length());
            
            // Security check - prevent directory traversal
            if (relativePath.contains("..") || relativePath.contains("//")) {
                httpResponse.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid path");
                return;
            }
            
            // Try external brand mount first, then fall back to local brand directory
            Path brandFile = null;
            Path externalBrandFile = Paths.get("/brand").resolve(relativePath);
            Path localBrandFile = Paths.get("brand").resolve(relativePath);
            
            if (Files.exists(externalBrandFile) && Files.isRegularFile(externalBrandFile)) {
                brandFile = externalBrandFile;
            } else if (Files.exists(localBrandFile) && Files.isRegularFile(localBrandFile)) {
                brandFile = localBrandFile;
            }
            
            if (brandFile == null) {
                httpResponse.sendError(HttpServletResponse.SC_NOT_FOUND, "File not found");
                return;
            }
            
            try {
                byte[] content = Files.readAllBytes(brandFile);
                String fileName = brandFile.getFileName().toString();
                String contentType = getContentType(fileName);
                
                httpResponse.setContentType(contentType);
                httpResponse.setHeader("Cache-Control", "public, max-age=3600");
                httpResponse.setContentLength(content.length);
                
                httpResponse.getOutputStream().write(content);
                httpResponse.getOutputStream().flush();
                
                return; // Don't continue the filter chain
                
            } catch (IOException e) {
                httpResponse.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Error reading file");
                return;
            }
        }
        
        // Continue the filter chain for non-brand requests
        chain.doFilter(request, response);
    }
    
    private String getContentType(String fileName) {
        String extension = getFileExtension(fileName);
        
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
            case "json" -> "application/json";
            default -> "application/octet-stream";
        };
    }
    
    private String getFileExtension(String fileName) {
        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex > 0 && lastDotIndex < fileName.length() - 1) {
            return fileName.substring(lastDotIndex + 1);
        }
        return "";
    }
}