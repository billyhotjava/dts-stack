# Container Recovery And Startup Hardening Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Make the stack recover automatically after host reboot and eliminate the need to manually restart `dts-platform-webapp` when backends are not ready yet.

**Architecture:** Tighten runtime compose policy in two layers. First, fix compose manifests so long-running services use consistent restart semantics and key backends expose health checks. Second, harden `dts-platform-webapp` so it stays alive, serves a safe placeholder config, and switches to the real Nginx upstream config only after backend endpoints are resolvable/reachable.

**Tech Stack:** Docker Compose (`docker compose` + `docker-compose`), shell entrypoint, Nginx template rendering, repo shell regression tests.

---

### Task 1: Lock Restart Policy For Long-Running Containers

**Files:**
- Modify: `docker-compose.yml`
- Modify: `docker-compose-app.yml`
- Modify: `docker-compose.legacy.yml`
- Test: `tests/test_compose_restart_policy.sh` (new)

**Step 1: Write the failing test**

Create `tests/test_compose_restart_policy.sh` to assert:
- `dts-admin-webapp` has `restart: unless-stopped` in legacy and normal runtime manifests
- `dts-platform-webapp` has `restart: unless-stopped` in legacy and normal runtime manifests
- one-shot services such as `dts-airflow-init`, `dts-openmetadata-init`, `dts-openmetadata-ingestion` remain `restart: "no"`

**Step 2: Run test to verify it fails**

Run:

```bash
bash tests/test_compose_restart_policy.sh
```

Expected: FAIL because webapp services currently lack restart policy.

**Step 3: Write minimal implementation**

Update:
- `docker-compose-app.yml`
- `docker-compose.legacy.yml`

Add `restart: unless-stopped` to long-running webapp services.
Do not change one-shot task restart semantics.

**Step 4: Run test to verify it passes**

Run:

```bash
bash tests/test_compose_restart_policy.sh
```

Expected: PASS

**Step 5: Commit**

```bash
git add docker-compose.yml docker-compose-app.yml docker-compose.legacy.yml tests/test_compose_restart_policy.sh
git commit -m "fix(F1/T02): harden compose restart policies"
```

### Task 2: Add Compose Health Gates For Key Backends

**Files:**
- Modify: `docker-compose-app.yml`
- Modify: `docker-compose.legacy.yml`
- Test: `tests/test_compose_health_gates.sh` (new)

**Step 1: Write the failing test**

Create `tests/test_compose_health_gates.sh` to assert:
- `dts-admin`, `dts-platform`, `dts-ingestion`, `dts-analytics` expose compose `healthcheck`
- `dts-platform-webapp` depends on backend services with explicit conditions in legacy manifest
- healthcheck commands target local `/management/health` or equivalent local endpoint

**Step 2: Run test to verify it fails**

Run:

```bash
bash tests/test_compose_health_gates.sh
```

Expected: FAIL because these app services currently have no compose healthcheck and weak dependency gates.

**Step 3: Write minimal implementation**

Add health checks:
- `dts-admin`: `curl -fsS http://127.0.0.1:8081/management/health`
- `dts-platform`: `curl -fsS http://127.0.0.1:8081/management/health`
- `dts-ingestion`: `curl -fsS http://127.0.0.1:8083/management/health`
- `dts-analytics`: `curl -fsS http://127.0.0.1:3000/health` or the actual local health endpoint confirmed in code/config

Then tighten `depends_on` only where it reduces real startup races without creating dependency loops.

**Step 4: Run test to verify it passes**

Run:

```bash
bash tests/test_compose_health_gates.sh
```

Expected: PASS

**Step 5: Commit**

```bash
git add docker-compose-app.yml docker-compose.legacy.yml tests/test_compose_health_gates.sh
git commit -m "fix(F2/T02): add compose health gates for backend services"
```

### Task 3: Keep platform-webapp Running While Waiting For Backends

**Files:**
- Modify: `builds/dts-platform-webapp/docker-entrypoint.sh`
- Modify: `builds/dts-platform-webapp/nginx.conf.template`
- Test: `tests/test_platform_webapp_waits_for_backends.sh` (new)

**Step 1: Write the failing test**

Create `tests/test_platform_webapp_waits_for_backends.sh` to simulate:
- backend hostnames initially unresolved or unreachable
- entrypoint should still start Nginx with a safe placeholder config
- once probe command succeeds, entrypoint renders the real config and reloads Nginx

Assertions:
- script does not exit non-zero during initial backend unavailability
- placeholder config is written first
- real config replaces it after readiness

**Step 2: Run test to verify it fails**

Run:

```bash
bash tests/test_platform_webapp_waits_for_backends.sh
```

Expected: FAIL because current entrypoint renders the final upstream config immediately and exits when Nginx cannot resolve upstream.

**Step 3: Write minimal implementation**

In `docker-entrypoint.sh`:
- extract config rendering into helper functions
- render placeholder config that serves static files and a temporary 503 for proxied routes without upstream host resolution
- start Nginx once with placeholder config
- in background loop, wait for `dts-platform`, `dts-admin`, `dts-analytics` readiness
- when ready, render real proxy config and `nginx -s reload`
- keep logs explicit but bounded

In `nginx.conf.template`:
- support both placeholder and active proxy modes with env-driven rendering

**Step 4: Run test to verify it passes**

Run:

```bash
bash tests/test_platform_webapp_waits_for_backends.sh
```

Expected: PASS

Then run the earlier upgrade/compose compatibility suite that covers legacy behavior:

```bash
bash tests/test_dts_upgrade_compose_compat.sh
bash tests/test_dts_upgrade_e2e.sh
```

Expected: PASS

**Step 5: Commit**

```bash
git add builds/dts-platform-webapp/docker-entrypoint.sh builds/dts-platform-webapp/nginx.conf.template tests/test_platform_webapp_waits_for_backends.sh
git commit -m "fix(F3/T02): make platform webapp wait for backend readiness"
```

### Task 4: Close Sprint Docs And Release Guidance

**Files:**
- Modify: `worklog/v2.2.2/sprint-10-202603/README.md`
- Modify: `worklog/v2.2.2/sprint-10-202603/features/F1-容器自恢复策略/README.md`
- Modify: `worklog/v2.2.2/sprint-10-202603/features/F2-启动顺序与健康门禁/README.md`
- Modify: `worklog/v2.2.2/sprint-10-202603/features/F3-platform-webapp自等待机制/README.md`
- Modify: `worklog/v2.2.2/sprint-10-202603/features/F4-重启回归与现场验收/README.md`
- Modify: `worklog/v2.2.2/sprint-10-202603/it/README.md`
- Modify: `docs/release/v2.2.2/offline-upgrade-guide-kylin-kunpeng.md`
- Modify: `docs/release/v2.2.2/offline-upgrade-checklist-kylin-kunpeng.md`

**Step 1: Write the failing documentation checklist**

Create a short checklist in `worklog/v2.2.2/sprint-10-202603/it/README.md` that names:
- host reboot recovery verification
- legacy compose restart verification
- platform-webapp delayed backend verification

**Step 2: Run documentation sanity check**

Run:

```bash
rg -n "restart: unless-stopped|platform-webapp|宿主机重启|legacy" worklog/v2.2.2/sprint-10-202603 docs/release/v2.2.2
```

Expected: Missing the new guidance before updates.

**Step 3: Update docs**

Document:
- which services must auto-restart
- what one-shot tasks are expected to exit
- how to validate recovery after host reboot
- why `platform-webapp` may stay up before backend is ready but still becomes healthy later

**Step 4: Run final verification**

Run:

```bash
bash tests/test_compose_restart_policy.sh
bash tests/test_compose_health_gates.sh
bash tests/test_platform_webapp_waits_for_backends.sh
bash tests/test_dts_upgrade_compose_compat.sh
bash tests/test_dts_upgrade_e2e.sh
bash -n builds/dts-platform-webapp/docker-entrypoint.sh
```

Expected: PASS

**Step 5: Commit**

```bash
git add worklog/v2.2.2/sprint-10-202603 docs/release/v2.2.2
git commit -m "docs(F4/T03): document reboot recovery and verification"
```
