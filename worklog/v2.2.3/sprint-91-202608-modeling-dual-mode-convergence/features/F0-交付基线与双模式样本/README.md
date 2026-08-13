# F0: 交付基线与双模式样本

**优先级**: P0
**状态**: IN_PROGRESS

## 目标

在任何 UI 改造前，证明维护账号、只读账号、运行实例、自动化测试和 Chrome 95 验收路径可用，并准备不会破坏现有模型的双模式样本。

## 契约定义

| 类型 | 契约 | 关键字段/断言 |
|---|---|---|
| 运行 | platform health | HTTP 200，`status=UP` |
| 登录 | 维护 / 只读 / 独立评审 / 发布操作账号 | 四个不同 actor；分别具备 `CATALOG_MAINTAINERS`、只读、`RELEASE_REVIEWER`、`RELEASE_OPERATOR` 所需 authority/duty |
| 样本 | `E2E_S91_DESIGNER_*` ×2、`E2E_S91_DBT_*` ×1 | 原生 DESIGNER 发布与接管不得争用同一模型；记录全部 revision/checksum/ownership |
| 验收 | `it/baseline.md` | P1～P9 有命令、结果和阻断结论，不写“应该可用” |

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|---|---|---|---|---|
| T01 | 关闭登录、样本、测试与浏览器基线 | P0 | READY | 无 |
| T02 | 实测编译产物与 canonical bundle 可行性 | P0 | IN_PROGRESS | 无（可与 T01 并行） |

> **T02 是当前执行 lane。** 它只关闭前向接管的 4 文件 bundle/compile 契约；回切已经移交 Sprint-92，不得由本探针重新扩回。

## Definition of Ready

- [x] 探针和关闭条件已写入 `it/baseline.md`
- [x] 当前真实模型分布已实测
- [x] 样本采用新增 E2E 模型，不修改唯一可视化 DRAFT
- [x] 验收证据位置已指定
- [x] 编译产物、4 文件 project、freeze/restore/compile 的判定标准已给出可执行判据

## 完成标准

- [ ] `it/baseline.md` P1～P9 对本 Sprint 所需路径均为 PASS 或有明确非阻断理由。
- [ ] 三个模型样本和四类独立账号均可重复使用，版本/checksum 与 duty 已归档。
- [ ] 前后端 focused tests、前端 build 与浏览器 harness 至少各成功运行一次。
- [ ] 复核结论 A 与 4 文件 bundle 契约已由实测确证，并已回写 Sprint README 与 ADR-91-04。
