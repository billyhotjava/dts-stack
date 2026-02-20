#!/usr/bin/env node

import { access, readdir, readFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const projectRoot = path.resolve(__dirname, '..');
const customPluginDir = path.join(projectRoot, 'src/pages/screens/plugins/custom');
const pluginRootDir = path.join(projectRoot, 'src/pages/screens/plugins');

function isRelativeImport(specifier) {
    return specifier.startsWith('./') || specifier.startsWith('../');
}

function normalizePathLike(specifier) {
    return specifier.replace(/\\/g, '/');
}

function collectImportSpecifiers(code) {
    const out = [];
    const importOrExportPattern = /(?:import|export)\s+(?:[\s\S]*?\s+from\s+)?["']([^"']+)["']/g;
    const dynamicImportPattern = /import\s*\(\s*["']([^"']+)["']\s*\)/g;
    let match = null;
    // eslint-disable-next-line no-cond-assign
    while ((match = importOrExportPattern.exec(code)) !== null) {
        out.push(match[1]);
    }
    // eslint-disable-next-line no-cond-assign
    while ((match = dynamicImportPattern.exec(code)) !== null) {
        out.push(match[1]);
    }
    return out;
}

async function pathExists(candidate) {
    try {
        await access(candidate);
        return true;
    } catch {
        return false;
    }
}

async function resolveImportPath(fromFile, specifier) {
    const base = path.resolve(path.dirname(fromFile), specifier);
    const candidates = [
        base,
        `${base}.ts`,
        `${base}.tsx`,
        `${base}.js`,
        `${base}.jsx`,
        path.join(base, 'index.ts'),
        path.join(base, 'index.tsx'),
        path.join(base, 'index.js'),
        path.join(base, 'index.jsx'),
    ];
    for (const candidate of candidates) {
        // eslint-disable-next-line no-await-in-loop
        if (await pathExists(candidate)) {
            return candidate;
        }
    }
    return base;
}

async function listCustomPluginSources(dir) {
    const entries = await readdir(dir, { withFileTypes: true }).catch(() => []);
    return entries
        .filter((entry) => entry.isFile() && (entry.name.endsWith('.ts') || entry.name.endsWith('.tsx')))
        .map((entry) => path.join(dir, entry.name))
        .sort();
}

async function main() {
    const files = await listCustomPluginSources(customPluginDir);
    if (files.length === 0) {
        console.log('[screen-plugin:boundary] no local custom plugin sources found');
        return;
    }

    const issues = [];
    for (const file of files) {
        // eslint-disable-next-line no-await-in-loop
        const code = await readFile(file, 'utf8');
        const specifiers = collectImportSpecifiers(code);
        for (const specifier of specifiers) {
            const normalized = normalizePathLike(String(specifier || '').trim());
            if (!normalized) {
                continue;
            }
            if (isRelativeImport(normalized)) {
                // eslint-disable-next-line no-await-in-loop
                const resolved = await resolveImportPath(file, normalized);
                const withinPluginRoot = resolved.startsWith(pluginRootDir);
                if (!withinPluginRoot) {
                    issues.push({
                        file,
                        specifier: normalized,
                        reason: 'relative import escapes src/pages/screens/plugins boundary',
                    });
                }
                continue;
            }
            if (
                normalized.includes('/pages/screens/components/')
                || normalized.includes('/pages/screens/Screen')
                || normalized.includes('pages/screens/components/')
            ) {
                issues.push({
                    file,
                    specifier: normalized,
                    reason: 'importing designer/renderer internals is forbidden for custom plugins',
                });
            }
        }
    }

    if (issues.length > 0) {
        for (const issue of issues) {
            const relFile = path.relative(projectRoot, issue.file);
            console.error(`[screen-plugin:boundary] ERROR ${relFile}: ${issue.specifier} -> ${issue.reason}`);
        }
        console.error(`[screen-plugin:boundary] failed with ${issues.length} violation(s)`);
        process.exit(1);
    }

    console.log(`[screen-plugin:boundary] checked ${files.length} file(s), no boundary violation`);
}

await main();
