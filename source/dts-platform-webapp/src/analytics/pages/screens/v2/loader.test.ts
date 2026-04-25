import { describe, expect, it } from 'vitest';

import { tryLoadV2 } from './loader';

describe('tryLoadV2', () => {
	it('returns null when v2Spec is missing', () => {
		expect(tryLoadV2({ id: 1, name: 'legacy' })).toBeNull();
	});

	it('loads pages and layout from a v2 payload', () => {
		const config = tryLoadV2({
			id: 12,
			name: '响应式大屏',
			theme: 'enterprise-dark',
			backgroundColor: '#111827',
			pages: [{
				id: 'page_1',
				name: '首页',
				backgroundColor: '#0f172a',
				components: [{
					id: 'comp_1',
					type: 'title',
					layout: { x: 0, y: 0, w: 6, h: 2 },
					config: { text: '总览' },
					dataSource: {
						type: 'card',
						sourceType: 'card',
						cardConfig: { cardId: 21 },
					},
				}],
			}],
			globalVariables: [{ key: 'dept', label: '科室', type: 'string', defaultValue: '产科' }],
			v2Spec: {
				schemaVersion: 2,
				layout: { cols: 12, rowHeight: 'auto', gap: 16 },
				referenceViewport: { width: 1920, height: 1080 },
			},
		});

		expect(config).not.toBeNull();
		expect(config?.schemaVersion).toBe(2);
		expect(config?.layout.cols).toBe(12);
		expect(config?.referenceViewport).toEqual({ width: 1920, height: 1080 });
		expect(config?.pages).toHaveLength(1);
		expect(config?.pages?.[0]?.name).toBe('首页');
		expect(config?.pages?.[0]?.components?.[0]?.layout).toMatchObject({ x: 0, y: 0, w: 6, h: 2 });
		expect(config?.pages?.[0]?.components?.[0]?.dataSource).toEqual(expect.objectContaining({
			type: 'card',
			cardConfig: expect.objectContaining({ cardId: 21 }),
		}));
		expect(config?.globalVariables?.[0]?.key).toBe('dept');
	});
});
