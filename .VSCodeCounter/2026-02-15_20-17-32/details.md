# Details

Date : 2026-02-15 20:17:32

Directory e:\\workspaces\\aicoderpg\\notebooklm

Total : 160 files,  24784 codes, 1674 comments, 3391 blanks, all 29849 lines

[Summary](results.md) / Details / [Diff Summary](diff.md) / [Diff Details](diff-details.md)

## Files
| filename | language | code | comment | blank | total |
| :--- | :--- | ---: | ---: | ---: | ---: |
| [.claude/settings.json](/.claude/settings.json) | JSON with Comments | 6 | 0 | 1 | 7 |
| [.claude/settings.local.json](/.claude/settings.local.json) | JSON | 53 | 0 | 1 | 54 |
| [CLAUDE.md](/CLAUDE.md) | Markdown | 239 | 0 | 79 | 318 |
| [QUICKSTART\_CHINESE.md](/QUICKSTART_CHINESE.md) | Markdown | 88 | 0 | 41 | 129 |
| [RESTART\_WITH\_FIXES.md](/RESTART_WITH_FIXES.md) | Markdown | 131 | 0 | 45 | 176 |
| [backend/CLAUDE.md](/backend/CLAUDE.md) | Markdown | 418 | 0 | 123 | 541 |
| [backend/build.gradle](/backend/build.gradle) | Gradle | 172 | 20 | 32 | 224 |
| [backend/compose.yaml](/backend/compose.yaml) | YAML | 29 | 4 | 1 | 34 |
| [backend/config/checkstyle/checkstyle.xml](/backend/config/checkstyle/checkstyle.xml) | XML | 70 | 18 | 15 | 103 |
| [backend/config/spotbugs/exclude-filter.xml](/backend/config/spotbugs/exclude-filter.xml) | XML | 38 | 20 | 9 | 67 |
| [backend/gradle.properties](/backend/gradle.properties) | Java Properties | 4 | 3 | 3 | 10 |
| [backend/gradle/wrapper/gradle-wrapper.properties](/backend/gradle/wrapper/gradle-wrapper.properties) | Java Properties | 7 | 0 | 1 | 8 |
| [backend/gradlew.bat](/backend/gradlew.bat) | Batch | 40 | 32 | 22 | 94 |
| [backend/restart-with-fixes.ps1](/backend/restart-with-fixes.ps1) | PowerShell | 19 | 5 | 8 | 32 |
| [backend/settings.gradle](/backend/settings.gradle) | Gradle | 1 | 0 | 1 | 2 |
| [backend/src/main/java/com/flamingo/ai/notebooklm/NotebooklmApplication.java](/backend/src/main/java/com/flamingo/ai/notebooklm/NotebooklmApplication.java) | Java | 9 | 0 | 4 | 13 |
| [backend/src/main/java/com/flamingo/ai/notebooklm/agent/MemoryExtractionAgent.java](/backend/src/main/java/com/flamingo/ai/notebooklm/agent/MemoryExtractionAgent.java) | Java | 32 | 4 | 7 | 43 |
| [backend/src/main/java/com/flamingo/ai/notebooklm/agent/QueryReformulationAgent.java](/backend/src/main/java/com/flamingo/ai/notebooklm/agent/QueryReformulationAgent.java) | Java | 39 | 4 | 10 | 53 |
| [backend/src/main/java/com/flamingo/ai/notebooklm/agent/dto/ExtractedMemory.java](/backend/src/main/java/com/flamingo/ai/notebooklm/agent/dto/ExtractedMemory.java) | Java | 6 | 4 | 2 | 12 |
| [backend/src/main/java/com/flamingo/ai/notebooklm/agent/dto/QueryReformulationResult.java](/backend/src/main/java/com/flamingo/ai/notebooklm/agent/dto/QueryReformulationResult.java) | Java | 6 | 4 | 2 | 12 |
| [backend/src/main/java/com/flamingo/ai/notebooklm/api/dto/request/ChatRequest.java](/backend/src/main/java/com/flamingo/ai/notebooklm/api/dto/request/ChatRequest.java) | Java | 18 | 2 | 5 | 25 |
| [backend/src/main/java/com/flamingo/ai/notebooklm/api/dto/request/CreateMemoryRequest.java](/backend/src/main/java/com/flamingo/ai/notebooklm/api/dto/request/CreateMemoryRequest.java) | Java | 28 | 1 | 6 | 35 |
| [backend/src/main/java/com/flamingo/ai/notebooklm/api/dto/request/CreateSessionRequest.java](/backend/src/main/java/com/flamingo/ai/notebooklm/api/dto/request/CreateSessionRequest.java) | Java | 18 | 1 | 5 | 24 |
| [backend/src/main/java/com/flamingo/ai/notebooklm/api/dto/request/UpdateSessionRequest.java](/backend/src/main/java/com/flamingo/ai/notebooklm/api/dto/request/UpdateSessionRequest.java) | Java | 16 | 1 | 5 | 22 |
| [backend/src/main/java/com/flamingo/ai/notebooklm/api/dto/response/ChatMessageResponse.java](/backend/src/main/java/com/flamingo/ai/notebooklm/api/dto/response/ChatMessageResponse.java) | Java | 32 | 2 | 5 | 39 |
| [backend/src/main/java/com/flamingo/ai/notebooklm/api/dto/response/DocumentResponse.java](/backend/src/main/java/com/flamingo/ai/notebooklm/api/dto/response/DocumentResponse.java) | Java | 39 | 2 | 5 | 46 |
| [backend/src/main/java/com/flamingo/ai/notebooklm/api/dto/response/MemoryResponse.java](/backend/src/main/java/com/flamingo/ai/notebooklm/api/dto/response/MemoryResponse.java) | Java | 32 | 2 | 5 | 39 |
| [backend/src/main/java/com/flamingo/ai/notebooklm/api/dto/response/SessionResponse.java](/backend/src/main/java/com/flamingo/ai/notebooklm/api/dto/response/SessionResponse.java) | Java | 47 | 3 | 6 | 56 |
| [backend/src/main/java/com/flamingo/ai/notebooklm/api/dto/response/SessionWithStats.java](/backend/src/main/java/com/flamingo/ai/notebooklm/api/dto/response/SessionWithStats.java) | Java | 18 | 2 | 4 | 24 |
| [backend/src/main/java/com/flamingo/ai/notebooklm/api/dto/response/StreamChunkResponse.java](/backend/src/main/java/com/flamingo/ai/notebooklm/api/dto/response/StreamChunkResponse.java) | Java | 59 | 11 | 13 | 83 |
| [backend/src/main/java/com/flamingo/ai/notebooklm/api/dto/response/SystemStats.java](/backend/src/main/java/com/flamingo/ai/notebooklm/api/dto/response/SystemStats.java) | Java | 16 | 1 | 3 | 20 |
| [backend/src/main/java/com/flamingo/ai/notebooklm/api/rest/DocumentController.java](/backend/src/main/java/com/flamingo/ai/notebooklm/api/rest/DocumentController.java) | Java | 57 | 6 | 9 | 72 |
| [backend/src/main/java/com/flamingo/ai/notebooklm/api/rest/HealthController.java](/backend/src/main/java/com/flamingo/ai/notebooklm/api/rest/HealthController.java) | Java | 30 | 3 | 6 | 39 |
| [backend/src/main/java/com/flamingo/ai/notebooklm/api/rest/MemoryController.java](/backend/src/main/java/com/flamingo/ai/notebooklm/api/rest/MemoryController.java) | Java | 55 | 28 | 11 | 94 |
| [backend/src/main/java/com/flamingo/ai/notebooklm/api/rest/SessionController.java](/backend/src/main/java/com/flamingo/ai/notebooklm/api/rest/SessionController.java) | Java | 67 | 8 | 10 | 85 |
| [backend/src/main/java/com/flamingo/ai/notebooklm/api/sse/ChatController.java](/backend/src/main/java/com/flamingo/ai/notebooklm/api/sse/ChatController.java) | Java | 73 | 21 | 12 | 106 |
| [backend/src/main/java/com/flamingo/ai/notebooklm/config/AiAgentConfig.java](/backend/src/main/java/com/flamingo/ai/notebooklm/config/AiAgentConfig.java) | Java | 18 | 14 | 5 | 37 |
| [backend/src/main/java/com/flamingo/ai/notebooklm/config/AsyncConfig.java](/backend/src/main/java/com/flamingo/ai/notebooklm/config/AsyncConfig.java) | Java | 30 | 1 | 5 | 36 |
| [backend/src/main/java/com/flamingo/ai/notebooklm/config/ElasticsearchConfig.java](/backend/src/main/java/com/flamingo/ai/notebooklm/config/ElasticsearchConfig.java) | Java | 31 | 1 | 9 | 41 |
| [backend/src/main/java/com/flamingo/ai/notebooklm/config/JacksonConfig.java](/backend/src/main/java/com/flamingo/ai/notebooklm/config/JacksonConfig.java) | Java | 11 | 0 | 4 | 15 |
| [backend/src/main/java/com/flamingo/ai/notebooklm/config/LangChain4jConfig.java](/backend/src/main/java/com/flamingo/ai/notebooklm/config/LangChain4jConfig.java) | Java | 69 | 1 | 16 | 86 |
| [backend/src/main/java/com/flamingo/ai/notebooklm/config/MetricsConfig.java](/backend/src/main/java/com/flamingo/ai/notebooklm/config/MetricsConfig.java) | Java | 12 | 7 | 4 | 23 |
| [backend/src/main/java/com/flamingo/ai/notebooklm/config/RagConfig.java](/backend/src/main/java/com/flamingo/ai/notebooklm/config/RagConfig.java) | Java | 68 | 1 | 11 | 80 |
| [backend/src/main/java/com/flamingo/ai/notebooklm/config/WebConfig.java](/backend/src/main/java/com/flamingo/ai/notebooklm/config/WebConfig.java) | Java | 17 | 1 | 4 | 22 |
| [backend/src/main/java/com/flamingo/ai/notebooklm/domain/entity/ChatMessage.java](/backend/src/main/java/com/flamingo/ai/notebooklm/domain/entity/ChatMessage.java) | Java | 61 | 7 | 15 | 83 |
| [backend/src/main/java/com/flamingo/ai/notebooklm/domain/entity/ChatSummary.java](/backend/src/main/java/com/flamingo/ai/notebooklm/domain/entity/ChatSummary.java) | Java | 54 | 8 | 14 | 76 |
| [backend/src/main/java/com/flamingo/ai/notebooklm/domain/entity/Document.java](/backend/src/main/java/com/flamingo/ai/notebooklm/domain/entity/Document.java) | Java | 68 | 6 | 17 | 91 |
| [backend/src/main/java/com/flamingo/ai/notebooklm/domain/entity/Memory.java](/backend/src/main/java/com/flamingo/ai/notebooklm/domain/entity/Memory.java) | Java | 55 | 6 | 14 | 75 |
| [backend/src/main/java/com/flamingo/ai/notebooklm/domain/entity/Session.java](/backend/src/main/java/com/flamingo/ai/notebooklm/domain/entity/Session.java) | Java | 80 | 4 | 18 | 102 |
| [backend/src/main/java/com/flamingo/ai/notebooklm/domain/enums/DocumentStatus.java](/backend/src/main/java/com/flamingo/ai/notebooklm/domain/enums/DocumentStatus.java) | Java | 7 | 5 | 5 | 17 |
| [backend/src/main/java/com/flamingo/ai/notebooklm/domain/enums/InteractionMode.java](/backend/src/main/java/com/flamingo/ai/notebooklm/domain/enums/InteractionMode.java) | Java | 18 | 4 | 8 | 30 |
| [backend/src/main/java/com/flamingo/ai/notebooklm/domain/enums/MessageRole.java](/backend/src/main/java/com/flamingo/ai/notebooklm/domain/enums/MessageRole.java) | Java | 6 | 4 | 4 | 14 |
| [backend/src/main/java/com/flamingo/ai/notebooklm/domain/repository/ChatMessageRepository.java](/backend/src/main/java/com/flamingo/ai/notebooklm/domain/repository/ChatMessageRepository.java) | Java | 51 | 14 | 16 | 81 |
| [backend/src/main/java/com/flamingo/ai/notebooklm/domain/repository/ChatSummaryRepository.java](/backend/src/main/java/com/flamingo/ai/notebooklm/domain/repository/ChatSummaryRepository.java) | Java | 23 | 7 | 9 | 39 |
| [backend/src/main/java/com/flamingo/ai/notebooklm/domain/repository/DocumentRepository.java](/backend/src/main/java/com/flamingo/ai/notebooklm/domain/repository/DocumentRepository.java) | Java | 23 | 8 | 10 | 41 |
| [backend/src/main/java/com/flamingo/ai/notebooklm/domain/repository/MemoryRepository.java](/backend/src/main/java/com/flamingo/ai/notebooklm/domain/repository/MemoryRepository.java) | Java | 23 | 7 | 9 | 39 |
| [backend/src/main/java/com/flamingo/ai/notebooklm/domain/repository/SessionRepository.java](/backend/src/main/java/com/flamingo/ai/notebooklm/domain/repository/SessionRepository.java) | Java | 18 | 5 | 7 | 30 |
| [backend/src/main/java/com/flamingo/ai/notebooklm/elasticsearch/DocumentChunk.java](/backend/src/main/java/com/flamingo/ai/notebooklm/elasticsearch/DocumentChunk.java) | Java | 28 | 11 | 8 | 47 |
| [backend/src/main/java/com/flamingo/ai/notebooklm/elasticsearch/DocumentChunkIndexService.java](/backend/src/main/java/com/flamingo/ai/notebooklm/elasticsearch/DocumentChunkIndexService.java) | Java | 88 | 45 | 19 | 152 |
| [backend/src/main/java/com/flamingo/ai/notebooklm/elasticsearch/ElasticsearchIndexOperations.java](/backend/src/main/java/com/flamingo/ai/notebooklm/elasticsearch/ElasticsearchIndexOperations.java) | Java | 12 | 47 | 10 | 69 |
| [backend/src/main/java/com/flamingo/ai/notebooklm/elasticsearch/ElasticsearchIndexService.java](/backend/src/main/java/com/flamingo/ai/notebooklm/elasticsearch/ElasticsearchIndexService.java) | Java | 411 | 30 | 38 | 479 |
| [backend/src/main/java/com/flamingo/ai/notebooklm/exception/ApiError.java](/backend/src/main/java/com/flamingo/ai/notebooklm/exception/ApiError.java) | Java | 27 | 8 | 10 | 45 |
| [backend/src/main/java/com/flamingo/ai/notebooklm/exception/DocumentNotFoundException.java](/backend/src/main/java/com/flamingo/ai/notebooklm/exception/DocumentNotFoundException.java) | Java | 12 | 1 | 6 | 19 |
| [backend/src/main/java/com/flamingo/ai/notebooklm/exception/DocumentProcessingException.java](/backend/src/main/java/com/flamingo/ai/notebooklm/exception/DocumentProcessingException.java) | Java | 27 | 1 | 9 | 37 |
| [backend/src/main/java/com/flamingo/ai/notebooklm/exception/GlobalExceptionHandler.java](/backend/src/main/java/com/flamingo/ai/notebooklm/exception/GlobalExceptionHandler.java) | Java | 177 | 1 | 36 | 214 |
| [backend/src/main/java/com/flamingo/ai/notebooklm/exception/LlmServiceException.java](/backend/src/main/java/com/flamingo/ai/notebooklm/exception/LlmServiceException.java) | Java | 29 | 1 | 8 | 38 |
| [backend/src/main/java/com/flamingo/ai/notebooklm/exception/MemoryAccessDeniedException.java](/backend/src/main/java/com/flamingo/ai/notebooklm/exception/MemoryAccessDeniedException.java) | Java | 19 | 1 | 7 | 27 |
| [backend/src/main/java/com/flamingo/ai/notebooklm/exception/MemoryNotFoundException.java](/backend/src/main/java/com/flamingo/ai/notebooklm/exception/MemoryNotFoundException.java) | Java | 12 | 1 | 6 | 19 |
| [backend/src/main/java/com/flamingo/ai/notebooklm/exception/SearchException.java](/backend/src/main/java/com/flamingo/ai/notebooklm/exception/SearchException.java) | Java | 15 | 1 | 6 | 22 |
| [backend/src/main/java/com/flamingo/ai/notebooklm/exception/SessionNotFoundException.java](/backend/src/main/java/com/flamingo/ai/notebooklm/exception/SessionNotFoundException.java) | Java | 12 | 1 | 6 | 19 |
| [backend/src/main/java/com/flamingo/ai/notebooklm/service/chat/ChatCompactionService.java](/backend/src/main/java/com/flamingo/ai/notebooklm/service/chat/ChatCompactionService.java) | Java | 137 | 29 | 29 | 195 |
| [backend/src/main/java/com/flamingo/ai/notebooklm/service/chat/ChatService.java](/backend/src/main/java/com/flamingo/ai/notebooklm/service/chat/ChatService.java) | Java | 11 | 21 | 6 | 38 |
| [backend/src/main/java/com/flamingo/ai/notebooklm/service/chat/ChatServiceImpl.java](/backend/src/main/java/com/flamingo/ai/notebooklm/service/chat/ChatServiceImpl.java) | Java | 264 | 23 | 43 | 330 |
| [backend/src/main/java/com/flamingo/ai/notebooklm/service/document/DocumentService.java](/backend/src/main/java/com/flamingo/ai/notebooklm/service/document/DocumentService.java) | Java | 12 | 32 | 8 | 52 |
| [backend/src/main/java/com/flamingo/ai/notebooklm/service/document/DocumentServiceImpl.java](/backend/src/main/java/com/flamingo/ai/notebooklm/service/document/DocumentServiceImpl.java) | Java | 152 | 9 | 25 | 186 |
| [backend/src/main/java/com/flamingo/ai/notebooklm/service/health/HealthService.java](/backend/src/main/java/com/flamingo/ai/notebooklm/service/health/HealthService.java) | Java | 5 | 6 | 4 | 15 |
| [backend/src/main/java/com/flamingo/ai/notebooklm/service/health/HealthServiceImpl.java](/backend/src/main/java/com/flamingo/ai/notebooklm/service/health/HealthServiceImpl.java) | Java | 33 | 1 | 6 | 40 |
| [backend/src/main/java/com/flamingo/ai/notebooklm/service/memory/MemoryService.java](/backend/src/main/java/com/flamingo/ai/notebooklm/service/memory/MemoryService.java) | Java | 16 | 60 | 11 | 87 |
| [backend/src/main/java/com/flamingo/ai/notebooklm/service/memory/MemoryServiceImpl.java](/backend/src/main/java/com/flamingo/ai/notebooklm/service/memory/MemoryServiceImpl.java) | Java | 205 | 8 | 49 | 262 |
| [backend/src/main/java/com/flamingo/ai/notebooklm/service/rag/AnswerVerificationService.java](/backend/src/main/java/com/flamingo/ai/notebooklm/service/rag/AnswerVerificationService.java) | Java | 135 | 44 | 34 | 213 |
| [backend/src/main/java/com/flamingo/ai/notebooklm/service/rag/CrossEncoderReranker.java](/backend/src/main/java/com/flamingo/ai/notebooklm/service/rag/CrossEncoderReranker.java) | Java | 122 | 37 | 33 | 192 |
| [backend/src/main/java/com/flamingo/ai/notebooklm/service/rag/DiversityReranker.java](/backend/src/main/java/com/flamingo/ai/notebooklm/service/rag/DiversityReranker.java) | Java | 102 | 44 | 26 | 172 |
| [backend/src/main/java/com/flamingo/ai/notebooklm/service/rag/DocumentMetadataExtractor.java](/backend/src/main/java/com/flamingo/ai/notebooklm/service/rag/DocumentMetadataExtractor.java) | Java | 207 | 77 | 46 | 330 |
| [backend/src/main/java/com/flamingo/ai/notebooklm/service/rag/DocumentProcessingService.java](/backend/src/main/java/com/flamingo/ai/notebooklm/service/rag/DocumentProcessingService.java) | Java | 313 | 63 | 58 | 434 |
| [backend/src/main/java/com/flamingo/ai/notebooklm/service/rag/EmbeddingService.java](/backend/src/main/java/com/flamingo/ai/notebooklm/service/rag/EmbeddingService.java) | Java | 117 | 38 | 21 | 176 |
| [backend/src/main/java/com/flamingo/ai/notebooklm/service/rag/HybridSearchService.java](/backend/src/main/java/com/flamingo/ai/notebooklm/service/rag/HybridSearchService.java) | Java | 155 | 40 | 32 | 227 |
| [backend/src/main/java/com/flamingo/ai/notebooklm/service/rag/QueryReformulationService.java](/backend/src/main/java/com/flamingo/ai/notebooklm/service/rag/QueryReformulationService.java) | Java | 6 | 12 | 3 | 21 |
| [backend/src/main/java/com/flamingo/ai/notebooklm/service/rag/QueryReformulationServiceImpl.java](/backend/src/main/java/com/flamingo/ai/notebooklm/service/rag/QueryReformulationServiceImpl.java) | Java | 92 | 7 | 21 | 120 |
| [backend/src/main/java/com/flamingo/ai/notebooklm/service/rag/RetrievalConfidenceService.java](/backend/src/main/java/com/flamingo/ai/notebooklm/service/rag/RetrievalConfidenceService.java) | Java | 163 | 42 | 45 | 250 |
| [backend/src/main/java/com/flamingo/ai/notebooklm/service/session/SessionService.java](/backend/src/main/java/com/flamingo/ai/notebooklm/service/session/SessionService.java) | Java | 19 | 55 | 12 | 86 |
| [backend/src/main/java/com/flamingo/ai/notebooklm/service/session/SessionServiceImpl.java](/backend/src/main/java/com/flamingo/ai/notebooklm/service/session/SessionServiceImpl.java) | Java | 123 | 1 | 23 | 147 |
| [backend/src/main/resources/application.yaml](/backend/src/main/resources/application.yaml) | YAML | 163 | 19 | 16 | 198 |
| [backend/src/test/java/com/flamingo/ai/notebooklm/ApplicationContextTest.java](/backend/src/test/java/com/flamingo/ai/notebooklm/ApplicationContextTest.java) | Java | 39 | 5 | 8 | 52 |
| [backend/src/test/java/com/flamingo/ai/notebooklm/NotebooklmApplicationTests.java](/backend/src/test/java/com/flamingo/ai/notebooklm/NotebooklmApplicationTests.java) | Java | 10 | 5 | 4 | 19 |
| [backend/src/test/java/com/flamingo/ai/notebooklm/api/ApiContractTest.java](/backend/src/test/java/com/flamingo/ai/notebooklm/api/ApiContractTest.java) | Java | 56 | 19 | 12 | 87 |
| [backend/src/test/java/com/flamingo/ai/notebooklm/api/sse/ChatControllerIntegrationTest.java](/backend/src/test/java/com/flamingo/ai/notebooklm/api/sse/ChatControllerIntegrationTest.java) | Java | 226 | 2 | 49 | 277 |
| [backend/src/test/java/com/flamingo/ai/notebooklm/service/chat/ChatServiceImplTest.java](/backend/src/test/java/com/flamingo/ai/notebooklm/service/chat/ChatServiceImplTest.java) | Java | 394 | 7 | 70 | 471 |
| [backend/src/test/java/com/flamingo/ai/notebooklm/service/document/DocumentServiceImplTest.java](/backend/src/test/java/com/flamingo/ai/notebooklm/service/document/DocumentServiceImplTest.java) | Java | 193 | 24 | 52 | 269 |
| [backend/src/test/java/com/flamingo/ai/notebooklm/service/memory/MemoryServiceImplTest.java](/backend/src/test/java/com/flamingo/ai/notebooklm/service/memory/MemoryServiceImplTest.java) | Java | 299 | 6 | 82 | 387 |
| [backend/src/test/java/com/flamingo/ai/notebooklm/service/rag/AnswerVerificationServiceTest.java](/backend/src/test/java/com/flamingo/ai/notebooklm/service/rag/AnswerVerificationServiceTest.java) | Java | 233 | 7 | 72 | 312 |
| [backend/src/test/java/com/flamingo/ai/notebooklm/service/rag/CrossEncoderRerankerTest.java](/backend/src/test/java/com/flamingo/ai/notebooklm/service/rag/CrossEncoderRerankerTest.java) | Java | 178 | 13 | 53 | 244 |
| [backend/src/test/java/com/flamingo/ai/notebooklm/service/rag/DocumentMetadataExtractorTest.java](/backend/src/test/java/com/flamingo/ai/notebooklm/service/rag/DocumentMetadataExtractorTest.java) | Java | 341 | 2 | 120 | 463 |
| [backend/src/test/java/com/flamingo/ai/notebooklm/service/rag/DocumentProcessingServiceTest.java](/backend/src/test/java/com/flamingo/ai/notebooklm/service/rag/DocumentProcessingServiceTest.java) | Java | 171 | 43 | 51 | 265 |
| [backend/src/test/java/com/flamingo/ai/notebooklm/service/rag/EmbeddingServiceTest.java](/backend/src/test/java/com/flamingo/ai/notebooklm/service/rag/EmbeddingServiceTest.java) | Java | 208 | 2 | 62 | 272 |
| [backend/src/test/java/com/flamingo/ai/notebooklm/service/rag/HybridSearchServiceTest.java](/backend/src/test/java/com/flamingo/ai/notebooklm/service/rag/HybridSearchServiceTest.java) | Java | 216 | 10 | 39 | 265 |
| [backend/src/test/java/com/flamingo/ai/notebooklm/service/rag/QueryReformulationServiceImplTest.java](/backend/src/test/java/com/flamingo/ai/notebooklm/service/rag/QueryReformulationServiceImplTest.java) | Java | 194 | 32 | 59 | 285 |
| [backend/src/test/java/com/flamingo/ai/notebooklm/service/rag/RagEvaluationTest.java](/backend/src/test/java/com/flamingo/ai/notebooklm/service/rag/RagEvaluationTest.java) | Java | 150 | 61 | 47 | 258 |
| [backend/src/test/java/com/flamingo/ai/notebooklm/service/rag/RagMetrics.java](/backend/src/test/java/com/flamingo/ai/notebooklm/service/rag/RagMetrics.java) | Java | 82 | 44 | 17 | 143 |
| [backend/src/test/java/com/flamingo/ai/notebooklm/service/rag/SessionIsolationIntegrationTest.java](/backend/src/test/java/com/flamingo/ai/notebooklm/service/rag/SessionIsolationIntegrationTest.java) | Java | 330 | 13 | 47 | 390 |
| [backend/src/test/java/com/flamingo/ai/notebooklm/service/session/SessionServiceImplTest.java](/backend/src/test/java/com/flamingo/ai/notebooklm/service/session/SessionServiceImplTest.java) | Java | 178 | 26 | 49 | 253 |
| [backend/src/test/resources/rag-test-set.json](/backend/src/test/resources/rag-test-set.json) | JSON | 181 | 0 | 1 | 182 |
| [backend/test-scripts/README.md](/backend/test-scripts/README.md) | Markdown | 110 | 0 | 38 | 148 |
| [backend/test-scripts/api-integration-test.ps1](/backend/test-scripts/api-integration-test.ps1) | PowerShell | 246 | 61 | 68 | 375 |
| [backend/test-scripts/api-integration-test.sh](/backend/test-scripts/api-integration-test.sh) | Shell Script | 226 | 63 | 65 | 354 |
| [docs/CHINESE\_SUPPORT.md](/docs/CHINESE_SUPPORT.md) | Markdown | 190 | 0 | 77 | 267 |
| [docs/DEBUG\_SEARCH\_ISSUE.md](/docs/DEBUG_SEARCH_ISSUE.md) | Markdown | 202 | 0 | 63 | 265 |
| [docs/FUTURE\_IMPROVEMENTS.md](/docs/FUTURE_IMPROVEMENTS.md) | Markdown | 140 | 0 | 37 | 177 |
| [docs/IMPLEMENTATION\_PLAN.md](/docs/IMPLEMENTATION_PLAN.md) | Markdown | 591 | 0 | 132 | 723 |
| [docs/MEMORY\_SERVICE\_DESIGN.md](/docs/MEMORY_SERVICE_DESIGN.md) | Markdown | 227 | 0 | 60 | 287 |
| [docs/RAG\_OPTIMIZATION\_DESIGN.md](/docs/RAG_OPTIMIZATION_DESIGN.md) | Markdown | 367 | 0 | 96 | 463 |
| [docs/SESSION\_ISOLATION\_TEST.md](/docs/SESSION_ISOLATION_TEST.md) | Markdown | 176 | 0 | 53 | 229 |
| [frontend/.postcssrc.json](/frontend/.postcssrc.json) | JSON | 5 | 0 | 1 | 6 |
| [frontend/CLAUDE.md](/frontend/CLAUDE.md) | Markdown | 371 | 0 | 95 | 466 |
| [frontend/README.md](/frontend/README.md) | Markdown | 36 | 0 | 24 | 60 |
| [frontend/angular.json](/frontend/angular.json) | JSON | 90 | 0 | 1 | 91 |
| [frontend/package-lock.json](/frontend/package-lock.json) | JSON | 9,781 | 0 | 1 | 9,782 |
| [frontend/package.json](/frontend/package.json) | JSON | 58 | 0 | 1 | 59 |
| [frontend/src/app/app.config.server.ts](/frontend/src/app/app.config.server.ts) | TypeScript | 10 | 0 | 3 | 13 |
| [frontend/src/app/app.config.ts](/frontend/src/app/app.config.ts) | TypeScript | 14 | 0 | 3 | 17 |
| [frontend/src/app/app.css](/frontend/src/app/app.css) | PostCSS | 4 | 0 | 1 | 5 |
| [frontend/src/app/app.html](/frontend/src/app/app.html) | HTML | 1 | 0 | 1 | 2 |
| [frontend/src/app/app.routes.server.ts](/frontend/src/app/app.routes.server.ts) | TypeScript | 15 | 0 | 2 | 17 |
| [frontend/src/app/app.routes.ts](/frontend/src/app/app.routes.ts) | TypeScript | 21 | 0 | 2 | 23 |
| [frontend/src/app/app.spec.ts](/frontend/src/app/app.spec.ts) | TypeScript | 22 | 0 | 4 | 26 |
| [frontend/src/app/app.ts](/frontend/src/app/app.ts) | TypeScript | 11 | 0 | 2 | 13 |
| [frontend/src/app/core/models/chat.model.ts](/frontend/src/app/core/models/chat.model.ts) | TypeScript | 29 | 0 | 6 | 35 |
| [frontend/src/app/core/models/document.model.ts](/frontend/src/app/core/models/document.model.ts) | TypeScript | 19 | 0 | 3 | 22 |
| [frontend/src/app/core/models/index.ts](/frontend/src/app/core/models/index.ts) | TypeScript | 3 | 0 | 1 | 4 |
| [frontend/src/app/core/models/session.model.ts](/frontend/src/app/core/models/session.model.ts) | TypeScript | 15 | 0 | 4 | 19 |
| [frontend/src/app/core/services/chat.service.ts](/frontend/src/app/core/services/chat.service.ts) | TypeScript | 229 | 20 | 38 | 287 |
| [frontend/src/app/core/services/document.service.ts](/frontend/src/app/core/services/document.service.ts) | TypeScript | 201 | 19 | 28 | 248 |
| [frontend/src/app/core/services/index.ts](/frontend/src/app/core/services/index.ts) | TypeScript | 3 | 0 | 1 | 4 |
| [frontend/src/app/core/services/session.service.ts](/frontend/src/app/core/services/session.service.ts) | TypeScript | 80 | 3 | 13 | 96 |
| [frontend/src/app/features/chat/components/chat-area/chat-area.component.ts](/frontend/src/app/features/chat/components/chat-area/chat-area.component.ts) | TypeScript | 201 | 5 | 19 | 225 |
| [frontend/src/app/features/chat/components/chat-message/chat-message.component.ts](/frontend/src/app/features/chat/components/chat-message/chat-message.component.ts) | TypeScript | 136 | 4 | 10 | 150 |
| [frontend/src/app/features/document/components/sources-panel/sources-panel.component.ts](/frontend/src/app/features/document/components/sources-panel/sources-panel.component.ts) | TypeScript | 199 | 0 | 10 | 209 |
| [frontend/src/app/features/session/components/session-layout/session-layout.component.ts](/frontend/src/app/features/session/components/session-layout/session-layout.component.ts) | TypeScript | 198 | 2 | 15 | 215 |
| [frontend/src/app/features/session/components/session-list/session-list.component.ts](/frontend/src/app/features/session/components/session-list/session-list.component.ts) | TypeScript | 206 | 0 | 10 | 216 |
| [frontend/src/app/shared/components/header/header.component.ts](/frontend/src/app/shared/components/header/header.component.ts) | TypeScript | 60 | 0 | 2 | 62 |
| [frontend/src/app/shared/components/mode-selector/mode-selector.component.ts](/frontend/src/app/shared/components/mode-selector/mode-selector.component.ts) | TypeScript | 103 | 0 | 9 | 112 |
| [frontend/src/environments/environment.prod.ts](/frontend/src/environments/environment.prod.ts) | TypeScript | 4 | 0 | 1 | 5 |
| [frontend/src/environments/environment.ts](/frontend/src/environments/environment.ts) | TypeScript | 4 | 0 | 1 | 5 |
| [frontend/src/index.html](/frontend/src/index.html) | HTML | 13 | 0 | 1 | 14 |
| [frontend/src/main.server.ts](/frontend/src/main.server.ts) | TypeScript | 6 | 0 | 3 | 9 |
| [frontend/src/main.ts](/frontend/src/main.ts) | TypeScript | 5 | 0 | 2 | 7 |
| [frontend/src/server.ts](/frontend/src/server.ts) | TypeScript | 36 | 24 | 9 | 69 |
| [frontend/src/styles.css](/frontend/src/styles.css) | PostCSS | 206 | 6 | 37 | 249 |
| [frontend/tsconfig.app.json](/frontend/tsconfig.app.json) | JSON | 15 | 2 | 1 | 18 |
| [frontend/tsconfig.json](/frontend/tsconfig.json) | JSON with Comments | 32 | 2 | 1 | 35 |
| [frontend/tsconfig.spec.json](/frontend/tsconfig.spec.json) | JSON | 12 | 2 | 1 | 15 |

[Summary](results.md) / Details / [Diff Summary](diff.md) / [Diff Details](diff-details.md)