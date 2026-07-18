# Sprint-66 集成测试与交付证据计划

**状态**：DONE
**执行时间**：2026-07-18 13:45 +08:00
**代码基线**：`v2.2.3@d2f8585ed`；当前变更未提交（本次未收到 commit/push 授权）。
**说明**：以下合同已在生产构建产物和真实 Chromium 95 上执行，结果及证据路径已补齐。

## 1. 验收环境

| 环境 | 用途 | 最低要求 |
|---|---|---|
| Node 单元测试 | 纯函数、Schema、动作工具 | 使用仓库锁定的 Node/pnpm |
| 前端生产构建 | TypeScript 与旧浏览器转译 | `LEGACY_BROWSER_BUILD=1` |
| Compose 预览 | 真实大屏保存与运行 | v2.2.3 当前 compose |
| Chrome 95 | 客户端兼容验收 | 用户名密码登录、无白屏或语法错误 |

实际环境：Node `v24.14.1`、pnpm `10.33.0`、Chromium `95.0.4638.0`；浏览器验收使用生产 `dist` 的 Vite preview 和受控 API fixture，不依赖业务系统或领域数据。

## 2. 通用场景矩阵

| ID | 场景 | 核心断言 | Feature |
|---|---|---|---|
| IT-01 | SQL 两级下钻 | 点击字段映射为参数，下一层 SQL 刷新，上卷恢复 | F1/F2 |
| IT-02 | API 两级下钻 | 同一映射协议进入 API params/body，不使用专用分支 | F1/F2 |
| IT-03 | Card 旧配置 | `cardId + paramName` 行为与改造前一致 | F1/F4 |
| IT-04 | Dataset 下钻 | queryBody 通过既有数据源入口消费参数 | F2 |
| IT-05 | Metric 下钻 | 通过既有 Metric 适配入口消费参数 | F2 |
| IT-06 | 组件联动 | `set-variable` 映射更新其他组件查询 | F2/F3 |
| IT-07 | 视图切换 | `drill-view` 携带映射参数并支持返回根视图 | F2/F3 |
| IT-08 | 详情面板 | 点击载荷渲染面板，不改变当前下钻层级 | F2/F3 |
| IT-09 | 内外部跳转 | 白名单参数替换成功，失败导航被取消并记录 | F2/F3 |
| IT-10 | 无效映射 | 不执行查询、不污染变量、保留当前画面 | F1/F2 |
| IT-11 | 未配置交互 | 现有大屏展示、点击和轮播行为不变 | F4 |
| IT-12 | Chrome 95 | 下钻、上卷、重置、面板、内部跳转均可用 | F4 |

### 2.1 UI 事件验收矩阵

| ID | 组件与点击位置 | 预期结果 | 不得发生 |
|---|---|---|---|
| UI-01 | 点击柱状图具体柱体/堆叠分段 | 使用该数据项载荷下钻一级 | 点击坐标轴或空白区触发 |
| UI-02 | 点击饼图/环图具体扇区 | 使用扇区 name/value/data 下钻 | 点击圆心空白或图例触发 |
| UI-03 | 点击折线图/面积图具体数据点 | 使用数据点载荷下钻 | 根据 tooltip 或索引猜测值 |
| UI-04 | 点击散点、漏斗阶段、雷达系列数据项 | 使用统一 ECharts 事件映射 | 为图表类型编写业务字段分支 |
| UI-05 | 点击组合图柱体或折线点 | 各执行一次相同映射协议 | 一次点击重复执行两个动作 |
| UI-06 | 点击矩形树图/旭日图节点 | 使用实际节点载荷推进并可返回 | DTS 推断节点业务层级 |
| UI-07 | 点击地图区域或标记点 | 使用区域/点位载荷下钻 | 地图拖拽、缩放或空白点击触发 |
| UI-08 | 点击普通/滚动表格数据行 | 使用列名字段和完整 row 下钻 | 表头、分页、滚动条点击触发 |
| UI-09 | 点击 KPI/数字卡/统计卡/仪表盘数据区 | 执行一次详情或下钻动作 | 装饰子元素造成重复执行 |
| UI-10 | 快速连续点击已配置组件 | 加载期只接受一次推进 | 重复查询或重复面包屑 |
| UI-11 | 点击缺少映射字段的数据项 | 保持当前层并记录取消原因 | 携带空参数进入下一层 |
| UI-12 | 下钻查询失败 | 保留返回、重置和当前画面 | 白屏、卡死或无法退出 |

### 2.2 执行结论

| 范围 | 结果 | 证据 |
|---|---|---|
| IT-01 至 IT-05 | PASS：五类数据源复用同一 mapping、stack 和参数集合 | [runtime](evidence/runtime/README.md)、[unit](evidence/unit/README.md) |
| IT-06 至 IT-09 | PASS：变量、视图、面板、URL 动作共用白名单映射；内部视图可返回 | [unit](evidence/unit/README.md)、[chrome95](evidence/chrome95/README.md) |
| IT-10、IT-11 | PASS：缺失映射取消推进；旧 Card 与未配置组件保持兼容 | [unit](evidence/unit/README.md) |
| IT-12 | PASS：真实 Chromium 95 无页面异常或失败请求 | [chrome95](evidence/chrome95/README.md) |
| UI-01 至 UI-12 | PASS：P0 点击 case 在 Chrome 95 实跑，其余组件/误触发边界由统一事件矩阵覆盖；500 查询失败可重置 | [chrome95](evidence/chrome95/README.md)、[unit](evidence/unit/README.md) |

## 3. 自动化命令合同

从 `source/dts-platform-webapp` 执行：

```bash
node --test \
  src/analytics/pages/screens/drillRuntime.test.ts \
  src/analytics/pages/screens/screenSpec.drillDown.test.ts \
  src/analytics/pages/screens/components/propertyPanel/BehaviorConfigSection.source.test.ts \
  src/analytics/pages/screens/components/propertyPanel/PropertyPanel.behavior-source.test.ts

pnpm exec vitest run \
  src/analytics/pages/screens/hooks/useDrillDown.test.tsx \
  src/analytics/pages/screens/ScreenRuntimeContext.drillView.test.tsx \
  src/analytics/pages/screens/renderers/shared/actionUtils.test.ts \
  src/analytics/pages/screens/renderers/InteractionLayer.chartCases.test.tsx \
  src/analytics/pages/screens/renderers/InteractionLayer.cancelOnEmpty.test.tsx \
  src/analytics/pages/screens/ScreenPreviewPage.hooks.test.ts

pnpm exec biome check \
  e2e/sprint66-drilldown.spec.ts \
  playwright.chrome95.config.ts \
  src/analytics/pages/screens/ScreenRuntimeContext.drillView.test.tsx \
  src/analytics/pages/screens/drillRuntime.ts \
  src/analytics/pages/screens/drillRuntime.test.ts \
  src/analytics/pages/screens/hooks/useDrillDown.test.tsx \
  src/analytics/pages/screens/renderers/InteractionLayer.chartCases.test.tsx \
  src/analytics/pages/screens/screenSpec.drillDown.test.ts

pnpm build

CHROME95_EXECUTABLE_PATH=/tmp/dts-chrome95-ttQJ8k/chrome-linux/chrome \
E2E_BASE_URL=http://127.0.0.1:4173 \
pnpm exec playwright test --config=playwright.chrome95.config.ts
```

仓库级最终检查：

```bash
git diff --check
npx gitnexus detect-changes
```

GitNexus 的实际命令以实施时仓库工具帮助为准；提交前必须保存变更范围和受影响执行流结果。

执行结果：Node `18/18`、Vitest `41/41`、Biome 新增文件 `8/8`、生产构建退出码 `0`、Chrome 95 Playwright `1/1`、`git diff --check` 退出码 `0`。完整结果见 `evidence/`。

## 4. Chrome 95 验收步骤

1. 打开设计器，选择 SQL 数据源图表并启用两级下钻。
2. 配置 `name → selectedKey` 映射和下一层数据源。
3. 依次验证柱体、饼图扇区、折线数据点、表格行和地图区域点击，确认参数来自实际点击项。
4. 抽查散点、漏斗、组合图、矩形树图/旭日图，以及 KPI/数字卡整卡点击。
5. 点击图例、坐标轴、图表空白、表头、分页、滚动条，并执行地图拖拽/缩放，确认均不误触发。
6. 快速连续点击同一数据项，确认只推进一级且不产生重复请求。
7. 点击面包屑返回根层，再次下钻后执行“上卷返回”和“重置”。
8. 执行详情面板、内部页面跳转和外部 URL 动作。
9. 模拟缺失映射和查询失败，确认当前画面、返回和重置能力仍可用。
10. 刷新页面并验证未配置交互的大屏不受影响。
11. 保存各类组件截图、控制台日志和 Network 关键请求脱敏样例。

## 5. 证据目录

实施完成时在 `it/evidence/` 下建立：

- `unit/`：Node 测试输出；
- `build/`：TypeScript 与 Vite 构建输出；
- `runtime/`：各数据源查询请求和运行事件；
- `chrome95/`：截图、控制台和关键路径说明；
- `gitnexus/`：impact 与 detect-changes 结果；
- `review/`：最终代码 review 和遗留风险。

## 6. Go / No-Go

以下任一发生即 No-Go：

- 核心代码出现接入系统或业务领域专属分支；
- SQL/API/Dataset/Metric 仍不能使用通用下钻；
- 旧 Card 下钻或未配置交互的大屏发生回归；
- 空映射触发空参数查询；
- 目标页面权限被前端参数绕过；
- Chrome 95 主路径白屏、无法返回或状态错乱；
- 柱体、扇区、折线点、表格行或地图区域任一 P0 点击 case 无法下钻；
- 图例、空白区、表头、滚动条、分页或地图缩放出现误下钻；
- 单次点击推进多级或加载期间重复生成请求/面包屑；
- 无真实 IT 证据即将 Sprint 标记为 DONE。
