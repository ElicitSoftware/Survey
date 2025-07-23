package com.elicitsoftware.report;

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

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * Servlet for handling PDF downloads.
 * This servlet provides PDF download functionality without using deprecated StreamResource.
 * It retrieves PDF data from the session and streams it directly to the client.
 */
@WebServlet(urlPatterns = {"/pdf-download", "/pdf-download/*"})
public class PDFDownloadServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        
        String key = request.getParameter("key");
        if (key == null) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing key parameter");
            return;
        }

        byte[] pdfContent = (byte[]) request.getSession().getAttribute(key);
        if (pdfContent == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "PDF not found or expired");
            return;
        }

        // Clean up the session attribute after use
        request.getSession().removeAttribute(key);

        // Set response headers for PDF
        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition", "inline; filename=\"family_history_report.pdf\"");
        response.setContentLength(pdfContent.length);
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        response.setHeader("Pragma", "no-cache");
        response.setDateHeader("Expires", 0);

        // Write PDF content to response
        response.getOutputStream().write(pdfContent);
        response.getOutputStream().flush();
    }
}
