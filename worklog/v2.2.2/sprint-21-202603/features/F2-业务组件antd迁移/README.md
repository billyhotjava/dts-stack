# F2: 业务组件 antd 迁移

**优先级**: P1
**状态**: READY
**依赖**: F1（antd 已引入）

## 目标

将 analytics-webapp 全部自定义 UI 组件（`ui/` 目录）替换为 antd 组件，消除双 UI 框架维护负担。

## 技术设计

### 迁移范围

12 个自定义 UI 组件，60 个文件使用，按引用数排序迁移：

| 自定义 UI | antd 替代 | 引用数 | 复杂度 |
|-----------|----------|--------|--------|
| `ui/Card/Card` | `antd/Card` | ~32 | 低 |
| `ui/Badge/Badge` | `antd/Badge` | ~29 | 低 |
| `ui/Loading/Spinner` | `antd/Spin` | ~31 | 中（CSS高度链问题） |
| `ui/Button/Button` | `antd/Button` | ~26 | 低 |
| `ui/Modal/Modal` | `antd/Modal` | ~17 | 中（Props差异） |
| `ui/Input/Input` | `antd/Input` | ~17 | 低 |
| `ui/Input/Select` | `antd/Select` | ~9 | 中（Options格式） |
| `ui/Loading/Skeleton` | `antd/Skeleton` | ~3 | 低 |
| `ui/Tabs/Tabs` | `antd/Tabs` | ~2 | 低 |
| `ui/Dropdown/Dropdown` | `antd/Dropdown` | ~1 | 低 |
| `ui/Drawer/Drawer` | `antd/Drawer` | ~1 | 低 |
| `ui/Input/Checkbox` | `antd/Checkbox` | ~1 | 低 |
| `ui/ThemeToggle/ThemeToggle` | 移除 | ~1 | 低 |

### 迁移策略

- **直接替换**：不做中间适配层，逐文件修改 import 和 Props
- **逐组件迁移**：每个组件一个 Task，替换所有引用后删除自定义组件文件
- **最后清理**：全部替换完成后删除 `ui/` 目录

### 注意事项

- `Spinner → Spin`：antd `<Spin>` 会破坏 CSS 高度链，需使用绝对定位覆盖方式
- `Modal`：自定义 `isOpen` prop → antd `open` prop
- `Select`：自定义 options 格式可能与 antd `{ value, label }` 不同
- `ThemeToggle`：统一后移除暗色主题切换，使用亮色主题

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | 迁移 Card 组件 | P1 | READY | F1/T03 |
| T02 | 迁移 Badge 组件 | P1 | READY | F1/T03 |
| T03 | 迁移 Button 组件 | P1 | READY | F1/T03 |
| T04 | 迁移 Spinner → Spin | P1 | READY | F1/T03 |
| T05 | 迁移 Modal 组件 | P1 | READY | F1/T03 |
| T06 | 迁移 Input 组件 | P1 | READY | F1/T03 |
| T07 | 迁移 Select 组件 | P1 | READY | F1/T03 |
| T08 | 迁移剩余组件（Skeleton/Tabs/Dropdown/Drawer/Checkbox） | P1 | READY | F1/T03 |
| T09 | 移除 ThemeToggle + 删除 ui/ 目录 | P1 | READY | T01-T08 |

## 完成标准
- [ ] 全部 60 个文件的自定义 UI 引用替换为 antd
- [ ] `ui/` 目录完全删除
- [ ] TypeScript 编译通过
- [ ] 生产构建通过
- [ ] 页面视觉无明显回归
