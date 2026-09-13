# T02: init 与 image/build 默认项退役

**优先级**: P0  
**状态**: DONE  
**依赖**: T01

## 目标

让默认初始化和默认构建不再生成或构建 `dts-metrics`。

## 技术设计

- `init.sh` 默认不生成：
  - `PG_DB_METRICS`
  - `PG_USER_METRICS`
  - `PG_PWD_METRICS`
  - `DTS_METRICS_TO_PLATFORM`
  - `DTS_METRICS_SERVICE_NAME`
  - `DTS_METRICS_API_BASE_PATH`
  - `IMAGE_DTS_METRICS`
- 如需保留，受 `DTS_LEGACY_METRICS_ENABLED=1` 或 legacy compose 控制。
- `imgversion.conf`、`imgversion.dts-source.conf` 移除默认 `IMAGE_DTS_METRICS` 或移到 legacy 文件。
- `builds/dts-build.sh` 默认 all 不构建 `dts-metrics`；显式 `--image dts-metrics` 可保留作为回滚。

## 影响范围

- `init.sh`
- `imgversion.conf`
- `imgversion.dts-source.conf`
- `builds/dts-build.sh`

## 验证

- [x] `init.sh` 默认 `.env` 模板不输出 metrics triplet；仅在 `DTS_LEGACY_METRICS_ENABLED=true|1` 时输出 legacy metrics 变量。
- [x] `builds/dts-build.sh` 默认 `build_all_normal` 不进入 `build_metrics_webapp`、`dts-metrics` Maven module 或 image build。
- [x] 显式 legacy 构建路径保留：`--image dts-metrics --legacy` 或 legacy all。
- [x] `bash -n init.sh && bash -n builds/dts-build.sh` 通过。

## 完成标准

- [x] 常规安装/构建不会意外拉起旧指标服务。
