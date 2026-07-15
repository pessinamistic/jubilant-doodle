package com.dbdeployer.runtime;

/**
 * A catalog model paired with how well it fits the current host.
 *
 * @param model the catalog definition
 * @param compatibility scored fit level
 * @param speedTier a short human label for expected throughput
 */
public record ModelSuggestion(
    ModelDefinition model, CompatibilityLevel compatibility, String speedTier) {}
