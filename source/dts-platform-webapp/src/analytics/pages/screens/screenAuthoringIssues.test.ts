import { describe, expect, it } from 'vitest';
import type { ScreenComponent, ScreenConfig } from './types';
import { deriveScreenAuthoringIssues } from './screenAuthoringIssues';

function component(overrides: Partial<ScreenComponent> = {}): ScreenComponent {
    return {
        id: 'chart-1',
        type: 'bar-chart',
        name: '销售趋势',
        x: 0,
        y: 0,
        width: 320,
        height: 180,
        zIndex: 1,
        locked: false,
        visible: true,
        config: {},
        ...overrides,
    };
}

function config(overrides: Partial<ScreenConfig> = {}): ScreenConfig {
    return {
        schemaVersion: 1,
        id: 'screen-1',
        name: '经营看板',
        width: 1920,
        height: 1080,
        backgroundColor: '#115aad',
        classification: 'INTERNAL',
        components: [],
        globalVariables: [],
        ...overrides,
    };
}

describe('deriveScreenAuthoringIssues', () => {
    it('reports governed data-source blockers with component location', () => {
        const issues = deriveScreenAuthoringIssues(config({
            components: [component({
                dataSource: {
                    type: 'api',
                    sourceType: 'api',
                    apiConfig: { url: '', method: 'GET' },
                },
            })],
        }));

        expect(issues).toEqual(expect.arrayContaining([
            expect.objectContaining({
                code: 'DATA_SOURCE_API_URL_MISSING',
                level: 'blocker',
                componentId: 'chart-1',
                tab: 'data',
            }),
            expect.objectContaining({
                code: 'DATA_SOURCE_IDENTITY_MISSING',
                level: 'blocker',
                componentId: 'chart-1',
                tab: 'data',
            }),
        ]));
    });

    it('maps page components and invalid interaction variables to their editing tabs', () => {
        const paged = component({
            id: 'chart-page-2',
            interaction: {
                enabled: true,
                mappings: [{ variableKey: '', sourcePath: 'name' }],
            },
        });
        const issues = deriveScreenAuthoringIssues(config({
            pages: [
                { id: 'page-1', name: '首页', components: [] },
                { id: 'page-2', name: '明细页', components: [paged] },
            ],
        }));

        expect(issues).toEqual(expect.arrayContaining([
            expect.objectContaining({
                level: 'blocker',
                componentId: 'chart-page-2',
                pageIndex: 1,
                tab: 'interaction',
            }),
        ]));
    });

    it('keeps a valid governed card source publishable', () => {
        const issues = deriveScreenAuthoringIssues(config({
            components: [component({
                dataSource: {
                    type: 'card',
                    sourceType: 'card',
                    cardConfig: { cardId: 42 },
                },
            })],
        }));

        expect(issues.filter((issue) => issue.level === 'blocker')).toHaveLength(0);
    });

    it('blocks publishing when the screen classification is missing', () => {
        const issues = deriveScreenAuthoringIssues(config({ classification: undefined }));

        expect(issues).toEqual(expect.arrayContaining([
            expect.objectContaining({
                code: 'SCREEN_CLASSIFICATION_MISSING',
                level: 'blocker',
                tab: 'style',
            }),
        ]));
    });
});
