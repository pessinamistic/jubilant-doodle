package com.dbdeployer.runtime;

/** Common Ollama quantization levels. {@code Q4_K_M} is the typical default. */
public enum Quantization {
  Q4_K_M,
  Q5_K_M,
  Q6_K,
  Q8_0,
  F16
}
