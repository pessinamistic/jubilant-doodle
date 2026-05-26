# 🛠️ Port Wrangler

![Java](https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.4.2-6DB33F?logo=springboot&logoColor=white)
![React](https://img.shields.io/badge/React-19-61DAFB?logo=react&logoColor=black)
![Vite](https://img.shields.io/badge/Vite-8-646CFF?logo=vite&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-4169E1?logo=postgresql&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-required-2496ED?logo=docker&logoColor=white)
![License](https://img.shields.io/badge/License-MIT-green)

A local self-serve portal for deploying and managing databases on your development machine using Docker. Previously known as DB Deployer.

## Overview

Port Wrangler lets developers spin up any of 14 popular databases locally with a single click — no manual Docker commands or config files required. It manages the full lifecycle of each database container (deploy, start, stop, remove), generates ready-to-use connection strings, streams live container logs, and persists all metadata in its own auto-provisioned PostgreSQL store. The project ships as a fullstack Spring Boot + React application that can be run locally with Gradle + npm or deployed entirely via Docker Compose.

---

## Tech Stack

| Technology              | Version        | Purpose                                               |
|-------------------------|----------------|-------------------------------------------------------|
| Java                    | 21             | Backend language (Spring Boot)                        |
| Spring Boot             | 3.4.2          | REST API, JPA, validation, scheduling                 |
| Spring Data JPA         | (managed)      | ORM / repository layer                                |
| PostgreSQL              | 16             | System config store (auto-provisioned on first start) |
| Docker Java SDK         | 3.4.1          | Docker daemon integration (zerodep Unix socket)       |
| Jackson                 | (managed)      | JSON serialisation                                    |
| Apache Commons Lang     | 3.17.0         | Utility helpers                                       |
| React                   | 19             | Frontend UI                                           |
| Vite                    | 8              | Frontend build tool / dev server                      |
| TailwindCSS             | 4              | UI styling                                            |
| Axios                   | 1.x            | HTTP client (frontend → backend)                      |
| React Router DOM        | 7              | Client-side routing                                   |
| Lucide React            | 0.525          | Icon set                                              |
| react-hot-toast         | 2.x            | Toast notifications                                   |
| Gradle (wrapper)        | bundled        | Backend build tool                                    |
| Node.js                 | 18+            | Frontend build runtime                                |
| Docker Compose          | any            | Full-stack containerised deployment                   |

---

## Features

- 🚀 **Route-based deploy workspace** — full deploy page with left-nav tool selection and right-side configuration form
- 🔄 **Async deployment pipeline** — 4-step pipeline (pull image → create container → start container → finalize) with per-step status tracking
- 🧭 **Global deploy navigation** — all Deploy actions now route to the dedicated deployment workspace
- 🖼️ **Image management system** — track image availability from local Docker store and Docker Hub fallback
- ✅ **Pre-deploy image guardrails** — block only when a tag is confirmed missing; warn-and-allow for indeterminate remote checks
- ▶️ **Start / Stop / Remove** — full container lifecycle management from the UI
- 📋 **Connection string generator** — auto-generates both plain and masked connection strings per DB type
- 📜 **Live container logs** — tail the last N lines of any container's stdout/stderr
- 🔍 **Container discovery** — scan Docker for untracked database containers and import them
- 📥 **Import existing containers** — register a pre-existing container as a managed instance without touching it
- 🔁 **Re-import** — rebind a previously removed imported instance to a new container, preserving all metadata
- ✏️ **Rename instances** — rename any managed instance at any time
- 📊 **Stats panel** — aggregate counts of running/stopped/failed instances
- 🖥️ **OS detection** — detects OS and available tools (Docker, Homebrew, apt, choco, winget)
- 🔄 **Auto status sync** — background scheduler syncs container statuses from Docker every 10 seconds
- 💾 **Persistent metadata** — all instance configs stored in a self-managed PostgreSQL database
- 🐳 **Zero-config local start** — `SystemDbProvisioner` automatically pulls and starts the system Postgres container on first run
- 📦 **Full Docker Compose stack** — one command brings up both the app and its system database

---

## Supported Databases

| Database             | Icon | Default Port | Versions Supported     |
|----------------------|------|--------------|------------------------|
| PostgreSQL           | 🐘   | 5432         | 12 – 17                |
| MySQL                | 🐬   | 3306         | 5.7 – 9.2              |
| MongoDB              | 🍃   | 27017        | 4.4 – 8.0              |
| Redis                | 🔴   | 6379         | 6.2 – 7.4              |
| MariaDB              | 🦭   | 3307         | 10.6 – 11.7            |
| Cassandra            | 👁️  | 9042         | 3.11 – 5.0             |
| Microsoft SQL Server | 🪟   | 1433         | 2017 – 2022            |
| ClickHouse           | 🖱️  | 9000         | 24.3 – 25.1            |
| Elasticsearch        | 🔍   | 9200         | 7.17 – 8.17            |
| CouchDB              | 🛋️  | 5984         | 3.2 – 3.4              |
| Neo4j                | 🕸️  | 7474 / 7687  | 4.4 – 5.26             |
| DynamoDB Local       | ⚡    | 8000         | 2.4.0 – 2.6.1 / latest |
| RabbitMQ             | 🐇   | 5672         | 3.12-management – 4.0  |
| Apache Kafka         | 📨   | 9092         | 3.7.2 – 3.9.0 / latest |

---

## Project Structure

```
db_deployer/
├── Dockerfile                           # Multi-stage build (frontend → backend → runtime)
├── docker-compose.yml                   # Full-stack compose (app + system Postgres)
│
├── backend/                             # Spring Boot 3 + Java 21
│   ├── build.gradle.kts
│   └── src/main/java/com/dbdeployer/
│       ├── DbDeployerApplication.java
│       ├── api/
│       │   ├── DbInstanceController.java    # REST endpoints (/api/*)
│       │   └── dto/                         # DeployRequest, InstanceResponse, PipelineResponse, …
│       ├── deploy/
│       │   ├── DatabaseCatalog.java         # Catalog of all 14 DB definitions
│       │   ├── DockerDeployEngine.java      # Docker SDK integration (deploy/start/stop/remove)
│       │   ├── BrewDeployEngine.java        # Homebrew fallback engine
│       │   ├── ConnectionStringBuilder.java # Connection string & masked string generation
│       │   └── OsDetector.java              # OS + package manager detection
│       ├── pipeline/
│       │   ├── PipelineOrchestrator.java    # Creates pipeline+step rows, fires async runner
│       │   ├── PipelineRunner.java          # Async step executor
│       │   ├── PipelineProperties.java      # Step delay config
│       │   ├── model/                       # DeploymentPipeline, PipelineStep, enums
│       │   ├── step/                        # ImagePullStep, ContainerCreateStep, …
│       │   └── store/                       # DeploymentPipelineRepository, PipelineStepRepository
│       ├── service/
│       │   ├── DbInstanceService.java       # Business logic (deploy, start, stop, sync, import, …)
│       │   ├── ImageValidationService.java  # Local + Docker Hub image validation decisions
│       │   ├── DockerHubTagClient.java      # Docker Hub tag existence checks
│       │   └── AsyncDeployer.java           # Async deployment wrapper
│       ├── model/                           # DeploymentConfig, DeployedContainer, image tracking enums/entities
│       ├── store/                           # Deployment repositories + image tracking repository
│       └── config/
│           ├── AppConfig.java               # CORS, async executor, beans
│           ├── SystemDbProvisioner.java     # Auto-provisions system Postgres before Spring starts
│           ├── SystemDbRegistrar.java       # Registers provisioner as ApplicationContextInitializer
│           ├── DockerSocketResolver.java    # Detects Docker socket path (Desktop / Colima / Linux)
│           ├── StatusSyncScheduler.java     # Scheduled Docker status sync
│           ├── ImageTrackingScheduler.java  # Scheduled image tracking refresh
│           └── DeploymentRecovery.java      # Recovers in-progress deployments on restart
│
└── frontend/                            # React 19 + Vite + TailwindCSS
    └── src/
        ├── api/client.js                # Axios API layer (all backend calls)
        ├── components/
        │   ├── InstanceCard.jsx         # Instance row (status, actions)
        │   ├── ConnectionString.jsx     # Masked connection string + copy button
        │   ├── StatusBadge.jsx          # Status pill (RUNNING / STOPPED / FAILED / …)
        │   └── SystemBanner.jsx         # Docker availability warning
    └── pages/
      ├── DeployPage.jsx           # Full deploy workspace (tool nav + deploy form)
      ├── ImageManagementPage.jsx  # Source-aware image tracking and refresh UI
      └── Dashboard.jsx            # Legacy dashboard page (not primary route)
```

---

## Prerequisites

| Requirement          | Version  | Notes                                              |
|----------------------|----------|----------------------------------------------------|
| Docker Desktop       | any      | Running and accessible — **required** at startup   |
| Java (JDK)           | 21+      | Amazon Corretto, Eclipse Temurin, or similar       |
| Node.js              | 18+      | Required to run or build the frontend              |
| npm                  | bundled  | Included with Node.js                              |

> **Colima users:** The Docker socket lives at `~/.colima/default/docker.sock`. Port Wrangler auto-detects this via `DockerSocketResolver`.

---

## Getting Started

### Option A — Local development (backend + frontend separately)

#### 1. Start the backend

```bash
cd backend
./gradlew bootRun
# Server starts at http://localhost:8080
# SystemDbProvisioner auto-provisions a Postgres container on port 5499 on first run
```

#### 2. Start the frontend

```bash
cd frontend
npm install
npm run dev
# UI available at http://localhost:5173
```

#### 3. Open the portal

Navigate to [http://localhost:5173](http://localhost:5173) in your browser.

---

### Option B — Docker Compose (everything containerized)

```bash
docker compose up --build
# App available at http://localhost:8080 (serves frontend from /static/)
```

Colima socket override:

```bash
DOCKER_SOCKET=$HOME/.colima/default/docker.sock docker compose up --build
```

---

### Option C — Native installers (macOS + Windows)

Download installer builds from GitHub Releases when available.

To build installers locally from source:

#### macOS

```bash
cd backend
./gradlew jpackageInstaller
```

#### Windows

```powershell
cd backend
.\gradlew.bat jpackageInstaller
```

Optional package-type override:

```bash
./gradlew jpackageInstaller -Pjpackage.type=dmg
./gradlew jpackageInstaller -Pjpackage.type=exe
```

Artifacts are written to `backend/build/dist/`.

Expected startup behavior after installation:

- If Docker is reachable, Port Wrangler starts and opens `http://localhost:8080` in your default browser.
- If Docker is not reachable, Port Wrangler shows an OS-specific remediation message and exits.

Data persistence:

- macOS: `~/.db-deployer/`
- Windows: `%USERPROFILE%\\.db-deployer\`

Uninstalling the app does not delete these data directories.

---

### Configuration

Edit `backend/src/main/resources/application.yml` (or use environment variables):

```yaml
server:
  port: ${SERVER_PORT:8080}           # backend port

spring:
  datasource:
    url: ${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5499/dbdeployer}
    username: ${SPRING_DATASOURCE_USERNAME:dbdeployer}
    password: ${SPRING_DATASOURCE_PASSWORD:dbdeployer_internal}

dbdeployer:
  system-db:
    auto-provision: true              # false when system DB is supplied externally
    host-port: 5499                   # local port for the auto-provisioned system Postgres
  cors:
    allowed-origins: ${DBDEPLOYER_CORS_ORIGINS:http://localhost:5173,http://localhost:3000,http://localhost:8080}
  pipeline:
    step-delay-ms: 1500               # ms delay between pipeline steps (visual pacing)
  image-validation:
    scheduler-enabled: true
    docker-hub-timeout-ms: 4000
    local-refresh-interval-ms: 120000
    docker-hub-refresh-interval-ms: 21600000
```

**Key environment variables:**

| Variable                              | Default                                       | Description                                          |
|---------------------------------------|-----------------------------------------------|------------------------------------------------------|
| `SERVER_PORT`                         | `8080`                                        | Backend HTTP port                                    |
| `SPRING_DATASOURCE_URL`               | `jdbc:postgresql://localhost:5499/dbdeployer` | System DB JDBC URL                                   |
| `SPRING_DATASOURCE_USERNAME`          | `dbdeployer`                                  | System DB username                                   |
| `SPRING_DATASOURCE_PASSWORD`          | `dbdeployer_internal`                         | System DB password                                   |
| `DBDEPLOYER_SYSTEM_DB_AUTO_PROVISION` | `true`                                        | Set to `false` when system DB is externally provided |
| `DBDEPLOYER_CORS_ORIGINS`             | `http://localhost:5173,...`                   | Comma-separated allowed CORS origins                 |
| `DOCKER_HOST`                         | auto-detected                                 | Docker socket URI (`unix:///var/run/docker.sock`)    |

---

## API Reference

All endpoints are under the `/api` prefix.

### Instances

| Method   | Endpoint                                | Description                                                         |
|----------|-----------------------------------------|---------------------------------------------------------------------|
| `GET`    | `/api/instances`                        | List all deployed instances                                         |
| `GET`    | `/api/instances/stats`                  | Aggregate status counts (running/stopped/failed)                    |
| `GET`    | `/api/instances/discover`               | Discover untracked Docker containers that look like databases       |
| `POST`   | `/api/instances/sync`                   | Force status sync from Docker daemon                                |
| `POST`   | `/api/instances/import`                 | Import a pre-existing container as a managed instance               |
| `POST`   | `/api/instances`                        | Deploy a new database instance                                      |
| `GET`    | `/api/instances/{id}`                   | Get a single instance by ID                                         |
| `PATCH`  | `/api/instances/{id}`                   | Rename an instance                                                  |
| `DELETE` | `/api/instances/{id}`                   | Remove an instance (stop + delete container)                        |
| `POST`   | `/api/instances/{id}/start`             | Start a stopped instance                                            |
| `POST`   | `/api/instances/{id}/stop`              | Stop a running instance                                             |
| `GET`    | `/api/instances/{id}/logs?tail=100`     | Get the last N lines of container logs                              |
| `GET`    | `/api/instances/{id}/pipeline`          | Get the latest deployment pipeline status                           |
| `GET`    | `/api/instances/{id}/connection-string` | Get plain and masked connection strings                             |
| `PUT`    | `/api/instances/{id}/reimport`          | Re-import a previously removed imported instance to a new container |

### Catalog & System

| Method | Endpoint       | Description                                     |
|--------|----------------|-------------------------------------------------|
| `GET`  | `/api/catalog` | List all supported database types with metadata |
| `GET`  | `/api/system`  | OS and tool availability info                   |

### Images

| Method | Endpoint                                                   | Description                                             |
|--------|------------------------------------------------------------|---------------------------------------------------------|
| `GET`  | `/api/images/check?dbType=POSTGRESQL&tag=16&refresh=false` | Check one tag with local Docker + Docker Hub fallback   |
| `GET`  | `/api/images/tracking`                                     | List tracked image statuses for supported tools/tags    |
| `POST` | `/api/images/refresh?scope=all`                            | Manually refresh image tracking (`local`, `hub`, `all`) |

### Example: Deploy a new instance

**Request:**
```json
POST /api/instances
{
  "dbType": "POSTGRESQL",
  "name": "my-postgres",
  "version": "16",
  "hostPort": 5432,
  "username": "admin",
  "password": "secret",
  "databaseName": "mydb"
}
```

**Response** (`202 Accepted`):
```json
{
  "id": "a3f12b7e-...",
  "name": "my-postgres",
  "dbType": "POSTGRESQL",
  "displayName": "PostgreSQL",
  "icon": "🐘",
  "version": "16",
  "hostPort": 5432,
  "status": "DEPLOYING",
  "connectionString": "postgresql://admin:secret@localhost:5432/mydb",
  "masked": "postgresql://admin:***@localhost:5432/mydb",
  "createdAt": "2026-05-25T10:30:00"
}
```

### Example: Get pipeline status

**Response:**
```json
GET /api/instances/{id}/pipeline

{
  "id": "pipeline-uuid",
  "status": "RUNNING",
  "steps": [
    { "stepType": "PULL_IMAGE",        "status": "COMPLETED", "stepOrder": 0 },
    { "stepType": "CREATE_CONTAINER",  "status": "COMPLETED", "stepOrder": 1 },
    { "stepType": "START_CONTAINER",   "status": "RUNNING",   "stepOrder": 2 },
    { "stepType": "FINALISE",          "status": "PENDING",   "stepOrder": 3 }
  ]
}
```

---

## Data Persistence

Port Wrangler stores all metadata in a self-managed PostgreSQL database:

| Location                             | Contents                                                     |
|--------------------------------------|--------------------------------------------------------------|
| System Postgres (auto-provisioned)   | Instance configs, container records, pipeline + step history |
| `~/.db-deployer/data/<instance-id>/` | Volume data for user-deployed databases (survives restarts)  |

The system Postgres container (`dbdeployer-system-db`) is automatically pulled and started by `SystemDbProvisioner` before any Spring bean initializes. JPA (`ddl-auto: update`) manages the schema.

### Core entities

| Entity               | Table                  | Key Fields                                                                   |
|----------------------|------------------------|------------------------------------------------------------------------------|
| `DeploymentConfig`   | `deployment_configs`   | id, name, dbType, version, hostPort, username, databaseName, status          |
| `DeployedContainer`  | `deployed_containers`  | id, containerId, containerName, latestPipelineId, isImported                 |
| `DeploymentPipeline` | `deployment_pipelines` | id, configId, status (PENDING/RUNNING/COMPLETED/FAILED)                      |
| `PipelineStep`       | `pipeline_steps`       | id, pipelineId, stepType, stepOrder, status                                  |
| `DbInstance`         | `db_instances`         | id, name, dbType, version, hostPort, status, isSystem, isImported, startedAt |

---

## Deployment Pipeline

Every new deploy goes through a 4-step async pipeline:

```
PULL_IMAGE → CREATE_CONTAINER → START_CONTAINER → FINALISE
```

- Steps are persisted to the database and can be queried via `/api/instances/{id}/pipeline`
- `PipelineOrchestrator` creates all rows within the same transaction as the deployment request, then fires the async `PipelineRunner` via an `afterCommit` hook to avoid a race condition
- Configurable step delay (`dbdeployer.pipeline.step-delay-ms`, default `1500 ms`) controls visual pacing in the UI

---

## Error Handling

Global exception handling is provided by `DbInstanceController`:

| Thrown by                  | HTTP Status       | Response body              |
|----------------------------|-------------------|----------------------------|
| `IllegalArgumentException` | `400 Bad Request` | `{ "error": "<message>" }` |

**Example error response:**
```json
{
  "error": "Instance name 'my-postgres' is already in use"
}
```

---

## Docker — Notes for macOS

### Docker Desktop

The standard socket `/var/run/docker.sock` is used automatically.

### Colima

Port Wrangler auto-detects Colima's socket at `~/.colima/default/docker.sock` via `DockerSocketResolver`. If auto-detection fails, set the environment variable explicitly:

```bash
DOCKER_HOST=unix://$HOME/.colima/default/docker.sock ./gradlew bootRun
```

For Docker Compose:
```bash
DOCKER_SOCKET=$HOME/.colima/default/docker.sock docker compose up
```

---

## Building for Production

The `Dockerfile` uses a 3-stage multi-stage build:

1. **`frontend-builder`** — builds the React app with `npm run build`
2. **`backend-builder`** — copies the frontend `dist/` into `src/main/resources/static/` and builds the Spring Boot fat JAR with `./gradlew bootJar`
3. **`runtime`** — minimal `eclipse-temurin:21-jre-alpine` image running the fat JAR

Spring Boot auto-serves the frontend from `classpath:/static/`, so the single JAR serves the full application.

For native installers, `bootJar` now drives frontend packaging directly via Gradle:

- If `../frontend` exists, Gradle runs frontend dependency install + build and packages `dist/` into the jar.
- If `../frontend` is unavailable (for example, backend-only container build contexts), Gradle packages prebuilt assets already present in `src/main/resources/static/`.

```bash
docker build -t port-wrangler .
docker run -p 8080:8080 \
  -v /var/run/docker.sock:/var/run/docker.sock \
  -v $HOME/.db-deployer:/root/.db-deployer \
  port-wrangler
```

---

## Contributing

1. Fork the repository
2. Create a feature branch: `git checkout -b feature/my-change`
3. Make your changes
4. Run the backend tests: `cd backend && ./gradlew test`
5. Run the frontend linter: `cd frontend && npm run lint`
6. Open a Pull Request against `main`

---

## License

MIT License — see [LICENSE](LICENSE) for details.
