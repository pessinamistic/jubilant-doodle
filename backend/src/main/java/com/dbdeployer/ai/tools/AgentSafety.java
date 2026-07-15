package com.dbdeployer.ai.tools;

import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Central safety policy for the agentic tool layer (roadmap §5):
 *
 * <ul>
 *   <li><b>read-only</b> tools run automatically;
 *   <li><b>write</b> and <b>destructive</b> tools require explicit user confirmation before the
 *       chat layer's manual tool-execution loop runs them;
 *   <li>a per-turn {@link #MAX_TOOL_ROUNDS} cap guards against runaway tool loops.
 * </ul>
 *
 * The classification is the single source of truth shared by the in-app agent and the MCP server.
 */
@Component
public class AgentSafety {

  /** Runaway-loop guard: max tool-call rounds per assistant turn. */
  public static final int MAX_TOOL_ROUNDS = 6;

  /** Irreversible operations — always confirmation-gated, target echoed back. */
  public static final Set<String> DESTRUCTIVE = Set.of("stopInstance", "removeInstance");

  /** State-changing but non-destructive — confirmation-gated. */
  public static final Set<String> WRITE = Set.of("deployDatabase", "createKafkaTopic", "pullModel");

  public boolean isDestructive(String toolName) {
    return DESTRUCTIVE.contains(toolName);
  }

  /** True if the tool must be confirmed before execution (write or destructive). */
  public boolean requiresConfirmation(String toolName) {
    return DESTRUCTIVE.contains(toolName) || WRITE.contains(toolName);
  }

  public boolean isReadOnly(String toolName) {
    return !requiresConfirmation(toolName);
  }
}
