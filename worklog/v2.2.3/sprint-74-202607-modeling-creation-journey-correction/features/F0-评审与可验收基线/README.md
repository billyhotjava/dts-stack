# F0：评审与可验收基线

**优先级**：P0  
**状态**：DONE

## 目标

在任何运行时代码开始前，确认 Sprint-74 架构获批，并让真实认证 API、Chrome95 和代表性数据具备可重复验收条件。

## 契约定义

| 类型 | 契约 | 关键内容 |
|---|---|---|
| 架构 | `assets/architecture-review.md` | 二次复审后的四条冻结决定全部确认 |
| 环境 | `it/baseline.md` | P1～P8 有真实命令和结果 |
| 部署 | 当前提交对应镜像与 `databasechangelog` | Sprint-73 前置迁移已落地，GitNexus 与 HEAD 一致 |
| 数据 | 隔离验收计划 | 四类模型、两种 implementation ownership、至少一条发布结果 |
| 证据 | `it/` | 真实认证，不用 mock 关闭 Sprint |

## UI/UX 规格

本 Feature 不新增产品 UI。验收入口复用 `/modeling/models` 和模型详情三阶段页面。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|---|---|---|---|---|
| T01 | 冻结架构、部署与认证验收基线 | P0 | DONE | 2026-07-27 架构复审已通过 |
| T02 | 建立四类模型代表性验收数据 | P0 | DONE | T01 |

## Definition of Ready

- [x] Sprint-73 已提交的对象所有权冲突和兼容迁移边界已定位
- [x] DNS/login/API/数据缺口已记录
- [x] 用户确认修订后的 `assets/architecture-review.md` 四条决定
- [x] GitNexus、运行镜像、数据库迁移与当前 HEAD 对齐
- [x] Sprint-74 owning files、排除文件与基线 commit 已冻结

## 完成标准

- [x] G0 交付基线由 BLOCKED 变为 PASS
- [x] 当前提交对应镜像与 Sprint-73 前置迁移可验证
- [x] 代表性 fixture 使用隔离计划和幂等键，可重复创建；授权清理步骤已写入 runbook
- [x] 证据中不包含凭据
