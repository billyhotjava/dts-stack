# Sprint-63 闭环契约

```text
SubjectAreasPage
  -- planningId/domainId/warehouseLayer=DWD/modelingMode=dimension -->
ElementsPage
  -- standardDraftId + planning context -->
LowCodeDevelopmentPage / SqlModelingPage
  -- dimension candidate + SQL micro-adjustment -->
model draft with planning and standard provenance
```

## 约束

- `DWD + dimension` 是本 Sprint 的维度建模表达。
- `planningId` 是 session 草稿索引，不代表后端已经落库。
- `standardDraftId` 是生成维度模型候选的必要输入。
- 所有页面都必须保留 `domainId` 和 `warehouseLayer`。
- 四个规划参数已纳入旅程白名单：旅程条继续/返回/清参与旅程快照必须透传，不得静默丢弃。
