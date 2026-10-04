# F6: 数据源增强

**优先级**: P1
**状态**: READY

## 目标
增强数据源系统：静态数据可编辑、缓存淘汰、修复关键 bug

## 现状分析

### 6 种数据源类型及来源

| 类型 | 来源 | 说明 |
|------|------|------|
| **static** | 组件 config 中的静态 JSON | 无数据获取，使用组件配置中的硬编码数据。当前 **不可在编辑器中编辑** |
| **card** | BI 平台的 Card 查询（analyticsApi.queryCard） | 引用平台内已保存的查询卡片（问题），通过 cardId 关联 |
| **api** | 外部 HTTP REST 接口 | 支持 GET/POST，URL/Header/Body 支持 `{{变量}}` 模板插值 |
| **sql** | 直连数据库执行 SQL（analyticsApi.runDatasetQuery） | 通过 databaseId 关联已注册的数据库连接，支持参数绑定 |
| **dataset** | BI 平台的 Dataset 查询体（analyticsApi.runDatasetQuery） | 传入完整的 queryBody 对象（Metabase 格式） |
| **metric** | 语义指标层（analyticsApi.queryCard + metricId） | 通过 cardId + metricId + metricVersion 绑定已定义的语义指标 |

### 数据获取链路
```
编辑器配置 → DataLayer.tsx (参数绑定+变量解析)
  → useCardDataSource.ts (缓存检查+去重+调度)
    → queryScheduler.ts (并发控制+超时+重试)
      → analyticsApi 或 fetch (实际网络请求)
    → toCardData() (响应归一化为 rows/cols)
  → cardDataMapper.ts / fieldMappingTransform.ts (映射到组件 config)
→ effectiveConfig (传入渲染器)
```

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | 静态数据源编辑器 | P1 | READY | - |
| T02 | 缓存 LRU 淘汰 + 过期清理 | P1 | READY | - |
| T03 | responsePath 错误提示 | P2 | READY | - |

## 完成标准
- [ ] 静态数据源支持在属性面板中编辑 JSON（表格/数组格式）
- [ ] 缓存有上限（最多 200 条），过期条目定期清理
- [ ] responsePath 提取失败时显示警告而非静默返回空
