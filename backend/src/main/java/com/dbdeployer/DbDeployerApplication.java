package com.dbdeployer;

import java.util.Map;
import java.util.TimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class DbDeployerApplication {

    private static final Logger log = LoggerFactory.getLogger(DbDeployerApplication.class);

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
