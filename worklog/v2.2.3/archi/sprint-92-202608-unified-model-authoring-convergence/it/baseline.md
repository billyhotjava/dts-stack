# 交付基线探针结果（Gate G0）

**探针日期**：2026-08-19  
**环境**：v2.2.3 当前工作区与近期 Docker/浏览器证据  
**结论**：PASS_WITH_GAPS——源码、构建命令、既有草稿/发布 schema 和现代 Chrome 验收路径已有证据；当前运行态、真实数据画像、统一草稿隔离样本和 Chrome 95 必须由 F0 刷新。

> 本文不把 Sprint-91/93 的历史 PASS 冒充本 Sprint 的运行态 PASS。未在本轮重跑的探针明确标为 GAP。

| # | 探针 | 结果 | 当前证据 | 阻断/责任 |
|---|---|---|---|---|
| P1 | 可运行实例 | GAP | Sprint-91 曾验证 platform/webapp/Keycloak/dbt/PG 运行；本轮未重新探测 | F0/T01 |
| P2 | 真实登录/权限 | PASS_WITH_GAPS | Sprint-93 已有 xiezm 真实登录和受保护 API 证据；本 Sprint authoring route 尚不存在 | F0/T01、F5/T02 |
| P3 | schema | PASS_WITH_GAPS | model spec/implementation/draft/artifact/candidate 既有 schema 已知；Expand changeSet 尚未实现 | F1/T01 |
| P4 | 代表性数据 | GAP | 历史 31 ModelSpec、28 DBT/3 DESIGNER；无三类隔离 projection 样本 | F0/T01、F0/T02 |
| P5 | API harness | PASS_WITH_GAPS | modeling MockMvc/Testcontainers 路径已有；新 facade 待 RED contract test | F1/T02 |
| P6 | UI harness | PASS_WITH_GAPS | 现代 Chrome/截图路径已有；Chrome95 和新 UI 未验证 | F5/T02 |
| P7 | build/test 命令 | PASS | `dts-platform` focused tests；webapp Vitest/tsc/`pnpm build` 已在 Sprint-91 运行 | - |
| P8 | 外部依赖 | PASS_WITH_GAPS | PG/Keycloak/dts-dbt 是既有依赖；故障注入待 F4/F5 | F4/T01、F5/T02 |

## F0 关闭条件

1. 只读记录当前容器/health、schema revision、真实账号访问和当前模型/draft 分布。
2. 新建或选取三个可回收隔离样本：平台生成、手工代码、ZIP 导入；不修改生产/现有业务模型。
3. 对三类 bundle 只读 inspect，形成 FULL/PARTIAL/NONE 与 lossless 基线；证据不包含 SQL 正文。
4. 确认测试命令、现代浏览器和 Chrome95 harness；Chrome95 可在最终集中执行，但运行路径必须在编码前可用。

## 本 Sprint 验收路径

- 后端：MockMvc + service unit + PostgreSQL/Testcontainers failure injection。
- 前端：source-contract + Vitest + TypeScript + legacy production build。
- 浏览器：真实登录 → 模型工作台 → PUBLISHED fork → visual/code 交替编辑 → 保存/校验/提交 → 发布/物化/二次物化 → Sprint-93 治理证据。
- 证据：只落 `it/`；Feature 未全部实现前不执行集中 E2E。

