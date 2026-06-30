package com.dbdeployer.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;

import com.dbdeployer.ai.tools.InfrastructureTools;
import com.dbdeployer.deploy.ConnectionStringBuilder;
import com.dbdeployer.service.DbInstanceService;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;

@ExtendWith(MockitoExtension.class)
class McpServerConfigTest {

  @Mock private DbInstanceService service;
  @Mock private ConnectionStringBuilder connBuilder;

  private ToolCallback[] allToolCallbacks() {
    lenient().when(service.listAll()).thenReturn(List.of());
    var tools = new InfrastructureTools(service, connBuilder);
    return MethodToolCallbackProvider.builder().toolObjects(tools).build().getToolCallbacks();
  }

  private static List<String> names(ToolCallback[] cbs) {
    return Arrays.stream(cbs).map(cb -> cb.getToolDefinition().name()).toList();
  }

  @Test
  void all_tools_include_read_only_and_destructive() {
    assertThat(names(allToolCallbacks()))
        .contains("listInstances", "stackSummary", "stopInstance", "removeInstance");
  }

  @Test
  void filterReadOnly_strips_destructive_tools() {
    ToolCallback[] readOnly = McpServerConfig.filterReadOnly(allToolCallbacks());

    assertThat(names(readOnly))
        .contains("listInstances", "readLogs", "connectionConfig", "stackSummary")
        .doesNotContain("stopInstance", "removeInstance");
  }
}
