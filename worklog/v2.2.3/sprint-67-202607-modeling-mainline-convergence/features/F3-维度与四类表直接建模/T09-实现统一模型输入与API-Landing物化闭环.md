# T09：实现统一模型输入与 API Landing 物化闭环

**优先级**：P0
**状态**：READY
**依赖**：F3-T06、F3-T07、F3-T08、F6-T06

## 目标

让模型实现只消费统一的物理资产、上游模型或受控生成器，API 数据先通过现有采集任务生成并登记 ODS Landing 资产，再进入同一物化和血缘链。

## 范围

- `ModelImplementation` 建立 `PHYSICAL_ASSET`、`UPSTREAM_MODEL`、`GENERATED` 三种主输入方式；
- 兼容读取现有 `sourceRefs`、`dependsOn`、`generationStrategy`，新写经适配层进入统一输入；
- 数据库同步表和 API Landing 表统一解析为当前计划已确认物理资产 revision；
- API 试跑成功后登记 Landing 元数据、采集任务 revision、checkpoint 和来源血缘；
- 目标 DDL、编译/测试、部署和物理资产回写关联同一 modelSpecId/revision；
- SUMMARY/APPLICATION 禁止直接物理来源，模型循环、自引用和版本漂移 fail closed。

## 不做

- 不实现 OpenAPI/Swagger 自动导入；
- 不支持 GraphQL、SOAP/XML 或复杂 JSON 自动拆表；
- 不允许模型运行时直接调用 API；
- 不扩展为通用虚拟表框架。

## 验证

- [ ] 连接测试不会自动创建规划来源或模型输入；
- [ ] 数据库表完成元数据同步和规划确认后可作为 `PHYSICAL_ASSET`；
- [ ] API 未试跑或未登记 Landing 时返回 `API_LANDING_NOT_READY`；
- [ ] API Landing 登记后可被计划确认并用于模型实现；
- [ ] 日期维度通过 `GENERATED` 实现，无需上游表；
- [ ] SUMMARY/APPLICATION 只接受锁定 revision 的 `UPSTREAM_MODEL`；
- [ ] 漂移、跨计划、跨部门、循环和目标自引用均拒绝；
- [ ] 部署后可从目标资产反查模型、Landing、采集任务和原始连接。

## 完成标准

- [ ] 后端契约、持久化、stage gate、编译和血缘测试通过；
- [ ] PostgreSQL 真实落库和元数据 revision 对账通过；
- [ ] API checkpoint 连续性和失败重试有真实证据；
- [ ] F6-T08 真实联动前保持 READY/IN_PROGRESS，不提前关闭。
