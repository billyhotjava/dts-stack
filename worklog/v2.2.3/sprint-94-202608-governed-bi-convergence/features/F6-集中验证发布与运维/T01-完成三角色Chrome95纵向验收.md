# T01：完成三角色 Chrome 95 纵向验收

**优先级**：P0
**状态**：BLOCKED
**阻断**：Sprint-94 当前登录/API 尚未复验；Chrome 95、A1～A4 真实账号/权限和目标环境画像未就绪（见 `../../it/baseline.md` P3、F0/T02、F0/T03）
**依赖**：F0/T02、F0/T03、F1～F4 全部完成

## 用户可测试目标

在真实构建与真实身份下，用 Chrome 95 一次完成 `../../it/README.md` 的数据集→分析→看板→受众消费纵向旅程；A3 正向、A4 负向，页面、API、数据库、审计和依赖版本结论一致。大屏不改造，但必须完成升级前后历史连续性对账。

## 前置门禁

- 变更集冻结，所有聚焦单元/契约/集成测试和模块构建通过。
- A1 维护、A2 独立发布、A3 授权消费、A4 非授权负向的职责/部门/角色/密级已核验。
- DS-PUBLISHED/STALE/DENIED、治理 Analysis/Dashboard/受众 fixture 与旧 BI cleanup cutoff 就绪；不再要求构造 legacy 三分类迁移样本。
- Chrome 95 版本、时区、分辨率、网络代理和构建 commit 固化。
- 不在 E2E 过程中修复代码；发现问题记录断点，完成修复后只针对断点重跑，再重跑唯一纵向 journey。

## 验收执行

1. 执行 IT-01～IT-09 的 API/数据库预检，以及 IT-10 大屏连续性和 IT-11 Contract 就绪检查。
2. 按 `../../it/README.md` §3 运行唯一 Chrome 95 journey。
3. 逐页面检查 loading/empty/error/success、键盘/焦点、布局 overflow、业务文案。
4. 记录每次 save/validate/publish/query/export 的 correlationId，并与 revision、registration、audit 对账。
5. 触发 401/403/409/422/429/502/504；确认无 SQL/stack/敏感值泄漏。
6. 用现代 Chrome 仅作差异诊断；结论单列，不覆盖 Chrome 95。

## RED → GREEN

- RED：当前只有 SPA shell 200，无登录/Chrome95/角色/BI fixture 证据。
- GREEN：A1 保存草稿、A2 独立发布、A3 消费/导出、A4 列表不可见+直链/export 403；数据集 v2 不使已发布 v1 漂移；冻结路由回访行为不变。
- GREEN：console 0 未解释 error，Network 0 未解释 4xx/5xx，所有业务错误有稳定 UI 与 audit。

## 影响范围与证据

按 `it/evidence/<timestamp>/` 保存脱敏环境、命令、API、DB 计数/状态、截图、Network/console/trace。不得保存凭据、业务行值或 raw SQL。此 Task 不授权部署生产或发送外部消息。

## Definition of Done

- [ ] IT-01～IT-11 与唯一纵向 E2E 全部通过，证据非占位。
- [ ] R1 未执行 R2 DELETE；大屏 durable set 和 cleanup dry-run 对账通过。
- [ ] A1/A2/A3 职责分离；A4 三类负向均由后端 fail-closed。
- [ ] Chrome 95 四态、键盘、焦点、布局、console/Network 通过。
- [ ] API/revision/registration/dataset checksum/audit 可按 correlationId 串联。
- [ ] 失败后的重跑范围和最终通过 commit/image 完全一致。
