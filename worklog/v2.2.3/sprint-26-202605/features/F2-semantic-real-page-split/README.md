# F2: 语义建模真实页面拆分

**优先级**: P0
**状态**: DONE
**目标**: 将 `SemanticModelingCenterPage` 从 section 巨型页拆为 layout、hooks 和独立子页面。

## 任务

| Task | 状态 | 内容 |
|------|------|------|
| T00 | DONE | 反转所有权：主实现迁入 `pages/metrics/semantic/SemanticWorkspacePage`，旧 `pages/modeling/SemanticModelingCenterPage` 改为兼容 wrapper |
| T00-1 | DONE | 拆出独立 `SemanticOverviewPage`，首页不再委托 workspace，直接读取真实语义 API 计数 |
| T01 | DONE | 提取共享语义导航 `SemanticSectionNav` 和页面元信息 |
| T02 | DONE | 取消集中式数据 hook，改为各真实页面自有主状态和 API 加载 |
| T03 | DONE | 拆出主题域映射页 |
| T04 | DONE | 拆出业务对象 Join 页 |
| T05 | DONE | 拆出指标可视化配置页 |
| T06 | DONE | 拆出 DWS/ADS 数据集页 |
| T07 | DONE | 拆出审核发布与血缘页 |
| T08 | DONE | 拆出模型运行监控页 |

## 当前状态

- `/metrics/semantic` 已是独立真实页面，负责展示语义建模流程和真实资产计数。
- `/metrics/semantic/subjects` 已是独立真实页面，负责语义主题域维护和 DWD 明细模型查看。
- `/metrics/semantic/objects` 已是独立真实页面，负责业务对象维护和 Join 画布。
- `/metrics/semantic/metrics` 已是独立真实页面，负责字段拖拽、维度保存和指标保存。
- `/metrics/semantic/models` 已是独立真实页面，负责 DWS/ADS 输出组合、模型绑定、数据预览和 dbt 生成。
- `/metrics/semantic/publish` 已是独立真实页面，负责审核、dbt 发布、BI 注册和血缘写入。
- `/metrics/semantic/runs` 已是独立真实页面，负责运行记录读取和触发运行。
- 旧 `/modeling/semantic-center*` 路径只保留兼容 wrapper，不再承载状态和 API 副作用。
- `SemanticWorkspacePage` 暂时仅用于旧建模兼容入口，后续可在删除旧路由时一并移除。
