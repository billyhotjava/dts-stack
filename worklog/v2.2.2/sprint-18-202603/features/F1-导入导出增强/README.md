# F1: 导入导出增强

**优先级**: P0
**状态**: DONE

## 目标

全面完善大屏编辑器和模板市场的导入导出功能：资源内联导出、导入预览 Modal、UI 入口优化、模板市场导入导出。

## 设计决策

| 决策项 | 选择 | 原因 |
|--------|------|------|
| 资源打包方式 | 内联 Base64 | 单文件自包含，跨机器不失真，无需解压逻辑 |
| 导入预览深度 | 轻量摘要 | 显示名称/尺寸/组件数/校验结果，实现成本低 |
| 编辑器内导入操作 | 覆盖 或 创建新大屏 | 给用户选择权，避免误覆盖 |
| 模板市场导入操作 | 注册为模板 或 创建为大屏 | 灵活满足两种场景 |
| 导入来源 | 仅文件上传 | 离线环境限制，URL/剪贴板使用率低 |
| 批量导出 | 不做 | 使用场景和频率低，YAGNI |

## 技术架构

### 新增文件

```
pages/screens/
├── utils/
│   ├── resourceInliner.ts      — 导出时内联图片资源为 Base64
│   └── resourceRestorer.ts     — 导入时上传还原 Base64 为 URL
├── components/
│   └── ImportPreviewModal.tsx   — 导入预览弹窗（编辑器 + 模板市场共用）
```

### 修改文件

- `ScreenHeader.tsx` — 新增"导入导出"HeaderMenu，重构导入流程
- 模板市场组件 — 接入 ImportPreviewModal 和 resourceInliner

### 资源内联规则

**导出时扫描的图片字段：**
- `screenSpec.backgroundImage`
- 组件 `config.src`（image 组件）
- 组件 `config.backgroundImage`（容器组件）
- 其他 config 中匹配图片 URL 模式的字段

**处理规则：**
- 只转换 `/analytics/` 或相对路径开头的内部资源
- 外部 URL、已有 `data:` 格式跳过
- 单张图片失败不阻断，保留原 URL 并 console.warn

**导入还原规则：**
- 检测 `resourcesInlined: true` 标记
- 将 Base64 并行上传（限制 3 并发）到服务端
- 上传失败保留 Base64 原值（至少能显示）

### 导出 JSON 格式

```json
{
  "schema": "dts.screen.spec",
  "schemaVersion": 2,
  "exportedAt": "2026-03-28T...",
  "resourcesInlined": true,
  "templateMeta": {
    "name": "...",
    "description": "...",
    "category": "...",
    "tags": ["..."]
  },
  "screenSpec": { ... }
}
```

`templateMeta` 仅在模板市场导出时包含。

### ImportPreviewModal

```
┌─────────────────────────────────────────────┐
│  导入预览                               [×] │
├─────────────────────────────────────────────┤
│  文件: xxx-spec.json                        │
│                                             │
│  基本信息                                    │
│  名称:     xxx          尺寸: 1920×1080     │
│  组件数:   24           主题: enterprise     │
│  资源内联: 是 (3 张图片)                     │
│                                             │
│  校验结果                                    │
│  ✓ 配置格式合法                              │
│  ⚠ 2 个警告: ...                            │
│                                             │
│  导入方式:                                   │
│  [替换当前大屏]  [创建为新大屏]  [注册为模板] │
│  ↑ 编辑器模式    ↑ 始终可用      ↑ 模板市场   │
│                                             │
│            [取消]        [确认导入]           │
└─────────────────────────────────────────────┘
```

Props:
```typescript
type ImportPreviewModalProps = {
  open: boolean;
  onClose: () => void;
  fileName: string;
  parsedSpec: ScreenConfig;
  templateMeta?: { name: string; description?: string; category?: string; tags?: string[] };
  validation: { errors: string[]; warnings: string[] };
  resourcesInlined: boolean;
  inlinedResourceCount: number;
  mode: "editor" | "marketplace";
  onReplaceCurrentScreen: () => void;
  onCreateNewScreen: () => void;
  onRegisterAsTemplate: () => void;
};
```

### 编辑器 UI 改进

新增"导入导出"一级 HeaderMenu，聚合所有操作：

```
[画布]  [工具箱]  [导入导出]  [版本]  [治理]    [预览] [发布] [保存]
```

菜单内容：
- 导入区：选择文件按钮
- 导出区：预览设备选择 + 三个直接触发按钮（JSON/PNG/PDF）

原"版本导出"菜单的导出项移到新菜单，改名为"版本"。
原"工具箱"菜单的"导入JSON"设计动作移除。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | resourceInliner — 导出资源内联工具 | P0 | READY | - |
| T02 | resourceRestorer — 导入资源还原工具 | P0 | READY | - |
| T03 | ImportPreviewModal — 导入预览弹窗 | P0 | READY | - |
| T04 | ScreenHeader 导入导出菜单重构 | P0 | READY | T01, T02, T03 |
| T05 | ScreenHeader 导入流程增强 | P0 | READY | T03, T04 |
| T06 | 模板市场导出功能 | P1 | READY | T01 |
| T07 | 模板市场导入功能 | P1 | READY | T02, T03 |

## 完成标准
- [ ] 编辑器导出 JSON 时图片资源自动内联为 Base64
- [ ] 导入 JSON 弹出预览 Modal，显示摘要和校验结果
- [ ] 导入支持"替换当前"和"创建新大屏"两种操作
- [ ] 编辑器有独立的"导入导出"一级菜单
- [ ] 模板市场"导入"按钮弹出预览 Modal，支持"注册为模板"和"创建为大屏"
- [ ] 模板市场"导出"按钮导出选中模板为含内联资源的 JSON
- [ ] 含内联资源的 JSON 导入时自动上传还原资源 URL
