# Sprint-11：专题模板与动态数据绑定中心

## 背景

当前项目管理专题已经证明一件事：`Excel/CSV -> ODS -> dbt -> 专题看板` 这条链能跑通，但“专题数据源绑定”还停留在专题特例阶段。

现状问题有三类：

1. **专题与场景是动态的** — 客户可能接入项目管理，也可能只接入 `PLM`、`QMS` 等别的业务域
2. **现场 ODS 表名是动态的** — 入湖时物理表名由现场填写，不能在 dbt 模型里硬编码
3. **绑定机制不平台化** — 当前更像“项目管理专题专用 vars”，不适合作为 DTS 中台长期方案

本 Sprint 的目标是把“项目管理专题特例”升级成“专题模板 + 数据绑定中心 + 运行时 source/vars 编译”的平台能力，让 DTS 能支持多个动态专题，而不是继续叠加专题特例。

## 目标

1. 建立统一的专题模板定义机制，而不是每个专题各自维护一套散乱 vars
2. 建立环境级全局数据绑定中心，把“逻辑实体 -> 真实 ODS 表/视图”绑定正式化
3. 在 dbt 运行前按绑定关系动态编译 `sources/vars`，让模型只依赖稳定逻辑实体
4. 让导入/入湖/建模/发布/专题消费围绕同一套绑定关系工作
5. 保持项目管理专题可继续运行，同时为 `PLM` 等后续专题预留扩展路径

## 范围

### 后端
- `source/dts-platform` — 专题模板、绑定中心、入湖后绑定动作、建模/发布校验
- `source/dts-analytics` — 专题绑定状态读取、专题消费前置校验

### 前端
- `source/dts-platform-webapp/src/pages/modeling/`
- `source/dts-platform-webapp/src/pages/foundation/`
- 必要的专题绑定管理页面与上传后绑定交互

### dbt 工作区
- `services/dts-dbt/` — 动态 source 模板、运行时 vars/sources 编译、专题模板示例

### 文档与验证
- `worklog/v2.2.1/sprint-11/`

## 设计文档

- [design.md](/opt/prod/s10/s10-stack/worklog/v2.2.1/sprint-11/design.md)
- [plan.md](/opt/prod/s10/s10-stack/worklog/v2.2.1/sprint-11/plan.md)

## 关键假设

- 第一阶段按**环境级全局绑定**实现，不按项目空间/工作区隔离
- 第一批专题模板至少覆盖：
  - `project-management`
  - `plm-overview`
- 现场真实 ODS 表名继续允许自由命名，但必须通过绑定中心挂到稳定逻辑实体上
- dbt 模型不再允许直接写现场物理 ODS 表名

## Task 列表

### 批次一：专题模板与绑定基础设施（BE-001 ~ BE-003）

| ID | 标题 | 类型 | 预估 | 状态 |
|----|------|------|------|------|
| BE-001 | 专题模板/逻辑实体/绑定关系领域模型与表结构 | 后端 | 1.5天 | DONE |
| BE-002 | 专题模板与绑定中心 API | 后端 | 1.5天 | DONE |
| BE-003 | 默认专题模板初始化与示例模板种子 | 后端 | 1天 | DONE |

### 批次二：入湖后绑定与运行时编译（BE-004 ~ BE-006）

| ID | 标题 | 类型 | 预估 | 状态 |
|----|------|------|------|------|
| BE-004 | Excel/ODS 成功后绑定到专题逻辑实体 | 后端 | 1.5天 | DONE |
| BE-005 | 运行时编译专题 sources/vars | 后端 | 2天 | DONE |
| BE-006 | 建模/发布门禁增加专题绑定校验 | 后端 | 1天 | DONE |

### 批次三：专题消费与界面（FE-001 ~ FE-003, BE-007）

| ID | 标题 | 类型 | 预估 | 状态 |
|----|------|------|------|------|
| FE-001 | 专题绑定中心页面 | 前端 | 2天 | DONE |
| FE-002 | 入湖成功后的“绑定到专题”交互 | 前端 | 1天 | DONE |
| FE-003 | 逻辑建模/发布页专题绑定诊断与提示 | 前端 | 1天 | DONE |
| BE-007 | analytics 专题读取绑定状态与空态治理 | 后端 | 1.5天 | DONE |

### 批次四：验证与收口（QA-001）

| ID | 标题 | 类型 | 预估 | 状态 |
|----|------|------|------|------|
| QA-001 | 专题模板到专题消费的端到端验证 | 验证 | 1.5天 | DONE |

## 本 Sprint 不做

- 不实现按项目空间/工作区隔离的绑定范围
- 不实现多版本绑定并行发布
- 不做完整主数据平台接入，只保留接口占位与绑定中心
- 不做可视化拖拽式专题模板设计器
- 不在本轮把所有历史专题都迁移到新机制，先覆盖项目管理和 `PLM` 示例

## 集成测试

`it/` 目录存放验证说明，覆盖：

- 主题模板初始化
- Excel 入湖后绑定到逻辑实体
- dbt 编译前自动生成专题 source/vars
- 发布门禁对缺失绑定的阻断
- 项目管理专题读取正式绑定后的 ODS 数据
- `PLM` 示例模板的最小绑定与编译验证

## 状态跟踪

各 Task 进度在本文件的 Task 列表中更新状态标记：

- `TODO` = 未开始
- `WIP` = 进行中
- `DONE` = 已完成
- `BLOCK` = 阻塞

## 本轮落地结果

- 新增专题模板、逻辑实体、绑定关系表结构与默认模板初始化，覆盖 `project-management` 和 `plm-overview`
- 新增专题绑定中心页面，支持查看模板、诊断缺失绑定、对逻辑实体执行绑定/换绑
- 项目主体域导入页在“正式落库”后支持直接打开绑定弹窗，把当前批次绑定到专题逻辑实体
- 逻辑建模页和提交上线弹窗展示专题绑定诊断，缺失必填绑定时由发布门禁阻断
- dbt 运行前自动编译专题 `sources/vars`，analytics 项目看板口径支撑显示当前专题绑定来源

## 本轮验证

- `cd source/dts-platform && mvn -Dtest=TopicBindingServiceIT,TopicBindingRuntimeServiceTest,TopicBindingResourceIT test`
- `cd source/dts-platform && mvn -Dtest=DbtReleaseGateServiceTest,DbtSourceServiceTest,EtlResourceTest test`
- `cd source/dts-platform && mvn -Dtest=TopicBindingResourceIT test`
- `cd source/dts-analytics && mvn -Dtest=ProjectCockpitResourceIT test`
- `cd source/dts-platform-webapp && pnpm exec tsx --test src/pages/foundation/topicBindingCenter.helpers.test.ts src/pages/foundation/projectCockpitImportBinding.helpers.test.ts`
- `cd source/dts-platform-webapp && pnpm build`
