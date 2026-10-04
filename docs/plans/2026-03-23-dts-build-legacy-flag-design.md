# dts-build Legacy Flag Design

## Goal

给 `builds/dts-build.sh` 增加显式 `--legacy` 参数，使其在 legacy 模式下只构建 legacy 交付链需要的镜像，而不再默认同时构建 normal + legacy 两套产物。

## Scope

- 支持 `-all --legacy`
- 支持 `--image <name> --legacy`
- 不改变未传 `--legacy` 时的现有行为
- `--pack` 继续保持独立，不与 `--legacy` 混用

## Behavior

- `-all --legacy`
  - 只执行 `build_all_legacy`
  - 输出目录仅使用 `builds/legacy-dist`
- `--image <name> --legacy`
  - 只构建该镜像的 legacy 变体
  - 对存在 `Dockerfile.offline` 的后端镜像使用 offline Dockerfile
  - 对 webapp / airflow / dbt / addax 这类没有 offline Dockerfile 的镜像，沿用现有 Dockerfile，但只输出到 `builds/legacy-dist`

## Non-Goals

- 不新增 `normal-only` 开关
- 不调整打包逻辑
- 不重构镜像列表定义

## Validation

- 新增 shell 回归测试覆盖 `--image dts-admin --legacy`
- 新增 shell 回归测试覆盖 `-all --legacy`
- 验证 legacy-only 模式不会写入 `builds/dist`
