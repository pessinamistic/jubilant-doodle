package com.dbdeployer.ai;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the Spring AI chat layer. The {@link ChatMemoryRepository} is auto-configured by the {@code
 * spring-ai-starter-model-chat-memory-repository-jdbc} starter (JDBC, backed by the {@code
 * SPRING_AI_CHAT_MEMORY} table from Liquibase V7). We override only the {@link ChatMemory} bean to
 * bound the verbatim window to the last 4 turns (8 messages) per the token-optimization design
 * (roadmap §3).
 */
@Configuration
public class ChatClientConfig {

  public static final String SYSTEM_PROMPT =
      """
      You are Port Wrangler's infrastructure assistant. Port Wrangler is a local-first Docker
      infrastructure manager that deploys databases, message brokers, observability tools, and
      local LLM runtimes as containers. Be concise and technical. When unsure, say so.
      """;

  /** Verbatim window: last 4 turns (8 messages), backed by the JDBC chat-memory repository. */
  @Bean
  ChatMemory chatMemory(ChatMemoryRepository chatMemoryRepository) {
    return MessageWindowChatMemory.builder()
        .chatMemoryRepository(chatMemoryRepository)
        .maxMessages(8)
        .build();
  }

  /**
   * Drives manual tool execution for the agentic chat loop. With {@code
   * internalToolExecutionEnabled(false)} on the request options, the model returns tool calls
   * without running them; this manager executes the approved ones and appends their results to the
   * conversation history so the loop can continue (roadmap §5).
   */
  @Bean
  ToolCallingManager toolCallingManager() {
    return ToolCallingManager.builder().build();
  }
}
