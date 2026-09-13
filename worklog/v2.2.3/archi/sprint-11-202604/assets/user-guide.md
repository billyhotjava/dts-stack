# SQL IDE 用户手册

## 快速入门

1. 左侧 Activity Bar 选择 `🗂 Schema`，选数据源，展开数据库
2. 中间编辑区输入 SQL，Ctrl+Enter 运行
3. 底部 5 tab 查看 Results / Chart / Pivot / Query Plan / Log
4. Ctrl+S 保存为查询
5. 查询历史在 `📋 History` icon

## 快捷键

| 快捷键 | 动作 |
|---|---|
| Ctrl+Enter | 执行当前 SQL |
| Ctrl+Shift+Enter | 执行并在新 Tab 显示结果 |
| Ctrl+Alt+F | 格式化 |
| Ctrl+/ | 行注释 |
| Ctrl+D | 多选下一个同词 |
| Alt+↑/↓ | 行上移/下移 |
| Ctrl+S | 保存为查询 |
| F8 | 跳到下一个错误 |
| Ctrl+\` | 切换底部面板 |

## 简洁 vs 高级模式

右上角切换。

| 能力 | 简洁 | 高级 |
|---|---|---|
| Monaco minimap | ✗ | ✓ |
| ActivityBar icons | Schema/History/Saved | + Search + Copilot |
| 右键菜单 | SELECT / Copy | + INSERT |
| Log 重写后 SQL | ✗ | ✓ toggle |

## 多 Tab

- 顶部 `+` 新建，上限 30
- 双击标题重命名
- Tab 状态跨设备同步（登录后自动恢复）
- Dirty 标记 `●` 表示尚未同步

## 导出

结果区右上角 `导出 ▾`：CSV / Excel / JSON。频控 5 次/10 分钟。

## 二次查询

结果区 "Query This Result" 按钮创建临时视图，新开 Tab 继续对结果写 SQL。30 分钟 TTL。
