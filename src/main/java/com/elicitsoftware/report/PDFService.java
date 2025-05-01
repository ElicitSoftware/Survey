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

import com.elicitsoftware.report.pdf.Content;
import com.elicitsoftware.report.pdfbox.Column;
import com.elicitsoftware.report.pdfbox.Table;
import com.elicitsoftware.report.pdfbox.TableBuilder;
import com.vaadin.flow.server.StreamResource;
import de.rototor.pdfbox.graphics2d.PdfBoxGraphics2D;
import de.rototor.pdfbox.graphics2d.PdfBoxGraphics2DFontTextDrawer;
import jakarta.enterprise.context.RequestScoped;
import org.apache.batik.anim.dom.SAXSVGDocumentFactory;
import org.apache.batik.bridge.BridgeContext;
import org.apache.batik.bridge.GVTBuilder;
import org.apache.batik.bridge.UserAgentAdapter;
import org.apache.batik.gvt.GraphicsNode;
import org.apache.batik.util.XMLResourceDescriptor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.util.Matrix;
import org.w3c.dom.svg.SVGDocument;

import java.awt.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@RequestScoped
public class PDFService {

    private static final PDRectangle PAGE_SIZE = PDRectangle.LETTER;
    static final float HEADER_MARGIN = 20f;
    static final float TEXT_MARGIN = 40f;
    static final float PADDING = 20f;
    // Font configuration
    static final PDFont TEXT_FONT = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    static final float FONT_SIZE = 10f;
    static final float LEADING = FONT_SIZE;

    // Table configuration
    private static final float ROW_HEIGHT = 15;
    private static final float CELL_MARGIN = 2;

    PDDocument document;
    PDPage page;
    PDPageContentStream contentStream;
    float yPosition;
    float pageHeight;
    float pageWidth;

    public StreamResource generatePDF(ArrayList<ReportResponse> reportResponses) {
        try {
            // Create a new document
            document = new PDDocument();
            page = new PDPage(PDRectangle.LETTER);
            document.addPage(page);
            contentStream = new PDPageContentStream(document, page);
            contentStream.setFont(TEXT_FONT, FONT_SIZE); // Ensure font is set

            pageHeight = PDRectangle.LETTER.getHeight();
            pageWidth = PDRectangle.LETTER.getWidth();
            yPosition = pageHeight - TEXT_MARGIN;

            for (ReportResponse response : reportResponses) {
                // Close the current content stream if a new page is needed
                if (response.pdf.pageBreak) {
                    page = new PDPage(PDRectangle.LETTER);
                    document.addPage(page);
                    yPosition = pageHeight - TEXT_MARGIN;
                }

                addTitleBlock(response.pdf.title);

                for (Content content : response.pdf.content) {
                    if (content == null) {
                        System.out.println("Content is null");
                    }
                    // Close the current content stream if a new page is needed
                    if (yPosition < TEXT_MARGIN + FONT_SIZE) {
                        page = new PDPage(PDRectangle.LETTER);
                        document.addPage(page);
                        yPosition = pageHeight - TEXT_MARGIN;
                    }

                    // Add the content
                    if (content.svg != null) {
                        addSVG(content);
                    } else if (content.table != null) {
                        drawTable(createContent(content));
                    } else {
                        addTextBlock(content.text);
                    }
                }
            }

            addHeadersAndFooters();

            if (contentStream != null) {
                contentStream.close(); // Ensure the stream is closed
            }

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            document.save(outputStream);
            ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());
            return new StreamResource("family_history_report.pdf", () -> inputStream);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void addTitleBlock(String title) throws IOException {
        if (title != null && !title.isEmpty()) {
            //Add some space between the last item and the new title.
            yPosition -= HEADER_MARGIN;
            // Check if we need a new page
            if (yPosition < FONT_SIZE) {
                page = new PDPage(PDRectangle.LETTER);
                document.addPage(page);
                contentStream.close();
                contentStream = new PDPageContentStream(document, page);
                contentStream.setFont(TEXT_FONT, FONT_SIZE); // Ensure font is set
            }
            PDRectangle mediaBox = page.getMediaBox();
            float width = mediaBox.getWidth() - 2 * PADDING;
            // Begin text
            contentStream.beginText();
            contentStream.setFont(TEXT_FONT, FONT_SIZE); // Ensure font is set before writing text
            contentStream.newLineAtOffset(PADDING, yPosition);
            contentStream.showText(title);
            contentStream.endText();
            // Update Y position
            yPosition -= LEADING;
        }
    }

    public void addTextBlock(String text) throws IOException {
        // Check if we need a new page
        if (yPosition < PADDING + FONT_SIZE) {
            page = new PDPage(PDRectangle.LETTER);
            document.addPage(page);
            contentStream.close();
            contentStream = new PDPageContentStream(document, page);
            contentStream.setFont(TEXT_FONT, FONT_SIZE); // Ensure font is set
        }
        PDRectangle mediaBox = page.getMediaBox();
        float width = mediaBox.getWidth() - 2 * PADDING;

        List<String> lines = wrapText(text, TEXT_FONT, FONT_SIZE, width);

        // Begin text
        for (String line : lines) {
            contentStream.beginText();
            contentStream.setFont(TEXT_FONT, FONT_SIZE); // Ensure font is set before writing text
            contentStream.newLineAtOffset(PADDING, yPosition);
            contentStream.showText(line);
            contentStream.endText();
            yPosition -= FONT_SIZE + 2; // Add some line spacing
        }
        // Update Y position
        yPosition -= LEADING;
    }

    void addSVG(Content content) throws IOException {
        PDRectangle landscape = new PDRectangle(PDRectangle.LETTER.getHeight(), PDRectangle.LETTER.getWidth());

        //Create a new page and open a new content stream.
        contentStream.close();
        page = document.getPage(document.getNumberOfPages() - 1);
        page.setMediaBox(landscape);
        contentStream = new PDPageContentStream(document, page);

        float pageWidth = landscape.getWidth();
        float pageHeight = landscape.getHeight();

        try {
            PdfBoxGraphics2D graphics2D = new PdfBoxGraphics2D(document, (int) pageWidth, (int) pageHeight);
            graphics2D.setFontTextDrawer(new PdfBoxGraphics2DFontTextDrawer());

            // Parse the SVG
            String parser = XMLResourceDescriptor.getXMLParserClassName();
            SAXSVGDocumentFactory factory = new SAXSVGDocumentFactory(parser);
            SVGDocument svgDocument = factory.createSVGDocument(null, new StringReader(content.svg));

            // Build GVT and calculate bounds
            GVTBuilder builder = new GVTBuilder();
            BridgeContext ctx = new BridgeContext(new UserAgentAdapter());
            GraphicsNode graphicsNode = builder.build(ctx, svgDocument);

            // Compute SVG dimensions
            Rectangle bounds = graphicsNode.getBounds().getBounds();

            // Calculate scale to fit
            double scaleX = pageWidth / bounds.getWidth();
            double scaleY = pageHeight / bounds.getHeight();
            double scale = Math.min(scaleX, scaleY); // Preserve aspect ratio

            graphics2D.scale(scale, scale);

            // Optional: center the image
            double translateX = (pageWidth / scale - bounds.getWidth()) / 2.0;
            double translateY = (pageHeight / scale - bounds.getHeight()) / 2.0;
            translateY += TEXT_MARGIN; // Add a margin to the top of the page
            graphics2D.translate(translateX - bounds.getX(), translateY - bounds.getY());

            graphicsNode.paint(graphics2D);
            graphics2D.dispose();

            contentStream.drawForm(graphics2D.getXFormObject());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static Table createContent(Content content) {

        // Total size of columns must not be greater than table width.
        List<Column> columns = new ArrayList<>();
        int i = 0;
        for (String header : content.table.headers) {
            columns.add(new Column(header, Float.valueOf(content.table.widths[i])));
            i++;
        }

        String[][] tableContent = content.table.body;

//        float tableHeight = content.landscape ? PAGE_SIZE.getWidth() - TEXT_MARGIN : PAGE_SIZE.getHeight() - TEXT_MARGIN;
        float tableHeight = PAGE_SIZE.getHeight() - TEXT_MARGIN;

        Table table = new TableBuilder()
                .setCellMargin(CELL_MARGIN)
                .setColumns(columns)
                .setContent(tableContent)
                .setHeight(tableHeight)
                .setNumberOfRows(tableContent.length)
                .setRowHeight(ROW_HEIGHT)
                .setMargin(PADDING)
                .setPageSize(PAGE_SIZE)
                .setLandscape(false)
                .setTextFont(TEXT_FONT)
                .setFontSize(FONT_SIZE)
                .build();
        return table;
    }

    void addHeadersAndFooters() {
        // Step 2: Add header and footer to each page
        int totalPages = document.getNumberOfPages();
        for (int i = 0; i < totalPages; i++) {
            PDPage page = document.getPage(i);
            try (PDPageContentStream contentStream = new PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
                PDRectangle mediaBox = page.getMediaBox();
                float yTop = mediaBox.getHeight() - HEADER_MARGIN;
                float yBottom = HEADER_MARGIN;

                // Header
                contentStream.beginText();
                contentStream.setFont(TEXT_FONT, 10);
                contentStream.newLineAtOffset(PADDING, yTop);
                contentStream.showText("Document Header - Page " + (i + 1) + " of " + totalPages);
                contentStream.endText();

                // Footer
                contentStream.beginText();
                contentStream.setFont(TEXT_FONT, 10);
                contentStream.newLineAtOffset(PADDING, yBottom - 10);
                contentStream.showText("Confidential Footer - Page " + (i + 1) + " of " + totalPages);
                contentStream.endText();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    // Configures basic setup for the table and draws it page by page
    public void drawTable(Table table) throws IOException {
        // Calculate pagination
        Integer rowsPerPage = (int) Math.floor(table.getHeight() / table.getRowHeight()) - 1;
        Integer numberOfPages = (int) Math.ceil(table.getNumberOfRows().floatValue() / rowsPerPage);

        // Generate each page, get the content and draw it
        for (int pageCount = 0; pageCount < numberOfPages; pageCount++) {
            PDPageContentStream contentStream = null;
            try {
                contentStream = generateContentStream(table);
                String[][] currentPageContent = getContentForCurrentPage(table, rowsPerPage, pageCount);
                drawCurrentPage(table, currentPageContent);
            } finally {
                if (contentStream != null) {
                    contentStream.close(); // Ensure the stream is closed
                }
            }
        }
    }

    private PDPageContentStream generateContentStream(Table table) throws IOException {

        PDPageContentStream contentStream = new PDPageContentStream(document, document.getPage(document.getNumberOfPages() - 1), PDPageContentStream.AppendMode.APPEND, false);
        // User transformation matrix to change the reference when drawing.
        // This is necessary for the landscape position to draw correctly
        if (table.isLandscape()) {
            contentStream.transform(new Matrix(0, 1, -1, 0, table.getPageSize().getWidth(), 0));
        }
        contentStream.setFont(table.getTextFont(), table.getFontSize());
        return contentStream;
    }

    private String[][] getContentForCurrentPage(Table table, Integer rowsPerPage, int pageCount) {
        int startRange = pageCount * rowsPerPage;
        int endRange = (pageCount * rowsPerPage) + rowsPerPage;
        if (endRange > table.getNumberOfRows()) {
            endRange = table.getNumberOfRows();
        }
        return Arrays.copyOfRange(table.getContent(), startRange, endRange);
    }

    // Draws current page table grid and borderlines and content
    private void drawCurrentPage(Table table, String[][] currentPageContent)
            throws IOException {
        PDPage page = document.getPage(document.getNumberOfPages() - 1);
//        float tableTopY = table.isLandscape() ? table.getPageSize().getWidth() - table.getMargin() : table.getPageSize().getHeight() - table.getMargin();
        float tableTopY = yPosition;
//        if (yPosition == TEXT_MARGIN) {
//            tableTopY = table.isLandscape() ? table.getPageSize().getWidth() - table.getMargin() : table.getPageSize().getHeight() - table.getMargin();
//        } else {
//            tableTopY = yPosition - table.getMargin();
//        }

        // Draws grid and borders
        drawTableGrid(table, currentPageContent, tableTopY);

        // Position cursor to start drawing content
        float nextTextX = table.getMargin() + table.getCellMargin();
        // Calculate center alignment for text in cell considering font height
        float nextTextY = tableTopY - (table.getRowHeight() / 2)
                - ((table.getTextFont().getFontDescriptor().getFontBoundingBox().getHeight() / 1000 * table.getFontSize()) / 4);

        // Write column headers
        writeContentLine(table.getColumnsNamesAsArray(), nextTextX, nextTextY, table);
        nextTextY -= table.getRowHeight();
        nextTextX = table.getMargin() + table.getCellMargin();

        // Write content
        for (int i = 0; i < currentPageContent.length; i++) {
            writeContentLine(currentPageContent[i], nextTextX, nextTextY, table);
            nextTextY -= table.getRowHeight();
            nextTextX = table.getMargin() + table.getCellMargin();
        }
        yPosition = nextTextY;
    }

    // Writes the content for one line
    private void writeContentLine(String[] lineContent, float nextTextX, float nextTextY,
                                  Table table) throws IOException {
        for (int i = 0; i < table.getNumberOfColumns(); i++) {
            String text = lineContent[i];
            contentStream.beginText();
            contentStream.newLineAtOffset(nextTextX, nextTextY);
            contentStream.showText(text != null ? text : "");
            contentStream.endText();
            nextTextX += table.getColumns().get(i).getWidth();
        }
    }

    private void drawTableGrid(Table table, String[][] currentPageContent, float tableTopY)
            throws IOException {
        // Draw row lines
        float nextY = tableTopY;
        for (int i = 0; i <= currentPageContent.length + 1; i++) {
            contentStream.moveTo(table.getMargin(), nextY);
            contentStream.lineTo(table.getMargin() + table.getWidth(), nextY);
            contentStream.stroke();
            nextY -= table.getRowHeight();
        }

        // Draw column lines
        final float tableYLength = table.getRowHeight() + (table.getRowHeight() * currentPageContent.length);
        final float tableBottomY = tableTopY - tableYLength;
        float nextX = table.getMargin();
        for (int i = 0; i < table.getNumberOfColumns(); i++) {
            contentStream.moveTo(nextX, tableTopY);
            contentStream.lineTo(nextX, tableBottomY);
            contentStream.stroke();
            nextX += table.getColumns().get(i).getWidth();
        }
        contentStream.moveTo(nextX, tableTopY);
        contentStream.lineTo(nextX, tableBottomY);
        contentStream.stroke();

    }

    public static List<String> wrapText(String text, PDFont font, float fontSize, float maxWidth) throws IOException {
        List<String> lines = new ArrayList<>();
        String[] words = new String[0];
        if (text != null && text.length() > 0) {
            words = text.split(" ");
        }
        StringBuilder currentLine = new StringBuilder();

        for (String word : words) {
            String lineWithWord = currentLine.length() == 0 ? word : currentLine + " " + word;
            float size = font.getStringWidth(lineWithWord) / 1000 * fontSize;
            if (size <= maxWidth) {
                currentLine.append(currentLine.length() == 0 ? word : " " + word);
            } else {
                lines.add(currentLine.toString());
                currentLine = new StringBuilder(word);
            }
        }

        if (currentLine.length() > 0) {
            lines.add(currentLine.toString());
        }

        return lines;
    }
}