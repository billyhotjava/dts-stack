# Sprint-59 编码指导

## 推荐实施顺序

1. **先补 source-contract**  
   为菜单、路由、向导文案、低代码入口、指标联动状态写失败测试，证明当前数据开发入口仍偏 SQL/脚本。

2. **收敛入口，不复制页面**  
   在数据开发菜单新增或调整低代码入口。页面实现为轻量编排页，跳转到已有数据源、资产、指标、发布审核和任务运维页面。

3. **实现向导状态模型**  
   前端先定义统一的 step model：数据准备、业务对象确认、指标设计、DWS/ADS 生成、发布审核、运行证据。状态来自现有 API 或可解释的前端聚合，不虚构已完成能力。

4. **接入指标上下文**  
   指标工作台入口要支持从向导携带上下文，例如业务对象、数据集、推荐指标、目标消费场景。先用 query/context contract 固化，不急于增加后端复杂接口。

5. **补治理和高级模式护栏**  
   在 DWD、DWS/ADS、发布审核节点明确“普通用户能做什么”和“需要工程师处理什么”。高级 SQL/dbt 入口以二级动作出现，不作为主流程必需。

6. **浏览器验证闭环**  
   最后用桌面和移动宽度跑 smoke：入口可见、步骤不遮挡、按钮可达、文案不暴露 SQL/dbt、跳转能回到现有页面。

## 预期改动文件

| 类型 | 可能文件 | 目的 |
|------|----------|------|
| 菜单 seed | `source/dts-admin/src/main/resources/config/data/portal-menu-seed.json` | 增加低代码开发入口或调整数据开发顺序 |
| 角色默认项 | `source/dts-admin/src/main/resources/config/data/role-menu-defaults.json` | 如新增菜单，需要对默认可见性做显式决策 |
| 菜单测试 | `source/dts-admin/src/test/java/com/yuzhi/dts/admin/service/PortalMenuSeedDefaultsContractTest.java` | 固化菜单 seed 和默认角色契约 |
| 路由 | `source/dts-platform-webapp/src/routes/sections/dashboard/static-routes.tsx`、`dynamic-resolver.tsx` | 注册低代码向导页面 |
| 页面 | `source/dts-platform-webapp/src/pages/studio/*` 或现有数据开发目录 | 实现低代码编排页 |
| 工作台 | `source/dts-platform-webapp/src/pages/workbench/*` | 保留首张报表旅程并与数据开发入口互相导流 |
| 指标 | `source/dts-platform-webapp/src/pages/modeling/*` | 接收低代码上下文，避免指标工作台割裂 |
| 契约测试 | `source/dts-platform-webapp/src/pages/**/*source-contract.test.ts` | 固化 no-SQL 文案、路由和入口关系 |

## 页面文案约束

- 主流程用“业务表、字段含义、业务对象、指标、维度、统计周期、报表数据集、发布审核”。
- 默认不使用“ODS、DWD、DWS、ADS、dbt、SQL、DAG”作为普通用户必须理解的词。
- 必须出现技术词时，放入高级说明、开发者详情、运行证据或审核明细。
- 按钮不写“生成 SQL”，优先写“生成汇总模型草案”“生成报表数据集”“提交发布审核”。

## 状态模型建议

| Step | 状态 | 下一步动作 |
|------|------|------------|
| data_ready | 已有业务表 / 待接入 / 接入失败 | 选择数据表、发起接入、查看接入任务 |
| object_confirmed | 已确认 / 待补字段 / 待工程审核 | 确认字段、补负责人、进入资产详情 |
| metric_designed | 已定义 / 待定义 / 口径冲突 | 进入指标设计、保存指标草案 |
| model_candidate | 可生成 / 待补粒度 / 需高级建模 | 生成 DWS/ADS 候选、进入 SQL 高级模式 |
| publish_ready | 可提交 / 门禁阻断 / 待审批 | 提交发布审核、查看阻断原因 |
| run_evidence | 已运行 / 未调度 / 失败 | 查看任务运维、重跑或补数 |

## 代码质量护栏

- 不把低代码向导写成一个超大页面；步骤配置、状态归一化、跳转构造应拆 helper 并单测。
- 不在前端硬编码“已完成”假状态；没有 API 时展示“待接入/待审核/暂未接通”，并提供真实跳转。
- 不让 DWS/ADS 生成绕过现有发布审核。
- 不新增平行指标模型概念；指标草案必须能进入现有指标管理或指标工作台。
- 不回退 Sprint-54/56 的指标工作台产品化能力。

## 验证命令建议

```bash
cd source/dts-platform-webapp
node --test src/pages/**/**source-contract.test.ts
pnpm exec tsc --noEmit
pnpm build

cd ../dts-admin
./mvnw -Dspotless.apply.skip=true -Dspotless.check.skip=true -Dspring-boot.build-info.skip=true -Dtest=PortalMenuSeedDefaultsContractTest test
```

浏览器 smoke 需要覆盖：

- `/studio/low-code-development` 或最终低代码入口路由。
- `/workbench?section=data-management&journey=first-report`。
- 指标工作台上下文进入路径。
- 发布审核或模型管理跳转路径。

