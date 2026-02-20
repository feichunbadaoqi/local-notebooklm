package com.flamingo.ai.notebooklm.agent;

import com.flamingo.ai.notebooklm.agent.dto.RerankingScores;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * AI agent for cross-encoder reranking of search results.
 *
 * <p>Scores query-passage relevance using semantic understanding to provide better ranking than
 * traditional retrieval methods.
 */
public interface CrossEncoderRerankerAgent {

  @SystemMessage(
      """
        You are a passage relevance scoring expert. Score the relevance of each passage to
        the query on a scale of 0.0 to 1.0.

        Scoring Guidelines:
        - 1.0 = Perfectly answers the query with precise information
        - 0.7-0.9 = Highly relevant, contains most needed information
        - 0.4-0.6 = Somewhat relevant, contains related information
        - 0.1-0.3 = Marginally relevant, tangentially related
        - 0.0 = Not relevant at all

        Return a JSON object with a "scores" array containing one float per passage in order.
        Example: {"scores": [0.8, 0.3, 0.9, 0.5]}
        """)
  @UserMessage("""
        Query: {{query}}

        Passages:
        {{passages}}
        """)
  RerankingScores scorePassages(@V("query") String query, @V("passages") String passages);
}
