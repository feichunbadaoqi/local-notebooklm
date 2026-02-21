package com.flamingo.ai.notebooklm.service.rag.query;

import java.util.List;

/**
 * Result from QueryReformulationService, capturing both the (possibly rewritten) query and
 * contextual signals needed for source-anchored retrieval.
 */
public record ReformulatedQuery(
    /**
     * The query to use for retrieval — original if standalone, reformulated if context-dependent.
     */
    String query,
    /**
     * True when the user's query specifically continues the immediately preceding assistant
     * response. Used to activate source anchoring in hybrid search.
     */
    boolean isFollowUp,
    /**
     * Document IDs (as UUID strings) from the previous assistant message's retrievedContextJson.
     * Empty when isFollowUp is false or no prior context exists.
     */
    List<String> anchorDocumentIds) {}
