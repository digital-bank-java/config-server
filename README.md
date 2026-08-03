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

The service uses the Spring Cloud Config Git backend. Client configuration is owned by the separate `config-repo` repository so configuration can change independently of the Config Server image.

### Config Server Runtime Configuration

`src/main/resources/application.yml` configures the Config Server process itself, including:

- HTTP port.
- Git repository URI and default branch.
- Service-specific configuration search paths.
- Git authentication supplied through runtime environment variables.
- Actuator endpoint exposure.

### Client Configuration Repository

The sibling `config-repo` repository contains configuration served to client services:

```text
config-repo/
├── application.yml
├── application-sit.yml
└── customer-service/
    ├── customer-service.yml
    └── customer-service-sit.yml
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

## Run From A Workstation

Clone `config-repo` beside this repository. The default local repository URI is `file:../config-repo`:

```text
digital-bank-java/
├── config-server/
└── config-repo/
```

Run commands from the `config-server` repository root.

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

Retrieve the SIT configuration:

```bash
curl --fail http://localhost:8888/customer-service/sit
```

The SIT response combines configuration from:

```text
config-repo/customer-service/customer-service-sit.yml
config-repo/application-sit.yml
config-repo/customer-service/customer-service.yml
config-repo/application.yml
```

More specific profile and service configuration takes precedence over shared defaults. The response `version` identifies the exact `config-repo` Git commit used. Supported runtime profiles are `sit`, `uat`, and `prod`; `local` is not a platform environment.

## Run With Docker

Build the image from the repository root:

```bash
docker build \
  --tag digital-bank-java/config-server:0.0.2 \
  .
```

Create a disposable writable copy of the local configuration repository. JGit requires write access for checkout metadata, so the real working repository is not mounted directly:

```bash
CONFIG_REPO_FIXTURE="$(mktemp -d)"
cp -R ../config-repo/. "$CONFIG_REPO_FIXTURE/"
chmod -R a+rwX "$CONFIG_REPO_FIXTURE"
```

Run the container as a non-root user with the fixture mounted as its Git repository:

```bash
docker run --detach \
  --rm \
  --name digital-bank-java-config-server \
  --publish 8888:8888 \
  --env CONFIG_REPO_URI=file:/config-repo \
  --env CONFIG_REPO_DEFAULT_LABEL=main \
  --volume "$CONFIG_REPO_FIXTURE:/config-repo" \
  digital-bank-java/config-server:0.0.2
```

Verify the container and stop it:

```bash
curl --fail http://localhost:8888/actuator/health
curl --fail http://localhost:8888/customer-service/sit
docker stop digital-bank-java-config-server
test -n "$CONFIG_REPO_FIXTURE" && rm -rf "$CONFIG_REPO_FIXTURE"
unset CONFIG_REPO_FIXTURE
```

The runtime image uses the numeric non-root user `10001:10001`. Client configuration and credentials are not included in the image.

## Deploy To Local SIT

The Helm chart deploys Config Server to Docker Desktop Kubernetes. Confirm that the active context is `docker-desktop` before continuing:

```bash
kubectl config current-context
kubectl get nodes
```

Validate the chart without changing the cluster:

```bash
helm lint helm --strict --values helm/values-sit.yaml

helm template config-server helm \
  --namespace digital-bank-sit \
  --values helm/values-sit.yaml |
  kubectl apply --dry-run=client -f -
```

Create the namespace and an opaque Secret containing a repository-scoped, read-only GitHub credential. Never commit the token or place it in Helm values. The Helm values file points to this existing Secret by name only:

```bash
kubectl create namespace digital-bank-sit --dry-run=client -o yaml |
  kubectl apply -f -

read -s CONFIG_REPO_TOKEN
printf %s "$CONFIG_REPO_TOKEN" |
  kubectl create secret generic config-server-git-credentials \
    --namespace digital-bank-sit \
    --from-literal=username=YOUR_GITHUB_USERNAME \
    --from-file=token=/dev/stdin \
    --dry-run=client \
    --output yaml |
  kubectl apply -f -
unset CONFIG_REPO_TOKEN
```

Install or upgrade the release in the SIT namespace:

```bash
helm upgrade --install config-server helm \
  --namespace digital-bank-sit \
  --create-namespace \
  --values helm/values-sit.yaml \
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
- Reads Git credentials from an existing Kubernetes Secret rather than Helm values.

## CI Validation

Pull requests and changes to `main` run independent CI jobs that:

- Execute Maven verification with Java 21.
- Lint and render the Helm chart with Helm 4.2.0.
- Build the container image and smoke-test it against a disposable Git repository fixture.

Third-party GitHub Actions are pinned to immutable commit SHAs.

## Environment Promotion

The same container and Helm chart are intended to move through SIT, UAT, and PROD without application rebuilds. Hosted environments will provide environment-specific image repositories, immutable image tags, resource sizing, and infrastructure configuration through the deployment pipeline.

Configuration is promoted independently through the dedicated Git-backed `config-repo` repository. Secrets must remain outside Git and Helm values and be supplied through the environment's approved secret-management integration. AWS environments will replace the manually created SIT Secret with managed secret delivery and rotation.

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
helm lint helm --strict --values helm/values-sit.yaml
git diff --check
```

Pull requests should reference their issue using a closing keyword:

```text
Closes #<issue-number>
```

The issue is closed automatically when the pull request is merged into `main`.
