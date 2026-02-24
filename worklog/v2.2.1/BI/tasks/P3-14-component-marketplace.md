# P3-14 组件市场与社区共享

`status`: `done` (frontend)
`priority`: `P3`
`sprint`: `Sprint 4 - 差异化`
`inspiration`: `DataEase(模板市场) + FlyFish(组件模板中心) + npm 生态思路`

## 目标

建立组件/模板的发布-发现-安装闭环，形成资产复用和社区共享机制。

## 当前状态

- P1-03 已建立 RendererPlugin 协议（插件注册/加载/渲染）。
- P1-04 已建立资产中心基础。
- 缺少：组件/模板的在线发布、搜索、安装流程。

## 子任务

### 1. 资产包格式定义

**组件包结构** (`.dts-component.zip`):
```
my-custom-kpi/
├── manifest.json        # 元数据
├── renderer.tsx         # 渲染组件（编译后 JS）
├── thumbnail.png        # 缩略图
├── README.md           # 使用说明
└── example.json        # 示例配置
```

**manifest.json**:
```json
{
  "id": "my-custom-kpi",
  "name": "高级 KPI 卡片",
  "version": "1.0.0",
  "author": "张三",
  "description": "支持趋势箭头和同环比的 KPI 卡片",
  "category": "metric",
  "tags": ["kpi", "trend", "comparison"],
  "baseType": "number-card",
  "minChrome": 95,
  "dependencies": {}
}
```

**模板包结构** (`.dts-template.zip`):
```
sales-dashboard/
├── manifest.json
├── config.json          # ScreenConfig
├── thumbnail.png
└── README.md
```

### 2. 资产仓库后端

**新增 API**:
```
# 组件包
POST   /api/analytics/marketplace/components          # 上传
GET    /api/analytics/marketplace/components           # 列表（搜索/分页）
GET    /api/analytics/marketplace/components/{id}      # 详情
DELETE /api/analytics/marketplace/components/{id}      # 删除
POST   /api/analytics/marketplace/components/{id}/install  # 安装到当前实例

# 模板包
POST   /api/analytics/marketplace/templates
GET    /api/analytics/marketplace/templates
POST   /api/analytics/marketplace/templates/{id}/install
```

**存储**: 资产包存储在文件系统 `data/marketplace/` 目录。

### 3. 前端市场浏览器

**新增页面**: `ScreenMarketplacePage.tsx`

**入口**: 大屏列表页 → "市场" 按钮

**布局**:
- 顶部搜索栏 + 分类标签过滤（图表/装饰/容器/模板）。
- 卡片网格展示：缩略图 + 名称 + 描述 + 作者 + 安装按钮。
- 详情 Modal：README + 示例截图 + 配置说明。

### 4. 安装与加载

**组件安装流程**:
1. 点击"安装" → 后端解压 zip → 校验 manifest → 注册到 plugin registry。
2. 前端刷新 plugin 列表 → 组件库面板新增该组件。
3. 用户拖拽到画布即可使用。

**卸载**:
- 卸载前检查是否有大屏正在使用该组件。
- 如有使用，提示"以下大屏正在使用此组件"并确认。

### 5. 组件发布向导

**入口**: 设计器 → "发布组件" 或插件开发面板

**流程**:
1. 选择要发布的自定义组件。
2. 填写 manifest 信息（名称/描述/分类/标签）。
3. 上传缩略图。
4. 打包并上传到资产仓库。

### 6. 模板发布

**入口**: 大屏列表页 → 大屏卡片 → "发布为模板"

**流程**:
1. 导出当前大屏的 ScreenConfig。
2. 清除敏感数据（数据源连接信息、API key 等）。
3. 填写模板信息。
4. 上传到资产仓库。

## Chrome 95 兼容性

- 文件上传使用 `<input type="file">` + FormData，Chrome 95 ✅。
- ZIP 解压在后端完成。

## 验收标准

- 可上传并安装自定义组件包。
- 安装后组件在组件库面板可见。
- 模板可发布和从市场安装。
- 搜索和分类过滤正常工作。
- Chrome 95 下市场页面正常交互。

## 风险与回滚

- 风险：恶意组件包（XSS / 任意代码执行）。
- 回滚：
  - 组件 JS 在 iframe sandbox 中执行。
  - manifest 校验（必须声明 baseType）。
  - 上传需管理员权限。
  - 安装前展示代码审核摘要。
