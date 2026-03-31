# T02: apiClient 全局超时调整

**模块**: Platform 前端
**文件**: `src/api/apiClient.ts`

## 问题

全局 axios timeout 从 50s 降到 15s。信创/离线环境硬件较慢，ingestion/catalog/governance 操作可能超 15s。

## 修复方案

恢复到合理默认值（30s），或区分场景设置不同超时：
- 常规 API: 15s
- 数据类操作（ingestion/catalog）: 60s
- modeling/dbt: 已有 `withModelingRequestTimeout` 覆盖
