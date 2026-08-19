# Sprint-92 集成验收计划

**状态**：AUTOMATION_COMPLETE / E2E_PENDING；聚焦自动化证据见 `evidence/20260820-automated.md`，以下 IT 条目仍不得提前标 PASS。  
**执行纪律**：所有 Feature 编码完成、聚焦自动化通过和 G3 发布准备完成后，只运行一次集中 E2E；失败仅定向重跑断点。

| IT | 场景 | 前置 | 核心断言 | 证据 |
|---|---|---|---|---|
| IT-01 | G0 基线刷新 | 运行实例/账号 | health、schema、三类样本、harness 可用 | `evidence/*-baseline.md` |
| IT-02 | PUBLISHED 派生新草稿 | 已发布模型 | 发布修订不变；同 ModelSpec 新 DRAFT；幂等重放同 draft | HTTP/DB/截图 |
| IT-03 | 平台生成模型跨视图编辑 | SYSTEM_GENERATED DRAFT | visual/code 同 draftId/ETag；切换无 revision；一次 commit pins 一致 | HTTP/DB/截图 |
| IT-04 | ZIP 导入模型视觉精修 | lossless ZIP 样本 | provenance 只展示；字段/依赖可改；原始 bundle 可追溯 | before/after checksum |
| IT-05 | PARTIAL/NONE 安全降级 | 宏/复杂 SQL 样本 | 结构化节点可改；raw node 可定位；unmanaged 文件零变化 | golden bundle/截图 |
| IT-06 | 权限、CAS、幂等、审计 | 维护/受限账号 | 403、409/412、幂等 replay；审计分类正确且无正文 | HTTP/DB/audit |
| IT-07 | 构建→质量→评审→发布→物化 | committed draft | 候选 pins 与 commit 一致；沿既有显式状态机 ONLINE | candidate/dispatch/physical table |
| IT-08 | 二次物化与治理身份 | IT-07 成功 | 新 attempt/observation；ModelSpec/AssetKey 不重复；血缘/质量仍可追溯 | DB/资产页 |
| IT-09 | 旧 REST/旧前端兼容 | expanded backend | `/dbt-drafts`、transition/convert 在兼容期稳定；新 UI 不调用接管/回切 | contract/network |
| IT-10 | migration preview/apply/rollback | 受控副本 | 漂移/过期/COMMITTED 正确处理；rollback 不覆盖后续修改 | migration report |
| IT-11 | Chrome95 与双视口 | 全部实现完成 | 空/加载/错误/成功/权限/PUBLISHED；console/network 无异常；visual 首屏无 Monaco | video/screenshot/network |

## 纵向 happy path

1. 使用 xiezm 登录真实平台，从模型列表进入同一模型工作台。
2. 打开 PUBLISHED 模型，确认只读并点击“创建新草稿版本”。
3. 在 visual 修改字段/依赖，切到 code 修改同一 bundle，再切回 visual，确认 dirty 未丢失。
4. 点击“保存草稿 → 校验 → 提交实现”，记录 model/implementation/dependency pins。
5. 通过既有入口创建候选，执行构建、工程测试、治理质量、评审/发布和开发/测试环境物化。
6. 检查物理表、字段、行数、candidate、pipeline、dispatch、审计、资产、血缘和质量。
7. 再次物化，确认仅新增执行历史，不重复 ModelSpec/implementation/AssetKey。

## 负向矩阵

- 无权限技术正文/写入：403；页面不得先下载正文再隐藏。
- 过期 model/implementation/draft ETag：409/412；本地未保存内容保留。
- 同 idempotencyKey 异 payload：409；相同 payload 返回旧 receipt。
- dts-dbt 不可用：save 成功，validate/commit 可重试且无半提交。
- projection 歧义/动态宏：raw node + reason，禁止猜测 rewrite。
- PUBLISHED `EDIT_DRAFT`：409 `MODEL_AUTHORING_FORK_REQUIRED`；只有 `FORK_PUBLISHED` 可写。
