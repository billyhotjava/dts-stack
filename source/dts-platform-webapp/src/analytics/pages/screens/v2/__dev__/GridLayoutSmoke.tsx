/**
 * Sprint-12 F1/T01 smoke 页 — 仅开发环境访问，用于验证 react-grid-layout
 * 在项目内可运行 + Chrome 95 兼容。
 *
 * 访问路径：/analytics/screens/__dev__/grid-smoke（仅挂在 dev 路由）。
 * 通过后可删除（或保留做迭代排查）。
 */
import { useState } from 'react';
import GridLayout, { type Layout } from 'react-grid-layout';
import '../responsiveLayout.css';

const INITIAL: Layout[] = [
    { i: 'kpi-1', x: 0, y: 0, w: 3, h: 2, minW: 2, minH: 1 },
    { i: 'kpi-2', x: 3, y: 0, w: 3, h: 2, minW: 2, minH: 1 },
    { i: 'chart', x: 0, y: 2, w: 8, h: 4, minW: 4, minH: 3 },
    { i: 'table', x: 8, y: 0, w: 4, h: 6, minW: 3, minH: 3 },
];

export default function GridLayoutSmoke() {
    const [layout, setLayout] = useState<Layout[]>(INITIAL);
    const [viewportWidth, setViewportWidth] = useState(window.innerWidth);

    return (
        <div style={{ padding: 16, color: '#e5e7eb', background: '#0f172a', minHeight: '100vh' }}>
            <h1 style={{ marginBottom: 8 }}>v2 Grid Layout Smoke</h1>
            <p style={{ marginBottom: 16, opacity: 0.7 }}>
                viewport: {viewportWidth}px — 拖动 / resize / 刷新窗口验证。
                状态：Chrome 95 兼容已通过静态检查。
            </p>
            <button
                onClick={() => setViewportWidth(window.innerWidth)}
                style={{ marginBottom: 16, padding: '4px 12px' }}
            >
                刷新 viewport 显示
            </button>
            <GridLayout
                className="v2-screen-grid"
                layout={layout}
                cols={12}
                rowHeight={40}
                width={viewportWidth - 32}
                onLayoutChange={setLayout}
                draggableHandle=".smoke-handle"
                compactType={null}
                preventCollision={false}
            >
                {INITIAL.map((item) => (
                    <div
                        key={item.i}
                        style={{
                            background: 'rgba(37, 99, 235, 0.15)',
                            border: '1px solid rgba(37, 99, 235, 0.5)',
                            borderRadius: 6,
                            padding: 12,
                            boxSizing: 'border-box',
                        }}
                    >
                        <div
                            className="smoke-handle"
                            style={{
                                cursor: 'move',
                                marginBottom: 8,
                                fontWeight: 600,
                                fontSize: 14,
                            }}
                        >
                            ⋮⋮ {item.i}
                        </div>
                        <div style={{ fontSize: 12, opacity: 0.7 }}>
                            {item.w} × {item.h}
                        </div>
                    </div>
                ))}
            </GridLayout>
        </div>
    );
}
