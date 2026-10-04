# T04: legacy 回滚与物理删除边界文档

**优先级**: P0  
**状态**: DONE  
**依赖**: T01 T02 T03

## 目标

记录退役后的回滚方式和物理删除前置条件，避免本 sprint 变成不可回退的大删除。

## 技术设计

- 在 `it/README.md` 或新增 ops note 中记录：
  - 如何验证默认路径没有 dts-metrics。
  - 如何显式启用 legacy/profile 或手工构建旧镜像。
  - 哪些目录暂不删除：`source/dts-metrics`、`source/dts-metrics-webapp`、`builds/dts-metrics`。
  - Sprint-55 物理删除条件。

## 影响范围

- `worklog/v2.2.3/sprint-53-202606/it/README.md`
- 如需：`docs/` 运维说明。

## 验证

- [x] `it/README.md` 记录默认路径验证、legacy compose 验证和显式构建方式。
- [x] 物理删除条件清晰绑定 Sprint-55。

## 完成标准

- [x] 现场能理解“默认退役”和“源码删除”不是同一件事。
