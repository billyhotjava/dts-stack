import assert from 'node:assert/strict';
import test from 'node:test';
import React from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { StatCardBasic } from './renderers/basic/ResponsiveText';
import { getThemeTokens } from './screenThemes';

test('StatCardBasic renders prefix and suffix around the metric value', () => {
    const html = renderToStaticMarkup(
        <StatCardBasic
            c={{
                title: '预算执行',
                value: '91.2',
                prefix: '¥',
                suffix: '万',
                trend: 'up',
                trendValue: '+3.1%',
            }}
            t={getThemeTokens('enterprise-light')}
        />,
    );

    assert.match(html, /预算执行/);
    assert.match(html, /¥/);
    assert.match(html, /91\.2/);
    assert.match(html, /万/);
});
