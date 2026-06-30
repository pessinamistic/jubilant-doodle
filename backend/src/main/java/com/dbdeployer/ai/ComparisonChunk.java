package com.dbdeployer.ai;

/**
 * One streamed token from a model-comparison run.
 *
 * @param slot the column slot ({@code A}, {@code B}, {@code C})
 * @param model the model id producing this token
 * @param text the token text
 */
public record ComparisonChunk(String slot, String model, String text) {}
