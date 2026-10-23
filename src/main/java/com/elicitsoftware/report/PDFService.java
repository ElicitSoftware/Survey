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

import com.elicitsoftware.i18n.Translations;
import java.time.format.FormatStyle;
import java.util.Locale;
import com.elicitsoftware.report.pdf.Content;
import com.elicitsoftware.report.pdfbox.Column;
import com.elicitsoftware.report.pdfbox.Table;
import com.elicitsoftware.report.pdfbox.TableBuilder;
import de.rototor.pdfbox.graphics2d.PdfBoxGraphics2D;
import de.rototor.pdfbox.graphics2d.PdfBoxGraphics2DFontTextDrawer;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
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
import io.quarkus.logging.Log;
import org.w3c.dom.svg.SVGDocument;

import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Service for generating PDF documents and reports.
 * This class provides functionality to create PDF documents with text, tables, and SVG graphics.
 * It uses Apache PDFBox for PDF generation and supports various content types including
 * formatted text, tables with custom styling, and embedded SVG graphics.
 *
 * <p>The service is request-scoped, meaning a new instance is created for each HTTP request,
 * ensuring thread safety and preventing state leakage between requests.
 *
 * <p>Key features:
 * <ul>
 *   <li>PDF document creation with custom page layouts</li>
 *   <li>Text rendering with font and styling support</li>
 *   <li>Table generation with customizable columns and data</li>
 *   <li>SVG graphics embedding and rendering</li>
 *   <li>Stream resource generation for web download</li>
 * </ul>
 */
@RequestScoped
public class PDFService {

    static final float HEADER_MARGIN = 20f;
    static final float TEXT_MARGIN = 40f;
    static final float PADDING = 40f;
    // Font configuration
    static final PDFont TEXT_FONT = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    static final float FONT_SIZE = 10f;
    static final float LEADING = FONT_SIZE;
    private static final PDRectangle PAGE_SIZE = PDRectangle.LETTER;
    // Table configuration
    private static final float ROW_HEIGHT = 15;
    private static final float CELL_MARGIN = 2;

    PDDocument document;
    PDPage page;
    PDPageContentStream contentStream;
    float yPosition;
    float pageHeight;
    float pageWidth;
    /** Orientation of the current page; a page started to continue a report keeps it. */
    boolean landscape;

    @Inject
    HttpServletRequest request;

    private String surveyTitle;
    private Locale locale = Locale.ENGLISH;

    private static Table createContent(Content content) {

        // Total size of columns must not be greater than table width.
        List<Column> columns = new ArrayList<>();
        int i = 0;
        for (String header : content.table.headers) {
            float columnWidth;

            // Set the first column (person) to auto width based on content
            if (i == 0) {
                // Calculate max width needed for the "person" column
                float maxWidth = 0;
                try {
                    // Check header width
                    float headerWidth = TEXT_FONT.getStringWidth(header) / 1000 * FONT_SIZE + (CELL_MARGIN * 2);
                    maxWidth = Math.max(maxWidth, headerWidth);

                    // Check all data in this column
                    for (String[] row : content.table.body) {
                        if (row.length > i && row[i] != null) {
                            float cellWidth = TEXT_FONT.getStringWidth(row[i]) / 1000 * FONT_SIZE + (CELL_MARGIN * 2);
                            maxWidth = Math.max(maxWidth, cellWidth);
                        }
                    }
                    columnWidth = maxWidth + 10; // Add some padding
                } catch (IOException e) {
                    // Fallback to a reasonable width if font width calculation fails
                    columnWidth = 120f;
                }
            } else {
                columnWidth = Float.valueOf(content.table.widths[i]);
            }

            columns.add(new Column(header, columnWidth));
            i++;
        }

        String[][] tableContent = content.table.body;

        // Reserve the top margin the table starts drawing from (TEXT_MARGIN) and the same
        // bottom clearance used elsewhere in this class before footer/content overlap
        // (FONT_SIZE + PADDING) - otherwise the last row on a page can be drawn low enough
        // to collide with the footer addHeadersAndFooters() stamps on afterward.
        float tableHeight = PAGE_SIZE.getHeight() - TEXT_MARGIN - (FONT_SIZE + PADDING);

        Table table = new TableBuilder()
                .setCellMargin(CELL_MARGIN)
                .setColumns(columns)
                .setContent(tableContent)
                .setRowHeights(computeRowHeights(tableContent, columns))
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

    /**
     * Computes the actual rendered height of every body row, so a cell whose text is wider
     * than its column wraps onto multiple lines instead of overflowing into the next column.
     *
     * @param tableContent the raw (unwrapped) row/cell values
     * @param columns the table's columns, used for each column's rendering width
     * @return one height per row, in the same order as {@code tableContent}
     */
    private static float[] computeRowHeights(String[][] tableContent, List<Column> columns) {
        float[] rowHeights = new float[tableContent.length];
        for (int r = 0; r < tableContent.length; r++) {
            String[] row = tableContent[r];
            int maxLines = 1;
            for (int c = 0; c < columns.size() && c < row.length; c++) {
                float availableWidth = columns.get(c).getWidth() - (2 * CELL_MARGIN);
                try {
                    List<String> lines = wrapText(row[c], TEXT_FONT, FONT_SIZE, availableWidth);
                    maxLines = Math.max(maxLines, Math.max(1, lines.size()));
                } catch (IOException e) {
                    // Fall back to a single line if font metrics can't be measured
                }
            }
            rowHeights[r] = maxLines * ROW_HEIGHT;
        }
        return rowHeights;
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

    /** Renders the report in English with no survey title in the page header. */
    public byte[] generatePDF(ArrayList<ReportResponse> reportResponses) {
        return generatePDF(reportResponses, null, Locale.ENGLISH);
    }

    /**
     * Renders the report with page headers, footers and error blocks in the given locale.
     *
     * @param reportResponses the report sections to render
     * @param surveyTitle     the survey title shown in the page header, or null for none
     * @param locale          the respondent's locale for texts and the footer date
     */
    public byte[] generatePDF(ArrayList<ReportResponse> reportResponses, String surveyTitle, Locale locale) {
        this.surveyTitle = surveyTitle;
        this.locale = locale == null ? Locale.ENGLISH : locale;
        try {
            // Create a new document
            document = new PDDocument();
            contentStream = null;
            newPage(false);

            for (ReportResponse response : reportResponses) {
                if (response == null) {
                    Log.warn("PDFService.generatePDF - Skipping null response");
                    continue;
                }

                Log.info("PDFService.generatePDF - Processing response: " + response.title + ", pdf is null: " + (response.pdf == null));

                // If the PDF payload is missing, render a safe error block instead of crashing
                if (response.pdf == null) {
                    Log.info("PDFService.generatePDF - PDF is null, rendering error block for: " + response.title);
                    String title = (response.title != null && !response.title.isEmpty()) ? response.title : Translations.get(locale, "pdf.error.title");
                    String errorText = (response.innerHTML != null && !response.innerHTML.isEmpty())
                            ? response.innerHTML.replaceAll("[\\r\\n\\t]", " ").replaceAll("<[^>]+>", " ").replaceAll("&nbsp;", " ").trim()
                            : Translations.get(locale, "pdf.error.content");
                    Log.info("PDFService.generatePDF - Error text: " + errorText);
                    newPage(false);

                    addTitleBlock(title);
                    // Add the content
                    addTextBlock(errorText);
                } else {
                    // A report asks for landscape, and one carrying a drawing (the pedigree) gets it
                    // whether or not it asks. An orientation is a property of a page, so a change
                    // of orientation starts a page unless nothing has been drawn on this one yet.
                    boolean wantsLandscape = response.pdf.landscape || hasSvg(response.pdf.content);
                    if (response.pdf.pageBreak) {
                        newPage(wantsLandscape);
                    } else if (wantsLandscape != landscape) {
                        if (pageIsEmpty()) {
                            orient(wantsLandscape);
                        } else {
                            newPage(wantsLandscape);
                        }
                    }

                    addTitleBlock(response.pdf.title);

                    Content[] contents = response.pdf.content;
                    for (int c = 0; c < contents.length; c++) {
                        Content content = contents[c];
                        if (content == null) {
                            Log.warn("Content is null");
                            continue;
                        }
                        if (yPosition < TEXT_MARGIN + FONT_SIZE) {
                            newPage(landscape);
                        }

                        // Add the content
                        if (content.svg != null) {
                            addSVG(content, textHeightAfter(contents, c));
                        } else if (content.table != null) {
                            drawTable(createContent(content));
                        } else {
                            addTextBlock(content.text);
                        }
                    }
                }
            }

            addHeadersAndFooters();

            if (contentStream != null) {
                contentStream.close();
            }

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            document.save(outputStream);
            document.close();
            return outputStream.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void addTitleBlock(String title) throws IOException {
        if (title != null && !title.isEmpty()) {
            //Add some space between the last item and the new title.
            yPosition -= HEADER_MARGIN;
            // Check if we need a new page
            if (yPosition < FONT_SIZE + PADDING) {
                newPage(landscape);
            }

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

    /**
     * Adds a text block to the current PDF page.
     * This method handles text wrapping, page breaks, and proper positioning.
     * If the text doesn't fit on the current page, a new page is created.
     *
     * @param text the text content to add to the PDF
     * @throws IOException if an error occurs while writing to the PDF
     */
    public void addTextBlock(String text) throws IOException {
        PDRectangle mediaBox = page.getMediaBox();
        float width = mediaBox.getWidth() - 2 * PADDING;

        List<String> lines = wrapText(text, TEXT_FONT, FONT_SIZE, width);

        for (String line : lines) {
            // Before every line, not once per block: a block longer than the rest of the page
            // continues at the top of a new one instead of running off the bottom (#178).
            if (yPosition < PADDING + FONT_SIZE) {
                newPage(landscape);
            }
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

    /**
     * Draws an SVG (the pedigree) on the current page, below what is already there: scaled to fit
     * between the cursor and the room kept for the report's text that follows it, centred across
     * the page and aligned to the top, and the cursor is moved to just below it. When too little
     * of the page is left for a legible drawing it starts a page of the same orientation first.
     * The drawing is added to the open content stream, so the report's title above it and its
     * legend below it stay on the page (UC-005).
     *
     * @param content       the content whose {@code svg} is drawn
     * @param reserveBelow  height of the text that follows the drawing in the same report
     * @throws IOException if the drawing cannot be written to the page
     */
    void addSVG(Content content, float reserveBelow) throws IOException {
        float bottomLimit = PADDING + FONT_SIZE;
        // The legend is a few short lines; never let it squeeze the drawing below half the page.
        float reserve = Math.min(reserveBelow, (pageHeight - TEXT_MARGIN - bottomLimit) / 2);
        if (yPosition - bottomLimit - reserve < pageHeight / 3) {
            newPage(landscape);
        }
        float top = yPosition;
        float boxWidth = pageWidth - 2 * TEXT_MARGIN;
        float boxHeight = top - bottomLimit - reserve;

        PdfBoxGraphics2D graphics2D = new PdfBoxGraphics2D(document, (int) boxWidth, (int) boxHeight);
        graphics2D.setFontTextDrawer(new PdfBoxGraphics2DFontTextDrawer());

        // Parse the SVG
        String parser = XMLResourceDescriptor.getXMLParserClassName();
        SAXSVGDocumentFactory factory = new SAXSVGDocumentFactory(parser);
        SVGDocument svgDocument = factory.createSVGDocument(null, new StringReader(content.svg));

        // Build GVT
        GVTBuilder builder = new GVTBuilder();
        BridgeContext ctx = new BridgeContext(new UserAgentAdapter());
        GraphicsNode graphicsNode = builder.build(ctx, svgDocument);

        // Use actual computed bounds that include all rendered content
        Rectangle bounds = graphicsNode.getBounds().getBounds();
        Rectangle primitiveBounds = graphicsNode.getPrimitiveBounds().getBounds();
        Rectangle actualBounds = bounds.union(primitiveBounds);

        // Add explicit padding to ensure content that extends beyond computed bounds is captured
        int padding = 30; // Add 30 pixels padding on all sides
        Rectangle expandedBounds = new Rectangle(
                actualBounds.x - padding,
                actualBounds.y - padding,
                actualBounds.width + (2 * padding),
                actualBounds.height + (2 * padding)
        );

        // Scale to fit the box, preserving the aspect ratio
        double scale = Math.min(boxWidth / expandedBounds.getWidth(), boxHeight / expandedBounds.getHeight());
        graphics2D.scale(scale, scale);

        // Centre across the box and align to its top (Graphics2D's y runs down from the top)
        double translateX = (boxWidth / scale - expandedBounds.getWidth()) / 2.0;
        graphics2D.translate(translateX - expandedBounds.getX(), -expandedBounds.getY());

        graphicsNode.paint(graphics2D);
        graphics2D.dispose();

        contentStream.saveGraphicsState();
        contentStream.transform(Matrix.getTranslateInstance(TEXT_MARGIN, top - boxHeight));
        contentStream.drawForm(graphics2D.getXFormObject());
        contentStream.restoreGraphicsState();

        // Reset color to black for subsequent content
        contentStream.setNonStrokingColor(0f, 0f, 0f);

        yPosition = top - (float) (expandedBounds.getHeight() * scale) - LEADING;
    }

    /**
     * Closes the current page's content stream, if any, and starts a Letter page in the given
     * orientation with the cursor at its top.
     */
    void newPage(boolean landscape) throws IOException {
        if (contentStream != null) {
            contentStream.close();
        }
        page = new PDPage(PDRectangle.LETTER);
        document.addPage(page);
        contentStream = new PDPageContentStream(document, page);
        contentStream.setFont(TEXT_FONT, FONT_SIZE);
        orient(landscape);
    }

    /** Turns the current page, on which nothing has been drawn yet, and puts the cursor at its top. */
    void orient(boolean landscape) {
        this.landscape = landscape;
        PDRectangle size = landscape
                ? new PDRectangle(PDRectangle.LETTER.getHeight(), PDRectangle.LETTER.getWidth())
                : PDRectangle.LETTER;
        page.setMediaBox(size);
        pageWidth = size.getWidth();
        pageHeight = size.getHeight();
        yPosition = pageHeight - TEXT_MARGIN;
    }

    /** Whether nothing has been drawn on the current page yet: the cursor is still at its top. */
    boolean pageIsEmpty() {
        return yPosition == pageHeight - TEXT_MARGIN;
    }

    private static boolean hasSvg(Content[] contents) {
        return contents != null && Arrays.stream(contents).anyMatch(c -> c != null && c.svg != null);
    }

    /**
     * The height the text blocks after {@code index} take on a page this wide, up to the next
     * drawing or table: the room a drawing leaves so its legend fits below it.
     */
    float textHeightAfter(Content[] contents, int index) throws IOException {
        float height = 0;
        for (int i = index + 1; i < contents.length; i++) {
            Content next = contents[i];
            if (next == null) {
                continue;
            }
            if (next.svg != null || next.table != null) {
                break;
            }
            int lines = wrapText(next.text, TEXT_FONT, FONT_SIZE, pageWidth - 2 * PADDING).size();
            height += lines * (FONT_SIZE + 2) + LEADING;
        }
        return height;
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

                // Check if this is a landscape page
                boolean isLandscape = mediaBox.getWidth() > mediaBox.getHeight();

                // Header text - adjust positioning based on orientation
                String headerText = surveyTitle == null || surveyTitle.isBlank()
                        ? Translations.get(locale, "pdf.page", i + 1, totalPages)
                        : Translations.get(locale, "pdf.header", surveyTitle, i + 1, totalPages);
                float headerTextWidth = TEXT_FONT.getStringWidth(headerText) / 1000 * 10;
                float headerTextX;

                if (isLandscape) {
                    // For landscape, center the text considering the wider page
                    headerTextX = (mediaBox.getWidth() - headerTextWidth) / 2;
                } else {
                    // For portrait, keep original positioning (to the right of image)
                    headerTextX = PADDING + 150;
                }

                contentStream.beginText();
                contentStream.setFont(TEXT_FONT, 10);
                contentStream.newLineAtOffset(headerTextX, yTop - 10);
                contentStream.showText(headerText);
                contentStream.endText();

                // Footer
                // Date in the form of MM/DD/YYYY (far left)
                String currentDate = LocalDate.now().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).withLocale(locale));
                contentStream.beginText();
                contentStream.setFont(TEXT_FONT, 10);
                contentStream.newLineAtOffset(PADDING, yBottom - 10);
                contentStream.showText(currentDate);
                contentStream.endText();

                // Base URL (center) - constructed from request
                String baseUrl = request.getScheme() + "://" + request.getServerName() +
                        (request.getServerPort() != 80 && request.getServerPort() != 443 ?
                                ":" + request.getServerPort() : "") + request.getContextPath();
                float baseUrlWidth = TEXT_FONT.getStringWidth(baseUrl) / 1000 * 10;
                float centerX = (mediaBox.getWidth() - baseUrlWidth) / 2;
                contentStream.beginText();
                contentStream.setFont(TEXT_FONT, 10);
                contentStream.newLineAtOffset(centerX, yBottom - 10);
                contentStream.showText(baseUrl);
                contentStream.endText();

                // Page numbers (far right)
                String pageText = Translations.get(locale, "pdf.page", i + 1, totalPages);
                float pageTextWidth = TEXT_FONT.getStringWidth(pageText) / 1000 * 10;
                contentStream.beginText();
                contentStream.setFont(TEXT_FONT, 10);
                contentStream.newLineAtOffset(mediaBox.getWidth() - PADDING - pageTextWidth, yBottom - 10);
                contentStream.showText(pageText);
                contentStream.endText();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    // Configures basic setup for the table and draws it page by page
    public void drawTable(Table table) throws IOException {
        int totalRows = table.getNumberOfRows();
        if (totalRows == 0) {
            return;
        }

        int rowIndex = 0;
        boolean firstPage = true;
        while (rowIndex < totalRows) {
            if (!firstPage) {
                // Every internal table page after the first needs a real, fresh physical
                // page - the caller only leaves one page/stream open for us to continue on.
                generateContentStream(table);
            }
            firstPage = false;

            int endRowIndex = rowsForNextPage(table, rowIndex);
            String[][] currentPageContent = Arrays.copyOfRange(table.getContent(), rowIndex, endRowIndex);
            float[] currentPageRowHeights = Arrays.copyOfRange(table.getRowHeights(), rowIndex, endRowIndex);
            drawCurrentPage(table, currentPageContent, currentPageRowHeights);
            rowIndex = endRowIndex;
        }
    }

    /**
     * Determines how many of the remaining rows fit on the current/next page, given each
     * row's actual (possibly wrapped, multi-line) height.
     *
     * @param table the table being paginated
     * @param startRowIndex the first not-yet-drawn row's index
     * @return the exclusive end index of the row range that fits on one page
     */
    private int rowsForNextPage(Table table, int startRowIndex) {
        // table.getHeight() is the max budget for a page the table starts at the top of
        // (used on every internal page after the first, since generateContentStream()
        // resets yPosition to the top margin). But drawTable()'s first page can start
        // wherever yPosition already is - e.g. after other content earlier on that same
        // physical page - so cap the budget at whatever room is actually left above the
        // same footer-clearance boundary (FONT_SIZE + PADDING), or rows overflow into the
        // footer without ever triggering a page break.
        float availableOnThisPage = yPosition - (FONT_SIZE + PADDING);
        float usableHeight = Math.min(table.getHeight(), availableOnThisPage) - table.getRowHeight(); // reserve the header row
        float accumulated = 0f;
        float[] rowHeights = table.getRowHeights();
        int endRowIndex = startRowIndex;
        while (endRowIndex < table.getNumberOfRows()) {
            float rowHeight = rowHeights[endRowIndex];
            // Always include at least one row per page, even if it alone overflows -
            // otherwise an oversized row could never be drawn and the loop would stall.
            if (endRowIndex > startRowIndex && accumulated + rowHeight > usableHeight) {
                break;
            }
            accumulated += rowHeight;
            endRowIndex++;
        }
        return endRowIndex;
    }

    /**
     * Starts a fresh physical page for the table to continue drawing on.
     * Applies the transformation matrix for landscape orientation if needed.
     *
     * @param table the table being drawn
     * @throws IOException if an error occurs creating the page or its content stream
     */
    private void generateContentStream(Table table) throws IOException {
        page = new PDPage(table.getPageSize());
        document.addPage(page);
        contentStream.close();
        contentStream = new PDPageContentStream(document, page);
        if (table.isLandscape()) {
            contentStream.transform(new Matrix(0, 1, -1, 0, table.getPageSize().getWidth(), 0));
        }
        contentStream.setFont(table.getTextFont(), table.getFontSize());
        yPosition = pageHeight - TEXT_MARGIN;
    }

    // Draws current page table grid and borderlines and content
    private void drawCurrentPage(Table table, String[][] currentPageContent, float[] currentPageRowHeights)
            throws IOException {
        float tableTopY = yPosition;

        // Draws grid and borders
        drawTableGrid(table, currentPageRowHeights, tableTopY);

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
            nextTextY -= currentPageRowHeights[i];
            nextTextX = table.getMargin() + table.getCellMargin();
        }
        yPosition = nextTextY;
    }

    // Writes the content for one line, wrapping any cell whose text is wider than its
    // column onto additional lines below it rather than overflowing into the next column.
    private void writeContentLine(String[] lineContent, float nextTextX, float nextTextY,
                                  Table table) throws IOException {
        for (int i = 0; i < table.getNumberOfColumns(); i++) {
            String text = lineContent[i];
            float columnWidth = table.getColumns().get(i).getWidth();
            List<String> lines = wrapText(text, table.getTextFont(), table.getFontSize(),
                    columnWidth - (2 * table.getCellMargin()));
            if (lines.isEmpty()) {
                lines = List.of("");
            }
            float lineY = nextTextY;
            for (String line : lines) {
                contentStream.beginText();
                contentStream.newLineAtOffset(nextTextX, lineY);
                contentStream.showText(line);
                contentStream.endText();
                lineY -= table.getRowHeight();
            }
            nextTextX += columnWidth;
        }
    }

    // Draws the table grid lines, sizing each row's box to its actual (possibly
    // multi-line) height instead of a uniform constant.
    private void drawTableGrid(Table table, float[] currentPageRowHeights, float tableTopY)
            throws IOException {
        // Draw row lines: one above the header, one below the header, then one below each body row
        float nextY = tableTopY;
        drawHorizontalLine(table, nextY);
        nextY -= table.getRowHeight();
        drawHorizontalLine(table, nextY);
        for (float rowHeight : currentPageRowHeights) {
            nextY -= rowHeight;
            drawHorizontalLine(table, nextY);
        }

        // Draw column lines
        final float tableBottomY = nextY;
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

    // Draws a single full-width horizontal grid line at the given Y coordinate.
    private void drawHorizontalLine(Table table, float y) throws IOException {
        contentStream.moveTo(table.getMargin(), y);
        contentStream.lineTo(table.getMargin() + table.getWidth(), y);
        contentStream.stroke();
    }
}