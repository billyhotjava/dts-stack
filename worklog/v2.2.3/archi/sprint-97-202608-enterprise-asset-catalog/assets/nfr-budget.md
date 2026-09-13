# 非功能预算

| 维度 | 预算 | 可执行检查 |
|---|---|---|
| 候选规模 | 聚合扫描上限 5000 | 超限返回 422，并提示增加关键词/家族/数据域条件 |
| 分页 | 1～200 条/页，页码上限 100000 | 单元测试边界和稳定排序 |
| 标签批量 | 复用现有 500 个资产上限；UI 选择上限 100 | service test + UI contract |
| 查询次数 | 每个资产家族固定批量查询，禁止逐资产 owner/tag/关系查询 | Mockito/JdbcTemplate 调用断言 |
| 排序 | 更新时间降序、名称和统一身份稳定兜底 | 单元测试同时间记录 |
| 权限 | 每条候选在分页前执行现有密级/部门判定 | 角色单元测试 |
| 兼容 | 未传 `assetFamily` 保持原 DATASET API | Resource test |
| 审计 | 目录查询沿用 `CATALOG_ASSET_LIST`，记录 family/returned/total | Resource test |
| 浏览器 | Chrome 95 兼容，不使用新浏览器专有 API | F5 聚焦回归 |
