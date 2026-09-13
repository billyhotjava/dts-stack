# F6: 迁移、兼容和验收闭环

**优先级**: P0
**状态**: DONE

## 目标

确保 Sprint-31A 的资产事实源改造可迁移、可回滚、可验收，并为 Sprint-31 和 Sprint-32 提供明确的依赖边界。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | 历史 Catalog 迁移 dry-run | P0 | DONE | F1-F4 |
| T02 | semantic/metric 历史映射报告 | P0 | DONE | F1-F4 |
| T03 | API 兼容和弃用矩阵 | P0 | DONE | F2-F4 |
| T04 | Sprint-31/32 依赖重排 | P0 | DONE | T01-T03 |
| T05 | 最终统一 review/test 协议 | P0 | DONE | T04 |

## 完成标准

- [x] 有迁移 dry-run，不默认破坏历史数据。
- [x] dts-metrics 所需的 platform API 和迁移输入明确。
- [x] 最终统一测试清单覆盖 Sprint-31A、31、32。

## 交付说明

- 新增 `GET /api/catalog/assets-v2/migration/dry-run`。
- 新增 semantic -> metrics 历史映射报告。
- 新增 API 兼容和弃用矩阵。
- Sprint-31 / Sprint-32 依赖已重排到 Sprint-31A 资产事实源之后。
- 最终 review/test 协议已固化。
