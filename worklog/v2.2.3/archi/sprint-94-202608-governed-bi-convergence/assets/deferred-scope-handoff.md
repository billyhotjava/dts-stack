# Sprint-94 后续范围交接

**修订日期**：2026-08-19
**状态**：2026-08-17 的 S1～S4 兼容迁移方案已被历史边界决策取代。当前唯一权威退役路线是 `metabase-retirement-roadmap.md`。

## 1. 已进入活跃任务

| 能力 | owner | 说明 |
|---|---|---|
| 大屏历史保留与 Card 引用预检 | F0/T04 | screen/version/access/template/audit/assets/menu bindings 是 durable set |
| 旧 BI 数据与路由 Contract | F6/T03 | 不迁移 Card/Dashboard/VDS/MBQL；cutoff + backup restore + dry-run 后清理 |
| DWD/DWS/ADS 重建 | 数据建模发布流程 | ModelSpec/dbt owner；不由 Analytics 清理脚本跨域 DROP |

## 2. 仍可后续立项的能力

未来若业务需要让大屏复用已发布 Analysis，可新建独立 Feature，契约建议如下：

```text
ScreenAnalysisSource {
  kind: "analysis",
  analysisId: long,
  revisionId: long,
  parameters: object
}
```

- 只能选择有 `read` 且 PUBLISHED 的 Analysis revision。
- Screen published version 钉定 `revisionId + datasetVersion + contractChecksum`，不得静默切 latest。
- 运行复用 `AnalysisQueryGateway`；Screen 不复制 SQL、QuerySpec 或权限规则。
- 沿用 `analytics_screen_version`，不得另建 Screen 生命周期。
- 该能力不是旧 Card 迁移义务；只在明确业务价值和独立验收环境就绪后启动。

## 3. 明确取消

- 不再建设 `convertible / legacy-read-only / invalid` 三分类迁移批次。
- 不再等待 14/30 天旧路由调用量才决定是否保留旧 BI 数据。
- 不自动把 MBQL/VDS 近似转换为 Analysis。
- 不为旧 Collection/Model/Trash/Pulse/Subscription 建兼容控制面。

## 4. 后续 DoR

- [ ] 独立目标、业务 owner、真实验收用户和 Chrome 95 环境齐备。
- [ ] 不影响 F0/T04 定义的大屏 durable set。
- [ ] 每个 Screen/gateway/editor 符号重新执行当期 impact；HIGH/CRITICAL 先报告再编码。
- [ ] Expand、Contract 和数据恢复保持分离，不复用 Sprint-94 的旧假设。
