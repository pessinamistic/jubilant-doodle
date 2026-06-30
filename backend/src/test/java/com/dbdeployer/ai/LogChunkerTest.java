package com.dbdeployer.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class LogChunkerTest {

  @Test
  void groups_records_and_promotes_error_level() {
    String logs =
        String.join(
            "\n",
            "2026-01-01T10:00:00Z starting up",
            "2026-01-01T10:00:01Z INFO ready",
            "2026-01-01T10:00:02Z ERROR connection refused",
            "    at com.example.Foo(Foo.java:1)");

    List<LogChunker.LogChunk> chunks = LogChunker.chunk(logs, 10);

    assertThat(chunks).hasSize(1);
    // The stack-trace continuation line attaches to the ERROR record.
    assertThat(chunks.get(0).content()).contains("at com.example.Foo");
    assertThat(chunks.get(0).level()).isEqualTo("ERROR");
  }

  @Test
  void splits_into_multiple_chunks_by_record_count() {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < 25; i++) {
      sb.append("2026-01-01T10:00:%02d INFO line %d%n".formatted(i, i));
    }
    List<LogChunker.LogChunk> chunks = LogChunker.chunk(sb.toString(), 10);
    assertThat(chunks).hasSize(3); // 10 + 10 + 5
  }

  @Test
  void continuation_lines_attach_to_current_record() {
    String logs = "2026-01-01T10:00:00Z WARN slow query\n  detail line 1\n  detail line 2";
    var records = LogChunker.splitIntoRecords(logs);
    assertThat(records).hasSize(1);
    assertThat(records.get(0)).contains("detail line 1").contains("detail line 2");
  }

  @Test
  void warn_beats_info_but_loses_to_error() {
    assertThat(LogChunker.dominantLevel("plain info line")).isEqualTo("INFO");
    assertThat(LogChunker.dominantLevel("a WARNING here")).isEqualTo("WARN");
    assertThat(LogChunker.dominantLevel("a warning then an Exception")).isEqualTo("ERROR");
  }

  @Test
  void empty_logs_yield_no_chunks() {
    assertThat(LogChunker.chunk("", 10)).isEmpty();
    assertThat(LogChunker.chunk(null, 10)).isEmpty();
  }
}
