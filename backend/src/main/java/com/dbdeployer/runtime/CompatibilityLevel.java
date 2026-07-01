package com.dbdeployer.runtime;

/**
 * How well a model fits the detected hardware.
 *
 * <ul>
 *   <li>{@code FAST} — GPU with comfortable VRAM headroom (badge: green)
 *   <li>{@code OK} — GPU, tight VRAM (badge: amber)
 *   <li>{@code CPU_ONLY} — no/insufficient GPU but enough system RAM; slow (badge: orange)
 *   <li>{@code TOO_LARGE} — insufficient RAM to run at all (badge: red)
 * </ul>
 */
public enum CompatibilityLevel {
  FAST,
  OK,
  CPU_ONLY,
  TOO_LARGE
}
