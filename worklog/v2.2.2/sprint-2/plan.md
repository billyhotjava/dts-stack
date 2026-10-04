# Offline In-Place Upgrade Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 为离线内网环境交付一个“旧配置优先”的一键升级器，自动完成升级包校验、镜像导入、配置合并、日志输出和新版本启动。

**Architecture:** 以 `bin/dts-upgrade` 作为单入口，在新包目录执行、对旧目录原地升级。升级器先做离线包与停机校验，再生成备份和升级锁，随后按“旧值优先、新值补缺”的规则合并 `.env`、`docker-compose*.yml` 和 `config/`，最后导入本地镜像并启动容器。升级全程生成结构化日志与回滚清单。

**Tech Stack:** Bash, docker compose, tar, local file merge helpers, offline image tar delivery

---

### Task 1: 定义升级包契约与升级器入口

**Files:**
- Create: `worklog/v2.2.2/sprint-2/tasks/OPS-001-offline-upgrade-package-contract.md`
- Create: `bin/dts-upgrade`
- Create: `bin/lib/dts-upgrade-common.sh`
- Modify: `builds/dts-build.sh`

**Step 1: Write the failing test**

- 为升级包生成逻辑补一个最小失败用例，验证 `--pack` 能额外携带 `extra/` 元数据目录和升级器脚本

**Step 2: Run test to verify it fails**

Run: `bash tests/test_dts_build_pack_contents.sh`
Expected: FAIL for missing upgrade package contract items

**Step 3: Write minimal implementation**

- 新增 `bin/dts-upgrade`
- 新增升级共享库
- 扩展 `builds/dts-build.sh --pack`，让升级包包含：
  - `bin/dts-upgrade`
  - `bin/lib/dts-upgrade-common.sh`
  - `extra/` 目录骨架

**Step 4: Run tests to verify they pass**

Run: `bash tests/test_dts_build_pack_contents.sh`
Expected: PASS

### Task 2: 增加离线预检查、停机校验和升级锁

**Files:**
- Create: `worklog/v2.2.2/sprint-2/tasks/OPS-002-preflight-stop-lock-and-logging.md`
- Modify: `bin/dts-upgrade`
- Modify: `bin/lib/dts-upgrade-common.sh`
- Create: `tests/test_dts_upgrade_preflight.sh`

**Step 1: Write the failing test**

- 校验旧目录不存在时报错
- 校验旧容器未停时报错
- 校验升级锁已存在时报错
- 校验日志文件和 summary 路径被创建

**Step 2: Run test to verify it fails**

Run: `bash tests/test_dts_upgrade_preflight.sh`
Expected: FAIL because upgrade preflight is not implemented yet

**Step 3: Write minimal implementation**

- 增加参数解析：
  - `--target`
  - `--images-dir`
  - `--extra-dir`
- 校验离线目录结构
- 校验旧容器必须已停
- 创建升级锁和日志目录

**Step 4: Run test to verify it passes**

Run: `bash tests/test_dts_upgrade_preflight.sh`
Expected: PASS

### Task 3: 镜像离线导入与完整性校验

**Files:**
- Create: `worklog/v2.2.2/sprint-2/tasks/OPS-003-offline-image-load-and-package-verification.md`
- Modify: `bin/dts-upgrade`
- Modify: `bin/lib/dts-upgrade-common.sh`
- Create: `tests/test_dts_upgrade_images.sh`

**Step 1: Write the failing test**

- 缺镜像 tar 时失败
- `checksums.txt` 校验失败时失败
- 本地镜像 tar 存在时按 manifest 顺序 `docker load`

**Step 2: Run test to verify it fails**

Run: `bash tests/test_dts_upgrade_images.sh`
Expected: FAIL because offline image verification is missing

**Step 3: Write minimal implementation**

- 增加 `release-manifest.json` / `checksums.txt` 校验
- 批量 `docker load`
- 所有导入结果写日志

**Step 4: Run test to verify it passes**

Run: `bash tests/test_dts_upgrade_images.sh`
Expected: PASS

### Task 4: `.env` 旧值优先自动合并

**Files:**
- Create: `worklog/v2.2.2/sprint-2/tasks/OPS-004-env-merge-old-priority.md`
- Modify: `bin/lib/dts-upgrade-common.sh`
- Modify: `bin/dts-upgrade`
- Create: `tests/test_dts_upgrade_env_merge.sh`

**Step 1: Write the failing test**

- 旧 key 存在且新包不同值时保留旧值
- 新包新增 key 自动补入
- 旧目录独有 key 不删除

**Step 2: Run test to verify it fails**

Run: `bash tests/test_dts_upgrade_env_merge.sh`
Expected: FAIL because env merge helper does not exist yet

**Step 3: Write minimal implementation**

- 实现 `.env` 键级合并
- 输出：
  - preserved keys
  - appended keys
  - conflicting keys

**Step 4: Run test to verify it passes**

Run: `bash tests/test_dts_upgrade_env_merge.sh`
Expected: PASS

### Task 5: `docker-compose*.yml` 旧值优先合并

**Files:**
- Create: `worklog/v2.2.2/sprint-2/tasks/OPS-005-compose-merge-old-priority.md`
- Modify: `bin/lib/dts-upgrade-common.sh`
- Modify: `bin/dts-upgrade`
- Create: `tests/test_dts_upgrade_compose_merge.sh`

**Step 1: Write the failing test**

- 新包新增 service 自动补入
- 旧环境中的 `environment/ports/volumes/extra_hosts/hostname/container_name/labels` 优先保留
- 被新包删除的旧 service 不自动删除

**Step 2: Run test to verify it fails**

Run: `bash tests/test_dts_upgrade_compose_merge.sh`
Expected: FAIL because compose merge is not implemented

**Step 3: Write minimal implementation**

- 实现 compose merge helper
- 先覆盖关键字段，再保留历史 service
- 输出 compose merge summary

**Step 4: Run test to verify it passes**

Run: `bash tests/test_dts_upgrade_compose_merge.sh`
Expected: PASS

### Task 6: `config/` 合并与冲突备份

**Files:**
- Create: `worklog/v2.2.2/sprint-2/tasks/OPS-006-config-merge-and-conflict-backup.md`
- Modify: `bin/lib/dts-upgrade-common.sh`
- Modify: `bin/dts-upgrade`
- Create: `tests/test_dts_upgrade_config_merge.sh`

**Step 1: Write the failing test**

- 结构化配置走键级合并且旧值优先
- 非结构化同名文件保留旧版
- 新版冲突文件写入冲突备份目录

**Step 2: Run test to verify it fails**

Run: `bash tests/test_dts_upgrade_config_merge.sh`
Expected: FAIL because config merge rules are missing

**Step 3: Write minimal implementation**

- 识别 `.yml/.yaml/.json/.properties`
- 对未知文件做“旧版保留 + 新版备份”

**Step 4: Run test to verify it passes**

Run: `bash tests/test_dts_upgrade_config_merge.sh`
Expected: PASS

### Task 7: 备份、回滚清单与运行数据保护

**Files:**
- Create: `worklog/v2.2.2/sprint-2/tasks/OPS-007-backup-runtime-data-and-rollback-manifest.md`
- Modify: `bin/dts-upgrade`
- Modify: `bin/lib/dts-upgrade-common.sh`
- Create: `tests/test_dts_upgrade_backup_and_rollback.sh`

**Step 1: Write the failing test**

- 升级前生成备份目录
- 生成 `rollback-manifest.json`
- `services/dts-pg/data` 不被覆盖

**Step 2: Run test to verify it fails**

Run: `bash tests/test_dts_upgrade_backup_and_rollback.sh`
Expected: FAIL because backup and rollback manifest are missing

**Step 3: Write minimal implementation**

- 创建备份目录
- 记录关键文件快照
- 记录不覆盖的数据目录

**Step 4: Run test to verify it passes**

Run: `bash tests/test_dts_upgrade_backup_and_rollback.sh`
Expected: PASS

### Task 8: 升级执行主链与启动后校验

**Files:**
- Create: `worklog/v2.2.2/sprint-2/tasks/OPS-008-upgrade-state-machine-and-postcheck.md`
- Modify: `bin/dts-upgrade`
- Modify: `start.sh`
- Create: `tests/test_dts_upgrade_e2e.sh`

**Step 1: Write the failing test**

- 完整跑通：
  - precheck
  - load images
  - merge env
  - merge compose
  - merge config
  - write summary
  - compose up

**Step 2: Run test to verify it fails**

Run: `bash tests/test_dts_upgrade_e2e.sh`
Expected: FAIL because end-to-end upgrade chain is incomplete

**Step 3: Write minimal implementation**

- 串联升级状态机
- 启动新容器
- 输出升级完成 summary

**Step 4: Run test to verify it passes**

Run: `bash tests/test_dts_upgrade_e2e.sh`
Expected: PASS

### Task 9: 离线升级演练文档与交付说明

**Files:**
- Create: `worklog/v2.2.2/sprint-2/tasks/OPS-009-offline-upgrade-runbook-and-acceptance.md`
- Create: `worklog/v2.2.2/sprint-2/it/README.md`
- Modify: `worklog/v2.2.2/sprint-2/README.md`

**Step 1: Write the checklist**

- 现场执行前置条件
- 停机要求
- 升级命令
- 验证项
- 失败回滚项

**Step 2: Run verification commands**

Run:
- `bash tests/test_dts_upgrade_preflight.sh`
- `bash tests/test_dts_upgrade_images.sh`
- `bash tests/test_dts_upgrade_env_merge.sh`
- `bash tests/test_dts_upgrade_compose_merge.sh`
- `bash tests/test_dts_upgrade_config_merge.sh`
- `bash tests/test_dts_upgrade_backup_and_rollback.sh`
- `bash tests/test_dts_upgrade_e2e.sh`

Expected: PASS
