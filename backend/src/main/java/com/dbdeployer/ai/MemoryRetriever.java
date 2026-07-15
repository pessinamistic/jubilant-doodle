package com.dbdeployer.ai;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

/**
 * Retrieves memories from pgvector and re-ranks them with the open-relay composite score (roadmap
 * §3.6). Because pgvector sorts only by vector distance, the pattern is <b>over-fetch by vector,
 * re-rank in Java</b>: pull {@code k·4} candidates by similarity, then apply {@link
 * RecencyFrequencyScorer} and take the top {@code k}.
 *
 * <p>{@link #rerank} is pure and unit-testable; {@link #retrieve} is the thin VectorStore adapter.
 */
@Slf4j
@Service
public class MemoryRetriever {

  private static final int OVERFETCH_FACTOR = 4;

  private final VectorStore vectorStore;
  private final RecencyFrequencyScorer scorer;

  public MemoryRetriever(VectorStore vectorStore, RecencyFrequencyScorer scorer) {
    this.vectorStore = vectorStore;
    this.scorer = scorer;
  }

  /**
   * Query for memories of an optional {@code type}, returning the top {@code k} after re-ranking.
   */
  public List<ScoredChunk> retrieve(String text, String metadataType, int k) {
    List<Document> results =
        vectorStore.similaritySearch(
            SearchRequest.builder().query(text).topK(k * OVERFETCH_FACTOR).build());
    if (results == null) return List.of();

    List<ScoredChunk> candidates =
        results.stream()
            .filter(d -> metadataType == null || metadataType.equals(d.getMetadata().get("type")))
            .map(MemoryRetriever::toChunk)
            .toList();
    return rerank(candidates, k, Instant.now());
  }

  /** Pure composite re-rank: score each candidate, sort best-first, take top k. */
  List<ScoredChunk> rerank(List<ScoredChunk> candidates, int k, Instant now) {
    return candidates.stream()
        .map(c -> c.withScore(scorer.score(c.semantic(), c.lastSeen(), c.accessCount(), now)))
        .sorted(Comparator.comparingDouble(ScoredChunk::score).reversed())
        .limit(Math.max(0, k))
        .toList();
  }

  static ScoredChunk toChunk(Document d) {
    Double s = d.getScore();
    double semantic = s != null ? s : 0.0;
    Map<String, Object> meta = d.getMetadata();
    return new ScoredChunk(
        d.getText(),
        meta,
        semantic,
        parseInstant(meta.get("last_seen")),
        parseInt(meta.get("access_count")),
        0.0);
  }

  private static Instant parseInstant(Object v) {
    if (v == null) return null;
    try {
      return Instant.parse(v.toString());
    } catch (Exception e) {
      return null;
    }
  }

  private static int parseInt(Object v) {
    if (v == null) return 0;
    try {
      return Integer.parseInt(v.toString());
    } catch (NumberFormatException e) {
      return 0;
    }
  }
}
