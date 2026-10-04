import assert from 'node:assert/strict';
import test from 'node:test';
import { parseRuntimeTargetsInput } from './TemplateGallery.helpers';

test('parseRuntimeTargetsInput parses valid runtime probe rows and skips invalid rows', () => {
    const targets = parseRuntimeTargetsInput(`
        analytics,http,127.0.0.1,3000,/analytics,true,200-499,metabase
        # comment
        edge-mqtt,mqtt,127.0.0.1,1883,,false,,
        invalid-row
    `);

    assert.equal(targets.length, 2);
    assert.deepEqual(targets[0], {
        id: 'analytics',
        protocol: 'http',
        host: '127.0.0.1',
        port: 3000,
        required: true,
        path: '/bi',
        expectedStatus: '200-499',
        expectedBodyContains: 'metabase',
    });
    assert.deepEqual(targets[1], {
        id: 'edge-mqtt',
        protocol: 'mqtt',
        host: '127.0.0.1',
        port: 1883,
        required: false,
    });
});
