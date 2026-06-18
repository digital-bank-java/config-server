# Config Server

Centralized configuration service for the Digital Bank Java platform, built with Spring Cloud Config Server.

## Responsibilities

- Serve shared configuration to platform services.
- Serve service-specific configuration.
- Resolve environment-specific profile overrides.
- Expose health endpoints for runtime monitoring and Kubernetes probes.

## Non-Responsibilities

- Business-domain logic.
- Service discovery or API routing.
- Storing credentials or secrets in source control.

## Configuration Model

The service currently uses the Spring Cloud Config native backend and reads configuration from the local `config-repo` directory.

### Config Server Runtime Configuration

`src/main/resources/application.yml` configures the Config Server process itself, including:

- HTTP port.
- Active Spring profile.
- Native configuration search locations.
- Actuator endpoint exposure.

### Client Configuration Repository

`config-repo` contains configuration served to client services:

```text
config-repo/
├── application.yml
├── application-local.yml
└── customer-service/
    ├── customer-service.yml
    └── customer-service-local.yml
```

- `application.yml` contains defaults shared by all client services.
- `application-{profile}.yml` contains shared profile-specific overrides.
- `{service}.yml` contains defaults for one service.
- `{service}-{profile}.yml` contains service-specific profile overrides.

## Prerequisites

- Java 21.
- Git.
- Network access to Maven Central for the initial dependency download.

A global Maven installation is not required because the repository includes the Maven Wrapper.

Verify the environment:

```bash
java -version
./mvnw --version
```

## Run Locally

Run commands from the repository root so the native backend can resolve `./config-repo`.

Execute the test suite:

```bash
./mvnw test
```

Start the Config Server:

```bash
./mvnw spring-boot:run
```

The service starts on `http://localhost:8888`.

## Verify the Service

Check service health:

```bash
curl --fail http://localhost:8888/actuator/health
```

Expected status:

```json
{
  "groups": ["liveness", "readiness"],
  "status": "UP"
}
```

Retrieve the default configuration for `customer-service`:

```bash
curl --fail http://localhost:8888/customer-service/default
```

Retrieve the local-profile configuration:

```bash
curl --fail http://localhost:8888/customer-service/local
```

The local response combines configuration from:

```text
config-repo/customer-service/customer-service-local.yml
config-repo/application-local.yml
config-repo/customer-service/customer-service.yml
config-repo/application.yml
```

More specific profile and service configuration takes precedence over shared defaults.

## Development Workflow

Changes must be made on a dedicated branch and merged through a pull request. Do not commit directly to `main`.

Recommended branch naming:

```text
feature/<issue-number>-<description>
docs/<issue-number>-<description>
fix/<issue-number>-<description>
```

Before opening a pull request:

```bash
git status
./mvnw test
git diff --check
```

Pull requests should reference their issue using a closing keyword:

```text
Closes #<issue-number>
```

The issue is closed automatically when the pull request is merged into `main`.
