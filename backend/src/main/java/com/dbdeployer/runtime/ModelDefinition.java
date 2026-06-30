package com.dbdeployer.runtime;

/**
 * A curated, catalog model definition (same spirit as {@link
 * com.dbdeployer.deploy.DatabaseCatalog.DbDefinition}).
 *
 * @param ollamaTag the pull tag, e.g. {@code "llama3.1:8b"}
 * @param family human-readable family, e.g. {@code "Llama 3.1"}
 * @param type functional category
 * @param paramsBillions approximate parameter count in billions
 * @param defaultQuant default quantization the size estimates assume
 * @param minVramMb minimum GPU VRAM to run comfortably (at the default quant)
 * @param minRamMb minimum system RAM for a CPU fallback
 * @param description one-line description for the model card
 */
public record ModelDefinition(
    String ollamaTag,
    String family,
    ModelType type,
    long paramsBillions,
    Quantization defaultQuant,
    long minVramMb,
    long minRamMb,
    String description) {}
