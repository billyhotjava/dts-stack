# dts-build Analytics Modern Minimal Context Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Change `builds/dts-build.sh` so `dts-analytics-webapp-modern` builds with a minimal temporary Docker context instead of the full repository root.

**Architecture:** Keep the existing Dockerfile and build arguments unchanged. Add one small helper that stages only the required files into a temporary context and route the analytics modern image through that helper.

**Tech Stack:** Bash, Docker CLI

---

### Task 1: Add a temporary minimal-context helper

**Files:**
- Modify: `builds/dts-build.sh`

**Steps:**
1. Add a helper that creates a temporary directory and preserves repo-relative paths.
2. Copy `builds/dts-analytics-webapp/modern/` and `source/dts-analytics-webapp/modern/` into that directory.
3. Return the temporary context path for the caller.

### Task 2: Route analytics modern builds through the helper

**Files:**
- Modify: `builds/dts-build.sh`

**Steps:**
1. Detect the `dts-analytics-webapp-modern` image inside the build path.
2. Build it with `build_image_ctx` using the temporary context.
3. Keep all current tags, build args, and save behavior unchanged.
4. Ensure the temporary directory is removed even when the build fails.

### Task 3: Verify the script

**Files:**
- Modify: `builds/dts-build.sh`

**Steps:**
1. Run `bash -n builds/dts-build.sh`.
2. Run a small shell-level check to confirm the helper stages only the required paths.
3. Summarize the behavior change for operators.
