# P3-15 3D 可视化

`status`: `done`
`priority`: `P3`
`sprint`: `Sprint 4 - 差异化`
`inspiration`: `GoView Pro(3D 模型) + ECharts GL + 科技感大屏需求`

## 目标

新增 3D 地球、3D 柱状图、3D 散点图等科技感可视化组件，提升大屏视觉冲击力。

## 子任务

### 1. 3D 地球（globe）

**新增组件类型**: `globe-chart`

**依赖**: `echarts-gl`（ECharts 3D 扩展）

**配置**:
```typescript
{
  type: 'globe-chart',
  config: {
    baseTexture: 'earth-blue' | 'earth-night' | 'custom-url',
    heightTexture?: string,
    scatterData: Array<{ name: string, coord: [lng, lat], value?: number }>,
    flowData: Array<{ from: [lng, lat], to: [lng, lat], value?: number }>,
    autoRotate: boolean,
    rotateSpeed: number,
  },
}
```

**特性**:
- 3D 地球自动旋转。
- 散点数据叠加。
- 飞线动画（from → to）。

### 2. 3D 柱状图

**新增组件类型**: `bar3d-chart`

**配置**:
```typescript
{
  type: 'bar3d-chart',
  config: {
    xAxisData: string[],
    yAxisData: string[],   // 第二维度
    data: Array<[x: number, y: number, value: number]>,
    colorRange: [min: string, max: string],
  },
}
```

### 3. 3D 散点图

**新增组件类型**: `scatter3d-chart`

**配置**: 三维坐标 `[x, y, z]` 数据。

### 4. 按需加载策略

- `echarts-gl` 体积较大（~500KB gzip），必须 dynamic import。
- 仅在用户添加 3D 组件时触发加载。
- 加载中展示 loading 状态。

```typescript
const [gl, setGl] = useState(null);
useEffect(() => {
  if (!needs3D) return;
  import('echarts-gl').then(mod => setGl(mod));
}, [needs3D]);
```

### 5. 属性面板

- 3D 组件属性：旋转速度、视角角度、光照方向。
- 地球组件：底图选择、散点/飞线数据配置。
- 柱状图：颜色范围、透视角度。

## Chrome 95 兼容性

- `echarts-gl` 依赖 WebGL，Chrome 95 支持 WebGL 2.0 ✅。
- 需检测 WebGL 支持：不支持时展示 fallback 提示。

```typescript
function isWebGLSupported(): boolean {
  try {
    const canvas = document.createElement('canvas');
    return !!(canvas.getContext('webgl') || canvas.getContext('webgl2'));
  } catch {
    return false;
  }
}
```

## 验收标准

- 3D 地球可旋转、展示散点和飞线。
- 3D 柱状图可透视查看。
- 按需加载不影响非 3D 组件性能。
- WebGL 不支持时展示友好提示。
- Chrome 95 下 WebGL 渲染正常。

## 风险与回滚

- 风险：3D 渲染在低端硬件上卡顿。
- 回滚：3D 组件提供"降级为 2D"选项，自动切换为对应 2D 图表。
