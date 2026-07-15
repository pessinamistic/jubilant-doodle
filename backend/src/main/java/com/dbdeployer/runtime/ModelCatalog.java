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
            "Tiny, fast general chat — great on CPU",
            true));
    add(
        new ModelDefinition(
            "llama3.2:3b",
            "Llama 3.2",
            ModelType.CHAT,
            3,
            Quantization.Q4_K_M,
            2800,
            5000,
            "Small general chat — good summariser model",
            true));
    add(
        new ModelDefinition(
            "llama3.1:8b",
            "Llama 3.1",
            ModelType.CHAT,
            8,
            Quantization.Q4_K_M,
            5500,
            9000,
            "Strong general-purpose 8B chat model",
            true));
    add(
        new ModelDefinition(
            "llama3.1:70b",
            "Llama 3.1",
            ModelType.CHAT,
            70,
            Quantization.Q4_K_M,
            42000,
            48000,
            "Flagship 70B — needs a serious GPU or lots of RAM",
            true));
    add(
        new ModelDefinition(
            "mistral:7b",
            "Mistral",
            ModelType.CHAT,
            7,
            Quantization.Q4_K_M,
            5000,
            8000,
            "Efficient 7B chat model",
            false));
    add(
        new ModelDefinition(
            "mistral-nemo:12b",
            "Mistral Nemo",
            ModelType.CHAT,
            12,
            Quantization.Q4_K_M,
            8000,
            13000,
            "12B with a 128k context window",
            true));
    add(
        new ModelDefinition(
            "gemma2:2b",
            "Gemma 2",
            ModelType.CHAT,
            2,
            Quantization.Q4_K_M,
            2000,
            4000,
            "Google's small, capable 2B model",
            false));
    add(
        new ModelDefinition(
            "gemma2:9b",
            "Gemma 2",
            ModelType.CHAT,
            9,
            Quantization.Q4_K_M,
            6500,
            10000,
            "Google's 9B mid-size chat model",
            false));
    add(
        new ModelDefinition(
            "qwen2.5:7b",
            "Qwen 2.5",
            ModelType.CHAT,
            7,
            Quantization.Q4_K_M,
            5000,
            8000,
            "Multilingual 7B with strong reasoning",
            true));
    add(
        new ModelDefinition(
            "qwen2.5:14b",
            "Qwen 2.5",
            ModelType.CHAT,
            14,
            Quantization.Q4_K_M,
            9500,
            15000,
            "Capable 14B all-rounder",
            true));
    add(
        new ModelDefinition(
            "qwen2.5:72b",
            "Qwen 2.5",
            ModelType.CHAT,
            72,
            Quantization.Q4_K_M,
            43000,
            49000,
            "Top-tier 72B — high hardware bar",
            true));
    add(
        new ModelDefinition(
            "phi3.5:3.8b",
            "Phi 3.5 Mini",
            ModelType.CHAT,
            4,
            Quantization.Q4_K_M,
            3000,
            5500,
            "Microsoft's small, sharp 3.8B model",
            false));

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
            "Code completion + infill, 7B",
            false));
    add(
        new ModelDefinition(
            "codellama:13b",
            "Code Llama",
            ModelType.CODE,
            13,
            Quantization.Q4_K_M,
            9000,
            14000,
            "Larger Code Llama for richer code tasks",
            false));
    add(
        new ModelDefinition(
            "codestral:22b",
            "Codestral",
            ModelType.CODE,
            22,
            Quantization.Q4_K_M,
            14000,
            20000,
            "Mistral's 22B code specialist",
            false));
    add(
        new ModelDefinition(
            "deepseek-coder-v2:16b",
            "DeepSeek Coder V2",
            ModelType.CODE,
            16,
            Quantization.Q4_K_M,
            10500,
            16000,
            "MoE code model with broad language support",
            false));
    add(
        new ModelDefinition(
            "qwen2.5-coder:7b",
            "Qwen 2.5 Coder",
            ModelType.CODE,
            7,
            Quantization.Q4_K_M,
            5000,
            8000,
            "Strong 7B code model",
            false));

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
            "Distilled reasoning model, 7B",
            false));
    add(
        new ModelDefinition(
            "deepseek-r1:8b",
            "DeepSeek R1",
            ModelType.REASONING,
            8,
            Quantization.Q4_K_M,
            5500,
            9000,
            "Distilled reasoning model, 8B",
            false));
    add(
        new ModelDefinition(
            "deepseek-r1:32b",
            "DeepSeek R1",
            ModelType.REASONING,
            32,
            Quantization.Q4_K_M,
            20000,
            26000,
            "Large distilled reasoning model",
            false));

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
            "Vision + language (image understanding)",
            false));
    add(
        new ModelDefinition(
            "llama3.2-vision:11b",
            "Llama 3.2 Vision",
            ModelType.VISION,
            11,
            Quantization.Q4_K_M,
            8000,
            13000,
            "Multimodal Llama 3.2, 11B",
            true));

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
            "768-dim embeddings — the RAG default",
            false));
    add(
        new ModelDefinition(
            "mxbai-embed-large:latest",
            "mxbai Embed Large",
            ModelType.EMBEDDING,
            1,
            Quantization.F16,
            1500,
            2500,
            "1024-dim embeddings, higher retrieval quality",
            false));

    // ── 2026 refresh — current-generation popular models ────────────────────
    // Sourced from ollama.com/library listings; VRAM/RAM keep the file's 0.65 GB/B convention,
    // cross-checked against published on-disk sizes at the default quantization. toolCalling
    // reflects each family's documented function/tool-calling support (conservative: "partial"/
    // "weak"/"indirect" upstream is recorded here as false).

    // Chat
    add(
        new ModelDefinition(
            "gpt-oss:20b",
            "gpt-oss",
            ModelType.CHAT,
            20,
            Quantization.MXFP4,
            14000,
            20000,
            "OpenAI's open-weight MoE — o3-mini class reasoning, fits a 16 GB GPU",
            true));
    add(
        new ModelDefinition(
            "gpt-oss:120b",
            "gpt-oss",
            ModelType.CHAT,
            120,
            Quantization.MXFP4,
            65000,
            80000,
            "Larger gpt-oss MoE — needs an 80 GB-class GPU or multi-GPU box",
            true));
    add(
        new ModelDefinition(
            "llama3.3:70b",
            "Llama 3.3",
            ModelType.CHAT,
            70,
            Quantization.Q4_K_M,
            43000,
            50000,
            "Latest 70B Llama — near 405B quality, still needs a serious GPU",
            true));
    add(
        new ModelDefinition(
            "llama4:scout",
            "Llama 4 Scout",
            ModelType.CHAT,
            109,
            Quantization.Q4_K_M,
            67000,
            76000,
            "Natively multimodal MoE, huge context — 60 GB+ VRAM, enthusiast/enterprise rigs only",
            true));
    add(
        new ModelDefinition(
            "qwen3:8b",
            "Qwen 3",
            ModelType.CHAT,
            8,
            Quantization.Q4_K_M,
            5500,
            9000,
            "Dense Qwen 3 — the recommended successor to Qwen 2.5 / Llama 3.1 8B",
            true));
    add(
        new ModelDefinition(
            "qwen3:14b",
            "Qwen 3",
            ModelType.CHAT,
            14,
            Quantization.Q4_K_M,
            9500,
            15000,
            "Dense Qwen 3 mid-size all-rounder",
            true));
    add(
        new ModelDefinition(
            "mistral-small:24b",
            "Mistral Small",
            ModelType.CHAT,
            24,
            Quantization.Q4_K_M,
            14000,
            20000,
            "Native function calling — the strongest mid-range agentic chat model",
            true));
    add(
        new ModelDefinition(
            "phi4:14b",
            "Phi 4",
            ModelType.CHAT,
            14,
            Quantization.Q4_K_M,
            9500,
            15000,
            "Microsoft's dense-knowledge STEM model — weak on tool calling",
            false));
    add(
        new ModelDefinition(
            "gemma3:1b",
            "Gemma 3",
            ModelType.CHAT,
            1,
            Quantization.Q4_K_M,
            1300,
            2500,
            "Tiny, text-only Gemma 3 — fast on CPU",
            false));

    // Code
    add(
        new ModelDefinition(
            "qwen3-coder:30b",
            "Qwen 3 Coder",
            ModelType.CODE,
            30,
            Quantization.Q4_K_M,
            19000,
            24000,
            "MoE agentic coding model — 256K context, fits a 24 GB card at Q4",
            true));
    add(
        new ModelDefinition(
            "devstral:24b",
            "Devstral",
            ModelType.CODE,
            24,
            Quantization.Q4_K_M,
            14000,
            20000,
            "Mistral's local agentic-coding model — pairs well with Aider/OpenCode",
            true));

    // Reasoning
    add(
        new ModelDefinition(
            "deepseek-r1:14b",
            "DeepSeek R1",
            ModelType.REASONING,
            14,
            Quantization.Q4_K_M,
            9500,
            15000,
            "Qwen-distilled reasoning — the sweet spot on 12 GB GPUs",
            false));
    add(
        new ModelDefinition(
            "deepseek-r1:70b",
            "DeepSeek R1",
            ModelType.REASONING,
            70,
            Quantization.Q4_K_M,
            43000,
            50000,
            "Llama 3.3-distilled reasoning model — not the real 671B R1",
            false));

    // Vision
    add(
        new ModelDefinition(
            "gemma3:4b",
            "Gemma 3",
            ModelType.VISION,
            4,
            Quantization.Q4_K_M,
            3500,
            6000,
            "Multimodal from 4B up — 140 languages, strong OCR/document understanding",
            false));
    add(
        new ModelDefinition(
            "gemma3:12b",
            "Gemma 3",
            ModelType.VISION,
            12,
            Quantization.Q4_K_M,
            8500,
            13000,
            "Mid-size multimodal Gemma 3",
            false));
    add(
        new ModelDefinition(
            "gemma3:27b",
            "Gemma 3",
            ModelType.VISION,
            27,
            Quantization.Q4_K_M,
            18000,
            24000,
            "Flagship Gemma 3 — DocVQA 85.6, the best compact vision model",
            false));
    add(
        new ModelDefinition(
            "qwen3-vl:8b",
            "Qwen 3 VL",
            ModelType.VISION,
            8,
            Quantization.Q4_K_M,
            6500,
            10000,
            "Strongest open vision-language family — screenshots, UI, visual agents",
            true));

    // Embedding
    add(
        new ModelDefinition(
            "embeddinggemma:latest",
            "EmbeddingGemma",
            ModelType.EMBEDDING,
            1,
            Quantization.F16,
            1000,
            2000,
            "Newest on-device embedder, built on Gemma 3 — 100+ languages, Matryoshka dims",
            false));
    add(
        new ModelDefinition(
            "bge-m3:latest",
            "BGE-M3",
            ModelType.EMBEDDING,
            1,
            Quantization.F16,
            1500,
            2500,
            "Multilingual, long-document RAG — also returns sparse vectors",
            false));
  }

  public static ModelDefinition get(String ollamaTag) {
    return CATALOG.get(ollamaTag);
  }

  public static List<ModelDefinition> all() {
    return List.copyOf(CATALOG.values());
  }
}
