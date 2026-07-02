package com.dbdeployer.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Pure-function coverage: JSON parsing of the Ollama admin API and request-body building. */
class OllamaAdminClientTest {

  private static final String TAGS_JSON =
      """
      {"models":[
        {"name":"llama3.1:8b","size":4661224676,"digest":"sha256:abc",
         "details":{"quantization_level":"Q4_K_M","parameter_size":"8.0B"}},
        {"name":"nomic-embed-text:latest","size":274302450,"digest":"sha256:def",
         "details":{"quantization_level":"F16","parameter_size":"137M"}}
      ]}
      """;

  private static final String PS_JSON =
      """
      {"models":[
        {"name":"llama3.1:8b","size":6654289920,"size_vram":6654289920,
         "expires_at":"2026-07-02T18:04:00Z","details":{}}
      ]}
      """;

  @Test
  void parseTags_maps_name_size_digest_and_quantization() throws Exception {
    List<OllamaAdminClient.LocalModel> models = OllamaAdminClient.parseTags(TAGS_JSON);

    assertThat(models).hasSize(2);
    assertThat(models.get(0).name()).isEqualTo("llama3.1:8b");
    assertThat(models.get(0).sizeBytes()).isEqualTo(4661224676L);
    assertThat(models.get(0).quantization()).isEqualTo("Q4_K_M");
    assertThat(models.get(0).parameterSize()).isEqualTo("8.0B");
    assertThat(models.get(1).name()).isEqualTo("nomic-embed-text:latest");
  }

  @Test
  void parsePs_maps_residency_footprint_and_expiry() throws Exception {
    List<OllamaAdminClient.LoadedModel> models = OllamaAdminClient.parsePs(PS_JSON);

    assertThat(models).hasSize(1);
    assertThat(models.get(0).name()).isEqualTo("llama3.1:8b");
    assertThat(models.get(0).sizeVramBytes()).isEqualTo(6654289920L);
    assertThat(models.get(0).expiresAt()).isEqualTo("2026-07-02T18:04:00Z");
  }

  @Test
  void parse_handles_empty_model_lists() throws Exception {
    assertThat(OllamaAdminClient.parseTags("{\"models\":[]}")).isEmpty();
    assertThat(OllamaAdminClient.parsePs("{}")).isEmpty();
  }

  @Test
  void keepAliveBody_emits_numbers_raw_and_durations_quoted() {
    assertThat(OllamaAdminClient.keepAliveBody("m", "-1"))
        .isEqualTo("{\"model\":\"m\",\"stream\":false,\"keep_alive\":-1}");
    assertThat(OllamaAdminClient.keepAliveBody("m", "0"))
        .isEqualTo("{\"model\":\"m\",\"stream\":false,\"keep_alive\":0}");
    assertThat(OllamaAdminClient.keepAliveBody("m", "5m"))
        .isEqualTo("{\"model\":\"m\",\"stream\":false,\"keep_alive\":\"5m\"}");
    // blank defaults to resident
    assertThat(OllamaAdminClient.keepAliveBody("m", " "))
        .isEqualTo("{\"model\":\"m\",\"stream\":false,\"keep_alive\":-1}");
  }

  @Test
  void bodies_escape_quotes_and_backslashes() {
    assertThat(OllamaAdminClient.nameBody("we\"ird\\tag"))
        .isEqualTo("{\"name\":\"we\\\"ird\\\\tag\"}");
  }
}
