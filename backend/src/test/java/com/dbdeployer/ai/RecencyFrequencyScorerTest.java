package com.dbdeployer.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class RecencyFrequencyScorerTest {

  private final RecencyFrequencyScorer scorer = new RecencyFrequencyScorer();

  @Test
  void fresh_frequent_high_semantic_scores_near_one() {
    Instant now = Instant.now();
    double s = scorer.score(1.0, now, 1000, now); // just seen, very frequent, perfect match
    assertThat(s).isCloseTo(1.0, within(0.01));
  }

  @Test
  void recency_decays_with_age_about_half_at_14_days() {
    Instant now = Instant.now();
    double fresh = RecencyFrequencyScorer.recency(now, now);
    double old = RecencyFrequencyScorer.recency(now.minus(Duration.ofDays(14)), now);
    assertThat(fresh).isCloseTo(1.0, within(0.001));
    assertThat(old).isCloseTo(0.5, within(0.05)); // λ=0.05 → ~14-day half-life
  }

  @Test
  void frequency_saturates_and_zero_for_unseen() {
    assertThat(RecencyFrequencyScorer.frequency(0)).isZero();
    assertThat(RecencyFrequencyScorer.frequency(1)).isPositive();
    // log1p saturates: a very high count is capped at <= 1 after Math.min in score().
    assertThat(RecencyFrequencyScorer.frequency(100000)).isGreaterThan(1.0);
  }

  @Test
  void weights_sum_correctly_for_pure_semantic() {
    Instant now = Instant.now();
    // semantic=1, recency≈1 (now), frequency=0 (unseen): 0.35 + 0.35 + 0 = 0.70
    double s = scorer.score(1.0, now, 0, now);
    assertThat(s).isCloseTo(0.70, within(0.01));
  }

  @Test
  void null_last_seen_yields_zero_recency() {
    assertThat(RecencyFrequencyScorer.recency(null, Instant.now())).isZero();
  }
}
