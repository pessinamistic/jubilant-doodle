package com.dbdeployer.runtime;

import java.util.List;

/**
 * A single result from the remote Ollama library search (see {@link OllamaLibrarySearchService}).
 * Distinct from {@link ModelDefinition} — this describes a model *family* as published on
 * ollama.com/library, not a hardware-scored catalog entry with a specific pull tag.
 *
 * @param modelIdentifier the family identifier used to build the pull tag, e.g. {@code "llama3.1"}
 * @param namespace community namespace, or {@code null} for official models
 * @param description one-line description from the library page
 * @param capability a notable capability tag (e.g. {@code "Tools"}), or {@code null}
 * @param labels available size labels, e.g. {@code ["8B", "70B"]}
 * @param pulls total pull count — used to rank/sort results by popularity
 * @param tagCount number of published tags for this family
 * @param officialSource true for official ollama.com/library models, false for community namespaces
 * @param url the ollama.com library page for this model
 */
public record OllamaLibraryModel(
    String modelIdentifier,
    String namespace,
    String description,
    String capability,
    List<String> labels,
    long pulls,
    int tagCount,
    boolean officialSource,
    String url) {}
