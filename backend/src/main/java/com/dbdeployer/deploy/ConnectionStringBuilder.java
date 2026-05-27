package com.dbdeployer.deploy;

import com.dbdeployer.model.DeploymentConfig;
import org.springframework.stereotype.Component;

@Component
public class ConnectionStringBuilder {

  public String build(DeploymentConfig config) {
    var def = DatabaseCatalog.get(config.getDbType());
    if (def == null) return "N/A";

    String username =
        coalesce(config.getUsername(), firstPlaceholder(def, DatabaseCatalog.EnvVarType.TEXT), "");
    String password =
        coalesce(
            config.getPassword(), firstPlaceholder(def, DatabaseCatalog.EnvVarType.PASSWORD), "");
    String database =
        coalesce(
            config.getDatabaseName(),
            firstPlaceholder(def, DatabaseCatalog.EnvVarType.DATABASE),
            "");

    return def.connectionStringTemplate()
        .replace("{username}", username)
        .replace("{password}", password)
        .replace("{port}", String.valueOf(config.getHostPort()))
        .replace("{database}", database);
  }

  /** Returns a masked version safe for display (password replaced with ****) */
  public String buildMasked(DeploymentConfig config) {
    return build(config).replaceAll(":[^@:/]+@", ":****@");
  }

  private static String firstPlaceholder(
      DatabaseCatalog.DbDefinition def, DatabaseCatalog.EnvVarType type) {
    return def.credentialEnvVars().stream()
        .filter(ev -> ev.type() == type)
        .map(DatabaseCatalog.EnvVar::placeholder)
        .findFirst()
        .orElse("");
  }

  @SafeVarargs
  private static <T extends CharSequence> String coalesce(T... candidates) {
    for (T c : candidates) {
      if (c != null && !c.toString().isBlank()) return c.toString();
    }
    return "";
  }
}
