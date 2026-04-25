# Offline Upgrade Reference

## Important Files

- `imgversion.conf`
- `imgversion.dts-source.conf`
- `docker-compose.yml`
- `docker-compose-app.yml`
- `docker-compose.dev.yml`
- `builds/buildAll.sh`
- `builds/*/Dockerfile`
- `services/dts-dbt`
- `services/dts-keycloak/realm-dts.json`
- `services/certs`
- `docs/release/v2.2.2/offline-upgrade-guide-kylin-kunpeng.md`
- `docs/release/v2.2.2/offline-upgrade-checklist-kylin-kunpeng.md`

## Build Notes

- `builds/buildAll.sh` prebuilds Java jars by default with `PREBUILD_JARS=1`.
- Use `MAVEN_UNRESTRICTED=1` when container JVM thread creation is constrained.
- dbt image build defaults should avoid unnecessary apt installs with `INSTALL_APT_DEPS=0`.
- Keep `JAVA_HOME` explicit in Java build-stage Dockerfiles.

## Package Contents

Expected release package categories:

- Application images and base images.
- Compose files and env templates.
- Initialization and start/stop scripts.
- Database migration or seed scripts.
- Keycloak realm and mapper changes.
- Certificates or certificate generation instructions.
- dbt project assets and drivers.
- Upgrade, verification, rollback, and known-issues notes.

## Upgrade Sequence

1. Record current versions and service status.
2. Back up database, Keycloak, uploaded files, dbt project, env, compose files, and certificates.
3. Load images into the offline registry or local Docker daemon.
4. Apply config changes and compose/image version updates.
5. Start base services, then app services.
6. Run smoke checks for login, platform API, dbt parse, data source connection, and key dashboards.
7. Keep rollback artifacts until acceptance is complete.

## Rollback

- Stop changed services.
- Restore compose/env/image tags.
- Restore persistent state only if migrations or application actions modified it.
- Verify login, API health, dbt project readability, and audit visibility.
