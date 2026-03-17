import assert from 'node:assert/strict';
import test from 'node:test';
import { readFileSync } from 'node:fs';

const composeFiles = [
    'docker-compose-app.yml',
    'docker-compose.dev.yml',
    'docker-compose.legacy.yml',
];

for (const file of composeFiles) {
    test(`${file} keeps analytics public router anonymous while protecting private api routers`, () => {
        const content = readFileSync(new URL(`../../${file}`, import.meta.url), 'utf8');

        assert.match(
            content,
            /traefik\.http\.routers\.dts-analytics-api\.middlewares=.*platform-forward-auth@file/,
        );
        assert.match(
            content,
            /traefik\.http\.routers\.dts-analytics-embed\.middlewares=.*platform-forward-auth@file/,
        );
        assert.doesNotMatch(
            content,
            /traefik\.http\.routers\.dts-analytics-public\.middlewares=.*platform-forward-auth@file/,
        );
        assert.match(
            content,
            /traefik\.http\.routers\.dts-analytics-public\.middlewares=.*dts-strip-analytics-api@docker/,
        );
    });
}
