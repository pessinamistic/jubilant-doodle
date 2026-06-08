package com.dbdeployer;

import java.util.Map;
import java.util.TimeZone;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@Slf4j
@EnableScheduling
@SpringBootApplication
public class DbDeployerApplication {

    private static final Map<String, String> LEGACY_TZ_ALIASES = Map.of("Asia/Calcutta", "Asia/Kolkata");

    public static void main(String[] args) {
        normalizeLegacyTimezoneAlias();
        SpringApplication.run(DbDeployerApplication.class, args);
    }

    private static void normalizeLegacyTimezoneAlias() {
        String currentTz = TimeZone.getDefault().getID();
        String normalizedTz = LEGACY_TZ_ALIASES.getOrDefault(currentTz, currentTz);

        if (!normalizedTz.equals(currentTz)) {
            TimeZone.setDefault(TimeZone.getTimeZone(normalizedTz));
            System.setProperty("user.timezone", normalizedTz);
            log.info("Normalized JVM timezone from {} to {} for PostgreSQL compatibility", currentTz, normalizedTz);
        }
    }
}
