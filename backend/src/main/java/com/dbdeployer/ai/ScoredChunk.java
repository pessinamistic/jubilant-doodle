package com.dbdeployer.ai;

import java.time.Instant;
import java.util.Map;

/**
 * A retrieval candidate with its raw semantic similarity and the signals needed for composite
 * re-ranking (roadmap §3.6).
 *
 * @param content the chunk text
 * @param metadata the document metadata
 * @param semantic semantic similarity in [0,1] ({@code 1 - cosineDistance})
 * @param lastSeen when this chunk was last injected (recency signal; may be null)
 * @param accessCount how many times this chunk has been injected (frequency signal)
 * @param score the composite score (filled in by the re-ranker)
 */
public record ScoredChunk(
    String content,
    Map<String, Object> metadata,
    double semantic,
    Instant lastSeen,
    int accessCount,
    double score) {

  public ScoredChunk withScore(double newScore) {
    return new ScoredChunk(content, metadata, semantic, lastSeen, accessCount, newScore);
  }
}
