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
        1. If the query is standalone (self-contained), return needsReformulation=false, isFollowUp=false
        2. If it references previous context (pronouns like "it"/"they", implicit topics,
           follow-ups like "what about X"), return needsReformulation=true
        3. When reformulating, create a self-contained query incorporating relevant context
        4. Keep reformulated queries concise (under 50 words)
        5. Preserve the user's intent and question type
        6. Only include context that is directly relevant to answering the query

        isFollowUp rules:
        - Set isFollowUp=true ONLY when the query specifically continues the topic of the
          MOST RECENT assistant response shown in "Most Recent Exchange"
        - Set isFollowUp=false when the query is standalone, references earlier (not the
          immediately preceding) exchange, or shifts to a clearly different topic

        Examples:
        - Standalone: "What is quantum computing?" → needsReformulation=false, isFollowUp=false
        - Follow-up: "What about chapter 3?" (after discussing climate change)
          → needsReformulation=true, isFollowUp=true, query="What does chapter 3 say about climate change?"
        - Pronoun continuing most recent response: "How efficient are they?" (after solar panels answer)
          → needsReformulation=true, isFollowUp=true, query="How efficient are solar panels?"
        - Topic shift: "Now tell me about Topic B" (after discussing Topic A)
          → needsReformulation=false, isFollowUp=false

        Return your analysis as JSON with these fields:
        - needsReformulation (boolean)
        - isFollowUp (boolean)
        - query (string) - original if standalone, reformulated if not
        - reasoning (string) - brief explanation of your decision
        """)
  @UserMessage(
      """
        Most Recent Exchange (the immediately preceding Q&A turn):
        {{recentExchange}}

        Broader Conversation History (for additional context):
        {{conversationHistory}}

        New User Query: {{query}}

        Analyze and return JSON with needsReformulation, isFollowUp, query, and reasoning fields.
        """)
  QueryReformulationResult reformulate(
      @V("recentExchange") String recentExchange,
      @V("conversationHistory") String conversationHistory,
      @V("query") String query);
}
