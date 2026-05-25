package com.dbdeployer.model;

public enum DbType {
    // ── Relational databases ──────────────────────────────────────────────────
    POSTGRESQL,
    MYSQL,
    H2,
    MARIADB,
    MSSQL,

    // ── Document / NoSQL databases ────────────────────────────────────────────
    MONGODB,
    COUCHDB,
    NEO4J,
    DYNAMODB_LOCAL,

    // ── Key-value / cache ─────────────────────────────────────────────────────
    REDIS,

    // ── Wide-column & OLAP ────────────────────────────────────────────────────
    CASSANDRA,
    CLICKHOUSE,

    // ── Search engines ────────────────────────────────────────────────────────
    ELASTICSEARCH,

    // ── Messaging & streaming ─────────────────────────────────────────────────
    RABBITMQ,
    KAFKA,

    // ── Observability ─────────────────────────────────────────────────────────
    GRAFANA,
    PROMETHEUS,
    LOKI,

    // ── Object storage ────────────────────────────────────────────────────────
    MINIO,

    // ── Identity & secrets ────────────────────────────────────────────────────
    KEYCLOAK,
    VAULT,

    // ── Web / proxy ───────────────────────────────────────────────────────────
    NGINX,

    // ── DB admin UIs ─────────────────────────────────────────────────────────
    ADMINER,
    PGADMIN
}
