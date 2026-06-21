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
- Docker Desktop for container builds.
- Docker Desktop Kubernetes and Helm 4 for the local SIT deployment.

A global Maven installation is not required because the repository includes the Maven Wrapper.

Verify the environment:

```bash
java -version
./mvnw --version
docker version
kubectl config current-context
helm version --short
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

## Run With Docker

Build the image from the repository root:

```bash
docker build \
  --tag digital-bank-java/config-server:0.0.1 \
  .
```

Run the container as a non-root user:

```bash
docker run --detach \
  --rm \
  --name digital-bank-java-config-server \
  --publish 8888:8888 \
  digital-bank-java/config-server:0.0.1
```

Verify the container and stop it:

```bash
curl --fail http://localhost:8888/actuator/health
curl --fail http://localhost:8888/customer-service/local
docker stop digital-bank-java-config-server
```

The runtime image uses the numeric non-root user `10001:10001`. The native `config-repo` directory is included in the image only for the current local configuration phase.

## Deploy To Local SIT

The Helm chart deploys Config Server to Docker Desktop Kubernetes. Confirm that the active context is `docker-desktop` before continuing:

```bash
kubectl config current-context
kubectl get nodes
```

Validate the chart without changing the cluster:

```bash
helm lint helm

helm template config-server helm |
  kubectl apply --dry-run=client -f -
```

Install or upgrade the release in the SIT namespace:

```bash
helm upgrade --install config-server helm \
  --namespace digital-bank-sit \
  --create-namespace \
  --wait \
  --timeout 5m
```

Inspect the release:

```bash
helm status config-server --namespace digital-bank-sit
kubectl get deployment,pods,service --namespace digital-bank-sit
kubectl logs deployment/config-server --namespace digital-bank-sit
```

Forward the internal Kubernetes Service to the workstation:

```bash
kubectl port-forward \
  service/config-server 8888:8888 \
  --namespace digital-bank-sit
```

While port forwarding is active, run the health and configuration requests from another terminal. Stop port forwarding with `Ctrl+C`.

Remove only this Helm release when cleanup is required:

```bash
helm uninstall config-server --namespace digital-bank-sit
```

## Deployment Security

The Kubernetes deployment:

- Runs as numeric non-root user and group `10001`.
- Disables privilege escalation.
- Drops Linux capabilities.
- Uses a read-only root filesystem with bounded temporary storage.
- Does not mount the default Kubernetes service account token.
- Exposes Config Server only through an internal `ClusterIP` Service.

## CI Validation

Pull requests and changes to `main` run independent CI jobs that:

- Execute Maven verification with Java 21.
- Lint and render the Helm chart with Helm 4.2.0.
- Build the container image and smoke-test its health and configuration endpoints.

Third-party GitHub Actions are pinned to immutable commit SHAs.

## Environment Promotion

The same container and Helm chart are intended to move through SIT, UAT, and PROD without application rebuilds. Hosted environments will provide environment-specific image repositories, immutable image tags, resource sizing, and infrastructure configuration through the deployment pipeline.

The current native configuration backend is suitable for local development and SIT bootstrap. Migration to the dedicated Git-backed `platform-config` repository is required before the hosted UAT and PROD deployment. Secrets must remain outside Git and Helm values and be supplied through the environment's approved secret-management integration.

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
./mvnw verify
helm lint helm --strict
git diff --check
```

Pull requests should reference their issue using a closing keyword:

```text
Closes #<issue-number>
```

The issue is closed automatically when the pull request is merged into `main`.
