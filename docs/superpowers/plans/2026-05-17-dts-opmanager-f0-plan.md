# dts-opmanager F0 Implementation Plan

Date: 2026-05-17
Scope: standalone `opmanager/` project

## Goal

Build the first independent `dts-opmanager` project skeleton with a real backend API, a simple Chrome95-compatible React UI, and offline-friendly deployment packaging. The implementation must not depend on `dts-upgrade-lite`.

## Tasks

1. Create standalone project metadata.
   - Add `opmanager/pom.xml` using Spring Boot 3.4.5, Java 21, and JHipster framework dependency.
   - Add root `.gitignore`, README, app config, and package metadata.

2. Add backend tests first.
   - Test manifest validation rejects unsafe paths.
   - Test manifest validation accepts a valid directory package.
   - Test runtime inspection maps command availability without needing real Docker.
   - Test job store persists plan events.

3. Implement backend F0.
   - Spring Boot entry point under `com.yuzhi.dts.opmanager`.
   - Configuration properties for data directory, package allow-list, target DTS directory, Docker enablement, and Portainer URL.
   - File-backed package registry and job store.
   - Manifest validation service.
   - Command runner and runtime inspector.
   - REST controllers under `/api/opmanager/*`.

4. Add frontend F0.
   - React 18 + Vite + TypeScript.
   - Chrome95 production target with `@vitejs/plugin-legacy`.
   - Single-page operational UI: overview, package registration, planning, containers.
   - No heavy admin-webapp UI dependencies.

5. Add offline deployment skeleton.
   - `Dockerfile` for frontend build, backend build, and Java runtime.
   - `deploy/docker-compose.yml` with mounted data directory and optional Docker socket.
   - `deploy/env.example`.

6. Verify.
   - Run focused Maven tests if dependencies are available locally.
   - Run TypeScript/Vite build if node dependencies are installable/available.
   - If verification is blocked by offline dependency availability, record the exact blocker.

## Review Gates

- No edits to existing DTS runtime modules unless explicitly required.
- No systemd dependency.
- No runtime dependency on Keycloak/Postgres.
- No full Portainer feature clone.
- No shell-command string concatenation for backend execution paths.
