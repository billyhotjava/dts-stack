# Sprint-66 最小通用下钻设计

**状态**：APPROVED
**批准依据**：产品确认 DTS 是通用通道，下钻不得依赖或绑定业务领域模型。
**实现范围**：`source/dts-platform-webapp` 大屏设计器与预览运行时。

## 1. 能力定义

DTS 不解释字段的业务含义，只执行以下稳定协议：

```text
ClickPayload + Mapping + TargetDataSource/Action
    → QueryParameters/RuntimeVariables
    → Render/Navigation
    → History
```

数据提供方负责查询和字段，设计人员负责选择映射，DTS 负责安全传递、执行和恢复状态。

## 2. 协议复用

不新增 `BusinessObject`、`Hierarchy` 或 `RelationPath`。扩展现有 `DrillLevel`，复用现有类型：

```ts
export interface DrillLevel {
    label: string;
    dataSource?: DataSourceConfig;
    mappings?: ComponentInteractionMapping[];
    inheritContext?: boolean;

    // Legacy read compatibility only.
    cardId?: number;
    paramName?: string;
}
```

语义固定如下：

- `sourcePath`：点击载荷中的取值路径，例如 `name`、`data.id`、`row[0]`。
- `variableKey`：目标查询参数或运行变量名称。
- `dataSource`：下一层完整数据源配置，可为 SQL、API、Card、Dataset、Metric。
- `inheritContext`：默认 `true`，决定是否合并父层参数。
- `cardId/paramName`：只用于历史配置读取兼容，不再作为新配置的主协议。

## 3. 运行时状态

新增纯函数模块 `drillRuntime.ts`，把 React hook 中的业务计算拆出：

```ts
export interface DrillEntry {
    label: string;
    parameters: Record<string, string>;
}

export interface GenericDrillSnapshot {
    effectiveDataSource: DataSourceConfig | undefined;
    queryParameters: Array<{ name: string; value: string }>;
    breadcrumbs: Array<{ label: string; depth: number }>;
    canDrillDown: boolean;
}
```

纯函数职责：

1. 将旧 `cardId/paramName` 归一化为通用层级；
2. 根据当前点击载荷和 mappings 生成白名单参数；
3. 合并或隔离父级参数；
4. 计算当前有效数据源、面包屑和是否可继续下钻；
5. 上卷时裁剪 stack 并恢复对应数据源和参数。

禁止在纯函数中出现 `项目`、`工单` 等字段兜底。无映射的新配置不执行下钻；只有旧配置可使用 `name → data.name → row[0]` 的兼容取值顺序。

## 4. 动作执行

继续使用现有动作类型，不新增平行协议：

| 动作 | 通用行为 |
|---|---|
| `set-variable` | 把映射值写入运行变量，触发组件联动 |
| `drill-down` | 使用点击载荷推进当前组件的下钻层级 |
| `drill-up` | 回到上一层并恢复参数 |
| `drill-view` | 使用映射值进入当前大屏的目标视图 |
| `open-panel` | 使用模板和映射值打开详情面板 |
| `jump-url` | 使用模板打开内部或外部地址 |
| `emit-intent` | 保留现有意图事件能力 |

`InteractionLayer` 负责统一执行；`DataLayer` 只消费 `effectiveDataSource + queryParameters`，不得按领域分支。

## 5. 设计器

在现有“交互/行为”配置中完成，不新增菜单或独立工作台：

1. 任何可点击且配置了受支持数据源的组件都可启用下钻；
2. 每层选择下一层数据源，字段由现有数据源配置能力提供；
3. 每层配置一组“取值路径 → 目标参数”映射；
4. 可选择是否继承父层上下文；
5. 页面/视图、面板和 URL 动作继续使用现有动作入口；
6. UI 使用“来源字段、目标参数、下一层数据源”等通用文案。

## 6. 失败行为

- 来源路径不存在：本次动作取消并写运行事件，不传空值污染查询。
- 目标参数为空：ScreenConfig 校验失败，不能发布。
- 下一层数据源无效：保留当前层，不清空当前图表。
- 查询失败：沿用数据源现有错误态，面包屑和返回能力仍可用。
- 目标视图或 URL 无法解析：取消导航并记录事件。
- 权限失败：由目标数据源或目标页面返回，不在前端放宽权限。

## 7. 兼容策略

```text
旧 DrillLevel(cardId, paramName, label)
    → 读取时适配为 Card DataSource + 单参数映射
    → 保存时允许继续保留旧字段
    → 新建配置只写通用字段
```

本 Sprint 不执行数据库批量迁移，不重写所有历史 ScreenConfig。

## 8. 验证策略

- 纯函数：参数映射、上下文继承、上卷、无映射取消、旧配置适配。
- Schema：合法/非法通用层级和旧层级均有测试。
- 运行时：五类数据源使用同一入口并得到相同参数结果。
- 动作：set-variable、drill-down/up、drill-view、panel、URL 回归。
- 设计器：非 Card 数据源可见下钻配置，文案不含领域术语。
- UI 事件：柱体、扇区、折线点、散点、漏斗阶段、雷达系列、组合图数据项、层级图节点、地图区域、表格行和 KPI/卡片均通过同一点击载荷协议。
- UI 边界：图例、坐标轴、空白区、表头、滚动条、分页和地图拖拽/缩放不产生下钻；加载期间连续点击不重复推进。
- 浏览器：Chrome 95 下完成两级下钻、上卷、重置和页面返回。

## 9. 止损边界

出现以下任一情况即停止扩展并回到本设计评审：

- 需要新增业务领域表或业务类型枚举；
- 为某个接入系统增加专用运行时判断；
- 新增第二套动作或变量协议；
- 为实现下钻而修改后端查询口径；
- 旧大屏未配置交互时行为发生变化。
