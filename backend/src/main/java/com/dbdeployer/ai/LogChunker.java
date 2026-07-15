package com.dbdeployer.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Splits container log text into retrievable chunks (logs are not prose). Strategy (roadmap Phase
 * 4):
 *
 * <ul>
 *   <li>split on log-record boundaries — a record starts at a timestamp-prefixed line; continuation
 *       lines (stack traces) attach to the current record;
 *   <li>group {@code recordsPerChunk} adjacent records into one chunk;
 *   <li>promote the highest severity seen in the chunk into {@code level} metadata ({@code ERROR |
 *       WARN | INFO}) so retrieval can boost it.
 * </ul>
 *
 * Pure and unit-testable — no Spring AI or DB coupling.
 */
public final class LogChunker {

  private LogChunker() {}

  /** A chunk of grouped log records with its dominant severity. */
  public record LogChunk(String content, String level) {}

  // Leading ISO-ish timestamp or Docker RFC3339 prefix marks a new record.
  private static final Pattern RECORD_START =
      Pattern.compile(
          "^\\s*(\\d{4}-\\d{2}-\\d{2}[T ]\\d{2}:\\d{2}:\\d{2}|\\[?\\d{4}/\\d{2}/\\d{2}).*");

  private static final Pattern ERROR = Pattern.compile("(?i)\\b(error|exception|fatal|panic)\\b");
  private static final Pattern WARN = Pattern.compile("(?i)\\b(warn|warning)\\b");

  public static List<LogChunk> chunk(String logs, int recordsPerChunk) {
    List<LogChunk> chunks = new ArrayList<>();
    if (logs == null || logs.isBlank()) return chunks;
    int perChunk = Math.max(1, recordsPerChunk);

    List<String> records = splitIntoRecords(logs);
    for (int i = 0; i < records.size(); i += perChunk) {
      List<String> group = records.subList(i, Math.min(i + perChunk, records.size()));
      String content = String.join("\n", group);
      chunks.add(new LogChunk(content, dominantLevel(content)));
    }
    return chunks;
  }

  /**
   * Group physical lines into logical records (continuation lines attach to the current record).
   */
  static List<String> splitIntoRecords(String logs) {
    List<String> records = new ArrayList<>();
    StringBuilder current = new StringBuilder();
    for (String line : logs.split("\n", -1)) {
      boolean isNewRecord = RECORD_START.matcher(line).matches();
      if (isNewRecord && current.length() > 0) {
        records.add(current.toString());
        current.setLength(0);
      }
      if (current.length() > 0) current.append("\n");
      current.append(line);
    }
    if (current.length() > 0) records.add(current.toString());
    // Drop fully-blank records.
    records.removeIf(r -> r.isBlank());
    return records;
  }

  /** Highest severity present wins: ERROR > WARN > INFO. */
  static String dominantLevel(String content) {
    if (ERROR.matcher(content).find()) return "ERROR";
    if (WARN.matcher(content).find()) return "WARN";
    return "INFO";
  }
}
