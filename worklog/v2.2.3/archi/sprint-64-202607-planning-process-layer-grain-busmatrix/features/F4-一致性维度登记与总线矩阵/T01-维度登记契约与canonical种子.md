# T01: 维度登记契约与 canonical 种子

**优先级**: P1
**状态**: DONE
**依赖**: F1/T01

## 目标

建立一致性维度登记与总线矩阵的数据内核。

## 技术设计

- 新增 `src/pages/governance/conformedDimensions.ts`：ConformedDimension 契约 + 8 项 canonical 种子常量；契约增加可选 `objectId` 关联业务对象（一致性维度即业务对象的维度化，与 F1/T04 打通）；矩阵=processId×dimensionId 勾选集合，session 版本化存储。
- API 缺口：`GET/POST /api/modeling/conformed-dimensions`、`GET/PUT /api/governance/bus-matrix/{domainId}`。

## 影响范围

- 新增 `conformedDimensions.ts` + vitest + source-contract

## 验证

- [x] 种子齐全；矩阵勾选/取消/恢复/降级用例。
