#!/usr/bin/env node
import { readFileSync, writeFileSync } from "node:fs";
import { resolve } from "node:path";

const SOURCE_FILE = resolve(process.cwd(), "src/_mock/assets_backup.ts");
const OUTPUT_FILE = resolve(process.cwd(), "menu-demo.json");

function extractMenuSource(text) {
	const match = text.match(/export const DB_MENU[^=]*=\s*(\[[\s\S]*?\n\]);/);
	if (!match) {
		throw new Error("Unable to locate DB_MENU definition in assets_backup.ts");
	}
	return match[1];
}

function stripComments(source) {
	return source.replace(/\/\/[\s\S]*?$/gm, "");
}

function parseMenu(source) {
	const sanitized = stripComments(source);
	return Function("GROUP", "CATALOGUE", "MENU", `return ${sanitized};`)(0, 1, 2);
}

function buildMenuTree(items) {
	const nodes = new Map();
	const roots = [];

	for (const item of items) {
		nodes.set(item.id, { ...item, children: [] });
	}

	for (const item of items) {
		const node = nodes.get(item.id);
		if (!node) continue;

		if (!item.parentId) {
			roots.push(node);
			continue;
		}

		const parent = nodes.get(item.parentId);
		if (parent) {
			parent.children.push(node);
		} else {
			roots.push(node);
		}
	}

	return roots;
}

function main() {
	const fileContent = readFileSync(SOURCE_FILE, "utf8");
	const arraySource = extractMenuSource(fileContent);
	const menuItems = parseMenu(arraySource);
	const menuTree = buildMenuTree(menuItems);
	writeFileSync(OUTPUT_FILE, `${JSON.stringify(menuTree, null, 2)}\n`, "utf8");
	console.log(`menu exported to ${OUTPUT_FILE}`);
}

main();
