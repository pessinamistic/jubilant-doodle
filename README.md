# 🛠️ Port Wrangler

A local self-serve portal for deploying and managing databases on your development machine using Docker. Previously known as DB Deployer.

## Features

- 🚀 **One-click deploy** for 12 popular databases
- 🔐 **Credential & version configuration** per instance
- ▶️ **Start / Stop / Remove** running containers
- 📋 **Connection string generator** with copy-to-clipboard
- 📜 **Live container logs** viewer
- 🖥️ **OS detection** — Docker primary, with Homebrew / apt / choco / winget fallback
- 🔄 **Auto status sync** every 10 seconds
- 💾 **Persistent config** stored in `~/.db-deployer/config.db` (SQLite)

## Supported Databases

| Database | Default Port | Versions |
|---|---|---|
| 🐘 PostgreSQL | 5432 | 12 – 17 |
| 🐬 MySQL | 3306 | 5.7 – 9.2 |
| 🍃 MongoDB | 27017 | 4.4 – 8.0 |
| 🔴 Redis | 6379 | 6.2 – 7.4 |
| 🦭 MariaDB | 3307 | 10.6 – 11.7 |
| 👁️ Cassandra | 9042 | 3.11 – 5.0 |
| 🪟 MS SQL Server | 1433 | 2017 – 2022 |
| 🖱️ ClickHouse | 9000 | 24.3 – 25.1 |
| 🔍 Elasticsearch | 9200 | 7.17 – 8.17 |
| 🛋️ CouchDB | 5984 | 3.2 – 3.4 |
| 🕸️ Neo4j | 7474 | 4.4 – 5.26 |
| ⚡ DynamoDB Local | 8000 | latest |

## Prerequisites

- **Docker Desktop** (or Docker Engine on Linux) — running and accessible
- **Java 21+** (Amazon Corretto, Temurin, etc.)
- **Node.js 18+** and **npm**

## Getting Started

### 1. Start the backend

```bash
cd backend
./gradlew bootRun
# Server starts at http://localhost:8080
```

### 2. Start the frontend

```bash
cd frontend
npm install
npm run dev
# UI available at http://localhost:5173
```

### 3. Open the portal

Navigate to **http://localhost:5173** in your browser.

## Project Structure

```
db_deployer/
├── backend/                         # Spring Boot 3 + Java 21
│   ├── src/main/java/com/dbdeployer/
│   │   ├── DbDeployerApplication.java
│   │   ├── api/
│   │   │   ├── DbInstanceController.java   # REST endpoints
│   │   │   └── dto/                        # Request/Response records
│   │   ├── deploy/
│   │   │   ├── DatabaseCatalog.java        # All 12 DB definitions
│   │   │   ├── DockerDeployEngine.java     # Docker SDK integration
│   │   │   ├── OsDetector.java             # OS + package manager detection
│   │   │   └── ConnectionStringBuilder.java
│   │   ├── model/                          # JPA entities + enums
│   │   ├── service/DbInstanceService.java  # Business logic
│   │   ├── store/DbInstanceRepository.java # SQLite persistence
│   │   └── config/                         # CORS, async, scheduler
│   └── build.gradle.kts
│
└── frontend/                        # React + Vite + TailwindCSS
    └── src/
        ├── api/client.js             # Axios API layer
        ├── components/
        │   ├── DeployModal.jsx       # DB picker + config form
        │   ├── InstanceCard.jsx      # Instance row with actions
        │   ├── ConnectionString.jsx  # Masked string + copy button
        │   ├── StatusBadge.jsx       # Status pill
        │   └── SystemBanner.jsx      # Docker availability notice
        └── pages/Dashboard.jsx       # Main page
```

## API Reference

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/instances` | List all instances |
| `POST` | `/api/instances` | Deploy a new instance |
| `GET` | `/api/instances/{id}` | Get instance details |
| `POST` | `/api/instances/{id}/start` | Start a stopped instance |
| `POST` | `/api/instances/{id}/stop` | Stop a running instance |
| `DELETE` | `/api/instances/{id}` | Remove an instance |
| `GET` | `/api/instances/{id}/logs?tail=100` | Get container logs |
| `GET` | `/api/instances/{id}/connection-string` | Get connection string |
| `GET` | `/api/catalog` | List all supported DB types |
| `GET` | `/api/system` | OS & tool availability info |
| `POST` | `/api/instances/sync` | Force status sync from Docker |

## Data Persistence

- Container metadata is stored in **`~/.db-deployer/config.db`** (SQLite)
- Volume data is stored in **`~/.db-deployer/data/<instance-id>/`**
- Data persists across container restarts

## Configuration

Edit `backend/src/main/resources/application.yml` to change port or database path:

```yaml
server:
  port: 8080   # change backend port here
```
