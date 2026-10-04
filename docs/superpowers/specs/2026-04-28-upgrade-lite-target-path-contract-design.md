# dts-upgrade-lite Target Path Contract — Design

- **Date:** 2026-04-28
- **Owner:** billy
- **Scope:** `bin/dts-upgrade-lite`, `bin/dts-upgrade`, `bin/dts-upgrade-rollback`, release docs

## Background

Customer offline sites run the legacy stack from a path like `/data/dts-stack`. That path is wired into operator runbooks, monitoring, cron jobs, and customer SOPs. Two things have to be true during an upgrade:

1. The operator is unambiguous about which directory the upgrader will touch (no placeholder substitution surprises).
2. Whatever path the operator passes, the upgrader must never rename, relocate, or symlink-swap it during execution.

Today's behaviour:
- `dts-upgrade-lite` requires `--target` (no default). If the operator forgets, it dies.
- All release docs use `/data/stack_old` as the example path (50+ occurrences across v2.2.2 and v2.2.3 guides). `stack_old` is purely placeholder and is easy to copy-paste literally.
- The script does not rename the target directory anywhere, but there is no explicit invariant guard. Adding one makes the contract auditable.

## Goals

- Make `/data/dts-stack` the **default** for `--target`. Operators can still override.
- Replace every `/data/stack_old` example in docs and `usage` with `/data/dts-stack`.
- Add a runtime invariant: if the target directory's inode or realpath changes during apply / rollback, fail loudly.
- Print a one-line operator banner at apply start showing the resolved target path and the contract ("will not be renamed/moved").

## Non-Goals

- Upgrade write strategy is unchanged (still: load images, KV-merge `.env`, sync configs/runtime under existing skip rules, restart stack).
- No change to `services/*/data` handling (already protected).
- No change to compose runner selection, lock semantics, trap behaviour from the previous round.

## Design

### 1. Default target resolution

In `dts-upgrade-lite`, after `parse_args` and before `normalize_paths`:

```bash
DEFAULT_TARGET_DIR="/data/dts-stack"

resolve_default_target() {
  if [[ -n "${TARGET_DIR}" ]]; then
    return 0
  fi
  if [[ -d "${DEFAULT_TARGET_DIR}" ]]; then
    TARGET_DIR="${DEFAULT_TARGET_DIR}"
    info "no --target provided; using default ${TARGET_DIR}"
    return 0
  fi
  die "--target is required (default ${DEFAULT_TARGET_DIR} does not exist)"
}
```

`normalize_paths` keeps its existing `[[ -d ... ]] || die` check after this.

The default resolution **must be opt-in via existence**, not a blind value: if the operator runs the upgrader on a fresh box without `/data/dts-stack`, the error message is clear instead of failing later with a misleading "directory not found".

`dts-upgrade` and `dts-upgrade-rollback` get the same default for symmetry.

### 2. Path invariance guard

Add to `dts-upgrade-lite`:

```bash
TARGET_INODE_AT_START=""
TARGET_REALPATH_AT_START=""

snapshot_target_identity() {
  TARGET_INODE_AT_START="$(stat -c %i "${TARGET_DIR}" 2>/dev/null || true)"
  TARGET_REALPATH_AT_START="$(readlink -f "${TARGET_DIR}" 2>/dev/null || printf '%s' "${TARGET_DIR}")"
}

assert_target_unchanged() {
  local now_inode now_real
  [[ -n "${TARGET_INODE_AT_START}" ]] || return 0
  now_inode="$(stat -c %i "${TARGET_DIR}" 2>/dev/null || true)"
  now_real="$(readlink -f "${TARGET_DIR}" 2>/dev/null || printf '%s' "${TARGET_DIR}")"
  if [[ "${now_inode}" != "${TARGET_INODE_AT_START}" || "${now_real}" != "${TARGET_REALPATH_AT_START}" ]]; then
    die "target directory identity changed (inode ${TARGET_INODE_AT_START} -> ${now_inode}, path ${TARGET_REALPATH_AT_START} -> ${now_real})"
  fi
}
```

Hook points:
- `run_apply` — call `snapshot_target_identity` after `init_report`; call `assert_target_unchanged` right before `start_stack` and again after `postcheck`.
- `apply_failure_trap` — call `assert_target_unchanged` before clearing the lock; if it fires, the trap prefers the invariant error message over the original.
- `run_rollback` — same snapshot + assertion bracketing.

`stat -c %i` is GNU; Kylin/Kunpeng customer hosts ship GNU coreutils, no portability concern.

### 3. Operator banner

At the top of `run_apply` after target resolution:

```
[dts-upgrade-lite] Target: /data/dts-stack (path will not be renamed/moved; inode=12345)
[dts-upgrade-lite] Source: /opt/dts-upgrades/v2.2.3/dts-stack
```

Same banner pattern in `run_plan` and `run_rollback`.

### 4. Documentation rewrites

- `bin/dts-upgrade-lite` `usage`: `--target <target-dir, default /data/dts-stack>`
- `bin/dts-upgrade` `usage`: same
- `bin/dts-upgrade-rollback` `usage`: same
- `README.md`: the lite quick-start block
- `docs/release/v2.2.3/offline-upgrade-guide-kylin-kunpeng.md`
- `docs/release/v2.2.3/offline-upgrade-checklist-kylin-kunpeng.md`
- `docs/release/v2.2.3/upgrade-lite-operations-kylin-kunpeng.md`
- `docs/release/v2.2.2/*.md` (3 files, kept consistent so v2.2.2 customers find the same wording when cross-referenced)

Replacement is mechanical: `s|/data/stack_old|/data/dts-stack|g` and `s|stack_old|dts-stack|g` for prose mentions. Each touched doc gets a new top-of-page block:

```markdown
## 目录契约

- 默认升级目标目录：`/data/dts-stack`（`--target` 不传时自动使用）
- 升级过程中目标目录的路径与 inode 不会改变
- 数据目录 `services/*/data` 不会被升级器写入
- 升级产物写入 `${target}/logs/upgrade-lite-${timestamp}/`
```

### 5. Tests

New file `tests/test_dts_upgrade_lite_path_invariant.sh`:

1. **Default target resolution, dir present** — symlink `/tmp/.../mock-default` to a fixture dir, override `DEFAULT_TARGET_DIR` via env (use `DTS_UPGRADE_LITE_DEFAULT_TARGET` env override — see below), run `plan` without `--target`, expect success.
2. **Default target resolution, dir missing** — point default at `/tmp/missing`, expect die with friendly message.
3. **Path invariance honoured** — run apply, capture inode pre/post, assert equal.
4. **Path invariance violated** — wrap apply with a malicious shim that `mv`s `target` to `target.bak` mid-flight; expect apply to die with the invariant message and not produce a "success" summary.

To make the default target overridable for tests without burning a hardcoded literal, support an env var:
```bash
DEFAULT_TARGET_DIR="${DTS_UPGRADE_LITE_DEFAULT_TARGET:-/data/dts-stack}"
```

Test (4) is hard to inject without modifying script flow. Drop it from the first cut and rely on the snapshot/assert bracketing being reviewed by eye + tests (1)-(3). If we want (4) later, we can expose a `DTS_UPGRADE_LITE_PRE_START_HOOK` env var that runs an arbitrary command between `sync_new_runtime_files` and `start_stack` — but that's extra surface area; defer.

## Risks

- **Default target on dev hosts that happen to have `/data/dts-stack`**: if a developer creates that path, plan/apply will silently default to it. Mitigation: the operator banner makes the resolved path explicit.
- **`readlink -f` semantics**: GNU returns absolute canonical path; on missing intermediate, it still returns a path. Acceptable for our use.
- **inode change during apply by a legitimate operation**: nothing in our flow renames the dir. If a customer's external monitoring/snapshot tool does, the upgrader bails with a clear message instead of completing on top of a moved dir.

## Out of Scope

- Multi-target / rolling upgrade orchestration.
- Symlinking `/data/dts-stack` to a versioned dir at the customer site (a separate operational pattern; doesn't affect the upgrader contract).
