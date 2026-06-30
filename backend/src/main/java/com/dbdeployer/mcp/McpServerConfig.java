package com.dbdeployer.mcp;

import com.dbdeployer.ai.tools.AgentSafety;
import com.dbdeployer.ai.tools.InfrastructureTools;
import java.util.Arrays;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Exposes {@link InfrastructureTools} as an MCP server so external IDE assistants (Cursor, Claude
 * Desktop, Continue.dev) can drive Port Wrangler's infrastructure tools directly (roadmap §5).
 *
 * <p><b>Safe by default:</b> destructive tools ({@code stopInstance}, {@code removeInstance}) are
 * stripped from the MCP surface unless {@code portwrangler.mcp.write-enabled=true}. The tool
 * definitions are identical to the in-app agent's — same validation, same pipeline, no duplication.
 */
@Slf4j
@Configuration
public class McpServerConfig {

  @Bean
  public ToolCallbackProvider portWranglerMcpTools(
      InfrastructureTools tools,
      @Value("${portwrangler.mcp.write-enabled:false}") boolean writeEnabled) {

    ToolCallback[] all =
        MethodToolCallbackProvider.builder().toolObjects(tools).build().getToolCallbacks();
    ToolCallback[] exposed = writeEnabled ? all : filterReadOnly(all);

    log.info(
        "[mcp] Port Wrangler MCP server exposing {} tool(s) (write-enabled={})",
        exposed.length,
        writeEnabled);
    return ToolCallbackProvider.from(exposed);
  }

  /** Pure: drop destructive tools by name. Unit-testable. */
  static ToolCallback[] filterReadOnly(ToolCallback[] all) {
    return Arrays.stream(all)
        .filter(cb -> !AgentSafety.DESTRUCTIVE.contains(cb.getToolDefinition().name()))
        .toArray(ToolCallback[]::new);
  }
}
