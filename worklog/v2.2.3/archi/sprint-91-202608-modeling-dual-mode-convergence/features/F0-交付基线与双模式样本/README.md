# F0: 交付基线与双模式样本

**优先级**: P0
**状态**: IN_PROGRESS

## 目标

在任何新增实现前，证明维护账号、只读账号、运行实例、自动化测试和 Chrome 95 验收路径可用，并准备两类共享样本：既有双模式/接管样本，以及不依赖 ZIP 的 `ODS → DWD/DIM → DWS → ADS` 手工全链路样本。所有后续 Feature 必须复用这些样本，不得各自创建孤立测试链。

## Feature 关联

- 上游：真实运行实例、现有权限 guard、数据集成已接入且含数据的 ODS source binding。
- 下游：F1～F4 使用双模式样本；F6～F8 与 F5 强制使用 T03 的同一全链路样本和 pins。
- 约束：F0 只建立验收事实，不通过数据库脚本、ZIP 或测试专用 API 预先替业务 Feature 完成建模。

## 契约定义

| 类型 | 契约 | 关键字段/断言 |
|---|---|---|
| 运行 | platform health | HTTP 200，`status=UP` |
| 登录 | 所级数据管理员 / 部门只读或受限账号 | `xiezm` 通过现有 command guard 执行其被授权的显式生命周期动作；部门账号仅能在所属范围按 guard 读写，越界 403。前端不得因同一 actor 可执行多项 duty 而自动审批或自动发布 |
| 样本 | `E2E_S91_DESIGNER_*` ×2、`E2E_S91_DBT_*` ×1 | 原生 DESIGNER 发布与接管不得争用同一模型；记录全部 revision/checksum/ownership |
| 全链路样本 | `E2E_S91_CHAIN_*` | 复用已有含数据 ODS；创建 DWD DIM、DWD FACT、DWS SUMMARY、ADS APPLICATION；固定 `sourceRefs/dependsOn/dimensionRefs` 和全部 revision/checksum |
| 验收 | `it/baseline.md` | P1～P9 有命令、结果和阻断结论，不写“应该可用” |

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|---|---|---|---|---|
| T01 | 关闭登录、样本、测试与浏览器基线 | P0 | READY | 无 |
| T02 | 实测编译产物与 canonical bundle 可行性 | P0 | IN_PROGRESS | 无（可与 T01 并行） |
| T03 | 建立手工全链路建模验收样本 | P0 | DRAFT | T01；真实 ODS source binding |

> **T02 是当前执行 lane。** 它只关闭前向接管的 4 文件 bundle/compile 契约；回切已经移交 Sprint-92，不得由本探针重新扩回。

## Definition of Ready

- [x] 探针和关闭条件已写入 `it/baseline.md`
- [x] 当前真实模型分布已实测
- [x] 样本采用新增 E2E 模型，不修改唯一可视化 DRAFT
- [x] 验收证据位置已指定
- [x] 编译产物、4 文件 project、freeze/restore/compile 的判定标准已给出可执行判据
- [x] 手工全链路样本的对象类型、分层、依赖和清理边界已定义
- [ ] 已选定真实且含测试数据的 ODS source binding，并记录 resolved version

## 完成标准

- [ ] `it/baseline.md` P1～P9 对本 Sprint 所需路径均为 PASS 或有明确非阻断理由。
- [ ] 双模式样本、全链路样本及所级/部门权限路径均可重复使用，版本/checksum 与 actor/duty 已归档。
- [ ] 前后端 focused tests、前端 build 与浏览器 harness 至少各成功运行一次。
- [ ] 复核结论 A 与 4 文件 bundle 契约已由实测确证，并已回写 Sprint README 与 ADR-91-04。
- [ ] F6～F8 与 F5 均引用同一 `E2E_S91_CHAIN_*` 清单，没有 Feature 私有样本或数据库后门造数。
