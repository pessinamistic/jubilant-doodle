package com.dbdeployer.ai;

/**
 * Selects which runtime + model a chat/comparison request targets.
 *
 * @param baseUrl the Ollama runtime base URL (e.g. {@code http://localhost:11434})
 * @param modelId the model tag (e.g. {@code llama3.1:8b})
 */
public record ModelSelection(String baseUrl, String modelId) {}
