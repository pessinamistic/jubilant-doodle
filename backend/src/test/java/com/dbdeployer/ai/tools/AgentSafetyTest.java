package com.dbdeployer.ai.tools;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AgentSafetyTest {

  private final AgentSafety safety = new AgentSafety();

  @Test
  void read_only_tools_need_no_confirmation() {
    assertThat(safety.isReadOnly("listInstances")).isTrue();
    assertThat(safety.isReadOnly("stackSummary")).isTrue();
    assertThat(safety.requiresConfirmation("readLogs")).isFalse();
  }

  @Test
  void destructive_tools_require_confirmation() {
    assertThat(safety.isDestructive("removeInstance")).isTrue();
    assertThat(safety.requiresConfirmation("removeInstance")).isTrue();
    assertThat(safety.requiresConfirmation("stopInstance")).isTrue();
  }

  @Test
  void write_tools_require_confirmation_but_are_not_destructive() {
    assertThat(safety.requiresConfirmation("deployDatabase")).isTrue();
    assertThat(safety.isDestructive("deployDatabase")).isFalse();
    assertThat(safety.isReadOnly("pullModel")).isFalse();
  }

  @Test
  void runaway_loop_cap_is_six() {
    assertThat(AgentSafety.MAX_TOOL_ROUNDS).isEqualTo(6);
  }
}
