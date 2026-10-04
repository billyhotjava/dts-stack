# 大屏跳转选择器设计

## 概述

在大屏设计器的 `jump-url` 动作配置中，增加"选择大屏"模式，允许用户从已发布大屏列表中选择跳转目标，替代手动输入 URL。

## 问题

当前配置 `jump-url` 动作时，用户需要手动输入跳转链接模板（如 `screen-ref:xxx|/bi/screens/123/preview`）。这对不了解 `screen-ref:` 协议的用户来说不可用，也容易出错。

## 改动范围

仅改动 **1 个文件**：`PropertyPanel.tsx` 的 `jump-url` 动作配置区（约第 6004-6028 行）。

## 设计

### UI 变化

在原有的"跳转链接模板"输入框前面，增加一个跳转目标模式选择（单选）和大屏选择器：

```
┌─ 跳转目标 ────────────────────────────────────────┐
│                                                    │
│  模式:  ○ 选择大屏    ○ 自定义URL                   │
│                                                    │
│  【选择大屏模式】                                    │
│  ┌────────────────────────────────────────────┐    │
│  │ 🔍 搜索大屏...                      ▾      │    │
│  ├────────────────────────────────────────────┤    │
│  │  执行层-项目执行详情          已发布 ✓      │    │
│  │  执行层-质量问题与跟进        已发布        │    │
│  │  综合态势总览                已发布         │    │
│  │  ...                                      │    │
│  └────────────────────────────────────────────┘    │
│                                                    │
│  【自定义URL模式】                                   │
│  URL模板: [____________________________________]    │
│                                                    │
│  打开方式:  ○ 当前窗口   ○ 新标签页                  │
└────────────────────────────────────────────────────┘
```

### 数据流

1. 用户选择"选择大屏"模式
2. 聚焦或展开时，调用 `analyticsApi.listScreens()` 获取大屏列表
3. 结果缓存在组件 state 中（同一编辑会话内不重复请求）
4. 列表支持关键字搜索过滤（前端过滤，不需要新 API）
5. 列表按更新时间倒序排列
6. 每项显示：大屏名称 + 发布状态标签（已发布/草稿）
7. 用户选中后，自动生成 `jumpUrlTemplate`：
   ```
   screen-ref:ENCODED_SCREEN_NAME|/bi/screens/SCREEN_ID/preview
   ```
8. 底层存储的仍然是 `jumpUrlTemplate` 字符串，不引入新字段

### 实现细节

**新增内部组件：`ScreenJumpPicker`**

位置：PropertyPanel.tsx 内部（或拆分为同目录单文件）

```typescript
function ScreenJumpPicker({
  value: string,              // 当前 jumpUrlTemplate
  onChange: (url: string) => void,
}) {
  const [screens, setScreens] = useState<ScreenListItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [search, setSearch] = useState('');
  const [mode, setMode] = useState<'screen' | 'custom'>(
    // 如果当前值以 screen-ref: 开头，默认选中"选择大屏"模式
    value.startsWith('screen-ref:') ? 'screen' : 'custom'
  );

  // 首次展开时加载
  const loadScreens = async () => {
    if (screens.length > 0) return;
    setLoading(true);
    const list = await analyticsApi.listScreens();
    setScreens(list.sort((a, b) =>
      new Date(b.updatedAt || 0).getTime() - new Date(a.updatedAt || 0).getTime()
    ));
    setLoading(false);
  };

  // 搜索过滤
  const filtered = screens.filter(s =>
    !search || (s.name || '').includes(search)
  );

  // 选中大屏 → 生成 screen-ref URL
  const selectScreen = (s: ScreenListItem) => {
    const name = encodeURIComponent(String(s.name || '').trim());
    const fallback = encodeURIComponent(`/bi/screens/${s.id}/preview`);
    onChange(`screen-ref:${name}|${fallback}`);
  };

  // 从当前 value 反解出选中的大屏名称
  const selectedName = parseSelectedScreenName(value);

  return (/* UI 如上述设计 */);
}
```

**解析已选大屏名称（回显用）：**

```typescript
function parseSelectedScreenName(url: string): string | null {
  if (!url.startsWith('screen-ref:')) return null;
  const raw = url.slice('screen-ref:'.length);
  const [namePart] = raw.split('|', 2);
  return decodeURIComponent(namePart || '').trim() || null;
}
```

### 兼容性

- **向后兼容**：存储格式不变（仍是 `jumpUrlTemplate` 字符串），已有配置不受影响
- **自定义 URL 模式保留**：用户仍可切换到"自定义URL"模式手动输入任意 URL
- **screen-ref 回显**：如果当前值是 `screen-ref:` 格式，自动识别并切换到"选择大屏"模式，回显已选大屏名称

### 不做的事

- 不新增组件类型
- 不新增 API 接口（复用 `analyticsApi.listScreens()`）
- 不修改 action type 枚举
- 不修改运行时跳转逻辑（`resolveScreenReferenceUrl` 不变）
- 不过滤只显示已发布大屏（草稿也显示，标签区分状态）
