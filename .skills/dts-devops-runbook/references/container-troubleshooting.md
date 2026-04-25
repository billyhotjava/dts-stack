# DTS Container Troubleshooting

## Common Modes

- Local development: `./dev-up.sh --mode local`
- Local stop: `./dev-stop.sh --mode local`
- Deploy mode: `docker compose -f docker-compose.yml -f docker-compose-app.yml up -d`
- Build image: `docker build -t dts-admin:TAG -f builds/dts-admin/Dockerfile .`

## First Checks

- Current branch and dirty files.
- `.env` exists and matches expected domain.
- `docker compose ... ps` service status.
- Recent logs for the failing service.
- Whether host ports are already occupied.
- Whether mounted paths are writable by the container user.

## Frequent Issues

### Maven Or JVM Thread Failures

Symptoms:

- `The JAVA_HOME environment variable is not defined correctly`
- `Failed to start thread "GC Thread#0"`
- `pthread_create failed (EPERM)`

Actions:

- Prefer `MAVEN_UNRESTRICTED=1 ./builds/buildAll.sh`.
- Use host Maven or prebuilt jars when the container runtime is constrained.
- Keep Dockerfile `JAVA_HOME` explicit for Java build stages.

### Corrupted dts-common Snapshot

Symptom:

- `bad class file ... class file truncated`

Action:

- Remove the corrupted Maven snapshot and rebuild. Do this only after confirming the target path.

### dbt Project Not Writable

Symptom:

- dbt project or `target` directory cannot be written.

Actions:

- Check ownership and mode for `services/dts-dbt` and `services/dts-dbt/target`.
- Avoid mounting the dbt project as read-only.

### OIDC Issuer Resolution

Symptom:

- `Unable to resolve Configuration with the provided Issuer`

Actions:

- Check DNS/host mapping inside containers.
- Confirm Keycloak issuer URL and host gateway mapping.
- Verify Traefik routing and TLS certificates.

### APT Or dpkg Failures During dts-dbt Build

Actions:

- Default to `INSTALL_APT_DEPS=0`.
- Enable apt dependencies only when adapters require build dependencies.
- Pass proxy or mirror settings explicitly when needed.
