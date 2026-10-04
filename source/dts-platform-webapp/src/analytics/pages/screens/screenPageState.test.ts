import assert from 'node:assert/strict';
import test from 'node:test';
import type { ScreenConfig } from './types';
import { commitScreenPageDraft, materializeScreenPage, resolveScreenPages, switchScreenPage } from './screenPageState';

const baseConfig: ScreenConfig = {
    id: 'screen-1',
    name: '多页大屏',
    width: 1920,
    height: 1080,
    backgroundColor: '#eef5fb',
    components: [],
    pages: [
        {
            id: 'page-overview',
            name: '总体态势',
            backgroundColor: '#eef5fb',
            components: [
                {
                    id: 'overview-title',
                    type: 'title',
                    name: '总体态势标题',
                    x: 40,
                    y: 40,
                    width: 360,
                    height: 48,
                    zIndex: 1,
                    locked: false,
                    visible: true,
                    config: { text: '总体态势' },
                },
            ],
        },
        {
            id: 'page-risk',
            name: '风险与变更',
            backgroundColor: '#f8fbff',
            components: [
                {
                    id: 'risk-title',
                    type: 'title',
                    name: '风险页标题',
                    x: 40,
                    y: 40,
                    width: 360,
                    height: 48,
                    zIndex: 1,
                    locked: false,
                    visible: true,
                    config: { text: '风险与变更' },
                },
            ],
        },
    ],
};

test('materializeScreenPage exposes page components for multi-page editor state', () => {
    const materialized = materializeScreenPage(baseConfig, 0);

    assert.equal(materialized.components.length, 1);
    assert.equal(materialized.components[0]?.id, 'overview-title');
    assert.equal(materialized.backgroundColor, '#eef5fb');
});

test('commitScreenPageDraft writes current working components back into the active page', () => {
    const working = {
        ...materializeScreenPage(baseConfig, 0),
        components: [
            {
                id: 'overview-kpi',
                type: 'number-card',
                name: '概览指标',
                x: 120,
                y: 120,
                width: 220,
                height: 100,
                zIndex: 2,
                locked: false,
                visible: true,
                config: { title: '项目总数' },
            },
        ],
    } satisfies ScreenConfig;

    const committed = commitScreenPageDraft(working, 0);

    assert.equal(committed.pages?.[0]?.components.length, 1);
    assert.equal(committed.pages?.[0]?.components[0]?.id, 'overview-kpi');
});

test('switchScreenPage preserves the source draft and projects the target page into working components', () => {
    const working = {
        ...materializeScreenPage(baseConfig, 0),
        components: [
            {
                id: 'overview-chart',
                type: 'line-chart',
                name: '推进趋势',
                x: 80,
                y: 220,
                width: 480,
                height: 260,
                zIndex: 2,
                locked: false,
                visible: true,
                config: { title: '推进趋势' },
            },
        ],
    } satisfies ScreenConfig;

    const switched = switchScreenPage(working, 0, 1);

    assert.equal(switched.pages?.[0]?.components[0]?.id, 'overview-chart');
    assert.equal(switched.components[0]?.id, 'risk-title');
    assert.equal(switched.backgroundColor, '#f8fbff');
});

test('resolveScreenPages wraps top-level components for legacy single-page screens', () => {
    const pages = resolveScreenPages({
        pages: undefined,
        components: [
            {
                id: 'single-title',
                type: 'title',
                name: '单页标题',
                x: 0,
                y: 0,
                width: 100,
                height: 30,
                zIndex: 1,
                locked: false,
                visible: true,
                config: { text: '单页' },
            },
        ],
    });

    assert.equal(pages.length, 1);
    assert.equal(pages[0]?.components[0]?.id, 'single-title');
});
