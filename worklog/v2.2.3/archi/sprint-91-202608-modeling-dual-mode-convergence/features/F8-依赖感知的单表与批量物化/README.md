# F8: 依赖感知的单表与批量物化

**优先级**: P0
**状态**: CODE_COMPLETE / E2E_PENDING

## 目标

把用户的“物化当前表”解释为一个可预览、可审计的依赖计划：已在目标环境验证的精确上游可复用，缺失上游进入同一候选按拓扑顺序构建，过期/冲突上游 fail closed；单选和多选共用同一 planner、candidate 和 runtime。

## Feature 关联

| 上游 | 本 Feature | 下游 |
|---|---|---|
| F6 dependency snapshot；F7 committed implementation；Sprint-76 候选/构建/质量/发布控制面 | preview→candidate pins→topological build/reuse→observation | F5 发布验收；Sprint-93 资产、元数据、血缘、质量证据 |

## 核心语义

- `BUILD`：目标环境不存在与固定 pins 完全一致的已验证物理关系，进入候选构建。
- `REUSE`：目标环境存在同 pins、`verified=true`、`exists=true` 的关系，以只读 proxy 复用。
- `BLOCK`：依赖环、缺失实现、stale pin、环境不一致、source 不可用或 selector 冲突。
- `BUILT` 候选再次物化复用其不可变 candidate scope 并新增 execution/observation；已发布或模型修订变化时创建新 candidate。两种路径均不复制 ModelSpec、implementation 或 CatalogAssetKey。

## Task 列表

| ID | Task | 状态 | 依赖 |
|---|---|---|---|
| T01 | 建立依赖物化计划预览与 checksum 契约 | CODE_COMPLETE | F6/T01、F6/T03 |
| T02 | 统一单表与批量物化交互 | CODE_COMPLETE | T01 |
| T03 | 收敛候选执行围栏、二次物化与治理证据 | CODE_COMPLETE | T01、T02、F7/T03 |

## Definition of Ready

- [x] BUILD/REUSE/BLOCK、preview/checksum 和单表/批量共同 owner 已定义。
- [x] Sprint-76 candidate/dispatch/observation 与 Sprint-93 evidence 边界已指定。
- [x] 容量、深度、延迟、幂等与 source fence 预算已进入 `assets/nfr-budget.md`。
- [x] F6 dependency snapshot 可执行，F7 committed implementation 可作为输入。
- [ ] F0/T03 全链路样本已固定，目标环境关系基线已记录。

## 完成标准

- [x] 单表与多表请求得到同一拓扑计划；缺失上游可解释地 BUILD，精确已验证上游 REUSE。
- [x] candidate 创建时服务端重新解析并核对 plan checksum，客户端不能删改依赖条目。
- [x] 单表/批量候选均复用既有质量/发布控制面；真实四层物理结果仍待 IT-13/IT-14。
- [x] 二次物化复用不可变身份并追加执行历史；真实数量对账仍待 IT-16。

## 代码证据（2026-08-18）

- 后端：planner、selector conflict、source availability、candidate create/replacement/rematerialize 与 REST 聚焦测试通过。
- 前端：同一发布弹窗展示 BUILD/REUSE/BLOCK、checksum、单选/多选与完整候选二次物化范围；组件测试通过。
- 未执行：真实调度、物理字段/行数、治理 correlation 与 Chrome 95；统一留到 F5 集中 E2E。
