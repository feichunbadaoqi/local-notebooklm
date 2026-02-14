package com.flamingo.ai.notebooklm.agent;

import com.flamingo.ai.notebooklm.agent.dto.QueryReformulationResult;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * AI agent for query reformulation in conversational RAG. Uses LangChain4j AI Services for
 * structured LLM interaction.
 */
public interface QueryReformulationAgent {

  @SystemMessage(
      """
        You are a query reformulation assistant for a conversational document Q&A system.

        Task: Analyze the user's new query in the context of their conversation history.

        Rules:
        1. If the query is standalone (self-contained), return needsReformulation=false
        2. If it references previous context (pronouns like "it"/"they", implicit topics,
           follow-ups like "what about X"), return needsReformulation=true
        3. When reformulating, create a self-contained query incorporating relevant context
        4. Keep reformulated queries concise (under 50 words)
        5. Preserve the user's intent and question type
        6. Only include context that is directly relevant to answering the query

        Examples:
        - Standalone: "What is quantum computing?" → needsReformulation=false, query unchanged
        - Follow-up: "What about chapter 3?" (after discussing climate change)
          → needsReformulation=true, query="What does chapter 3 say about climate change?"
        - Pronoun: "How efficient are they?" (after discussing solar panels)
          → needsReformulation=true, query="How efficient are solar panels?"

        Return your analysis as JSON with these fields:
        - needsReformulation (boolean)
        - query (string) - original if standalone, reformulated if not
        - reasoning (string) - brief explanation of your decision
        """)
  @UserMessage(
      """
        Conversation History:
        {{conversationHistory}}

        New User Query: {{query}}

        Analyze and return JSON with needsReformulation, query, and reasoning fields.
        """)
  QueryReformulationResult reformulate(
      @V("conversationHistory") String conversationHistory, @V("query") String query);
}
