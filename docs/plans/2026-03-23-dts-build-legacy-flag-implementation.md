# dts-build Legacy Flag Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 为 `builds/dts-build.sh` 增加 `--legacy` 参数，支持 legacy-only 构建。

**Architecture:** 通过增加一个布尔开关控制主分支调度和单镜像构建路径；默认行为不变，仅在显式 legacy-only 时切到 legacy 输出目录与 Dockerfile 选择逻辑。

**Tech Stack:** Bash, shell tests

---

### Task 1: 增加失败测试

**Files:**
- Create: `tests/test_dts_build_legacy_mode.sh`

**Step 1: 写 shell 回归测试**

- 覆盖 `--image dts-admin --legacy`
- 覆盖 `-all --legacy`
- 断言 legacy-only 不写 `builds/dist`

**Step 2: 运行测试确认失败**

Run: `bash tests/test_dts_build_legacy_mode.sh`

Expected: 由于脚本还不支持 `--legacy`，测试失败。

### Task 2: 实现 legacy-only 参数

**Files:**
- Modify: `builds/dts-build.sh`

**Step 1: 增加参数解析**

- 新增 `--legacy`
- 更新 usage
- 限制 `--legacy` 不能和 `--pack` 同用

**Step 2: 改主分支调度**

- `-all --legacy` 只跑 `build_all_legacy`
- 默认 `-all` 仍然跑 normal + legacy

**Step 3: 改单镜像构建**

- `--image <name> --legacy` 只构建 legacy 变体
- 没有 offline Dockerfile 的镜像仍可走 normal Dockerfile，但仅输出到 `legacy-dist`

**Step 4: 调整 preflight 磁盘估算**

- legacy-only 的 `-all` 采用低于“双构建”模式的估算值

### Task 3: 回归验证

**Step 1: 跑新增测试**

Run: `bash tests/test_dts_build_legacy_mode.sh`

**Step 2: 补静态自检**

Run: `bash -n builds/dts-build.sh`
