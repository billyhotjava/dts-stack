# Sprint-63: 数仓规划、数据标准与维度建模闭环

**时间**: 2026-07  
**状态**: DONE
**类型**: Warehouse Planning / Standards / Dimension Modeling / UI-first Loop
**目标**: 让客户从主题域和数仓分层规划出发，经过数据标准和数据元，形成带来源证据的维度模型草稿。

## 背景

Sprint-61 已经把数据集成、标准、建模和服务页面串成旅程，但数仓规划仍主要是工作台上的说明卡片，主题域、数据元和模型之间没有共享的规划对象。客户无法回答“这个维度模型为什么建在这里、来自哪个标准、服务哪个主题域”。

本 Sprint 采用 UI-first 规划上下文：先形成可恢复、可诊断、可测试的 session 草稿和 query 契约，后端规划实体列为后续缺口。

## Feature 列表

| ID | Feature | Task 数 | 状态 | 优先级 |
|----|---------|---------|------|--------|
| F1 | 数仓规划上下文与主题域入口 | 3 | DONE | P0 |
| F2 | 规划上下文到数据标准与字段草稿 | 3 | DONE | P0 |
| F3 | 标准草稿到维度建模候选 | 3 | DONE | P0 |
| F4 | 规划-标准-建模闭环验证 | 3 | DONE | P0 |

**统计**: READY=0, IN_PROGRESS=0, DONE=12, BLOCKED=0

## 关键决策

- **旅程白名单扩展**：`planningId/domainId/warehouseLayer/modelingMode` 纳入 `JOURNEY_CONTEXT_PARAM_KEYS`，否则旅程条"继续/返回/清参"与旅程快照会静默丢失规划上下文（设计文档 3.3，连锁改动计入 F1/T01）。
- **status 为计算态**：规划存储只存事实字段，draft/ready/blocked 由纯函数现算，防止存储态漂移成假 ready（设计文档 3.1）。
- **session 草稿为权威**：URL 与 session 关键字段不一致判 blocked，给"以草稿为准/重建"动作，不静默采信任一方。
- 维度建模使用 `warehouseLayer=DWD` + `modelingMode=dimension`，暂不新增 `DIM` 层。
- 规划上下文采用版本化 session storage，同时通过 URL 白名单参数保持页面可回跳。
- 没有后端规划持久化时显示 API 缺口，不伪装成已保存对象。
- 标准草稿是进入维度模型候选的必要前置条件。

## 完成标准

- [x] 主题域页可以创建/恢复 DWD 维度建模规划上下文。
- [x] 数据元页显示规划来源，并能生成带规划上下文的标准字段草稿。
- [x] 低代码/SQL 建模页显示规划、主题域、标准草稿和维度模式。
- [x] 缺少规划或标准时，模型候选动作阻断并给出可执行回退。
- [x] 规划上下文 session 恢复、版本失效和 storage 降级有测试覆盖。
- [x] 旅程条"继续/返回/清参"与旅程快照对四个规划参数的透传有测试覆盖（含"快照恢复但 session 草稿已失效"场景）。
- [x] 前端构建、相关契约测试和集成验证记录完整。

## 设计文档

- `docs/superpowers/specs/2026-07-11-warehouse-planning-standard-dimension-loop-design.md`

## 依赖

- Sprint-61 F2/F3：数据源、标准草稿和建模页面已有能力。
- Sprint-62 F1/F2：旅程快照、阶段真实性校验和 session 降级模式。
- Sprint-61 F9：真实浏览器登录/DNS 证据仍为外部依赖。
