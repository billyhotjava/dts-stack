# T02：建立 authoring context/create/save facade

**优先级**：P0  
**状态**：IMPLEMENTED  
**依赖**：T01

## 目标

提供唯一 authoring read/create/save 边界，返回同一 ModelSpec、implementation、bundle、projection 与 draft pins，并委托现有 owner 持久化。

## 技术设计（Contract-first）

- **输入契约**：README §4.1/4.2 的 GET/POST/PUT DTO；`CATALOG_MAINTAINERS`；现有 draft 文件限制。
- **输出契约**：`AuthoringContextView`、`AuthoringDraftView`、新 strong ETag；save 响应 `{draftId,etag,expiresAt,fileCount,totalBytes,modelChecksum,projectionChecksum}`。
- **数据流**：Resource → `ModelAuthoringDraftService` → ModelSpec reader/representation → existing draft service/repository/file repository。
- **错误路径**：400 字段非法；403 无技术/写权限；404 model/draft 不存在；409 status/pins/idempotency 冲突；412 draft ETag 过期；422 容量/模型契约错误。
- **复用点**：ModelSpec decoder/validator、draft contract/CAS、bundle source snapshot、representation evaluator。
- **实现方案**：facade 只编排；文件正文仍写既有 file table，不聚合进 model snapshot。

## 影响范围

`dts-platform` 新 resource/thin service/DTO 与 focused tests；不得新建 repository/table/parser。

## 验证（RED→GREEN）

- [ ] MockMvc 先定义精确 JSON、ETag 和错误矩阵。
- [ ] service IT 断言 create replay、save CAS、容量边界、无 N+1。
- [ ] 旧 `/dbt-drafts` create/save 测试继续通过。

## Definition of Done

- [ ] context 不泄露无权限文件正文。
- [ ] visual/code 保存共享 draftId/ETag，且切换 view 不写库。
- [ ] facade 无第二持久化 owner，账本与影响分析已更新。
