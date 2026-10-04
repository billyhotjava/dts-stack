# F5 发布门禁接入标准校验

**状态**: DONE  
**目标**: 发布前能够检查字段缺失映射、码表字段缺标准编码、标准版本漂移。

## Tasks

| Task | 内容 | 状态 | 代码/证据 |
|------|------|------|-----------|
| T01 | 后端提供标准门禁检查接口 | DONE | `POST /api/modeling/sql-models/{id}/standard-gate/check` |
| T02 | 门禁返回 blockers、warnings、mapped/missing 统计 | DONE | `SqlModelStandardGateResult` |
| T03 | SQL 建模页展示标准门禁结果 | DONE | `SqlModelingPage.tsx` |
| T04 | 生成 schema.yml 按钮依赖至少一个字段已绑定 | DONE | disabled state in `SqlModelingPage.tsx` |

## 验收

- source-contract 覆盖 `checkSqlModelStandardGate`、`标准门禁`。
