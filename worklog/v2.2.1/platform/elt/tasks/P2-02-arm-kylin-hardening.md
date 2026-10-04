# P2-02 鲲鹏/麒麟兼容加固与复测

## 状态
- `done-first-pass`

## 范围
- 补齐 ARM/麒麟环境下脚本、镜像、路径、时区、权限等兼容点。

## 依赖
- P2-01 完成。

## 交付物
- `platform/arm-kylin-hardening.md`
- `platform/scripts/arm-kylin-preflight.sh`

## 本轮进展
- 已将 ARM/麒麟复测入口统一到 `run-all.sh`。
- 已补充 `run-matrix.sh`，支持 ARM 下 `normal/legacy/dev` 一次执行。
- 已补充 `arm-kylin-preflight.sh`，用于在目标机执行架构/发行版/docker/容器门禁检查。
- 已补充架构门禁：默认禁止 `--arch` 与宿主架构不一致，防止伪造 ARM 证据。
- 当前仅完成 x86 复跑证据，ARM/麒麟环境实测仍待回填。

## 验收标准
- ARM/麒麟环境下可重复执行 P0/P1 脚本并产出一致证据。
- 预检门禁通过：`arm-kylin-preflight-*.txt` 中 `status=PASS`。
- 矩阵回填完成：`env-matrix.csv` 新增 `arch=aarch64` 的 `normal/legacy/dev` 三行有效样本（`total>0`）。

## 回滚点
- 文档与脚本改动可按 commit 回退。
