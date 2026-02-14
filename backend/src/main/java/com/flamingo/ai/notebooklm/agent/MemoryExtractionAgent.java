package com.flamingo.ai.notebooklm.agent;

import com.flamingo.ai.notebooklm.agent.dto.ExtractedMemory;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import java.util.List;

/**
 * AI agent for extracting memories from conversation exchanges. Uses LangChain4j AI Services for
 * structured LLM interaction.
 */
public interface MemoryExtractionAgent {

  @SystemMessage(
      """
        You are a memory extraction assistant. Analyze conversation exchanges and extract
        important facts, user preferences, or insights worth remembering long-term.

        Memory Types:
        - fact: Specific data points, dates, names, numbers from documents
        - preference: How the user likes information presented or what they focus on
        - insight: Connections, conclusions, or patterns discovered

        Rules:
        - Only extract genuinely important information
        - Keep each memory concise (1-2 sentences max)
        - Assign importance: 0.0 (trivial) to 1.0 (critical)
        - Return empty array if nothing worth remembering
        - Return ONLY valid JSON array of objects
        """)
  @UserMessage(
      """
        User message: {{userMessage}}
        Assistant response: {{assistantResponse}}

        Extract memories as JSON array:
        [{"type": "fact|preference|insight", "content": "...", "importance": 0.0-1.0}]
        """)
  List<ExtractedMemory> extract(
      @V("userMessage") String userMessage, @V("assistantResponse") String assistantResponse);
}
