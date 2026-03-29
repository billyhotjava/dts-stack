# DTS Build Multi-Image Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Allow `builds/dts-build.sh` to build multiple named images in one invocation and perform a non-blocking `git pull` before builds.

**Architecture:** Keep the existing single-image build path as the execution primitive. Change CLI parsing from a single `IMAGE_ONLY` scalar to an array of selected image names, then loop over the existing `build_single_image` function. Add a lightweight `attempt_git_pull` helper before the build/pack dispatch that warns on failure but never stops execution.

**Tech Stack:** Bash, existing shell test harness in `tests/`, fake `docker`/`git` stubs.

---

### Task 1: Lock Desired Behavior With a Failing Shell Test

**Files:**
- Create: `tests/test_dts_build_multi_image_and_git_pull.sh`
- Reuse: `tests/test_dts_build_legacy_mode.sh`

**Steps:**
1. Write a shell test that copies `builds/dts-build.sh` into a temp repo and stubs `docker`, `git`, `free`, `df`, and `unzip`.
2. Add one scenario for `--image dts-admin dts-platform --legacy` and assert both images are built.
3. Add one scenario where fake `git pull --ff-only` exits non-zero and assert the build still completes.
4. Run the new test and verify it fails against the current script.

### Task 2: Implement Multi-Image Selection

**Files:**
- Modify: `builds/dts-build.sh`
- Test: `tests/test_dts_build_multi_image_and_git_pull.sh`

**Steps:**
1. Replace `IMAGE_ONLY` scalar handling with an array, while preserving `--all` / `--pack` mutual exclusivity.
2. Support `--image` followed by one or more image names until the next flag, and also support repeated `--image`.
3. Update usage text and examples to show multi-image invocations.
4. Dispatch selected images by looping over the existing `build_single_image`.
5. Run the new test and verify the multi-image scenario passes.

### Task 3: Implement Non-Blocking Git Pull

**Files:**
- Modify: `builds/dts-build.sh`
- Test: `tests/test_dts_build_multi_image_and_git_pull.sh`

**Steps:**
1. Add `attempt_git_pull()` that checks for `git`, verifies `REPO_ROOT/.git`, and runs `git -C "$REPO_ROOT" pull --ff-only`.
2. If pull fails, print a warning and continue without changing exit status.
3. Invoke the helper before `pack_deployment` / build dispatch after `preflight_check`.
4. Run the new test and verify a failed `git pull` still allows the build path to succeed.

### Task 4: Regression Verification

**Files:**
- Modify: none unless fixes are needed
- Test: `tests/test_dts_build_multi_image_and_git_pull.sh`
- Test: `tests/test_dts_build_legacy_mode.sh`
- Test: `tests/test_dts_build_disk_safety.sh`

**Steps:**
1. Run the new targeted test.
2. Run existing build-script regression tests that cover legacy mode and argument safety.
3. Review diffs for `builds/dts-build.sh` and test files only.
