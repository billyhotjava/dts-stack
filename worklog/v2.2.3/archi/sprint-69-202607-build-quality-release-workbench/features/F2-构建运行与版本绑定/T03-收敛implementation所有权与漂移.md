# T03：收敛 implementation ownership 与漂移

**优先级**：P0
**状态**：READY
**依赖**：T02、Sprint-67 F3

## 目标

确保一个候选 revision 只关联一个有效实现，并与 Sprint-67 的 ModelImplementation/PhysicalAssetRevision 四层边界兼容。

## 技术设计

- 消费稳定的 `implementationId/mode/path/checksum`，不依赖维度内部设计字段。
- 同一 revision 多 owner、路径越界、checksum 变化和被替换实现均标记 STALE。
- SQL/dbt 实现修改必须产生新 checksum 和新 build run。
- DIMENSION 专属映射等待 Sprint-67 接口冻结，其他模型类型先实现。

## 影响范围

- `ModelLifecycleService.java`
- ModelImplementation 查询端口及 adapter
- implementation drift 测试

## 实施步骤

1. 先写单 owner、多 owner、替换、checksum 漂移和路径越界测试。
2. 接入稳定实现端口，不读取页面临时状态。
3. 在 Sprint-67 接口冻结后补 DIMENSION fixture。

## 完成标准

- [ ] 实现归属和漂移判断不复制 Sprint-67 四层模型。
- [ ] 漂移后的旧构建、质量和批准证据均不可继续发布。
- [ ] **UI 契约验收**：实现版本卡展示 owner、mode、path 和 checksum；多 owner 或漂移时显示 STALE Banner、影响说明及重新锁定入口。
