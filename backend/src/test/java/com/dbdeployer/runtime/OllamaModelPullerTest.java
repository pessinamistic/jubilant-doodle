package com.dbdeployer.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OllamaModelPullerTest {

  @Test
  void builds_pull_body_with_stream_false() {
    assertThat(OllamaModelPuller.buildPullBody("llama3.1:8b"))
        .isEqualTo("{\"name\":\"llama3.1:8b\",\"stream\":false}");
  }

  @Test
  void escapes_quotes_and_backslashes_in_tag() {
    assertThat(OllamaModelPuller.buildPullBody("weird\"\\tag"))
        .isEqualTo("{\"name\":\"weird\\\"\\\\tag\",\"stream\":false}");
  }

  @Test
  void null_tag_yields_empty_name() {
    assertThat(OllamaModelPuller.buildPullBody(null)).isEqualTo("{\"name\":\"\",\"stream\":false}");
  }
}
