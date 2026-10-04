# dts-opmanager Independent Upgrade Engine Design

Date: 2026-05-17
Root: `opmanager/`
Status: F0 design baseline

## Capability

`dts-opmanager` is an independent operations and upgrade control plane for DTS offline deployments. It provides a local web UI and backend API for package registration, validation, upgrade planning, controlled Docker/container inspection, execution logging, and rollback orchestration.

The service is intentionally independent from `dts-admin`, `dts-platform`, Keycloak, the main DTS PostgreSQL database, and Traefik because the product stack may be partially stopped during an upgrade.

## Explicit Non-Goals

- Do not wrap or depend on `dts-upgrade-lite` at runtime.
- Do not implement a full Portainer replacement.
- Do not add `opmanager` to the existing `source/pom.xml` multi-module build.
- Do not require systemd on customer machines.
- Do not require onsite Maven, npm, apt, or Internet access to run an already packaged release.

## Deployment Constraints

- Target includes offline customer sites.
- Hardware/OS includes Kunpeng ARM64 servers and Kylin OS.
- Java and Docker are available; systemd and OS package completeness are not guaranteed.
- Chrome 95 must be supported by the UI.
- Upgrade packages can be large and may be pre-copied to a server directory or uploaded from a browser.

## Runtime Model

F0 runtime is a standalone Spring Boot service serving a React/Vite static UI from the same jar/image.

Primary deployment:

- Docker image: `dts-opmanager:<version>`
- Data volume: `/var/lib/dts-opmanager`
- Package inbox: `/var/lib/dts-opmanager/packages`
- Job state: `/var/lib/dts-opmanager/jobs`
- Logs: `/var/lib/dts-opmanager/logs`
- Docker access: mounted Docker socket when container operations are enabled

Fallback deployment:

- `java -jar dts-opmanager.jar`
- The same data directories can be configured by environment variables.

## Upgrade Package Contract

The new package contract belongs to opmanager. Historical DTS scripts can inform migration, but the opmanager backend validates and executes by this contract.

Required top-level manifest: `manifest.json`

```json
{
  "schemaVersion": 1,
  "packageId": "dts-2.2.4-arm64",
  "product": "dts-stack",
  "version": "2.2.4",
  "targetArch": "arm64",
  "createdAt": "2026-05-17T00:00:00Z",
  "files": [
    {
      "path": "dts-stack/imgversion.conf",
      "sha256": "hex",
      "size": 123
    }
  ],
  "images": [
    {
      "file": "images/dts-admin.tar",
      "image": "dts-admin:2.2.4",
      "sha256": "hex",
      "size": 123
    }
  ]
}
```

F0 validates manifest shape, target architecture, path safety, declared file presence, optional size, and optional sha256. Archive extraction and full apply are F1 unless needed earlier.

## Backend API F0

- `GET /api/opmanager/runtime`: host, OS, arch, Java, Docker and Compose status.
- `POST /api/opmanager/packages/register-path`: register a package already present on the server.
- `POST /api/opmanager/packages/upload`: browser upload entry point for smaller packages.
- `GET /api/opmanager/packages/{id}`: package validation result.
- `GET /api/opmanager/jobs`: list persisted upgrade jobs.
- `POST /api/opmanager/jobs/plan`: create a dry-run plan job from a validated package.
- `GET /api/opmanager/jobs/{id}`: job status and events.
- `GET /api/opmanager/containers`: bounded Docker container view.

## Portainer Boundary

Opmanager may expose a limited DTS-focused container view:

- list Docker runtime status, containers, images, and recent logs
- controlled start/stop/restart for known DTS services in later phases
- optional link to the onsite Portainer URL

It must not expose arbitrary container creation, shell exec, volume browsing, network rewiring, or registry management.

## State Machine

F0:

`REGISTERED -> VALIDATED -> PLANNED`

F1:

`READY -> BACKING_UP -> LOADING_IMAGES -> APPLYING_CONFIG -> RECREATING_SERVICES -> HEALTH_CHECKING -> COMPLETED`

Failure paths:

`FAILED`, `ROLLBACK_REQUIRED`, `ROLLING_BACK`, `ROLLED_BACK`

Every state transition writes an append-only event entry under the job directory.

## Security Baseline

- Execution auth must be local to opmanager, not dependent on Keycloak.
- F0 can start with local-only deployment assumptions, but APIs must stay under `/api/opmanager/*` so auth can be inserted centrally.
- Server path registration must be limited to configured allow-list roots.
- File paths in manifests must reject absolute paths and `..` traversal.
- Shell command execution must use argument arrays, timeouts, and captured audit logs.

## Phase Plan

F0 creates a working standalone project with:

- backend validation and planning APIs
- file-backed state
- Docker runtime inspection
- simple React UI for overview, package registration, planning, and container view
- Docker/offline deployment skeleton

F1 adds:

- large archive safe extraction
- browser chunk upload
- image tar loading
- DTS compose/config mutation
- health checks and rollback
- local authentication hardening
