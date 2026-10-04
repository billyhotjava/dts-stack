# T06: SQL 格式化（sql-formatter + 引擎 dialect）

**优先级**: P1
**状态**: READY
**依赖**: T03

## 目标

集成 `sql-formatter` 库，提供一键格式化能力，按当前 Tab 绑定的引擎选择 dialect。

## 技术设计

### 依赖

```
npm i sql-formatter@^15
```

### 封装

```typescript
// editor/formatter.ts
import { format } from 'sql-formatter';

const DIALECT_MAP: Record<Engine, string> = {
  trino: 'trino',
  hive: 'hive',
  postgresql: 'postgresql',
  generic: 'sql',
};

export function formatSql(sql: string, engine: Engine): string {
  return format(sql, {
    language: DIALECT_MAP[engine] ?? 'sql',
    keywordCase: 'upper',
    indentStyle: 'standard',
    tabWidth: 2,
  });
}
```

### 动态 import

- `sql-formatter` 包体积约 200KB，仅在触发格式化时动态加载
- 避免进入 SQL IDE 首屏就拉取

### 触发点

- T05 的 `Ctrl+Alt+F` 快捷键
- 顶部工具栏"Format"按钮（高级模式显示）

## 影响范围

- 新增 `editor/formatter.ts`
- `SqlEditor` 增加 `formatCurrent()` action
- 顶部工具栏新增 Format 按钮

## 验证

- [ ] 格式化一个 4 行未缩进 SELECT，缩进对齐关键字大写
- [ ] Trino / Hive / PostgreSQL 三种 dialect 分别生效（关键字差异体现）
- [ ] 首次触发加载 formatter 后缓存，二次触发立即执行
- [ ] 选中部分 SQL 时只格式化选中内容

## 完成标准

- [ ] Ctrl+Alt+F 可格式化
- [ ] 工具栏按钮在高级模式下显示并生效
- [ ] sql-formatter 动态 import 确认，首屏不含此包
