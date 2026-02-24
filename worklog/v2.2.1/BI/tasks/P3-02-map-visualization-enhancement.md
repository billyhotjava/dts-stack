# P3-02 地图可视化增强

`status`: `done`
`priority`: `P3`
`sprint`: `Sprint 1 - 核心图表扩展`
`inspiration`: `DataEase(5 种地图类型) + 政务/能源行业交付需求`

## 目标

将地图从单一区域填色图扩展为 4 种地图类型，满足政务大屏和行业交付的地理可视化需求。

## 差距分析

| 地图类型 | DTS 当前 | DataEase | 场景 |
|---------|:---:|:---:|------|
| 区域填色图 | ✅ | ✅ | 省/市 GDP、人口分布 |
| 气泡地图 | ❌ | ✅ | 城市规模、门店分布 |
| 热力地图 | ❌ | ✅ | 人流密度、信号覆盖 |
| 流向地图 | ❌ | ✅ | 物流线路、人口迁徙 |
| 散点地图 | ❌ | ✅ | POI 标注、设备位置 |

## 子任务

### 1. 气泡地图（scatter on map）

**在现有 `map-chart` 基础上扩展**，通过 `config.mapMode` 区分。

**mapMode 枚举**: `region`（现有）| `bubble` | `heatmap` | `flow` | `scatter`

**配置新增**:
```typescript
{
  mapMode: 'bubble',
  scatterData: Array<{
    name: string,
    value: [lng: number, lat: number, magnitude: number],
  }>,
  bubbleSizeRange: [min: number, max: number],  // 默认 [8, 40]
  bubbleColor?: string,
}
```

**实现要点**:
- ECharts `scatter` 系列叠加在 `geo` 组件上。
- 气泡大小映射 `symbolSize` 函数。
- 支持 tooltip 悬停显示详情。

### 2. 热力地图（heatmap on map）

**配置新增**:
```typescript
{
  mapMode: 'heatmap',
  heatmapData: Array<[lng: number, lat: number, value: number]>,
  heatmapRadius: number,      // 默认 20
  heatmapOpacity: [min, max], // 默认 [0, 0.8]
}
```

**实现要点**:
- 需引入 ECharts `heatmap` 组件（按需导入）。
- 叠加在 `geo` 坐标系上。

### 3. 流向地图（lines on map）

**配置新增**:
```typescript
{
  mapMode: 'flow',
  flowData: Array<{
    from: { name: string, coord: [lng, lat] },
    to: { name: string, coord: [lng, lat] },
    value?: number,
  }>,
  flowLineStyle?: { curveness: number, color?: string },
  showFlowEffect?: boolean,  // 流动动画
}
```

**实现要点**:
- ECharts `lines` 系列 + `effectScatter` 端点标记。
- `effect.show: true` 开启流动动画。
- 支持线条粗细映射 value。

### 4. 散点地图

与气泡地图共用 scatter 系列，`bubbleSizeRange` 设为固定值即可。

### 5. GeoJSON 管理增强

**当前**: 仅支持中国/世界两个预设 + 自定义 URL。

**增强**:
- 新增省级预设（34 个省份 GeoJSON URL，基于 DataV.aliyun 服务）。
- 属性面板新增"省份选择"下拉菜单。
- 缓存策略：`geoJsonCache.ts` 已有 LRU（MAX=8），可扩展到 16。

### 6. 数据源字段映射

地图组件的字段映射：
- `nameField` — 区域名称列（匹配 GeoJSON feature.properties.name）
- `valueField` — 数值列
- `lngField` / `latField` — 经纬度列（散点/气泡/热力/流向模式）
- `fromLngField` / `fromLatField` / `toLngField` / `toLatField` — 流向模式起止点

在属性面板增加字段映射配置区。

## Chrome 95 兼容性

- ECharts geo/scatter/heatmap/lines 均为 ECharts 5.x 内置，Chrome 95 ✅。
- DataV.aliyun GeoJSON 服务为公共 CDN，无浏览器限制。

## 验收标准

- 5 种 mapMode 均可通过属性面板切换。
- 气泡/散点大小正确映射数据。
- 热力图渲染平滑无明显锯齿。
- 流向图动画流畅。
- 省级 GeoJSON 预设可一键选择。
- 所有模式在 Chrome 95 下正常渲染。

## 风险与回滚

- 风险：ECharts heatmap 组件体积增大（~30KB gzip）。
- 回滚：heatmap 按需 import，非地图场景不加载。
