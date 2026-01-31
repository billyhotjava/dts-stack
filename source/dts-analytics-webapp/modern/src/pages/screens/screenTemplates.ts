import type { ScreenConfig, ScreenComponent } from './types';

/**
 * Screen Template definition
 */
export interface ScreenTemplate {
    id: string;
    name: string;
    description: string;
    thumbnail: string; // emoji or icon
    category: 'business' | 'tech' | 'dashboard' | 'monitor';
    config: Omit<ScreenConfig, 'id'>;
}

// Helper to create component with unique ID
function createComponent(
    id: string,
    type: ScreenComponent['type'],
    name: string,
    x: number,
    y: number,
    width: number,
    height: number,
    zIndex: number,
    config: Record<string, unknown>
): ScreenComponent {
    return {
        id,
        type,
        name,
        x,
        y,
        width,
        height,
        zIndex,
        locked: false,
        visible: true,
        config,
    };
}

/**
 * 内置模板：科技数据中心
 * 深蓝科技风格大屏，适合展示核心业务指标
 */
const techDataCenterTemplate: ScreenTemplate = {
    id: 'tech-data-center',
    name: '科技数据中心',
    description: '深蓝科技风格大屏，适合展示核心业务指标和实时数据监控',
    thumbnail: '🌐',
    category: 'tech',
    config: {
        name: '科技数据中心',
        description: '数据可视化大屏',
        width: 1920,
        height: 1080,
        backgroundColor: '#0a0e27',
        components: [
            // ===== 顶部区域 =====
            // 主标题
            createComponent('title-main', 'title', '主标题', 760, 15, 400, 60, 100, {
                text: '智能数据监控中心',
                fontSize: 42,
                fontWeight: 'bold',
                color: '#00d4ff',
                textAlign: 'center',
            }),
            // 标题装饰
            createComponent('deco-title-left', 'decoration', '标题装饰左', 300, 35, 400, 40, 99, {
                decorationType: 3,
                color: ['#00d4ff', '#0066ff'],
            }),
            createComponent('deco-title-right', 'decoration', '标题装饰右', 1220, 35, 400, 40, 99, {
                decorationType: 3,
                color: ['#00d4ff', '#0066ff'],
            }),
            // 日期时间
            createComponent('datetime-top', 'datetime', '日期时间', 1650, 25, 230, 40, 98, {
                format: 'YYYY-MM-DD HH:mm:ss',
                fontSize: 18,
                color: '#66ccff',
            }),

            // ===== 顶部数据卡片区 =====
            createComponent('card-1', 'number-card', '总用户数', 60, 100, 240, 100, 50, {
                title: '总用户数',
                value: 1285634,
                prefix: '',
                suffix: '',
                precision: 0,
                titleColor: '#66ccff',
                valueColor: '#00ffcc',
                backgroundColor: 'rgba(0, 100, 200, 0.15)',
            }),
            createComponent('card-2', 'number-card', '在线用户', 320, 100, 240, 100, 50, {
                title: '实时在线',
                value: 42568,
                prefix: '',
                suffix: '',
                precision: 0,
                titleColor: '#66ccff',
                valueColor: '#00ff88',
                backgroundColor: 'rgba(0, 100, 200, 0.15)',
            }),
            createComponent('card-3', 'number-card', '今日交易', 580, 100, 240, 100, 50, {
                title: '今日交易额',
                value: 8956234,
                prefix: '¥',
                suffix: '',
                precision: 0,
                titleColor: '#66ccff',
                valueColor: '#ffcc00',
                backgroundColor: 'rgba(0, 100, 200, 0.15)',
            }),
            createComponent('card-4', 'number-card', '系统负载', 840, 100, 240, 100, 50, {
                title: '系统负载',
                value: 67.5,
                prefix: '',
                suffix: '%',
                precision: 1,
                titleColor: '#66ccff',
                valueColor: '#ff6600',
                backgroundColor: 'rgba(0, 100, 200, 0.15)',
            }),
            createComponent('card-5', 'number-card', 'API调用', 1100, 100, 240, 100, 50, {
                title: 'API调用次数',
                value: 15678923,
                prefix: '',
                suffix: '',
                precision: 0,
                titleColor: '#66ccff',
                valueColor: '#cc66ff',
                backgroundColor: 'rgba(0, 100, 200, 0.15)',
            }),
            createComponent('card-6', 'number-card', '成功率', 1360, 100, 240, 100, 50, {
                title: '服务成功率',
                value: 99.97,
                prefix: '',
                suffix: '%',
                precision: 2,
                titleColor: '#66ccff',
                valueColor: '#00ffcc',
                backgroundColor: 'rgba(0, 100, 200, 0.15)',
            }),
            createComponent('card-7', 'number-card', '新增用户', 1620, 100, 240, 100, 50, {
                title: '今日新增',
                value: 3256,
                prefix: '+',
                suffix: '',
                precision: 0,
                titleColor: '#66ccff',
                valueColor: '#66ff66',
                backgroundColor: 'rgba(0, 100, 200, 0.15)',
            }),

            // ===== 左侧区域 =====
            // 左侧边框
            createComponent('border-left', 'border-box', '左边框', 30, 220, 450, 420, 10, {
                boxType: 7,
                color: ['#00d4ff', '#0066ff'],
            }),
            // 左侧小标题
            createComponent('title-left', 'title', '流量趋势', 50, 235, 150, 30, 30, {
                text: '📈 流量趋势',
                fontSize: 18,
                fontWeight: 'bold',
                color: '#00d4ff',
                textAlign: 'left',
            }),
            // 折线图
            createComponent('chart-line', 'line-chart', '流量趋势图', 45, 270, 420, 350, 20, {
                title: '',
                xAxisData: ['00:00', '04:00', '08:00', '12:00', '16:00', '20:00', '24:00'],
                series: [
                    { name: '今日', data: [1200, 800, 2400, 4800, 3600, 5200, 4100] },
                    { name: '昨日', data: [1000, 700, 2000, 4200, 3200, 4800, 3800] },
                ],
                lineSmooth: true,
                areaStyle: true,
            }),

            // ===== 左下区域 =====
            createComponent('border-left-bottom', 'border-box', '左下边框', 30, 660, 450, 400, 10, {
                boxType: 8,
                color: ['#00d4ff', '#0066ff'],
            }),
            createComponent('title-left-bottom', 'title', '热门地区', 50, 675, 150, 30, 30, {
                text: '🏆 热门地区',
                fontSize: 18,
                fontWeight: 'bold',
                color: '#00d4ff',
                textAlign: 'left',
            }),
            createComponent('ranking', 'scroll-ranking', '地区排行', 50, 715, 410, 330, 20, {
                data: [
                    { name: '北京市', value: 89532 },
                    { name: '上海市', value: 76234 },
                    { name: '广东省', value: 68921 },
                    { name: '浙江省', value: 54328 },
                    { name: '江苏省', value: 48762 },
                    { name: '四川省', value: 42156 },
                    { name: '湖北省', value: 38654 },
                ],
                rowNum: 7,
                waitTime: 2500,
            }),

            // ===== 中央区域 =====
            createComponent('border-center', 'border-box', '中间边框', 500, 220, 920, 520, 10, {
                boxType: 5,
                color: ['#00d4ff', '#0066ff'],
            }),
            createComponent('title-center', 'title', '业务概览', 520, 235, 150, 30, 30, {
                text: '📊 业务概览',
                fontSize: 18,
                fontWeight: 'bold',
                color: '#00d4ff',
                textAlign: 'left',
            }),
            // 中央大型饼图
            createComponent('chart-pie', 'pie-chart', '业务分布', 540, 280, 420, 420, 20, {
                title: '业务类型分布',
                data: [
                    { name: '数据分析', value: 3350 },
                    { name: '实时监控', value: 2810 },
                    { name: 'API服务', value: 2340 },
                    { name: '数据同步', value: 1350 },
                    { name: '报表生成', value: 1540 },
                ],
            }),
            // 右侧仪表盘
            createComponent('gauge-1', 'gauge-chart', 'CPU使用率', 980, 280, 200, 200, 20, {
                title: 'CPU',
                value: 67,
                min: 0,
                max: 100,
            }),
            createComponent('gauge-2', 'gauge-chart', '内存使用率', 1200, 280, 200, 200, 20, {
                title: '内存',
                value: 82,
                min: 0,
                max: 100,
            }),
            createComponent('water', 'water-level', '磁盘使用', 980, 500, 180, 180, 20, {
                value: 45,
                shape: 'round',
            }),
            createComponent('percent', 'percent-pond', '网络带宽', 1180, 560, 220, 60, 20, {
                value: 78,
                borderWidth: 3,
                borderRadius: 5,
                colors: ['#00d4ff', '#0066ff'],
            }),
            createComponent('digital', 'digital-flop', '今日请求', 1180, 640, 220, 60, 20, {
                number: [9876543],
                content: '{nt} 次',
                style: {
                    fontSize: 28,
                    fill: '#00ffcc',
                },
            }),

            // ===== 中下区域 =====
            createComponent('border-center-bottom', 'border-box', '中下边框', 500, 760, 920, 300, 10, {
                boxType: 6,
                color: ['#00d4ff', '#0066ff'],
            }),
            createComponent('title-center-bottom', 'title', '实时日志', 520, 775, 150, 30, 30, {
                text: '📜 实时日志',
                fontSize: 18,
                fontWeight: 'bold',
                color: '#00d4ff',
                textAlign: 'left',
            }),
            createComponent('scroll-board', 'scroll-board', '日志表', 520, 815, 880, 230, 20, {
                header: ['时间', '服务', '事件', '状态'],
                data: [
                    ['2024-01-30 14:32:15', 'API Gateway', '请求处理完成', '✅ 成功'],
                    ['2024-01-30 14:32:14', '数据同步', '增量同步执行', '✅ 成功'],
                    ['2024-01-30 14:32:13', '监控告警', '负载恢复正常', '✅ 成功'],
                    ['2024-01-30 14:32:12', '报表服务', '日报生成完成', '✅ 成功'],
                    ['2024-01-30 14:32:11', '用户服务', '登录验证通过', '✅ 成功'],
                    ['2024-01-30 14:32:10', 'ETL Pipeline', '数据抽取完成', '✅ 成功'],
                ],
                rowNum: 5,
                headerBGC: '#003366',
                oddRowBGC: 'rgba(0, 100, 200, 0.1)',
                evenRowBGC: 'rgba(0, 50, 100, 0.1)',
                waitTime: 3000,
            }),

            // ===== 右侧区域 =====
            createComponent('border-right', 'border-box', '右边框', 1440, 220, 450, 420, 10, {
                boxType: 7,
                color: ['#00d4ff', '#0066ff'],
            }),
            createComponent('title-right', 'title', '周数据对比', 1460, 235, 180, 30, 30, {
                text: '📊 周数据对比',
                fontSize: 18,
                fontWeight: 'bold',
                color: '#00d4ff',
                textAlign: 'left',
            }),
            createComponent('chart-bar', 'bar-chart', '周对比图', 1455, 270, 420, 350, 20, {
                title: '',
                xAxisData: ['周一', '周二', '周三', '周四', '周五', '周六', '周日'],
                series: [
                    { name: '本周', data: [320, 332, 301, 334, 390, 230, 210] },
                    { name: '上周', data: [220, 182, 191, 234, 290, 330, 310] },
                ],
            }),

            // ===== 右下区域 =====
            createComponent('border-right-bottom', 'border-box', '右下边框', 1440, 660, 450, 400, 10, {
                boxType: 8,
                color: ['#00d4ff', '#0066ff'],
            }),
            createComponent('title-right-bottom', 'title', '服务状态', 1460, 675, 150, 30, 30, {
                text: '⚡ 服务状态',
                fontSize: 18,
                fontWeight: 'bold',
                color: '#00d4ff',
                textAlign: 'left',
            }),
            createComponent('radar', 'radar-chart', '系统健康', 1455, 715, 420, 330, 20, {
                title: '系统健康度',
                indicator: [
                    { name: '响应速度', max: 100 },
                    { name: '稳定性', max: 100 },
                    { name: '可用性', max: 100 },
                    { name: '安全性', max: 100 },
                    { name: '性能', max: 100 },
                ],
                data: [92, 96, 99, 88, 85],
            }),

            // ===== 底部装饰 =====
            createComponent('deco-bottom-1', 'decoration', '底部装饰1', 100, 1050, 300, 20, 5, {
                decorationType: 5,
                color: ['#00d4ff', '#0066ff'],
            }),
            createComponent('deco-bottom-2', 'decoration', '底部装饰2', 810, 1050, 300, 20, 5, {
                decorationType: 5,
                color: ['#00d4ff', '#0066ff'],
            }),
            createComponent('deco-bottom-3', 'decoration', '底部装饰3', 1520, 1050, 300, 20, 5, {
                decorationType: 5,
                color: ['#00d4ff', '#0066ff'],
            }),
        ],
    },
};

/**
 * 内置模板：空白模板
 */
const blankTemplate: ScreenTemplate = {
    id: 'blank',
    name: '空白模板',
    description: '从零开始创建你的数据大屏',
    thumbnail: '📄',
    category: 'dashboard',
    config: {
        name: '未命名大屏',
        description: '',
        width: 1920,
        height: 1080,
        backgroundColor: '#0d1b2a',
        components: [],
    },
};

/**
 * 所有可用模板
 */
export const screenTemplates: ScreenTemplate[] = [
    blankTemplate,
    techDataCenterTemplate,
];

/**
 * 根据ID获取模板
 */
export function getTemplateById(id: string): ScreenTemplate | undefined {
    return screenTemplates.find(t => t.id === id);
}

/**
 * 基于模板创建新配置（生成新的组件ID）
 */
export function createConfigFromTemplate(template: ScreenTemplate): Omit<ScreenConfig, 'id'> {
    const timestamp = Date.now();
    return {
        ...template.config,
        components: template.config.components.map((comp, idx) => ({
            ...comp,
            id: `comp_${timestamp}_${idx}_${Math.random().toString(36).substr(2, 9)}`,
        })),
    };
}
