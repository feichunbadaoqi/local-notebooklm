package com.flamingo.ai.notebooklm.service.rag.model;

import java.util.List;

/**
 * A section node in the hierarchical structure of a parsed document.
 *
 * <p>Sections are detected by {@link com.flamingo.ai.notebooklm.service.rag.parsing.DocumentParser}
 * implementations (e.g. via PDF font metrics or XHTML heading tags) and form a tree that mirrors
 * the document outline.
 *
 * @param title section heading text
 * @param level heading depth (1 = H1, 2 = H2, …, 6 = H6)
 * @param breadcrumb path from root to this section, e.g. {@code ["Doc Title", "Chapter", "Sub"]}
 * @param content body text belonging exclusively to this section (excluding sub-sections)
 * @param children nested sub-sections
 * @param startOffset character offset in the full document text where this section begins
 * @param endOffset character offset where this section ends (exclusive)
 */
public record DocumentSection(
    String title,
    int level,
    List<String> breadcrumb,
    String content,
    List<DocumentSection> children,
    int startOffset,
    int endOffset) {}
