package com.dbdeployer.deploy;

import com.dbdeployer.model.DbType;
import java.util.*;

/**
 * Central catalog of all supported databases.
 * Defines Docker images, default ports, supported versions,
 * required env vars for credentials, and connection string templates.
 */
public class DatabaseCatalog {

    public record DbDefinition(
            DbType type,
            String displayName,
            String description,
            String icon,
            String dockerImage,
            int defaultPort,
            List<String> versions,
            List<EnvVar> credentialEnvVars,
            String connectionStringTemplate,
            String dataVolumePath,
            boolean supportsDatabase,
            boolean supportsUsername,
            boolean supportsPassword
    ) {}

    public record EnvVar(
            String name,
            String label,
            String placeholder,
            boolean required,
            EnvVarType type
    ) {}

    public enum EnvVarType { TEXT, PASSWORD, DATABASE }

    private static final Map<DbType, DbDefinition> CATALOG = new LinkedHashMap<>();

    static {
        CATALOG.put(DbType.POSTGRESQL, new DbDefinition(
                DbType.POSTGRESQL,
                "PostgreSQL",
                "The world's most advanced open source relational database",
                "🐘",
                "postgres",
                5432,
                List.of("17", "16", "15", "14", "13", "12"),
                List.of(
                        new EnvVar("POSTGRES_USER",     "Username",      "postgres",  true,  EnvVarType.TEXT),
                        new EnvVar("POSTGRES_PASSWORD", "Password",      "secret",    true,  EnvVarType.PASSWORD),
                        new EnvVar("POSTGRES_DB",       "Database Name", "mydb",      false, EnvVarType.DATABASE)
                ),
                "postgresql://{username}:{password}@localhost:{port}/{database}",
                "/var/lib/postgresql/data",
                true, true, true
        ));

        CATALOG.put(DbType.MYSQL, new DbDefinition(
                DbType.MYSQL,
                "MySQL",
                "The world's most popular open source relational database",
                "🐬",
                "mysql",
                3306,
                List.of("9.2", "8.4", "8.0", "5.7"),
                List.of(
                        new EnvVar("MYSQL_USER",          "Username",      "user",      false, EnvVarType.TEXT),
                        new EnvVar("MYSQL_PASSWORD",      "Password",      "secret",    false, EnvVarType.PASSWORD),
                        new EnvVar("MYSQL_ROOT_PASSWORD", "Root Password", "rootsecret",true,  EnvVarType.PASSWORD),
                        new EnvVar("MYSQL_DATABASE",      "Database Name", "mydb",      false, EnvVarType.DATABASE)
                ),
                "mysql://{username}:{password}@localhost:{port}/{database}",
                "/var/lib/mysql",
                true, true, true
        ));

        CATALOG.put(DbType.MONGODB, new DbDefinition(
                DbType.MONGODB,
                "MongoDB",
                "The developer data platform built on a document model",
                "🍃",
                "mongo",
                27017,
                List.of("8.0", "7.0", "6.0", "5.0", "4.4"),
                List.of(
                        new EnvVar("MONGO_INITDB_ROOT_USERNAME", "Username",      "admin",  false, EnvVarType.TEXT),
                        new EnvVar("MONGO_INITDB_ROOT_PASSWORD", "Password",      "secret", false, EnvVarType.PASSWORD),
                        new EnvVar("MONGO_INITDB_DATABASE",      "Database Name", "mydb",   false, EnvVarType.DATABASE)
                ),
                "mongodb://{username}:{password}@localhost:{port}/{database}?authSource=admin",
                "/data/db",
                true, true, true
        ));

        CATALOG.put(DbType.REDIS, new DbDefinition(
                DbType.REDIS,
                "Redis",
                "In-memory data structure store — cache, message broker, streaming",
                "🔴",
                "redis",
                6379,
                List.of("7.4", "7.2", "7.0", "6.2"),
                List.of(
                        new EnvVar("REDIS_PASSWORD", "Password", "secret", false, EnvVarType.PASSWORD)
                ),
                "redis://:{password}@localhost:{port}",
                "/data",
                false, false, true
        ));

        CATALOG.put(DbType.MARIADB, new DbDefinition(
                DbType.MARIADB,
                "MariaDB",
                "Community-developed fork of MySQL — highly compatible drop-in replacement",
                "🦭",
                "mariadb",
                3307,
                List.of("11.7", "11.6", "10.11", "10.6"),
                List.of(
                        new EnvVar("MARIADB_USER",          "Username",      "user",       false, EnvVarType.TEXT),
                        new EnvVar("MARIADB_PASSWORD",      "Password",      "secret",     false, EnvVarType.PASSWORD),
                        new EnvVar("MARIADB_ROOT_PASSWORD", "Root Password", "rootsecret", true,  EnvVarType.PASSWORD),
                        new EnvVar("MARIADB_DATABASE",      "Database Name", "mydb",       false, EnvVarType.DATABASE)
                ),
                "mysql://{username}:{password}@localhost:{port}/{database}",
                "/var/lib/mysql",
                true, true, true
        ));

        CATALOG.put(DbType.CASSANDRA, new DbDefinition(
                DbType.CASSANDRA,
                "Cassandra",
                "Highly scalable distributed NoSQL database for massive datasets",
                "👁️",
                "cassandra",
                9042,
                List.of("5.0", "4.1", "4.0", "3.11"),
                List.of(
                        new EnvVar("CASSANDRA_USER",     "Username", "cassandra", false, EnvVarType.TEXT),
                        new EnvVar("CASSANDRA_PASSWORD", "Password", "cassandra", false, EnvVarType.PASSWORD)
                ),
                "cassandra://localhost:{port}",
                "/var/lib/cassandra",
                false, true, true
        ));

        CATALOG.put(DbType.MSSQL, new DbDefinition(
                DbType.MSSQL,
                "Microsoft SQL Server",
                "Enterprise relational database management system by Microsoft",
                "🪟",
                "mcr.microsoft.com/mssql/server",
                1433,
                List.of("2022-latest", "2019-latest", "2017-latest"),
                List.of(
                        new EnvVar("SA_PASSWORD",   "SA Password",    "YourStr0ngPassw0rd!", true,  EnvVarType.PASSWORD),
                        new EnvVar("ACCEPT_EULA",   "Accept EULA",    "Y",                  true,  EnvVarType.TEXT),
                        new EnvVar("MSSQL_PID",     "Edition",        "Express",            false, EnvVarType.TEXT)
                ),
                "sqlserver://sa:{password}@localhost:{port}",
                "/var/opt/mssql",
                false, false, true
        ));

        CATALOG.put(DbType.CLICKHOUSE, new DbDefinition(
                DbType.CLICKHOUSE,
                "ClickHouse",
                "Fast open-source column-oriented OLAP database management system",
                "🖱️",
                "clickhouse/clickhouse-server",
                9000,
                List.of("25.1", "24.12", "24.11", "24.3"),
                List.of(
                        new EnvVar("CLICKHOUSE_USER",     "Username",      "default", false, EnvVarType.TEXT),
                        new EnvVar("CLICKHOUSE_PASSWORD", "Password",      "secret",  false, EnvVarType.PASSWORD),
                        new EnvVar("CLICKHOUSE_DB",       "Database Name", "default", false, EnvVarType.DATABASE)
                ),
                "clickhouse://localhost:{port}/{database}",
                "/var/lib/clickhouse",
                true, true, true
        ));

        CATALOG.put(DbType.ELASTICSEARCH, new DbDefinition(
                DbType.ELASTICSEARCH,
                "Elasticsearch",
                "Distributed, RESTful search and analytics engine",
                "🔍",
                "elasticsearch",
                9200,
                List.of("8.17.0", "8.16.2", "7.17.26"),
                List.of(
                        new EnvVar("ELASTIC_PASSWORD",      "Password",        "secret",       true,  EnvVarType.PASSWORD),
                        new EnvVar("discovery.type",        "Discovery Type",  "single-node",  true,  EnvVarType.TEXT),
                        new EnvVar("xpack.security.enabled","Security Enabled","true",         false, EnvVarType.TEXT)
                ),
                "http://elastic:{password}@localhost:{port}",
                "/usr/share/elasticsearch/data",
                false, false, true
        ));

        CATALOG.put(DbType.COUCHDB, new DbDefinition(
                DbType.COUCHDB,
                "CouchDB",
                "Document-oriented NoSQL database with HTTP/JSON API",
                "🛋️",
                "couchdb",
                5984,
                List.of("3.4", "3.3", "3.2"),
                List.of(
                        new EnvVar("COUCHDB_USER",     "Username", "admin",  true, EnvVarType.TEXT),
                        new EnvVar("COUCHDB_PASSWORD", "Password", "secret", true, EnvVarType.PASSWORD)
                ),
                "http://{username}:{password}@localhost:{port}",
                "/opt/couchdb/data",
                false, true, true
        ));

        CATALOG.put(DbType.NEO4J, new DbDefinition(
                DbType.NEO4J,
                "Neo4j",
                "Graph database platform for connected data",
                "🕸️",
                "neo4j",
                7474,
                List.of("5.26", "5.25", "5.24", "4.4"),
                List.of(
                        new EnvVar("NEO4J_AUTH", "Auth (user/pass)", "neo4j/secret", true, EnvVarType.TEXT)
                ),
                "bolt://localhost:7687",
                "/data",
                false, false, false
        ));

        CATALOG.put(DbType.DYNAMODB_LOCAL, new DbDefinition(
                DbType.DYNAMODB_LOCAL,
                "DynamoDB Local",
                "Local version of Amazon DynamoDB for offline development",
                "⚡",
                "amazon/dynamodb-local",
                8000,
                List.of("2.6.1", "2.5.3", "2.4.0", "latest"),
                List.of(),
                "http://localhost:{port}",
                null,
                false, false, false
        ));

        CATALOG.put(DbType.RABBITMQ, new DbDefinition(
                DbType.RABBITMQ,
                "RabbitMQ",
                "Open-source message broker supporting AMQP, MQTT, and STOMP protocols",
                "🐇",
                "rabbitmq",
                5672,
                List.of("4.0-management", "3.13-management", "3.12-management", "management"),
                List.of(
                        new EnvVar("RABBITMQ_DEFAULT_USER", "Username", "guest", true,  EnvVarType.TEXT),
                        new EnvVar("RABBITMQ_DEFAULT_PASS", "Password", "guest", true,  EnvVarType.PASSWORD)
                ),
                "amqp://{username}:{password}@localhost:{port}/",
                "/var/lib/rabbitmq",
                false, true, true
        ));

        CATALOG.put(DbType.KAFKA, new DbDefinition(
                DbType.KAFKA,
                "Apache Kafka",
                "Distributed event streaming platform using KRaft mode (no Zookeeper required)",
                "📨",
                "apache/kafka",
                9092,
                List.of("3.9.0", "3.8.1", "3.7.2", "latest"),
                List.of(
                        new EnvVar("KAFKA_NODE_ID",                                "Node ID",                "1",                              false, EnvVarType.TEXT),
                        new EnvVar("KAFKA_PROCESS_ROLES",                          "Process Roles",          "broker,controller",              false, EnvVarType.TEXT),
                        new EnvVar("KAFKA_LISTENERS",                              "Listeners",              "PLAINTEXT://:9092,CONTROLLER://:9093", false, EnvVarType.TEXT),
                        new EnvVar("KAFKA_ADVERTISED_LISTENERS",                   "Advertised Listeners",   "PLAINTEXT://localhost:{port}",    false, EnvVarType.TEXT),
                        new EnvVar("KAFKA_CONTROLLER_LISTENER_NAMES",              "Controller Listener",    "CONTROLLER",                     false, EnvVarType.TEXT),
                        new EnvVar("KAFKA_LISTENER_SECURITY_PROTOCOL_MAP",         "Listener Protocol Map",  "CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT", false, EnvVarType.TEXT),
                        new EnvVar("KAFKA_CONTROLLER_QUORUM_VOTERS",               "Quorum Voters",          "1@localhost:9093",               false, EnvVarType.TEXT),
                        new EnvVar("KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR",       "Offsets Replication",    "1",                              false, EnvVarType.TEXT),
                        new EnvVar("KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR", "Txn Log Replication", "1",                              false, EnvVarType.TEXT),
                        new EnvVar("KAFKA_TRANSACTION_STATE_LOG_MIN_ISR",          "Txn Log Min ISR",        "1",                              false, EnvVarType.TEXT),
                        new EnvVar("KAFKA_LOG_DIRS",                               "Log Dirs",               "/var/lib/kafka/data",            false, EnvVarType.TEXT),
                        new EnvVar("KAFKA_AUTO_CREATE_TOPICS_ENABLE",              "Auto Create Topics",     "true",                           false, EnvVarType.TEXT)
                ),
                "localhost:{port}",
                "/var/lib/kafka/data",
                false, false, false
        ));
    }

    public static DbDefinition get(DbType type) {
        return CATALOG.get(type);
    }

    public static Collection<DbDefinition> all() {
        return CATALOG.values();
    }
}
