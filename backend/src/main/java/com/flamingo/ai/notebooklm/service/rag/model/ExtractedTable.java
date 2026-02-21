package com.flamingo.ai.notebooklm.service.rag.model;

/**
 * A table extracted from a document, represented as GitHub-Flavored Markdown.
 *
 * <p>The {@code markdownContent} field uses the standard GFM pipe-table syntax so that the
 * frontend's {@code marked} renderer can display it directly.
 *
 * @param index sequential index of this table within the document (0-based)
 * @param markdownContent GFM table string, e.g. {@code "| col |\n|---|\n| val |"}
 * @param caption optional table title or caption extracted from the surrounding text
 * @param approximateOffset character offset in the document's full text (best-effort)
 */
public record ExtractedTable(
    int index, String markdownContent, String caption, int approximateOffset) {}
