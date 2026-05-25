package com.dbdeployer.config;

import com.dbdeployer.pipeline.PipelineProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@EnableAsync
@EnableScheduling
@EnableConfigurationProperties(PipelineProperties.class)
public class AppConfig implements WebMvcConfigurer {

    /**
     * Comma-separated allowed origins for CORS.
     * Configured via {@code dbdeployer.cors.allowed-origins} in application.yml
     * or the {@code DBDEPLOYER_CORS_ORIGINS} environment variable.
     *
     * <p>When the app is containerised, the frontend is served from the same
     * Spring Boot origin so CORS is not needed for the UI. The env var exists
     * for development (Vite on 5173) and any edge case where the UI is served
     * from a different host.
     */
    @Value("${dbdeployer.cors.allowed-origins:http://localhost:5173,http://localhost:3000,http://localhost:8080}")
    private String[] allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*");
    }
}
