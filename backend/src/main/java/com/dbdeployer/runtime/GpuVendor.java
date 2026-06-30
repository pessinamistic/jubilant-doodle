package com.dbdeployer.runtime;

/** GPU vendor detected on the host (or {@code NONE} for CPU-only). */
public enum GpuVendor {
  NVIDIA,
  AMD,
  APPLE,
  NONE
}
