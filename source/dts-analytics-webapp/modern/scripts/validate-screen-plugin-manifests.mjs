#!/usr/bin/env node

import { readdir, readFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const projectRoot = path.resolve(__dirname, '..');
const customManifestDir = path.join(projectRoot, 'src/pages/screens/plugins/custom');

const ID_PATTERN = /^[a-z][a-z0-9-]{1,63}$/;
const SEMVER_PATTERN = /^\d+\.\d+\.\d+(?:-[0-9A-Za-z.-]+)?(?:\+[0-9A-Za-z.-]+)?$/;
const PROPERTY_FIELD_TYPE_SET = new Set(["string", "number", "boolean", "color", "json", "array", "select"]);

function asArray(input) {
    return Array.isArray(input) ? input : [];
}

function normalizeManifests(json) {
    if (Array.isArray(json)) {
        return json;
    }
    if (json && typeof json === 'object' && Array.isArray(json.plugins)) {
        return json.plugins;
    }
    return [json];
}

function pushIssue(list, level, file, message) {
    list.push({ level, file, message });
}

function validatePropertySchema(pluginId, componentId, schema, file, issues) {
    if (!schema || typeof schema !== "object") {
        return;
    }
    const fields = Array.isArray(schema.fields) ? schema.fields : [];
    if (fields.length === 0) {
        return;
    }
    for (let i = 0; i < fields.length; i += 1) {
        const field = fields[i];
        if (!field || typeof field !== "object") {
            pushIssue(
                issues,
                "error",
                file,
                `component "${pluginId}:${componentId}" propertySchema.fields[${i}] is not an object`,
            );
            continue;
        }
        const fieldKey = String(field.key ?? "").trim();
        const fieldType = String(field.type ?? "").trim();
        if (!fieldKey) {
            pushIssue(
                issues,
                "error",
                file,
                `component "${pluginId}:${componentId}" has property field without key`,
            );
        }
        if (!PROPERTY_FIELD_TYPE_SET.has(fieldType)) {
            pushIssue(
                issues,
                "error",
                file,
                `component "${pluginId}:${componentId}" field "${fieldKey || `#${i}`}" has invalid type "${fieldType}"`,
            );
            continue;
        }
        if (fieldType === "select") {
            const options = Array.isArray(field.options) ? field.options : [];
            if (options.length === 0) {
                pushIssue(
                    issues,
                    "warning",
                    file,
                    `component "${pluginId}:${componentId}" field "${fieldKey || `#${i}`}" type=select has no options`,
                );
                continue;
            }
            for (let j = 0; j < options.length; j += 1) {
                const option = options[j];
                if (!option || typeof option !== "object") {
                    pushIssue(
                        issues,
                        "error",
                        file,
                        `component "${pluginId}:${componentId}" field "${fieldKey || `#${i}`}" option[${j}] is not an object`,
                    );
                    continue;
                }
                const optionLabel = String(option.label ?? "").trim();
                const optionValue = option.value;
                if (!optionLabel) {
                    pushIssue(
                        issues,
                        "error",
                        file,
                        `component "${pluginId}:${componentId}" field "${fieldKey || `#${i}`}" option[${j}] missing label`,
                    );
                }
                if (
                    typeof optionValue !== "string"
                    && typeof optionValue !== "number"
                    && typeof optionValue !== "boolean"
                ) {
                    pushIssue(
                        issues,
                        "error",
                        file,
                        `component "${pluginId}:${componentId}" field "${fieldKey || `#${i}`}" option[${j}] has non-primitive value`,
                    );
                }
            }
        }
    }
}

function validateComponent(pluginId, component, file, issues, globalComponentKeys, localComponentKeys) {
    if (!component || typeof component !== 'object') {
        pushIssue(issues, 'error', file, `plugin "${pluginId}" has non-object component entry`);
        return;
    }
    const id = String(component.id ?? '').trim();
    if (!id) {
        pushIssue(issues, 'error', file, `plugin "${pluginId}" has component without id`);
        return;
    }
    if (!ID_PATTERN.test(id)) {
        pushIssue(issues, 'error', file, `plugin "${pluginId}" component "${id}" is invalid (pattern: ${ID_PATTERN})`);
    }
    const runtimeKey = `${pluginId}:${id}`;
    if (localComponentKeys.has(runtimeKey)) {
        pushIssue(issues, 'error', file, `plugin "${pluginId}" has duplicated component "${id}"`);
    } else {
        localComponentKeys.add(runtimeKey);
    }
    if (globalComponentKeys.has(runtimeKey)) {
        pushIssue(issues, 'error', file, `runtime component id "${runtimeKey}" duplicated across manifests`);
    } else {
        globalComponentKeys.add(runtimeKey);
    }
    const baseType = String(component.baseType ?? '').trim();
    if (!baseType) {
        pushIssue(issues, 'warning', file, `component "${runtimeKey}" has no baseType; runtime fallback may be degraded`);
    } else if (!ID_PATTERN.test(baseType)) {
        pushIssue(issues, 'warning', file, `component "${runtimeKey}" baseType "${baseType}" is not normalized`);
    }
    const w = Number(component.defaultWidth ?? 0);
    const h = Number(component.defaultHeight ?? 0);
    validatePropertySchema(pluginId, id, component.propertySchema, file, issues);
    if (Number.isFinite(w) && w > 0 && Number.isFinite(h) && h > 0) {
        return;
    }
    pushIssue(issues, 'warning', file, `component "${runtimeKey}" missing positive defaultWidth/defaultHeight`);
}

function validatePlugin(manifest, file, issues, globalPluginIds, globalComponentKeys) {
    if (!manifest || typeof manifest !== 'object') {
        pushIssue(issues, 'error', file, 'manifest entry is not an object');
        return;
    }
    const pluginId = String(manifest.id ?? '').trim();
    if (!pluginId) {
        pushIssue(issues, 'error', file, 'manifest missing plugin id');
        return;
    }
    if (!ID_PATTERN.test(pluginId)) {
        pushIssue(issues, 'error', file, `plugin id "${pluginId}" is invalid (pattern: ${ID_PATTERN})`);
    }
    if (globalPluginIds.has(pluginId)) {
        pushIssue(issues, 'error', file, `plugin id "${pluginId}" duplicated across manifests`);
    } else {
        globalPluginIds.add(pluginId);
    }

    const version = String(manifest.version ?? '').trim();
    if (!version) {
        pushIssue(issues, 'warning', file, `plugin "${pluginId}" missing version`);
    } else if (!SEMVER_PATTERN.test(version)) {
        pushIssue(issues, 'warning', file, `plugin "${pluginId}" version "${version}" is not semver`);
    }

    const components = asArray(manifest.components);
    if (components.length === 0) {
        pushIssue(issues, 'warning', file, `plugin "${pluginId}" has no components`);
        return;
    }
    const localComponentKeys = new Set();
    for (const component of components) {
        validateComponent(pluginId, component, file, issues, globalComponentKeys, localComponentKeys);
    }
}

async function listManifestFiles(dir) {
    const entries = await readdir(dir, { withFileTypes: true });
    return entries
        .filter((entry) => entry.isFile() && entry.name.endsWith('.manifest.json'))
        .map((entry) => path.join(dir, entry.name))
        .sort();
}

async function listAdapterRuntimeKeys(dir) {
    const entries = await readdir(dir, { withFileTypes: true });
    const keys = new Set();
    for (const entry of entries) {
        if (!entry.isFile()) continue;
        if (!(entry.name.endsWith('.tsx') || entry.name.endsWith('.ts'))) continue;
        const stem = entry.name.replace(/\.(tsx|ts)$/i, '');
        const parts = stem.split('__');
        if (parts.length !== 2) continue;
        const pluginId = parts[0]?.trim();
        const componentId = parts[1]?.trim();
        if (!pluginId || !componentId) continue;
        keys.add(`${pluginId}:${componentId}`);
    }
    return keys;
}

async function loadJson(file) {
    const content = await readFile(file, 'utf8');
    return JSON.parse(content);
}

async function main() {
    const issues = [];
    const globalPluginIds = new Set();
    const globalComponentKeys = new Set();
    const localAdapterKeys = await listAdapterRuntimeKeys(customManifestDir).catch(() => new Set());
    const files = await listManifestFiles(customManifestDir).catch(() => []);
    if (files.length === 0) {
        console.log('[screen-plugin:validate] no local manifest found under src/pages/screens/plugins/custom');
        return;
    }

    for (const file of files) {
        try {
            const json = await loadJson(file);
            const manifests = normalizeManifests(json);
            if (!Array.isArray(manifests) || manifests.length === 0) {
                pushIssue(issues, 'error', file, 'manifest file has no usable entries');
                continue;
            }
            for (const manifest of manifests) {
                validatePlugin(manifest, file, issues, globalPluginIds, globalComponentKeys);
                const pluginId = String(manifest?.id ?? '').trim();
                const components = asArray(manifest?.components);
                if (!pluginId || components.length === 0) {
                    continue;
                }
                for (const component of components) {
                    const componentId = String(component?.id ?? '').trim();
                    if (!componentId) continue;
                    const runtimeKey = `${pluginId}:${componentId}`;
                    if (!localAdapterKeys.has(runtimeKey)) {
                        pushIssue(
                            issues,
                            'warning',
                            file,
                            `component "${runtimeKey}" has no local adapter file "${pluginId}__${componentId}.tsx/.ts"`,
                        );
                    }
                }
            }
        } catch (error) {
            pushIssue(issues, 'error', file, `invalid json: ${error instanceof Error ? error.message : String(error)}`);
        }
    }

    const errors = issues.filter((item) => item.level === 'error');
    const warnings = issues.filter((item) => item.level === 'warning');

    for (const issue of issues) {
        const rel = path.relative(projectRoot, issue.file);
        const tag = issue.level === 'error' ? 'ERROR' : 'WARN';
        console.log(`[screen-plugin:validate] ${tag} ${rel}: ${issue.message}`);
    }
    console.log(
        `[screen-plugin:validate] checked ${files.length} file(s), `
        + `${globalPluginIds.size} plugin(s), ${globalComponentKeys.size} component(s), `
        + `${errors.length} error(s), ${warnings.length} warning(s)`,
    );
    if (errors.length > 0) {
        process.exit(1);
    }
}

await main();
