import { describe, it, expect } from "vitest";
import { migrateV1ToV2 } from "./migrate";
import type { ScreenComponent, ScreenConfig } from "../types";

function makeComponent(overrides: Partial<ScreenComponent>): ScreenComponent {
    return {
        id: "c1",
        type: "line-chart",
        name: "Chart",
        x: 0,
        y: 0,
        width: 480,
        height: 320,
        zIndex: 1,
        locked: false,
        visible: true,
        config: {},
        ...overrides,
    };
}

function makeConfig(overrides: Partial<ScreenConfig>): ScreenConfig {
    return {
        id: "s1",
        name: "test",
        width: 1920,
        height: 1080,
        components: [],
        ...overrides,
    } as ScreenConfig;
}

describe("migrateV1ToV2", () => {
    it("maps 1920x1080 components to 12-col grid", () => {
        const v1 = makeConfig({
            components: [
                // 整左上角：x=0 y=0, 480x320 → 3 列 × 8 行
                makeComponent({ id: "a", x: 0, y: 0, width: 480, height: 320 }),
                // 右上角：x=1440 y=0 → 第 9 列起，480x320 → 3×8
                makeComponent({ id: "b", x: 1440, y: 0, width: 480, height: 320 }),
            ],
        });
        const { config, warnings } = migrateV1ToV2(v1);
        expect(config.schemaVersion).toBe(2);
        expect(config.layout.cols).toBe(12);
        expect(config.components).toHaveLength(2);
        expect(config.components[0].layout).toEqual(expect.objectContaining({ x: 0, y: 0, w: 3, h: 8 }));
        expect(config.components[1].layout).toEqual(expect.objectContaining({ x: 9, y: 0, w: 3, h: 8 }));
        expect(warnings).toHaveLength(0);
    });

    it("clamps negative coordinates to 0 with warning", () => {
        const v1 = makeConfig({
            components: [makeComponent({ id: "a", x: -100, y: -50, width: 320, height: 160 })],
        });
        const { config, warnings } = migrateV1ToV2(v1);
        expect(config.components[0].layout.x).toBe(0);
        expect(config.components[0].layout.y).toBe(0);
        expect(warnings.some((w) => w.includes("x=-100"))).toBe(true);
        expect(warnings.some((w) => w.includes("y=-50"))).toBe(true);
    });

    it("clamps oversize width to cols with warning", () => {
        const v1 = makeConfig({
            components: [makeComponent({ id: "a", x: 0, y: 0, width: 9999, height: 200 })],
        });
        const { config, warnings } = migrateV1ToV2(v1);
        expect(config.components[0].layout.w).toBe(12);
        expect(warnings.some((w) => w.includes("超过画布宽度"))).toBe(true);
    });

    it("clamps right-side overflow of x", () => {
        const v1 = makeConfig({
            components: [
                // x=1800 + width=480 超出 1920；x_grid=round(1800/160)=11, w=3, xMax=9 → clamp to 9
                makeComponent({ id: "a", x: 1800, y: 0, width: 480, height: 120 }),
            ],
        });
        const { config, warnings } = migrateV1ToV2(v1);
        const lay = config.components[0].layout;
        expect(lay.x + lay.w).toBeLessThanOrEqual(12);
        expect(warnings.some((w) => w.includes("右侧溢出"))).toBe(true);
    });

    it("uses minimum w=1 h=1 for zero/invalid sizes", () => {
        const v1 = makeConfig({
            components: [makeComponent({ id: "a", x: 100, y: 100, width: 0, height: 0 })],
        });
        const { config } = migrateV1ToV2(v1);
        expect(config.components[0].layout.w).toBe(1);
        expect(config.components[0].layout.h).toBe(1);
    });

    it("preserves id/type/name/config/visible/zIndex", () => {
        const v1 = makeConfig({
            components: [
                makeComponent({
                    id: "keep-me",
                    type: "bar-chart",
                    name: "Sales",
                    x: 0,
                    y: 0,
                    width: 320,
                    height: 200,
                    zIndex: 5,
                    visible: false,
                    config: { title: "Foo" },
                }),
            ],
        });
        const { config } = migrateV1ToV2(v1);
        const c = config.components[0];
        expect(c.id).toBe("keep-me");
        expect(c.type).toBe("bar-chart");
        expect(c.name).toBe("Sales");
        expect(c.config).toEqual({ title: "Foo" });
        expect(c.visible).toBe(false);
        expect(c.zIndex).toBe(5);
    });

    it("produces valid layout with custom cols/rowHeight", () => {
        const v1 = makeConfig({
            width: 2400,
            height: 1200,
            components: [makeComponent({ id: "a", x: 0, y: 0, width: 600, height: 300 })],
        });
        const { config } = migrateV1ToV2(v1, { cols: 24, rowHeightPx: 20, gap: 4 });
        expect(config.layout.cols).toBe(24);
        expect(config.layout.rowHeight).toBe(20);
        expect(config.layout.gap).toBe(4);
        // 600 / (2400/24) = 6；300 / 20 = 15
        expect(config.components[0].layout.w).toBe(6);
        expect(config.components[0].layout.h).toBe(15);
    });

    it("keeps referenceViewport based on v1 width/height", () => {
        const v1 = makeConfig({ width: 2560, height: 1440 });
        const { config } = migrateV1ToV2(v1);
        expect(config.referenceViewport).toEqual({ width: 2560, height: 1440 });
    });

    it("preserves v1 pages when migrating to v2", () => {
        const v1 = makeConfig({
            components: [makeComponent({ id: "root", x: 0, y: 0, width: 320, height: 160 })],
            pages: [
                {
                    id: "page-1",
                    name: "第一页",
                    backgroundColor: "#102030",
                    components: [makeComponent({ id: "page-comp", x: 480, y: 80, width: 640, height: 240 })],
                },
            ],
        });
        const { config } = migrateV1ToV2(v1);
        expect(config.pages).toHaveLength(1);
        expect(config.pages?.[0].id).toBe("page-1");
        expect(config.pages?.[0].backgroundColor).toBe("#102030");
        expect(config.pages?.[0].components[0].layout).toEqual(expect.objectContaining({ x: 3, y: 2, w: 4, h: 6 }));
    });

    it("preserves v1 dataSource and interaction fields", () => {
        const v1 = makeConfig({
            components: [
                makeComponent({
                    id: "with-data",
                    dataSource: {
                        type: "sql",
                        sourceType: "sql",
                        sqlConfig: { query: "select * from demo", databaseId: 3 },
                    },
                    interaction: {
                        enabled: true,
                        mappings: [{ variableKey: "dept", sourcePath: "name" }],
                    },
                }),
            ],
        });
        const { config } = migrateV1ToV2(v1);
        expect(config.components[0].dataSource).toEqual(expect.objectContaining({
            type: "sql",
            sqlConfig: expect.objectContaining({ query: "select * from demo", databaseId: 3 }),
        }));
        expect(config.components[0].interaction).toEqual(expect.objectContaining({
            enabled: true,
            mappings: expect.any(Array),
        }));
    });
});
