package com.dbdeployer.ai;

import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Component;

/**
 * Composite memory-retrieval scorer (open-relay formula, roadmap §3.6):
 *
 * <pre>score = 0.35·semantic + 0.35·recency + 0.30·frequency</pre>
 *
 * <ul>
 *   <li><b>semantic</b> — {@code 1 - cosineDistance} (caller-provided)
 *   <li><b>recency</b> — exponential decay, λ≈0.05 ⇒ ~14-day half-life
 *   <li><b>frequency</b> — {@code log1p(accessCount)} saturated against {@code log1p(FREQ_NORM)}
 * </ul>
 *
 * Pure and unit-testable; the {@code now}-parameterised overload makes recency deterministic.
 */
@Component
public class RecencyFrequencyScorer {

  static final double RECENCY_LAMBDA = 0.05; // ~14-day half-life
  static final double FREQ_NORM = 20.0;
  static final double W_SEMANTIC = 0.35;
  static final double W_RECENCY = 0.35;
  static final double W_FREQUENCY = 0.30;

  public double score(double semantic, Instant lastSeen, int accessCount) {
    return score(semantic, lastSeen, accessCount, Instant.now());
  }

  /** Deterministic overload — {@code now} injected for testing. */
  public double score(double semantic, Instant lastSeen, int accessCount, Instant now) {
    double recency = recency(lastSeen, now);
    double frequency = frequency(accessCount);
    return W_SEMANTIC * semantic + W_RECENCY * recency + W_FREQUENCY * Math.min(frequency, 1.0);
  }

  static double recency(Instant lastSeen, Instant now) {
    if (lastSeen == null) return 0.0;
    double ageDays = Duration.between(lastSeen, now).toHours() / 24.0;
    if (ageDays < 0) ageDays = 0;
    return Math.exp(-RECENCY_LAMBDA * ageDays);
  }

  static double frequency(int accessCount) {
    if (accessCount <= 0) return 0.0;
    return Math.log1p(accessCount) / Math.log1p(FREQ_NORM);
  }
}
