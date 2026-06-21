# StudyDeck AI — Backend

Modular hexagonal monolith · Java 25 · Spring Boot 4.0.7 · PostgreSQL + pgvector

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Java 25 (`--release 25` on JDK 26) |
| Framework | Spring Boot 4.0.7, Spring MVC |
| Persistence | Spring Data JPA + PostgreSQL 17 + pgvector |
| Migrations | Flyway |
| Build | Gradle 9.0.0 (Kotlin DSL) |
| Testing | JUnit 5, Testcontainers, ArchUnit |
| Quality | Spotless (google-java-format), JaCoCo |
| Containerization | Docker (multi-stage), Docker Compose |

## Quick Start

### Prerequisites

- JDK 26 (compiles to Java 25 bytecode)
- Docker 29+ and Docker Compose 5+

### Run locally with Compose (recommended)

```bash
# Start PostgreSQL + backend
docker compose up -d

# Watch logs
docker compose logs -f backend

# Health check
curl http://localhost:8080/actuator/health
```

The bundled compose runs the **dev** profile by default (static HS256 JWT decoder + dev-only OpenAPI). A real deployment sets `SPRING_PROFILES_ACTIVE=prod` **and** `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI` (prod refuses to start without a real issuer).

### Authentication: dev token vs. real IdP

Two local options:

**a) Dev token (fastest)** — no IdP. Mint an HS256 JWT signed with the dev secret and call the API directly. Good for API/feature work:

```bash
TOKEN=$(node -e 'const c=require("crypto"),S="studydeck-dev-secret-key-32-chars-min-!!";const b=o=>Buffer.from(JSON.stringify(o)).toString("base64url");const n=Math.floor(Date.now()/1e3);const d=b({alg:"HS256",typ:"JWT"})+"."+b({sub:"00000000-0000-0000-0000-000000000001",email:"dev@studydeck.local",scope:"study.read study.write review.write import.write export.read documents.read documents.write rag.query ai.generate mcp.invoke",iat:n,exp:n+86400});process.stdout.write(d+"."+c.createHmac("sha256",S).update(d).digest("base64url"))')
curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8080/v1/auth/me | jq
```

**b) Real OIDC login (Keycloak)** — full login flow, mirrors production. Brings up a local Keycloak with a pre-imported `studydeck` realm, SPA client (PKCE) and a seeded `dev`/`dev` user:

```bash
docker compose -f compose.yaml -f compose.idp.yaml up -d
# Keycloak admin: http://localhost:8081 (admin/admin) · realm: studydeck · user: dev/dev
```

This overlay disables the dev HS256 decoder and validates real Keycloak RS256 tokens. Run the frontend with `studydeck-frontend/env.idp.example` to get the browser login. Fetch a token headless for testing:

```bash
curl -s -X POST http://localhost:8081/realms/studydeck/protocol/openid-connect/token \
  -d client_id=studydeck-spa -d grant_type=password -d username=dev -d password=dev -d scope=openid \
  | jq -r .access_token
```

### Run with Gradle (dev profile, requires local PostgreSQL)

```bash
# Start only the database
docker compose up -d postgres

# Run backend in dev profile (enables debug logging, OpenAPI endpoint)
SPRING_PROFILES_ACTIVE=dev ./gradlew bootRun
```

### Environment variables

| Variable | Default | Description |
|----------|---------|-------------|
| `DB_URL` | `jdbc:postgresql://localhost:5432/studydeck` | JDBC URL |
| `DB_USER` | `studydeck` | Database user |
| `DB_PASS` | `studydeck` | Database password |
| `SPRING_PROFILES_ACTIVE` | `dev` (compose) | Active profile (`dev` or `prod`) |
| `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI` | _(empty)_ | Required in `prod`; set to your OIDC issuer |
| `BACKEND_PORT` | `8080` | Host port mapping |
| `POSTGRES_PORT` | `5432` | Host PostgreSQL port mapping |

**Never set real credentials as defaults. Use a `.env` file (git-ignored) for local overrides.**

## Development

### Run quality gate

```bash
./gradlew check
```

Runs: Spotless → compileJava → test (Testcontainers + ArchUnit) → JaCoCo threshold.

### Run only tests

```bash
./gradlew test
```

### Format code

```bash
./gradlew spotlessApply
```

### Run ArchUnit tests only

```bash
./gradlew test --tests "com.studydeck.architecture.*"
```

## Architecture

Hexagonal (Ports and Adapters) — dependency rule: `infrastructure → application → domain`.

```
src/main/java/com/studydeck/
├── StudyDeckApplication.java          # @SpringBootApplication entry point
├── domain/
│   ├── model/                         # Entities, value objects — pure Java
│   ├── port/in/                       # Input ports (*UseCase, *Query)
│   └── port/out/                      # Output ports (*Port)
├── application/
│   └── service/                       # Use case implementations — framework-free
└── infrastructure/
    ├── adapter/
    │   ├── in/web/                    # REST controllers, DTOs
    │   └── out/persistence/           # JPA entities, repositories, adapters
    └── config/                        # @Configuration bean wiring
```

Domain and application layers have zero Spring/Jakarta dependencies.
ArchUnit enforces this at test time.

## API Contract

`openapi.yaml` at the repo root is the source of truth.

In dev profile, it is served at `GET /v3/api-docs.yaml`.

## Database

PostgreSQL 17 with pgvector extension. Flyway manages all migrations.

Migrations live at `src/main/resources/db/migration/`.

| Migration | Contents |
|-----------|----------|
| `V1__init_core.sql` | pgvector extension + `user_account` + `deck` tables |

## Docker

```bash
# Build image
docker build -t studydeck-backend .

# Validate compose
docker compose config

# Full stack
docker compose up -d

# Tear down (preserves data volume)
docker compose down

# Tear down + remove data
docker compose down -v
```
