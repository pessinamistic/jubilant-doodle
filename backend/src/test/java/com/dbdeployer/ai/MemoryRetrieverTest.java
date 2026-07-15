package com.dbdeployer.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MemoryRetrieverTest {

  private final MemoryRetriever retriever = new MemoryRetriever(null, new RecencyFrequencyScorer());

  private static ScoredChunk chunk(String id, double semantic, Instant lastSeen, int access) {
    return new ScoredChunk(id, Map.of(), semantic, lastSeen, access, 0.0);
  }

  @Test
  void recent_frequent_chunk_outranks_a_marginally_more_similar_stale_one() {
    Instant now = Instant.now();
    var stale = chunk("stale", 0.95, now.minus(Duration.ofDays(60)), 0); // high semantic, very old
    var fresh = chunk("fresh", 0.80, now, 25); // slightly lower semantic, just-seen + frequent

    var ranked = retriever.rerank(List.of(stale, fresh), 2, now);

    assertThat(ranked).extracting(ScoredChunk::content).containsExactly("fresh", "stale");
  }

  @Test
  void limits_to_top_k() {
    Instant now = Instant.now();
    var ranked =
        retriever.rerank(
            List.of(chunk("a", 0.9, now, 5), chunk("b", 0.8, now, 5), chunk("c", 0.7, now, 5)),
            2,
            now);
    assertThat(ranked).hasSize(2);
    assertThat(ranked).extracting(ScoredChunk::content).containsExactly("a", "b");
  }

  @Test
  void pure_semantic_ordering_when_recency_and_frequency_equal() {
    Instant now = Instant.now();
    var ranked =
        retriever.rerank(List.of(chunk("low", 0.2, now, 3), chunk("high", 0.9, now, 3)), 2, now);
    assertThat(ranked).extracting(ScoredChunk::content).containsExactly("high", "low");
  }

  @Test
  void empty_candidates_yield_empty_result() {
    assertThat(retriever.rerank(List.of(), 5, Instant.now())).isEmpty();
  }
}
