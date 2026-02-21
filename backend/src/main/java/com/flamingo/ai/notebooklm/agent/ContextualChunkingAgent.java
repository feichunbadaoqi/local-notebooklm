package com.flamingo.ai.notebooklm.agent;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * AI agent for generating contextual prefixes for document chunks.
 *
 * <p>Implements Anthropic's "Contextual Chunking" technique: for each chunk, generates a 1-2
 * sentence prefix situating it within the overall document, improving both BM25 keyword matching
 * and vector embedding quality.
 */
public interface ContextualChunkingAgent {

  @SystemMessage(
      """
        You are a document analysis expert. Given a document summary and a specific chunk
        from that document, generate a short 1-2 sentence prefix that situates the chunk
        within the overall document context. The prefix should help a search engine understand
        what this chunk is about in the broader document context.

        Rules:
        - Write exactly 1-2 sentences
        - Start with "This chunk" or "This section"
        - Include key topic keywords that connect the chunk to the document
        - Be factual and concise
        - Do not repeat the chunk content verbatim
        """)
  @UserMessage(
      """
        Document summary:
        {{summary}}

        Chunk content:
        {{chunkContent}}
        """)
  String generatePrefix(@V("summary") String summary, @V("chunkContent") String chunkContent);
}
