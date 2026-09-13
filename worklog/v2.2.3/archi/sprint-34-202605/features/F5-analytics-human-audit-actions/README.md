# F5: dts-analytics 人工操作审计动作收敛

**优先级**: P0
**状态**: DONE

## 目标

将 dts-analytics 的大屏、语义层和 fallback HTTP 审计接入同一套 DB catalog 分类机制，避免 BI 操作落成管理端审计或 platform generic。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | analytics actionCode 生成与转发 | P0 | DONE | F1, F2 |
| T02 | analytics DB catalog seed 与未分类治理 | P0 | DONE | T01 |
| T03 | 审计中心 analytics 展示映射 | P0 | DONE | T02 |
| T04 | focused 验证与证据补充 | P0 | DONE | T01-T03 |

## 完成标准

- [x] dts-analytics fallback filter 生成稳定 actionCode，而不是只传中文 action。
- [x] analytics forwarder 将 actionCode 写入 `buttonCode`/`operationCode`，供 dts-admin DB catalog 解析。
- [x] 大屏、语义层、仪表板、数据源同步、报告工厂导出等首批人工操作写入 analytics catalog。
- [x] analytics 未注册 actionCode 落入 `analytics.unclassified` 并记录 miss。
- [x] 审计中心将 `sourceSystem=analytics` 显示为 BI 分析/分析端审计。
