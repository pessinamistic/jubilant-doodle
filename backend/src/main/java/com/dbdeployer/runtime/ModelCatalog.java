package com.dbdeployer.runtime;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Curated catalog of popular Ollama models for the Model Cookbook. Static and refreshed via PR — no
 * external API call at runtime (same pattern as {@link com.dbdeployer.deploy.DatabaseCatalog}).
 *
 * <p>VRAM/RAM minimums are rough rules of thumb at the default quantization (Q4_K_M ≈ 0.65 GB per
 * billion params plus runtime overhead). They are deliberately conservative — used only to bucket a
 * model into FAST / OK / CPU_ONLY / TOO_LARGE, not to guarantee exact fit.
 */
public final class ModelCatalog {

  private ModelCatalog() {}

  private static final Map<String, ModelDefinition> CATALOG = new LinkedHashMap<>();

  private static void add(ModelDefinition d) {
    CATALOG.put(d.ollamaTag(), d);
  }

  static {
    // ── Chat ────────────────────────────────────────────────────────────────
    add(
        new ModelDefinition(
            "llama3.2:1b",
            "Llama 3.2",
            ModelType.CHAT,
            1,
            Quantization.Q4_K_M,
            1300,
            2500,
            "Tiny, fast general chat — great on CPU"));
    add(
        new ModelDefinition(
            "llama3.2:3b",
            "Llama 3.2",
            ModelType.CHAT,
            3,
            Quantization.Q4_K_M,
            2800,
            5000,
            "Small general chat — good summariser model"));
    add(
        new ModelDefinition(
            "llama3.1:8b",
            "Llama 3.1",
            ModelType.CHAT,
            8,
            Quantization.Q4_K_M,
            5500,
            9000,
            "Strong general-purpose 8B chat model"));
    add(
        new ModelDefinition(
            "llama3.1:70b",
            "Llama 3.1",
            ModelType.CHAT,
            70,
            Quantization.Q4_K_M,
            42000,
            48000,
            "Flagship 70B — needs a serious GPU or lots of RAM"));
    add(
        new ModelDefinition(
            "mistral:7b",
            "Mistral",
            ModelType.CHAT,
            7,
            Quantization.Q4_K_M,
            5000,
            8000,
            "Efficient 7B chat model"));
    add(
        new ModelDefinition(
            "mistral-nemo:12b",
            "Mistral Nemo",
            ModelType.CHAT,
            12,
            Quantization.Q4_K_M,
            8000,
            13000,
            "12B with a 128k context window"));
    add(
        new ModelDefinition(
            "gemma2:2b",
            "Gemma 2",
            ModelType.CHAT,
            2,
            Quantization.Q4_K_M,
            2000,
            4000,
            "Google's small, capable 2B model"));
    add(
        new ModelDefinition(
            "gemma2:9b",
            "Gemma 2",
            ModelType.CHAT,
            9,
            Quantization.Q4_K_M,
            6500,
            10000,
            "Google's 9B mid-size chat model"));
    add(
        new ModelDefinition(
            "qwen2.5:7b",
            "Qwen 2.5",
            ModelType.CHAT,
            7,
            Quantization.Q4_K_M,
            5000,
            8000,
            "Multilingual 7B with strong reasoning"));
    add(
        new ModelDefinition(
            "qwen2.5:14b",
            "Qwen 2.5",
            ModelType.CHAT,
            14,
            Quantization.Q4_K_M,
            9500,
            15000,
            "Capable 14B all-rounder"));
    add(
        new ModelDefinition(
            "qwen2.5:72b",
            "Qwen 2.5",
            ModelType.CHAT,
            72,
            Quantization.Q4_K_M,
            43000,
            49000,
            "Top-tier 72B — high hardware bar"));
    add(
        new ModelDefinition(
            "phi3.5:3.8b",
            "Phi 3.5 Mini",
            ModelType.CHAT,
            4,
            Quantization.Q4_K_M,
            3000,
            5500,
            "Microsoft's small, sharp 3.8B model"));

    // ── Code ──────────────────────────────────────────────────────────────────
    add(
        new ModelDefinition(
            "codellama:7b",
            "Code Llama",
            ModelType.CODE,
            7,
            Quantization.Q4_K_M,
            5000,
            8000,
            "Code completion + infill, 7B"));
    add(
        new ModelDefinition(
            "codellama:13b",
            "Code Llama",
            ModelType.CODE,
            13,
            Quantization.Q4_K_M,
            9000,
            14000,
            "Larger Code Llama for richer code tasks"));
    add(
        new ModelDefinition(
            "codestral:22b",
            "Codestral",
            ModelType.CODE,
            22,
            Quantization.Q4_K_M,
            14000,
            20000,
            "Mistral's 22B code specialist"));
    add(
        new ModelDefinition(
            "deepseek-coder-v2:16b",
            "DeepSeek Coder V2",
            ModelType.CODE,
            16,
            Quantization.Q4_K_M,
            10500,
            16000,
            "MoE code model with broad language support"));
    add(
        new ModelDefinition(
            "qwen2.5-coder:7b",
            "Qwen 2.5 Coder",
            ModelType.CODE,
            7,
            Quantization.Q4_K_M,
            5000,
            8000,
            "Strong 7B code model"));

    // ── Reasoning ──────────────────────────────────────────────────────────────
    add(
        new ModelDefinition(
            "deepseek-r1:7b",
            "DeepSeek R1",
            ModelType.REASONING,
            7,
            Quantization.Q4_K_M,
            5000,
            8000,
            "Distilled reasoning model, 7B"));
    add(
        new ModelDefinition(
            "deepseek-r1:8b",
            "DeepSeek R1",
            ModelType.REASONING,
            8,
            Quantization.Q4_K_M,
            5500,
            9000,
            "Distilled reasoning model, 8B"));
    add(
        new ModelDefinition(
            "deepseek-r1:32b",
            "DeepSeek R1",
            ModelType.REASONING,
            32,
            Quantization.Q4_K_M,
            20000,
            26000,
            "Large distilled reasoning model"));

    // ── Vision ─────────────────────────────────────────────────────────────────
    add(
        new ModelDefinition(
            "llava:7b",
            "LLaVA",
            ModelType.VISION,
            7,
            Quantization.Q4_K_M,
            5500,
            9000,
            "Vision + language (image understanding)"));
    add(
        new ModelDefinition(
            "llama3.2-vision:11b",
            "Llama 3.2 Vision",
            ModelType.VISION,
            11,
            Quantization.Q4_K_M,
            8000,
            13000,
            "Multimodal Llama 3.2, 11B"));

    // ── Embedding ──────────────────────────────────────────────────────────────
    add(
        new ModelDefinition(
            "nomic-embed-text:latest",
            "Nomic Embed Text",
            ModelType.EMBEDDING,
            1,
            Quantization.F16,
            1000,
            2000,
            "768-dim embeddings — the RAG default"));
    add(
        new ModelDefinition(
            "mxbai-embed-large:latest",
            "mxbai Embed Large",
            ModelType.EMBEDDING,
            1,
            Quantization.F16,
            1500,
            2500,
            "1024-dim embeddings, higher retrieval quality"));
  }

  public static ModelDefinition get(String ollamaTag) {
    return CATALOG.get(ollamaTag);
  }

  public static List<ModelDefinition> all() {
    return List.copyOf(CATALOG.values());
  }
}
