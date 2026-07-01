package com.dbdeployer.ai;

/**
 * One event in the agentic chat SSE stream. The SSE {@code event:} name carries the kind ({@code
 * token}, {@code tool_call}, {@code confirmation_request}, {@code error}, {@code done}); this
 * record carries the payload.
 *
 * @param tool the tool name (for {@code tool_call} / {@code confirmation_request} events)
 * @param arguments the JSON arguments the model proposed for the tool (may be {@code null})
 * @param text assistant text (for {@code token} / {@code error} events)
 */
public record AgentEvent(String tool, String arguments, String text) {

  /** A tool the agent is invoking (read-only, auto-run) or proposing. */
  public static AgentEvent toolCall(String tool, String arguments) {
    return new AgentEvent(tool, arguments, null);
  }

  /** A chunk of assistant text. */
  public static AgentEvent text(String text) {
    return new AgentEvent(null, null, text);
  }
}
