# T03: drift、dbt parse/test 与产物发布门禁

**优先级**: P0
**状态**: IN_PROGRESS
**依赖**: T01,T02

## 目标

将 dbt 编译、parse、test 和 ModelSpec 漂移纳入统一发布门禁。

## 技术设计

漂移分类：

- `FIELD_DRIFT`
- `GRAIN_DRIFT`
- `SOURCE_DRIFT`
- `STANDARD_DRIFT`
- `ARTIFACT_CHECKSUM_DRIFT`

门禁规则：有未处理漂移、dbt parse 失败、dbt test 失败或未登记业务对象时不得进入受控发布态。

## 影响范围

- drift service、发布审核状态机、前端错误提示。
- dbt CI/test script 与证据目录。

## 验证

- [x] 人为修改 PJM 字段快照后得到 FIELD_DRIFT。
- [x] 修改 grain 后可被发布门禁识别为 GRAIN_DRIFT。
- [x] dbt parse/test 失败和未登记对象会阻断受控发布。
- [ ] parse/test 结果回写真实模型台账。

## 完成标准

- [x] 发布门禁返回稳定 blocker code，可解释并映射修复入口。
