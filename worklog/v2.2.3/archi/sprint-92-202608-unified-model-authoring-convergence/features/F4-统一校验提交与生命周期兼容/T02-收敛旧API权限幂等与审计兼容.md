# T02：收敛旧 API、权限、幂等与审计兼容

**优先级**：P0  
**状态**：IMPLEMENTED  
**依赖**：T01、F3/T02

## 目标

让旧 `/dbt-drafts`、ownership transition 和 convert 路由在兼容期委托同一 authoring service/repository，且新 UI 不再调用这些产品语义。

## 技术设计（Contract-first）

- **输入契约**：账本 L07/L12/L13；旧请求/响应 DTO、If-Match、idempotencyKey；现有 `CATALOG_MAINTAINERS`。
- **输出契约**：旧响应字段不删改，可追加 deprecation metadata/header；相同请求仍返回稳定状态；新 facade 和旧 adapter 共用 receipt/audit owner。
- **数据流**：legacy resource → adapter mapper → `ModelAuthoringDraftService` → existing repository；不得调用第二 service 状态机。
- **错误路径**：未知/非法旧 targetOwnership 维持 4xx；无权限 403；不能安全映射的 convert fail closed 并给稳定 code，不伪造成功。
- **审计动作**：登记 fork/save/validate/commit 和 legacy adapter 分类；payload 只含 IDs/pins/checksum/provenance。
- **Contract 条件**：F5/T01 取得连续观测前不得删除路由/字段。

## 影响范围

`ModelLifecycleResource`、`DbtImplementationDraftResource`、审计资源字典及三份 runtime mirror、前端 API adapter/source-contract。

## 验证（RED→GREEN）

- [ ] 旧 MockMvc/前端 contract 全回归；新增旧/新同结果对账。
- [ ] 角色矩阵：维护者成功，受限账号技术正文/写入 403。
- [ ] 审计动作均非“未分类”，正文 key 负向断言。

## Definition of Done

- [ ] 新 UI network 零 ownership transition/convert 调用。
- [ ] 旧路由保持兼容且不再造成 visual 整页只读。
- [ ] 权限/审计/幂等只有一套实现。
