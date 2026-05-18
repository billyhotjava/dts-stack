# DTS OpManager

`dts-opmanager` is an independent DTS operations and offline upgrade control plane. It is not a wrapper around `dts-upgrade-lite`.

## Scope

- Spring Boot 3.4.5 / Java 21 backend.
- React 18 / Vite frontend served by the same Spring Boot app.
- Chrome 95 compatible production frontend target.
- File-backed state under `/var/lib/dts-opmanager`.
- Fixed upgrade workspace under `/var/lib/dts-opmanager/packages` with `images/`, `dts-stack/`, and `misc/`.
- Docker runtime inspection through a bounded API.
- No Keycloak, main DTS PostgreSQL, Traefik, or systemd dependency.

## Local Development

Backend:

```bash
mvn test
mvn spring-boot:run
```

Frontend:

```bash
cd src/main/webapp
pnpm install
pnpm dev
pnpm build
```

The Vite dev server proxies `/api` to `http://localhost:18090`.

## Configuration

| Variable | Default | Purpose |
| --- | --- | --- |
| `OPMANAGER_SERVER_PORT` | `18095` | HTTP port for `java -jar`; Docker image sets the internal port to `18090` |
| `OPMANAGER_DATA_DIR` | `/var/lib/dts-opmanager` | state, uploads, jobs |
| `OPMANAGER_PACKAGE_ROOTS` | `/var/lib/dts-opmanager/packages` | opmanager upgrade workspace root; keep only `images/`, `dts-stack/`, and `misc/` |
| `OPMANAGER_TARGET_STACK_DIR` | `/opt/dts-stack` | DTS stack directory |
| `OPMANAGER_DOCKER_ENABLED` | `true` | Docker inspection switch |
| `OPMANAGER_PORTAINER_URL` | empty | optional Portainer link |

## Offline Deployment

Build the opmanager image in a connected build environment and export it:

```bash
docker build -t dts-opmanager:2.2.3 -f Dockerfile .
docker save dts-opmanager:2.2.3 -o dts-opmanager-2.2.3-linux-arm64.tar
```

Load and run on site:

```bash
docker load -i dts-opmanager-2.2.3-linux-arm64.tar
docker compose -f deploy/docker-compose.yml --env-file deploy/.env up -d
```

The compose file publishes host port `18095` by default and uses Docker restart policy only; it does not require systemd.

## Upgrade Workspace

Prepare the DTS upgrade input with the main stack build script:

```bash
cd /opt/prod/s10/v2.2.3
OPMANAGER_PACKAGE_ROOTS=/var/lib/dts-opmanager/packages ./builds/dts-build.sh -all --opmanager-package
```

The output directory is intentionally simple:

```text
/var/lib/dts-opmanager/packages/
  images/      # docker save tar files from builds/dist and builds/legacy-dist
  dts-stack/   # stripped DTS stack tree generated from dts-build --pack
  misc/        # extra metadata and miscellaneous update files
```

In the UI, register `/var/lib/dts-opmanager/packages`. Config precheck compares `packages/dts-stack` against `OPMANAGER_TARGET_STACK_DIR`; image loading only reads `packages/images/*.tar`.
