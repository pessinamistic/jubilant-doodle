package com.dbdeployer.service;

import com.dbdeployer.api.dto.MetricsHistoryResponse;
import com.dbdeployer.api.dto.MetricsHistoryResponse.MetricSample;
import com.zaxxer.hikari.HikariDataSource;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Collects JVM heap and HikariCP pool metrics on a fixed 30-second schedule,
 * keeping the last 60 samples in a ring buffer (~30-minute window).
 */
@Service
public class MetricsHistoryService {

  /** Number of samples kept (60 × 30 s = 30-minute window). */
  private static final int MAX_SAMPLES = 60;

  private static final int WINDOW_SECONDS = MAX_SAMPLES * 30;

  private final DataSource dataSource;
  private final JdbcTemplate jdbc;
  private final Deque<MetricSample> ring = new ArrayDeque<>(MAX_SAMPLES + 1);

  public MetricsHistoryService(
    DataSource dataSource) {
    this.dataSource = dataSource;
    this.jdbc = new JdbcTemplate(dataSource);
  }

  /** Called every 30 seconds after a 5-second initial delay. */
  @Scheduled(fixedRate = 30_000, initialDelay = 5_000)
  public synchronized void sample() {
    Runtime rt = Runtime.getRuntime();
    long heapUsed = (rt.totalMemory() - rt.freeMemory()) / (1024L * 1024L);
    long heapMax = rt.maxMemory() / (1024L * 1024L);
    int heapPct = heapMax > 0 ? (int) Math.round((double) heapUsed / heapMax * 100) : 0;

    int poolActive = 0;
    int poolMax = 0;
    int poolPct = 0;

    if (dataSource instanceof HikariDataSource hds) {
      var pool = hds.getHikariPoolMXBean();
      if (pool != null) {
        poolActive = pool.getActiveConnections();
        poolMax = hds.getMaximumPoolSize();
        poolPct = poolMax > 0 ? Math.round((float) poolActive / poolMax * 100) : 0;
      }
    }

    String ts = Instant.now().atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

    double pgDbSizeMb = 0.0;
    int pgActiveConns = 0;
    try {
      Double sizeResult = jdbc.queryForObject("SELECT pg_database_size(current_database()) / (1024.0 * 1024.0)",
          Double.class);
      if (sizeResult != null)
        pgDbSizeMb = Math.round(sizeResult * 100.0) / 100.0;
      Integer connResult = jdbc.queryForObject(
          "SELECT count(*)::int FROM pg_stat_activity WHERE datname = current_database() AND state IS NOT NULL",
          Integer.class);
      if (connResult != null)
        pgActiveConns = connResult;
    } catch (Exception ignored) {
      // Non-fatal: Postgres metrics default to 0 if unavailable
    }

    ring.addLast(
        new MetricSample(ts, heapUsed, heapMax, heapPct, poolActive, poolMax, poolPct, pgDbSizeMb, pgActiveConns));
    while (ring.size() > MAX_SAMPLES)
      ring.removeFirst();
  }

  /** Returns an immutable snapshot of the ring buffer. */
  public synchronized MetricsHistoryResponse getHistory() {
    return new MetricsHistoryResponse(List.copyOf(ring), WINDOW_SECONDS);
  }
}
