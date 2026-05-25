package com.dbdeployer.deploy;

import com.dbdeployer.model.DbInstance;
import org.springframework.stereotype.Component;

@Component
public class ConnectionStringBuilder {

    public String build(DbInstance instance) {
        var def = DatabaseCatalog.get(instance.getDbType());
        if (def == null) return "N/A";

        // Resolve each placeholder — prefer stored value, fall back to catalog default
        String username = coalesce(instance.getUsername(),
                firstPlaceholder(def, DatabaseCatalog.EnvVarType.TEXT), "");
        String password = coalesce(instance.getPassword(),
                firstPlaceholder(def, DatabaseCatalog.EnvVarType.PASSWORD), "");
        String database = coalesce(instance.getDatabaseName(),
                firstPlaceholder(def, DatabaseCatalog.EnvVarType.DATABASE), "");

        return def.connectionStringTemplate()
                .replace("{username}", username)
                .replace("{password}", password)
                .replace("{port}",     String.valueOf(instance.getHostPort()))
                .replace("{database}", database);
    }

    /** Returns a masked version safe for display (password replaced with ****) */
    public String buildMasked(DbInstance instance) {
        return build(instance).replaceAll(":[^@:/]+@", ":****@");
    }

    private static String firstPlaceholder(DatabaseCatalog.DbDefinition def,
                                           DatabaseCatalog.EnvVarType type) {
        return def.credentialEnvVars().stream()
                .filter(ev -> ev.type() == type)
                .map(DatabaseCatalog.EnvVar::placeholder)
                .findFirst()
                .orElse("");
    }

    /** Returns the first non-null, non-blank value from the candidates. */
    @SafeVarargs
    private static <T extends CharSequence> String coalesce(T... candidates) {
        for (T c : candidates) {
            if (c != null && !c.toString().isBlank()) return c.toString();
        }
        return "";
    }
}
