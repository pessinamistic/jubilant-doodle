package com.dbdeployer.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OllamaModelPullerTest {

  @Test
  void builds_pull_body_with_stream_true() {
    assertThat(OllamaModelPuller.buildPullBody("llama3.1:8b"))
        .isEqualTo("{\"name\":\"llama3.1:8b\",\"stream\":true}");
  }

  @Test
  void escapes_quotes_and_backslashes_in_tag() {
    assertThat(OllamaModelPuller.buildPullBody("weird\"\\tag"))
        .isEqualTo("{\"name\":\"weird\\\"\\\\tag\",\"stream\":true}");
  }

  @Test
  void null_tag_yields_empty_name() {
    assertThat(OllamaModelPuller.buildPullBody(null)).isEqualTo("{\"name\":\"\",\"stream\":true}");
  }

  @Test
  void parses_download_progress_line() {
    OllamaModelPuller.PullProgress p =
        OllamaModelPuller.parseProgressLine(
            "{\"status\":\"pulling 6a0746a1ec1a\",\"digest\":\"sha256:abc\",\"total\":4661211808,\"completed\":1073741824}");
    assertThat(p.status()).isEqualTo("pulling 6a0746a1ec1a");
    assertThat(p.totalBytes()).isEqualTo(4661211808L);
    assertThat(p.completedBytes()).isEqualTo(1073741824L);
  }

  @Test
  void parses_status_only_line_with_zero_bytes() {
    OllamaModelPuller.PullProgress p =
        OllamaModelPuller.parseProgressLine("{\"status\":\"verifying sha256 digest\"}");
    assertThat(p.status()).isEqualTo("verifying sha256 digest");
    assertThat(p.totalBytes()).isZero();
    assertThat(p.completedBytes()).isZero();
  }

  @Test
  void unparseable_line_yields_null() {
    assertThat(OllamaModelPuller.parseProgressLine("not json")).isNull();
  }

  @Test
  void extracts_error_lines() {
    assertThat(
            OllamaModelPuller.extractError(
                "{\"error\":\"pull model manifest: file does not exist\"}"))
        .isEqualTo("pull model manifest: file does not exist");
    assertThat(OllamaModelPuller.extractError("{\"status\":\"success\"}")).isNull();
    assertThat(OllamaModelPuller.extractError("not json")).isNull();
  }
}
