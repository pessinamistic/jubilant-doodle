package com.dbdeployer.ai;

import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * Runs the same prompt against two or three models concurrently and merges their token streams,
 * each tagged with its slot + model id (roadmap Phase 3). Because {@link ModelRouter} already
 * exists, this is a {@link Flux#merge} over per-model {@link
 * org.springframework.ai.chat.client.ChatClient} streams.
 */
@Service
public class ModelComparisonService {

  private final ModelRouter modelRouter;

  public ModelComparisonService(ModelRouter modelRouter) {
    this.modelRouter = modelRouter;
  }

  public Flux<ServerSentEvent<ComparisonChunk>> compare(
      String prompt, String baseUrl, String modelA, String modelB, String modelC) {
    Flux<ServerSentEvent<ComparisonChunk>> merged =
        Flux.merge(
            streamFor("A", baseUrl, modelA, prompt), streamFor("B", baseUrl, modelB, prompt));
    if (modelC != null && !modelC.isBlank()) {
      merged = Flux.merge(merged, streamFor("C", baseUrl, modelC, prompt));
    }
    return merged;
  }

  private Flux<ServerSentEvent<ComparisonChunk>> streamFor(
      String slot, String baseUrl, String model, String prompt) {
    return modelRouter.statelessClientFor(baseUrl, model).prompt(prompt).stream()
        .content()
        .map(
            text ->
                ServerSentEvent.builder(new ComparisonChunk(slot, model, text))
                    .event("token")
                    .build());
  }
}
