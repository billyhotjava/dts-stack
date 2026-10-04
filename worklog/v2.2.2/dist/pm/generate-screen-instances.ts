#!/usr/bin/env npx tsx
/**
 * 从 gpmcTemplates.ts + gpmcDrillTemplates.ts 导出 6+5=11 个大屏实例 JSON
 * - 删除所有 staticData (mockData)
 * - 切换为 sqlConfig 引用查询卡片 SQL
 * - 输出到 screen-instances/ 目录
 *
 * 运行方式: cd source/dts-platform-webapp && npx tsx ../../worklog/v2.2.2/dist/pm/generate-screen-instances.ts
 */
import { gpmcTemplates } from '/opt/prod/s10/s10-stack/source/dts-platform-webapp/src/analytics/pages/screens/gpmcTemplates';
import { gpmcDrillTemplates } from '/opt/prod/s10/s10-stack/source/dts-platform-webapp/src/analytics/pages/screens/gpmcDrillTemplates';
import { writeFileSync, mkdirSync } from 'fs';
import { join, dirname } from 'path';
import { fileURLToPath } from 'url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const outDir = join(__dirname, 'screen-instances');
mkdirSync(outDir, { recursive: true });

// 查询卡片 SQL 映射: 根据组件 ID 前缀或名称匹配对应的查询卡片
const CARD_SQL_MAP: Record<string, { sqlFile: string; description: string }> = {
	// ─── 执行域 ───
	'card-execution-kpi-overview': { sqlFile: 'card-execution-kpi-overview.sql', description: '项目执行KPI总览' },
	'card-milestone-kpi': { sqlFile: 'card-milestone-kpi.sql', description: '里程碑KPI' },
	'card-incomplete-risk': { sqlFile: 'card-incomplete-risk.sql', description: '未完成风险' },
	'card-non-general-kpi': { sqlFile: 'card-non-general-kpi.sql', description: '非一般节点KPI' },
	'card-major-project-overview': { sqlFile: 'card-major-project-overview.sql', description: '重大项目概览' },
	'card-project-tree-snapshot': { sqlFile: 'card-project-tree-snapshot.sql', description: '项目树快照' },
	'card-delay-reason-trend': { sqlFile: 'card-delay-reason-trend.sql', description: '延期原因趋势' },
	'card-weekly-subproject-summary': { sqlFile: 'card-weekly-subproject-summary.sql', description: '子项目周汇总' },
	// ─── 质量域 ───
	'card-quality-kpi': { sqlFile: 'card-quality-kpi.sql', description: '质量域KPI' },
	'card-quality-period-summary': { sqlFile: 'card-quality-period-summary.sql', description: '质量周期汇总' },
	'card-quality-issue-list': { sqlFile: 'card-quality-issue-list.sql', description: '质量问题明细' },
	'card-quality-measure-list': { sqlFile: 'card-quality-measure-list.sql', description: '质量措施明细' },
	// ─── 技术状态域 ───
	'card-tech-state-kpi': { sqlFile: 'card-tech-state-kpi.sql', description: '技术状态KPI' },
	'card-tech-state-period-summary': { sqlFile: 'card-tech-state-period-summary.sql', description: '技术状态周期汇总' },
	'card-tech-state-list': { sqlFile: 'card-tech-state-list.sql', description: '技术状态明细' },
	'card-tech-state-measure-list': { sqlFile: 'card-tech-state-measure-list.sql', description: '技术状态措施明细' },
	// ─── 成本域 ───
	'card-cost-kpi': { sqlFile: 'card-cost-kpi.sql', description: '成本域KPI' },
	'card-cost-period-summary': { sqlFile: 'card-cost-period-summary.sql', description: '成本周期汇总' },
	'card-cost-detail-list': { sqlFile: 'card-cost-detail-list.sql', description: '成本明细' },
	// ─── 风险域 ───
	'card-risk-kpi': { sqlFile: 'card-risk-kpi.sql', description: '风险域KPI' },
	'card-risk-period-summary': { sqlFile: 'card-risk-period-summary.sql', description: '风险周期汇总' },
	'card-risk-info-list': { sqlFile: 'card-risk-info-list.sql', description: '风险明细' },
	'card-risk-measure-list': { sqlFile: 'card-risk-measure-list.sql', description: '风险措施明细' },
};

// 组件到查询卡片的映射规则（基于组件 type + name 关键词）
interface CardMapping {
	match: (comp: any) => boolean;
	cardKey: string;
}

const COMPONENT_CARD_MAPPINGS: CardMapping[] = [
	// ─── Screen 1: Strategic Overview ───
	// KPI cards and charts pull from multiple ADS tables
	{ match: c => c.name?.includes('项目') && c.name?.includes('总') && c.type === 'number-card', cardKey: 'card-execution-kpi-overview' },
	{ match: c => c.name?.includes('预算') || c.name?.includes('成本'), cardKey: 'card-cost-kpi' },
	{ match: c => c.name?.includes('风险') && !c.name?.includes('措施'), cardKey: 'card-risk-kpi' },
	{ match: c => c.name?.includes('质量') && !c.name?.includes('措施'), cardKey: 'card-quality-kpi' },
	{ match: c => c.name?.includes('技术状态') && !c.name?.includes('措施'), cardKey: 'card-tech-state-kpi' },
	{ match: c => c.name?.includes('延期') && c.type !== 'table', cardKey: 'card-delay-reason-trend' },
	{ match: c => c.name?.includes('里程碑'), cardKey: 'card-milestone-kpi' },
	{ match: c => c.name?.includes('项目状态') && c.type === 'table', cardKey: 'card-major-project-overview' },
	{ match: c => c.name?.includes('健康'), cardKey: 'card-major-project-overview' },

	// ─── Screen 2: Execution Board ───
	{ match: c => c.name?.includes('完成率') || c.name?.includes('达成率'), cardKey: 'card-execution-kpi-overview' },
	{ match: c => c.type === 'gantt-chart', cardKey: 'card-project-tree-snapshot' },
	{ match: c => c.name?.includes('工作量') || c.name?.includes('阶段分布'), cardKey: 'card-non-general-kpi' },
	{ match: c => c.name?.includes('延期') && c.type === 'table', cardKey: 'card-delay-reason-trend' },
	{ match: c => c.name?.includes('阻塞') || c.name?.includes('链路'), cardKey: 'card-incomplete-risk' },

	// ─── Screen 3: Quality Board ───
	{ match: c => c.name?.includes('归零') || c.name?.includes('闭环') && c.name?.includes('质量'), cardKey: 'card-quality-kpi' },
	{ match: c => c.name?.includes('质量') && c.name?.includes('排名'), cardKey: 'card-quality-period-summary' },
	{ match: c => c.name?.includes('质量') && c.name?.includes('问题') && c.type === 'table', cardKey: 'card-quality-issue-list' },
	{ match: c => c.name?.includes('质量') && c.name?.includes('措施') && c.type === 'table', cardKey: 'card-quality-measure-list' },

	// ─── Screen 4: Tech State Board ───
	{ match: c => c.name?.includes('签署') || c.name?.includes('更改'), cardKey: 'card-tech-state-kpi' },
	{ match: c => c.name?.includes('技术状态') && c.type === 'table' && c.name?.includes('措施'), cardKey: 'card-tech-state-measure-list' },
	{ match: c => c.name?.includes('技术状态') && c.type === 'table', cardKey: 'card-tech-state-list' },

	// ─── Screen 5: Cost Board ───
	{ match: c => c.name?.includes('部门') && c.name?.includes('预算'), cardKey: 'card-cost-period-summary' },
	{ match: c => c.name?.includes('偏差') && c.type === 'table', cardKey: 'card-cost-detail-list' },
	{ match: c => c.name?.includes('成本') && c.type === 'table', cardKey: 'card-cost-detail-list' },

	// ─── Screen 6: Risk Board ───
	{ match: c => c.name?.includes('风险') && c.name?.includes('矩阵'), cardKey: 'card-risk-period-summary' },
	{ match: c => c.name?.includes('风险') && c.name?.includes('措施') && c.type === 'table', cardKey: 'card-risk-measure-list' },
	{ match: c => c.name?.includes('风险') && c.type === 'table', cardKey: 'card-risk-info-list' },
];

import { readFileSync } from 'fs';

function readCardSqlSync(sqlFile: string): string {
	const path = join(__dirname, 'screen-queries', sqlFile);
	try {
		const content = readFileSync(path, 'utf-8');
		return content
			.split('\n')
			.filter(line => !line.startsWith('--'))
			.join('\n')
			.trim();
	} catch {
		return `SELECT 'TODO: load ${sqlFile}' AS placeholder`;
	}
}

function findCardForComponent(comp: any): string | null {
	for (const mapping of COMPONENT_CARD_MAPPINGS) {
		if (mapping.match(comp)) {
			return mapping.cardKey;
		}
	}
	return null;
}

// 处理单个组件：删除 staticData，添加 sqlConfig
function processComponent(comp: any): any {
	const result = { ...comp };

	// 只处理有 dataSource 且 type 为 static 的组件（有 mockData 的）
	if (result.dataSource?.type === 'static' || result.dataSource?.staticData) {
		const cardKey = findCardForComponent(result);
		if (cardKey && CARD_SQL_MAP[cardKey]) {
			const sqlContent = readCardSqlSync(CARD_SQL_MAP[cardKey].sqlFile);
			result.dataSource = {
				type: 'sql',
				sqlConfig: {
					databaseId: '{{DATABASE_ID}}',
					query: sqlContent,
					queryTimeoutSeconds: 30,
					maxRows: 2000,
				},
			};
		} else {
			// 没有匹配的卡片，清除 staticData 并标记
			delete result.dataSource.staticData;
			result.dataSource._note = 'TODO: 请绑定查询卡片';
		}
	}

	// 对于有 staticData 但没有 dataSource 的组件（chart config 中的 data）
	if (result.config?.data && Array.isArray(result.config.data)) {
		const cardKey = findCardForComponent(result);
		if (cardKey && CARD_SQL_MAP[cardKey]) {
			const sqlContent = readCardSqlSync(CARD_SQL_MAP[cardKey].sqlFile);
			result.dataSource = {
				type: 'sql',
				sqlConfig: {
					databaseId: '{{DATABASE_ID}}',
					query: sqlContent,
					queryTimeoutSeconds: 30,
					maxRows: 2000,
				},
			};
			delete result.config.data;
		}
	}

	return result;
}

// 下钻模板的组件按 screen ID 前缀匹配查询卡片
const DRILL_SCREEN_CARD_MAP: Record<string, string> = {
	'gpmc-drill-execution': 'card-execution-kpi-overview',
	'gpmc-drill-quality': 'card-quality-kpi',
	'gpmc-drill-tech-state': 'card-tech-state-kpi',
	'gpmc-drill-cost': 'card-cost-kpi',
	'gpmc-drill-risk': 'card-risk-kpi',
};

const DRILL_TABLE_CARD_MAP: Record<string, Record<string, string>> = {
	'gpmc-drill-execution': {
		'detail': 'card-project-tree-snapshot',
		'side': 'card-weekly-subproject-summary',
		'support': 'card-delay-reason-trend',
	},
	'gpmc-drill-quality': {
		'detail': 'card-quality-issue-list',
		'side': 'card-quality-kpi',
		'support': 'card-quality-measure-list',
	},
	'gpmc-drill-tech-state': {
		'detail': 'card-tech-state-list',
		'side': 'card-tech-state-kpi',
		'support': 'card-tech-state-measure-list',
	},
	'gpmc-drill-cost': {
		'detail': 'card-cost-period-summary',
		'side': 'card-cost-kpi',
		'support': 'card-cost-kpi',
	},
	'gpmc-drill-risk': {
		'detail': 'card-risk-info-list',
		'side': 'card-risk-period-summary',
		'support': 'card-risk-measure-list',
	},
};

function processDrillComponent(comp: any, screenId: string): any {
	const result = { ...comp };
	const compId = comp.id || '';

	// 判断组件角色（KPI / detail / side / support）
	let cardKey: string | null = null;
	if (comp.type === 'number-card') {
		cardKey = DRILL_SCREEN_CARD_MAP[screenId] || null;
	} else if (comp.type === 'table') {
		const tableMap = DRILL_TABLE_CARD_MAP[screenId];
		if (tableMap) {
			if (compId.includes('detail')) cardKey = tableMap['detail'];
			else if (compId.includes('side')) cardKey = tableMap['side'];
			else if (compId.includes('support')) cardKey = tableMap['support'];
			else cardKey = tableMap['detail']; // fallback
		}
	}

	if (cardKey && CARD_SQL_MAP[cardKey]) {
		const sqlContent = readCardSqlSync(CARD_SQL_MAP[cardKey].sqlFile);
		result.dataSource = {
			type: 'sql',
			sqlConfig: {
				databaseId: '{{DATABASE_ID}}',
				query: sqlContent,
				queryTimeoutSeconds: 30,
				maxRows: 2000,
			},
		};
		// 清除 dataSource 中的 staticData，但保留 config.data（表格内联数据，作为默认展示）
		if (result.dataSource?.staticData) delete result.dataSource.staticData;
	}

	// 清除残留 staticData
	if (result.dataSource?.type === 'static') {
		delete result.dataSource.staticData;
	}

	return result;
}

// 处理全部模板（6 主屏 + 5 下钻）
const allTemplates = [...gpmcTemplates, ...gpmcDrillTemplates];
for (const template of allTemplates) {
	const isDrill = template.id.startsWith('gpmc-drill-');
	const screenConfig = {
		schemaVersion: 2,
		id: template.id,
		name: template.name + ' (实例)',
		description: template.description,
		width: template.config.width,
		height: template.config.height,
		backgroundColor: template.config.backgroundColor,
		theme: template.config.theme || 'enterprise-light',
		globalVariables: template.config.globalVariables || [],
		components: (template.config.components || []).map(c =>
			isDrill ? processDrillComponent(c, template.id) : processComponent(c)
		),
	};

	const filename = `${template.id}.json`;
	const filepath = join(outDir, filename);
	writeFileSync(filepath, JSON.stringify(screenConfig, null, 2), 'utf-8');
	console.log(`Generated: ${filename} (${screenConfig.components.length} components)`);
}

console.log('\nDone! Files in:', outDir);
