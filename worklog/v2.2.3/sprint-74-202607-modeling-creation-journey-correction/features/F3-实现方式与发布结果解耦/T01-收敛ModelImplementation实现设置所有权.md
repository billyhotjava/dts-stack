# T01：收敛 ModelImplementation 实现设置所有权

**优先级**：P0  
**状态**：DRAFT  
**依赖**：F2

## 目标

把来源、字段映射、物理名、装载、分区和保留统一写入既有 ModelImplementation revision。

## 技术设计（Contract-first）

- **输入契约**：既有 `ModelImplementationWriteCommand`；Sprint-73 已提交的 ModelSpec `implementationPolicy` 仅作为兼容读取/迁移输入。
- **settings 精确字段**：
  - `targetPhysicalName:string`
  - `loadStrategy:"FULL"|"INCREMENTAL"|"SNAPSHOT"`
  - `partitionFields:string[]`
  - `retentionDays?:number(0..36000)`
  - 其他既有 join/settings 保持兼容。
- **输出契约**：append-only implementation revision/checksum，CAS `If-Match-Implementation`。
- **技术标识**：DESIGNER_GENERATED 的 `projectKey/dbtUniqueId` 由服务端稳定生成；DBT_MANAGED 从受控 dbt project/node 投影。新 UI 不要求用户填写，旧 API 字段兼容接收但必须与服务端解析结果一致。
- **数据流**：DESIGNED ModelSpec pin → implementation form → validate → save → IMPLEMENTATION_READY gate。
- **错误路径**：model revision 漂移 409；旧 implementation CAS 412；输入 stale 422；物理名冲突 409。
- **复用点**：账本 L13；既有 lifecycle resource/service/checksum/compatibility adapter。
- **实现方案**：新 UI 从逻辑 form/save command 移除实现字段；扩 settings validator 和 projection；旧 snapshot 保持可读，旧值变化由 F4/T03 迁移契约处理；不新建 implementation table。

## UI 交互规格

实现页按“输入、映射、目标与运行策略”分区；字段值来自当前逻辑 revision；保存失败保留表单。projectKey、dbtUniqueId 和 implementation revision 仅在“技术详情”折叠区只读展示。

## 影响范围

ModelLifecycleContract/Resource/Service、implementation checksum、ImplementationStage、logical form mapper、tests。

## 验证（RED→GREEN）

- [ ] settings round-trip/checksum/CAS
- [ ] target name/partition field/load strategy 边界
- [ ] 切换后 ModelSpec snapshot 不再新增或修改 implementationPolicy；既有值仍可回读
- [ ] stale model/implementation fail closed

## Definition of Done

- [ ] 架构：实现设置唯一 owner
- [ ] UI：配置位置与含义一致
- [ ] 切片：普通模式保存并达到 IMPLEMENTATION_READY
