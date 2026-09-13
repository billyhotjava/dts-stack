# T01：重接并删除立即可删 HTTP 面

**优先级**：P0
**状态**：PLANNED
**依赖**：F0 caller/data/backup gate；F1～F3 canonical ports

## 目标

同批重接调用方并物理删除旧 `/api/semantic/**`、旧 `modeling_plan` 面和 `/api/modeling/vnext/**` HTTP shell。

## 技术设计（Contract-first）

- **输入契约**：三类 route 的 caller manifest、runtime usage、外部调用确认、对应 canonical API 映射。
- **输出契约**：所有 caller 改用 WarehousePlan/ModelSpec/StageGate/Lifecycle/Candidate；旧 route 从 router/OpenAPI/Spring context 移除。
- **旧 plan 数据**：逐 tenant 为 0 或映射到唯一 WarehousePlan，版本/CAS/domain/mart/source refs 校验。
- **错误路径**：任何 caller>0、外部清单未知、migration conflict/orphan、备份未恢复即 NO_GO。
- **删除**：resource/DTO/mapper/route-only facade/test fixture；内部 vNext service 若仍消费，留给 T02，不作为 HTTP shim 保留。
- **URL 行为**：统一普通 404；禁止 410/redirect/tombstone。

## 影响范围

旧 HTTP resources/contracts/clients/tests 与 canonical client adapters；每个 symbol 编辑前 impact。

## 验证

- [ ] route/client/OpenAPI/source/runtime consumer=0。
- [ ] canonical API contract tests 通过。
- [ ] old URL 普通 404，响应中无 migration/compatibility 语义。
- [ ] Spring context 无旧 resource bean。

## Definition of Done

- [ ] 三个 HTTP 面物理删除且新 UI/API 旅程不依赖它们。
- [ ] 未误删 canonical semantic/indicator consumption 能力。
- [ ] 删除动作分类审计已耐久投递。
