#!/usr/bin/env node

import { access, mkdir, writeFile } from "node:fs/promises";
import path from "node:path";
import process from "node:process";

const ID_PATTERN = /^[a-z][a-z0-9-]{1,63}$/;

function parseArgs(argv) {
  const out = {
    pluginId: "",
    componentId: "",
    componentName: "",
    force: false,
    dryRun: false,
    help: false,
  };
  for (let i = 0; i < argv.length; i += 1) {
    const arg = argv[i];
    if (arg === "--plugin-id") {
      out.pluginId = String(argv[i + 1] || "");
      i += 1;
      continue;
    }
    if (arg === "--component-id") {
      out.componentId = String(argv[i + 1] || "");
      i += 1;
      continue;
    }
    if (arg === "--component-name") {
      out.componentName = String(argv[i + 1] || "");
      i += 1;
      continue;
    }
    if (arg === "--force") {
      out.force = true;
      continue;
    }
    if (arg === "--dry-run") {
      out.dryRun = true;
      continue;
    }
    if (arg === "--help" || arg === "-h") {
      out.help = true;
      continue;
    }
  }
  return out;
}

async function exists(filePath) {
  try {
    await access(filePath);
    return true;
  } catch {
    return false;
  }
}

function fail(message) {
  console.error(`[screen-plugin-scaffold] ${message}`);
  process.exit(1);
}

function printHelp() {
  console.log(`Usage:
  node scripts/scaffold-screen-plugin.mjs --plugin-id demo-stat-pack --component-id line-pro [--component-name "增强折线图"] [--force] [--dry-run]
`);
}

function buildAdapterTemplate(pluginId, componentId, componentName) {
  const defaultVersion = "1.0.0";
  return `import type { RendererPlugin } from "../types";

export function createPlugin(
  pluginId = "${pluginId}",
  componentId = "${componentId}",
  version = "${defaultVersion}",
): RendererPlugin {
  const runtimeId = \`\${pluginId}:\${componentId}@\${version}\`;
  return {
    id: runtimeId,
    version,
    name: "${escapeTemplateText(componentName)}",
    baseType: "line-chart",
    defaultWidth: 520,
    defaultHeight: 320,
    defaultConfig: {
      title: "${escapeTemplateText(componentName)}",
    },
    propertySchema: {
      fields: [
        { key: "title", label: "标题", type: "string", placeholder: "请输入标题" },
        {
          key: "mode",
          label: "展示模式",
          type: "select",
          defaultValue: "card",
          options: [
            { label: "卡片", value: "card" },
            { label: "紧凑", value: "compact" },
          ],
        },
      ],
    },
    dataContract: {
      kind: "table",
      description: "rows/cols dataframe",
    },
    render: ({ config, width, height }) => (
      <div
        style={{
          width,
          height,
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
          border: "1px dashed rgba(148,163,184,0.4)",
          color: "#94a3b8",
          fontSize: 12,
        }}
      >
        {String(config.title || "${escapeTemplateText(componentName)}")} · {String(config.mode || "card")}
      </div>
    ),
  };
}
`;
}

function buildManifestTemplate(pluginId, componentId, componentName) {
  return JSON.stringify(
    {
      id: pluginId,
      name: pluginId,
      version: "1.0.0",
      enabled: true,
      signatureRequired: false,
      components: [
        {
          id: componentId,
          name: componentName,
          icon: "🧩",
          baseType: "chart",
          defaultWidth: 520,
          defaultHeight: 320,
          defaultConfig: { title: componentName },
          propertySchema: {
            fields: [
              { key: "title", label: "标题", type: "string", placeholder: "请输入标题" },
              {
                key: "mode",
                label: "展示模式",
                type: "select",
                defaultValue: "card",
                options: [
                  { label: "卡片", value: "card" },
                  { label: "紧凑", value: "compact" },
                ],
              },
            ],
          },
          dataContract: { kind: "table", description: "rows/cols dataframe" },
        },
      ],
      dataSources: [],
    },
    null,
    2,
  );
}

function toPascalCase(value) {
  return String(value || "")
    .split(/[^a-zA-Z0-9]+/)
    .filter((part) => part.length > 0)
    .map((part) => part[0].toUpperCase() + part.slice(1))
    .join("");
}

function escapeTemplateText(value) {
  return String(value || "").replaceAll("\\", "\\\\").replaceAll('"', '\\"');
}

async function main() {
  const args = parseArgs(process.argv.slice(2));
  if (args.help) {
    printHelp();
    return;
  }
  if (!ID_PATTERN.test(args.pluginId)) {
    fail("invalid --plugin-id, expected /^[a-z][a-z0-9-]{1,63}$/");
  }
  if (!ID_PATTERN.test(args.componentId)) {
    fail("invalid --component-id, expected /^[a-z][a-z0-9-]{1,63}$/");
  }
  const componentName = args.componentName.trim() || `${args.pluginId}-${args.componentId}`;

  const customDir = path.resolve(process.cwd(), "src/pages/screens/plugins/custom");
  const fileStem = `${args.pluginId}__${args.componentId}`;
  const adapterPath = path.join(customDir, `${fileStem}.tsx`);
  const manifestPath = path.join(customDir, `${fileStem}.manifest.json`);

  if (!args.force) {
    if (await exists(adapterPath)) {
      fail(`adapter already exists: ${adapterPath} (use --force to overwrite)`);
    }
    if (await exists(manifestPath)) {
      fail(`manifest already exists: ${manifestPath} (use --force to overwrite)`);
    }
  }

  const adapterContent = buildAdapterTemplate(args.pluginId, args.componentId, componentName);
  const manifestContent = buildManifestTemplate(args.pluginId, args.componentId, componentName);

  if (args.dryRun) {
    console.log("[screen-plugin-scaffold] dry-run");
    console.log(`would write: ${adapterPath}`);
    console.log(`would write: ${manifestPath}`);
    return;
  }

  await mkdir(customDir, { recursive: true });
  await writeFile(adapterPath, adapterContent, "utf8");
  await writeFile(manifestPath, manifestContent, "utf8");

  console.log("[screen-plugin-scaffold] generated files:");
  console.log(`- ${adapterPath}`);
  console.log(`- ${manifestPath}`);
  console.log("");
  console.log("Next steps:");
  console.log("1. Wire adapter into builtinPluginAdapters.tsx (or runtime loader).");
  console.log("2. Add plugin manifest item in backend /api/screen-plugins list.");
  console.log("3. Drag plugin component from component library to verify rendering.");
}

void main();
