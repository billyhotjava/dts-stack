import assert from 'node:assert/strict';
import test from 'node:test';
import { getTemplateById } from './screenTemplates';

test('project management cockpit template is not registered', () => {
    assert.equal(getTemplateById('project-management-cockpit'), undefined);
});
