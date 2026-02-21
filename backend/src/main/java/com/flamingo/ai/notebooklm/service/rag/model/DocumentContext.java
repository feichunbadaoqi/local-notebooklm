package com.flamingo.ai.notebooklm.service.rag.model;

import java.util.UUID;

/**
 * Input context passed to a {@link
 * com.flamingo.ai.notebooklm.service.rag.chunking.DocumentChunkingStrategy}.
 *
 * <p>Carries document identity information so strategies can annotate chunks and store derived
 * artefacts (e.g. extracted images) without depending on the JPA {@code Document} entity.
 */
public record DocumentContext(UUID documentId, UUID sessionId, String fileName, String mimeType) {}
