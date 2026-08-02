# AGENTS.md

## Repository Purpose

`config-server` hosts the Spring Cloud Config Server for the platform.

It serves externalized runtime configuration from `config-repo` to the platform services.

## What Belongs Here

- Config Server runtime configuration
- tests for configuration lookup and health behavior
- Docker packaging
- Helm deployment for the service
- CI validation for build, chart, and container smoke test

## What Does Not Belong Here

- client-service business logic
- application secrets
- runtime config values that belong in `config-repo`
- infrastructure shared by many services that belongs in `infra-sit`

## Key Commands

```bash
./mvnw test
./mvnw verify
./mvnw spring-boot:run
helm lint helm --strict --values helm/values-sit.yaml
```

## Runtime Notes

- Default local endpoint: `http://localhost:8888`
- Local runtime config source is the `config-repo` repository.
- SIT and higher environments should use Git-backed configuration, not hardcoded local files.

## Dependency Notes

- Depends on `config-repo` content being valid.
- In SIT, other services depend on this service before they can boot with externalized configuration.

## Deployment Notes

- Kubernetes namespace for SIT workloads: `digital-bank-sit`
- Validate health before testing downstream services.
- If config changes are merged, restart dependent services if the new values must be loaded immediately.

## Working Rules

- Keep file naming aligned with Spring Config conventions.
- Do not move service-specific values into this repository.
- Treat config retrieval endpoints as operational tooling, not public customer APIs.
