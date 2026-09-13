# T04：建立候选读模型与 API

**优先级**：P0
**状态**：IN_PROGRESS
**依赖**：T03

## 目标

提供计划级交付工作台所需的聚合读模型和细粒度命令 API，避免前端自行拼接状态和推断允许动作。

## 技术设计

- 查询返回候选摘要、条目、证据摘要、首要阻塞、允许动作和 ETag。
- 命令 API 分离 create/update/lock/retry，不使用万能 PATCH。
- plan、candidate、modelSpec 归属和 tenant 在服务端逐层复验。
- 空候选、无权限、版本冲突、STALE 使用稳定错误码。

## 影响范围

- 新增 `ModelReleaseCandidateResource.java`
- 扩展 candidate contract/service
- 新增 resource/security 测试
- 扩展 `modelSpecApi.ts` 的类型契约

## 实施步骤

1. 先写 MockMvc 契约测试和 OpenAPI 响应快照。
2. 实现查询投影和最小命令资源层。
3. 编写 Resource/Application/前端 source-contract 测试源码并完成静态审查；运行验证留到 F6。

## 完成标准

- [x] 工作台首屏只需一个聚合查询即可得到可靠状态。
- [x] API 使用服务端 tenant 与计划维护权限，所有写动作要求权限、幂等键和强 ETag。
- [x] **UI 契约验收**：聚合响应完整驱动工作台的 loading、empty、error、forbidden、ready 五类首屏，不要求前端再拼接或猜测 allowedActions。

## 实现与验证证据

- 已实现计划级 workspace、候选 item GET，以及 create/scope/lock/retry/refresh/replacement 细粒度命令。
- canonical drift 在只读首屏显示稳定 blocker，显式 refresh 命令在加锁复核后原子写入 STALE，避免 GET 副作用和恢复死路。
- 活动候选部分唯一索引、存量重复预检、排他 rollback guard、append-only command trigger 与对应 IT 源码已补齐。
- Java、TypeScript、数据库和通用代码静态复审无 High 阻断；安全复审结果待回收。
- 按统一测试窗口不运行 Maven、pnpm、PostgreSQL 或浏览器测试；最终验证前保持 IN_PROGRESS。
