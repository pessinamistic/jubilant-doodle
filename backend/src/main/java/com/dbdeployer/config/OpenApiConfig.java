package com.dbdeployer.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

  @Bean
  public OpenAPI openAPI() {
    return new OpenAPI()
        .info(
            new Info()
                .title("Port Wrangler API")
                .description(
                    "REST API for managing and deploying local database instances. "
                        + "Supports PostgreSQL, MySQL, Redis, MongoDB, Elasticsearch and more.")
                .version("1.0.0")
                .license(new License().name("MIT")));
  }

  @Bean
  public GroupedOpenApi defaultApi() {
    return GroupedOpenApi.builder()
        .group("default")
        .pathsToMatch("/**") // Scans all endpoints
        .build();
  }
}
