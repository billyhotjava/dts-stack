# Offline Upgrade Reference

## Important Files

- `imgversion.conf`
- `imgversion.dts-source.conf`
- `docker-compose-app.yml`
- `docker-compose.dev.yml`
- `docker-compose.legacy.yml`
- `builds/dts-build.sh`
- `builds/*/Dockerfile`
- `services/dts-dbt`
- `services/dts-keycloak/realm-dts.json`
- `services/certs`
- `bin/dts-upgrade-lite`
- `docs/release/v2.2.3/upgrade-lite-operations-kylin-kunpeng.md`
- `docs/release/v2.2.3/offline-upgrade-guide-kylin-kunpeng.md`
- `docs/release/v2.2.3/offline-upgrade-checklist-kylin-kunpeng.md`

## Build Notes

- `builds/dts-build.sh` prebuilds Java jars by default with `PREBUILD_JARS=1`.
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
- Pure shell upgrade reports for legacy sites without Python.

## Upgrade Sequence

1. Record current versions and service status.
2. Back up database, Keycloak, uploaded files, dbt project, env, compose files, and certificates.
3. Load images into the offline registry or local Docker daemon.
4. Generate a `dts-upgrade-lite plan` report and review env/compose/config risks.
5. Apply image version updates while preserving customer compose/config values.
6. Start base services, then app services.
7. Run smoke checks for login, platform API, dbt parse, data source connection, and key dashboards.
8. Keep rollback artifacts until acceptance is complete.

## Rollback

- Stop changed services.
- Restore compose/env/image tags.
- Restore persistent state only if migrations or application actions modified it.
- Verify login, API health, dbt project readability, and audit visibility.
