package com.dbdeployer.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ModelCatalogTest {

  @Test
  void catalog_is_non_empty_and_well_formed() {
    var all = ModelCatalog.all();
    assertThat(all).hasSizeGreaterThanOrEqualTo(20);
    assertThat(all)
        .allSatisfy(
            m -> {
              assertThat(m.ollamaTag()).isNotBlank();
              assertThat(m.family()).isNotBlank();
              assertThat(m.type()).isNotNull();
              assertThat(m.paramsBillions()).isPositive();
              assertThat(m.minVramMb()).isPositive();
              assertThat(m.minRamMb()).isGreaterThanOrEqualTo(m.minVramMb());
              assertThat(m.description()).isNotBlank();
            });
  }

  @Test
  void tags_are_unique() {
    var tags = ModelCatalog.all().stream().map(ModelDefinition::ollamaTag).toList();
    assertThat(tags).doesNotHaveDuplicates();
  }

  @Test
  void covers_every_model_type() {
    var types = ModelCatalog.all().stream().map(ModelDefinition::type).distinct().toList();
    assertThat(types)
        .contains(
            ModelType.CHAT,
            ModelType.CODE,
            ModelType.EMBEDDING,
            ModelType.VISION,
            ModelType.REASONING);
  }

  @Test
  void get_returns_a_known_model() {
    assertThat(ModelCatalog.get("llama3.1:8b")).isNotNull();
    assertThat(ModelCatalog.get("does-not-exist")).isNull();
  }
}
