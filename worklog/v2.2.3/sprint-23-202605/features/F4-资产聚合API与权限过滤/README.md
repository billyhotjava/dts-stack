# F4: 资产聚合 API 与权限过滤

**优先级**: P0  
**状态**: DONE

## 目标

提供面向前端的统一资产 API：主数据来自 OM cache，治理属性来自 DTS extension，权限过滤和审计继续由 DTS 控制。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | 资产聚合列表 API | P0 | DONE | F1,F2 |
| T02 | 资产详情聚合 API | P0 | DONE | T01 |
| T03 | 权限过滤、部门可见性和 opadmin bypass | P0 | DONE | T01 |
| T04 | 搜索、筛选、排序和待治理过滤 | P0 | DONE | T01 |
| T05 | OpenMetadata cache 状态与 fallback reason 输出 | P1 | DONE | T02 |

## 完成标准

- [x] API 返回技术资产、治理扩展、同步状态和映射状态。
- [x] 权限过滤不会因 OM 资产进入门户而绕过 DTS 控制。
- [x] 支持按 keyword、domain、classification、ownerDept、source、governanceStatus 查询。
- [x] 支持待治理资产列表。
- [x] API 能说明数据来自 OM cache、DTS extension 或 legacy fallback。
