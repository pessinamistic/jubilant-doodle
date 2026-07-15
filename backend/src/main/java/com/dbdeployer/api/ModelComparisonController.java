package com.dbdeployer.api;

import com.dbdeployer.ai.ComparisonChunk;
import com.dbdeployer.ai.ModelComparisonService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/** Streams a side-by-side comparison of the same prompt across 2–3 models over SSE. */
@Slf4j
@RestController
@RequestMapping("/models")
public class ModelComparisonController {

  private final ModelComparisonService comparisonService;

  public ModelComparisonController(ModelComparisonService comparisonService) {
    this.comparisonService = comparisonService;
  }

  @GetMapping(value = "/compare", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public Flux<ServerSentEvent<ComparisonChunk>> compare(
      @RequestParam String prompt,
      @RequestParam String modelA,
      @RequestParam String modelB,
      @RequestParam(required = false) String modelC,
      @RequestParam(required = false) String baseUrl) {
    log.info("[api] model compare: A={}, B={}, C={}", modelA, modelB, modelC);
    return comparisonService.compare(prompt, baseUrl, modelA, modelB, modelC);
  }
}
