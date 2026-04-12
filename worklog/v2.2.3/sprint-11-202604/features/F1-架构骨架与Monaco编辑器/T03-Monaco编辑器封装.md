# T03: Monaco 编辑器封装（SqlEditor + 主题）

**优先级**: P0
**状态**: READY
**依赖**: T01

## 目标

把项目已安装但未使用的 `monaco-editor` + `@monaco-editor/react` 接入 SQL IDE，取代原 textarea，提供语法高亮、行号、括号匹配、主题等基础能力。

## 技术设计

### SqlEditor 组件接口

```typescript
interface SqlEditorProps {
  value: string;
  onChange: (sql: string) => void;
  engine: 'trino' | 'hive' | 'postgresql' | 'generic';  // 仅用于标签显示
  readOnly?: boolean;
  mode: 'simple' | 'advanced';
  onExecute?: (sql: string) => void;  // Ctrl+Enter 触发
  onCursorPositionChange?: (pos: Position) => void;
  markers?: MonacoMarker[];  // 服务端校验结果
}
```

### Monaco 配置

```typescript
{
  language: 'sql',
  theme: isDark ? 'sqlide-dark' : 'sqlide-light',
  automaticLayout: true,
  minimap: { enabled: mode === 'advanced' },
  fontSize: 14,
  lineNumbers: 'on',
  wordWrap: 'on',
  suggestOnTriggerCharacters: true,
  quickSuggestions: { other: true, comments: false, strings: true },
  tabSize: 2,
  bracketPairColorization: { enabled: true },
  scrollBeyondLastLine: false,
  renderLineHighlight: 'line',
}
```

### 自定义主题

- 注册 `sqlide-dark` / `sqlide-light`，配色匹配 Ant Design Token
- 主题切换跟随 `ThemeContext`

### Bundle 优化

- `monaco-editor-webpack-plugin` 限定只打包 `sql` 语言
- 路由级 `React.lazy` 懒加载 SqlIdePage

## 影响范围

- `components/sql-ide/editor/SqlEditor.tsx` 新增
- `vite.config.ts` / `webpack.config.js` 配置 Monaco 插件
- 全局样式无影响

## 验证

- [ ] 打开 SqlIdePage 编辑器首次渲染 ≤800ms
- [ ] SQL 关键字高亮（SELECT/FROM/WHERE 等）
- [ ] 括号匹配着色启用
- [ ] 切换全局暗黑模式，编辑器主题同步切换
- [ ] 简洁模式下 minimap 隐藏，高级模式显示
- [ ] Bundle 分析确认 Monaco 仅包含 SQL 语言，体积 < 500KB gzip

## 完成标准

- [ ] SqlEditor 组件实现并通过上述验证
- [ ] 被 SqlIde 根组件使用
- [ ] 文件行数 ≤ 300
