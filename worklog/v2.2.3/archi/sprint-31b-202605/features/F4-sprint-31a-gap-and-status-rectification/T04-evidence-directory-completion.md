# T04: Sprint-31A 5 个空 evidence 目录补齐

**优先级**: P1
**状态**: DONE
**依赖**: 无

## 目标

补齐 Sprint-31A IT evidence 5 个未创建/空 README 的目录，让 Sprint-32 最终统一 IT 时可以直接 `tee` 输出，不需要现场建目录。

## 背景

Sprint-31A `it/README.md` 列出 7 个 evidence 目录，但实际只有 `final-review-test/` 与 `migration-compatibility/` 存在文件。其余 5 个：

| 场景 | 路径 | 状态 |
|---|---|---|
| 资产身份和生命周期 | `it/evidence/asset-identity/` | MISSING |
| 治理字段和缺口识别 | `it/evidence/governance-contract/` | MISSING |
| 血缘统一写入 | `it/evidence/lineage-provenance/` | MISSING |
| 权限和密级一致性 | `it/evidence/permission-classification/` | MISSING |
| 资产门户体验 | `it/evidence/asset-portal/` | MISSING |

## 技术设计

每个目录加 `README.md`：
```markdown
# {场景} Evidence

**Sprint**: 31A → 31B → 32 final IT 共用
**Owner**: {负责人 / 角色}
**触发命令**: {最终 IT 时跑哪个脚本}
**预期输出**: {pass / fail 与日志位置}
**目前状态**: PENDING (待 Sprint-32 final IT)
```

并在每个 README 链接到对应 Sprint-31A Feature README 与 Sprint-31B 收口任务。

## 影响范围

- 5 个新 `worklog/v2.2.3/sprint-31a-202605/it/evidence/*/README.md`

## 验证

- [x] 5 个 README 全部存在。
- [x] Sprint-31A `it/README.md` 中列出的 evidence 目录均已具备可写入说明。

## 完成标准

- [x] 5 个 evidence README 创建并归 owner。

## 实现记录

已补齐：

- `it/evidence/asset-identity/README.md`
- `it/evidence/governance-contract/README.md`
- `it/evidence/lineage-provenance/README.md`
- `it/evidence/permission-classification/README.md`
- `it/evidence/asset-portal/README.md`
