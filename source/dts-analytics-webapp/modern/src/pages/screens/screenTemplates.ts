import type { ScreenConfig, ScreenComponent } from './types';
import { SCREEN_SCHEMA_VERSION } from './specV2';

/**
 * Screen Template definition
 */
export type TemplateCategory = 'general' | 'government' | 'manufacturing' | 'retail' | 'finance' | 'education' | 'blank';

export interface ScreenTemplate {
    id: string;
    name: string;
    description: string;
    thumbnail: string; // emoji or icon
    category: TemplateCategory;
    tags: string[];
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
    category: 'general',
    tags: ['监控', '实时', '运维', 'KPI'],
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
    category: 'blank',
    tags: ['空白', '自定义'],
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
 * 内置模板：专利数据中心
 * 深蓝背景，展示专利申请/受理/授权核心指标、类型分布、月度趋势、部门排行和最新授权
 */
const patentDataCenterTemplate: ScreenTemplate = {
    id: 'patent-data-center',
    name: '专利数据中心',
    description: '专利数据可视化大屏：KPI指标、类型占比、月度趋势、部门排行、近期授权、申请详情、超期预警',
    thumbnail: '📋',
    category: 'general',
    tags: ['专利', '知识产权', 'KPI', '趋势'],
    config: {
        name: '专利数据中心',
        description: '专利数据可视化大屏',
        width: 1920,
        height: 1080,
        backgroundColor: '#0a0e27',
        components: [
            // =============================================================
            //  顶部标题区  y: 0–80
            // =============================================================
            createComponent('patent-title', 'title', '主标题', 660, 12, 600, 55, 100, {
                text: '专利数据中心',
                fontSize: 38,
                fontWeight: 'bold',
                color: '#00d4ff',
                textAlign: 'center',
            }),
            createComponent('patent-deco-left', 'decoration', '标题装饰左', 200, 30, 400, 35, 99, {
                decorationType: 3,
                color: ['#00d4ff', '#0066ff'],
            }),
            createComponent('patent-deco-right', 'decoration', '标题装饰右', 1320, 30, 400, 35, 99, {
                decorationType: 3,
                color: ['#00d4ff', '#0066ff'],
            }),
            createComponent('patent-datetime', 'datetime', '日期时间', 1660, 22, 220, 35, 98, {
                format: 'YYYY-MM-DD HH:mm:ss',
                fontSize: 16,
                color: '#66ccff',
            }),

            // =============================================================
            //  第一行: KPI 指标卡  y: 78–168  (6 个等宽卡片)
            //  每卡 280w, 间距 20, 起始 x=40
            // =============================================================
            createComponent('patent-kpi-1', 'number-card', '申请总量', 40, 78, 280, 90, 50, {
                title: '申请总量',
                value: 12586,
                prefix: '',
                suffix: '件',
            }),
            createComponent('patent-kpi-2', 'number-card', '受理数量', 340, 78, 280, 90, 50, {
                title: '受理数量',
                value: 10234,
                prefix: '',
                suffix: '件',
            }),
            createComponent('patent-kpi-3', 'number-card', '授权数量', 640, 78, 280, 90, 50, {
                title: '授权数量',
                value: 6892,
                prefix: '',
                suffix: '件',
            }),
            createComponent('patent-kpi-4', 'number-card', '授权率', 940, 78, 280, 90, 50, {
                title: '授权率',
                value: 67.3,
                prefix: '',
                suffix: '%',
            }),
            createComponent('patent-kpi-5', 'number-card', '同比增长', 1240, 78, 280, 90, 50, {
                title: '同比增长',
                value: 12.5,
                prefix: '+',
                suffix: '%',
            }),
            createComponent('patent-kpi-6', 'number-card', '当年授权', 1540, 78, 340, 90, 50, {
                title: '当年授权',
                value: 1856,
                prefix: '',
                suffix: '件',
            }),

            // =============================================================
            //  第二行: 三列图表  y: 180–530
            //  列1(x:30  w:590)  列2(x:640 w:640)  列3(x:1300 w:590)
            // =============================================================

            // ---- 列 1: 专利类型占比 (饼图) ----
            createComponent('patent-border-pie', 'border-box', '类型占比边框', 30, 180, 590, 350, 10, {
                boxType: 7,
                color: ['#00d4ff', '#0066ff'],
            }),
            createComponent('patent-pie-title', 'title', '类型占比标题', 50, 190, 200, 28, 30, {
                text: '专利类型占比',
                fontSize: 15,
                fontWeight: 'bold',
                color: '#00d4ff',
                textAlign: 'left',
            }),
            createComponent('patent-pie', 'pie-chart', '专利类型占比', 40, 220, 570, 300, 20, {
                title: '',
                data: [
                    { name: '发明专利', value: 5230 },
                    { name: '实用新型', value: 4826 },
                    { name: '外观设计', value: 2530 },
                ],
            }),

            // ---- 列 2: 月度专利趋势 (折线图) ----
            createComponent('patent-border-line', 'border-box', '月度趋势边框', 640, 180, 640, 350, 10, {
                boxType: 7,
                color: ['#00d4ff', '#0066ff'],
            }),
            createComponent('patent-line-title', 'title', '月度趋势标题', 660, 190, 220, 28, 30, {
                text: '月度专利趋势',
                fontSize: 15,
                fontWeight: 'bold',
                color: '#00d4ff',
                textAlign: 'left',
            }),
            createComponent('patent-line', 'line-chart', '月度趋势', 650, 220, 620, 300, 20, {
                title: '',
                xAxisData: ['1月', '2月', '3月', '4月', '5月', '6月',
                    '7月', '8月', '9月', '10月', '11月', '12月'],
                series: [
                    { name: '受理', data: [820, 932, 901, 934, 1290, 1330, 1320, 1100, 1250, 1380, 1420, 1500] },
                    { name: '授权', data: [520, 632, 601, 634, 890, 930, 920, 800, 850, 980, 1020, 1100] },
                ],
            }),

            // ---- 列 3: 部门专利排行 (柱状图) ----
            createComponent('patent-border-bar', 'border-box', '部门排行边框', 1300, 180, 590, 350, 10, {
                boxType: 7,
                color: ['#00d4ff', '#0066ff'],
            }),
            createComponent('patent-bar-title', 'title', '部门排行标题', 1320, 190, 200, 28, 30, {
                text: '部门专利排行',
                fontSize: 15,
                fontWeight: 'bold',
                color: '#00d4ff',
                textAlign: 'left',
            }),
            createComponent('patent-bar', 'bar-chart', '部门排行', 1310, 220, 570, 300, 20, {
                title: '',
                xAxisData: ['研发一部', '研发二部', '研发三部', '产品部', '设计部',
                    '测试部', '工程部', '市场部', '质量部', '制造部'],
                series: [
                    { name: '申请数', data: [2350, 1980, 1650, 1420, 1180, 980, 860, 720, 650, 580] },
                ],
            }),

            // =============================================================
            //  第三行: 三列表格  y: 545–1050
            //  同上三列宽度
            // =============================================================

            // ---- 列 1: 近期专利授权 (近半年) ----
            createComponent('patent-border-grant', 'border-box', '近期授权边框', 30, 545, 590, 500, 10, {
                boxType: 8,
                color: ['#00d4ff', '#0066ff'],
            }),
            createComponent('patent-grant-title', 'title', '近期授权标题', 50, 555, 250, 28, 30, {
                text: '近期专利授权（近半年）',
                fontSize: 15,
                fontWeight: 'bold',
                color: '#00d4ff',
                textAlign: 'left',
            }),
            createComponent('patent-grant-board', 'scroll-board', '近期授权列表', 45, 590, 560, 445, 20, {
                header: ['授权日期', '专利号', '专利名称', '部门'],
                data: [
                    ['2025-01-28', 'ZL2024100012.5', '一种智能数据处理方法', '研发一部'],
                    ['2025-01-25', 'ZL2024100013.X', '分布式存储系统及装置', '研发二部'],
                    ['2025-01-22', 'ZL2024100014.4', '基于AI的图像识别系统', '研发三部'],
                    ['2025-01-18', 'ZL2024100015.9', '多模态交互界面设计', '设计部'],
                    ['2025-01-15', 'ZL2024100016.3', '高性能缓存优化方法', '研发一部'],
                    ['2024-12-28', 'ZL2024100017.8', '自动化测试框架系统', '测试部'],
                    ['2024-12-20', 'ZL2024100018.0', '数据安全加密传输协议', '研发二部'],
                    ['2024-12-15', 'ZL2024100019.5', '智能推荐算法引擎', '产品部'],
                    ['2024-11-28', 'ZL2024100020.X', '低功耗芯片散热结构', '工程部'],
                    ['2024-11-15', 'ZL2024100021.4', '新型柔性显示面板', '制造部'],
                ],
                rowNum: 8,
                headerBGC: '#003366',
                oddRowBGC: 'rgba(0, 100, 200, 0.1)',
                evenRowBGC: 'rgba(0, 50, 100, 0.1)',
                waitTime: 3000,
            }),

            // ---- 列 2: 当年申请详情 ----
            createComponent('patent-border-detail', 'border-box', '申请详情边框', 640, 545, 640, 500, 10, {
                boxType: 8,
                color: ['#00d4ff', '#0066ff'],
            }),
            createComponent('patent-detail-title', 'title', '申请详情标题', 660, 555, 200, 28, 30, {
                text: '当年申请详情',
                fontSize: 15,
                fontWeight: 'bold',
                color: '#00d4ff',
                textAlign: 'left',
            }),
            createComponent('patent-detail-board', 'scroll-board', '申请详情列表', 655, 590, 610, 445, 20, {
                header: ['申请日期', '专利号', '专利名称', '类型', '状态'],
                data: [
                    ['2025-02-05', 'CN2025100001.2', '一种新型机器学习框架', '发明', '已受理'],
                    ['2025-02-03', 'CN2025100002.7', '物联网设备管理平台', '发明', '审查中'],
                    ['2025-01-28', 'CN2025100003.1', '便携式检测装置', '实用新型', '已受理'],
                    ['2025-01-25', 'CN2025100004.6', '智能温控系统', '发明', '已受理'],
                    ['2025-01-20', 'CN2025100005.0', '电子设备外壳结构', '外观设计', '已授权'],
                    ['2025-01-18', 'CN2025100006.5', '自适应负载均衡方法', '发明', '审查中'],
                    ['2025-01-15', 'CN2025100007.X', '新型散热器结构', '实用新型', '已受理'],
                    ['2025-01-10', 'CN2025100008.4', '语音交互处理方法', '发明', '已授权'],
                    ['2025-01-08', 'CN2025100009.9', '数据压缩编码方法', '发明', '审查中'],
                    ['2025-01-05', 'CN2025100010.0', '柔性电路板结构设计', '实用新型', '已受理'],
                ],
                rowNum: 8,
                headerBGC: '#003366',
                oddRowBGC: 'rgba(0, 100, 200, 0.1)',
                evenRowBGC: 'rgba(0, 50, 100, 0.1)',
                waitTime: 3500,
            }),

            // ---- 列 3: 受理超期预警 ----
            createComponent('patent-border-overdue', 'border-box', '超期预警边框', 1300, 545, 590, 500, 10, {
                boxType: 8,
                color: ['#ff6b6b', '#cc3333'],
            }),
            createComponent('patent-overdue-title', 'title', '超期预警标题', 1320, 555, 250, 28, 30, {
                text: '受理超期预警',
                fontSize: 15,
                fontWeight: 'bold',
                color: '#ff6b6b',
                textAlign: 'left',
            }),
            createComponent('patent-overdue-board', 'scroll-board', '超期预警列表', 1315, 590, 560, 445, 20, {
                header: ['申请日期', '专利号', '专利名称', '超期天数'],
                data: [
                    ['2023-05-10', 'CN2023100001.5', '高并发消息队列系统', '1006'],
                    ['2023-06-15', 'CN2023100002.X', '智能仓储管理方法', '970'],
                    ['2023-07-20', 'CN2023100003.4', '分布式计算调度引擎', '935'],
                    ['2023-08-08', 'CN2023100004.9', '生物特征识别装置', '916'],
                    ['2023-09-12', 'CN2023100005.3', '自动驾驶决策系统', '881'],
                    ['2023-10-05', 'CN2023100006.8', '量子加密通信协议', '858'],
                    ['2023-11-18', 'CN2023100007.2', '柔性传感器阵列', '814'],
                    ['2023-12-01', 'CN2023100008.7', '智能电网调度方法', '801'],
                    ['2024-01-10', 'CN2024100009.1', '新型催化剂制备方法', '761'],
                    ['2024-02-20', 'CN2024100010.3', '多模态融合检测方法', '720'],
                ],
                rowNum: 8,
                headerBGC: '#4a1a1a',
                oddRowBGC: 'rgba(200, 50, 50, 0.1)',
                evenRowBGC: 'rgba(150, 30, 30, 0.1)',
                waitTime: 3000,
            }),

            // ===== 底部装饰 =====
            createComponent('patent-deco-bottom-1', 'decoration', '底部装饰1', 100, 1052, 300, 18, 5, {
                decorationType: 5,
                color: ['#00d4ff', '#0066ff'],
            }),
            createComponent('patent-deco-bottom-2', 'decoration', '底部装饰2', 810, 1052, 300, 18, 5, {
                decorationType: 5,
                color: ['#00d4ff', '#0066ff'],
            }),
            createComponent('patent-deco-bottom-3', 'decoration', '底部装饰3', 1520, 1052, 300, 18, 5, {
                decorationType: 5,
                color: ['#00d4ff', '#0066ff'],
            }),
        ],
    },
};

/**
 * 专利数据中心 · 钛合金灰
 * 深灰色调，无 DataV 边框/装饰，纯卡片布局
 */
const patentTitaniumTemplate: ScreenTemplate = {
    id: 'patent-titanium',
    name: '专利数据中心 · 钛合金灰',
    description: '克制专业的深灰色调，无霓虹边框，适合涉密科技企业',
    thumbnail: '🔩',
    category: 'general',
    tags: ['专利', '钛合金', '深灰'],
    config: {
        name: '专利数据中心 · 钛合金灰',
        description: '专利数据可视化大屏（钛合金灰）',
        width: 1920,
        height: 1080,
        backgroundColor: '#1a1d23',
        theme: 'titanium',
        components: [
            createComponent('ti-title', 'title', '主标题', 660, 15, 600, 50, 100, {
                text: '专利数据中心', fontSize: 34, fontWeight: '600', color: '#e8eaed', textAlign: 'center',
            }),
            createComponent('ti-datetime', 'datetime', '日期时间', 1660, 22, 220, 35, 98, {
                format: 'YYYY-MM-DD HH:mm:ss', fontSize: 16, color: '#6b7280',
            }),
            createComponent('ti-kpi-1', 'number-card', '申请总量', 40, 78, 280, 90, 50, {
                title: '申请总量', value: 12586, prefix: '', suffix: '件',
            }),
            createComponent('ti-kpi-2', 'number-card', '受理数量', 340, 78, 280, 90, 50, {
                title: '受理数量', value: 10234, prefix: '', suffix: '件',
            }),
            createComponent('ti-kpi-3', 'number-card', '授权数量', 640, 78, 280, 90, 50, {
                title: '授权数量', value: 6892, prefix: '', suffix: '件',
            }),
            createComponent('ti-kpi-4', 'number-card', '授权率', 940, 78, 280, 90, 50, {
                title: '授权率', value: 67.3, prefix: '', suffix: '%',
            }),
            createComponent('ti-kpi-5', 'number-card', '同比增长', 1240, 78, 280, 90, 50, {
                title: '同比增长', value: 12.5, prefix: '+', suffix: '%',
            }),
            createComponent('ti-kpi-6', 'number-card', '当年授权', 1540, 78, 340, 90, 50, {
                title: '当年授权', value: 1856, prefix: '', suffix: '件',
            }),
            createComponent('ti-pie-title', 'title', '类型占比标题', 50, 185, 200, 28, 30, {
                text: '专利类型占比', fontSize: 15, fontWeight: '600', color: '#e8eaed', textAlign: 'left',
            }),
            createComponent('ti-pie', 'pie-chart', '专利类型占比', 30, 215, 590, 310, 20, {
                title: '', data: [
                    { name: '发明专利', value: 5230 }, { name: '实用新型', value: 4826 }, { name: '外观设计', value: 2530 },
                ],
            }),
            createComponent('ti-line-title', 'title', '月度趋势标题', 650, 185, 220, 28, 30, {
                text: '月度专利趋势', fontSize: 15, fontWeight: '600', color: '#e8eaed', textAlign: 'left',
            }),
            createComponent('ti-line', 'line-chart', '月度趋势', 640, 215, 640, 310, 20, {
                title: '',
                xAxisData: ['1月', '2月', '3月', '4月', '5月', '6月', '7月', '8月', '9月', '10月', '11月', '12月'],
                series: [
                    { name: '受理', data: [820, 932, 901, 934, 1290, 1330, 1320, 1100, 1250, 1380, 1420, 1500] },
                    { name: '授权', data: [520, 632, 601, 634, 890, 930, 920, 800, 850, 980, 1020, 1100] },
                ],
            }),
            createComponent('ti-bar-title', 'title', '部门排行标题', 1320, 185, 200, 28, 30, {
                text: '部门专利排行', fontSize: 15, fontWeight: '600', color: '#e8eaed', textAlign: 'left',
            }),
            createComponent('ti-bar', 'bar-chart', '部门排行', 1300, 215, 590, 310, 20, {
                title: '',
                xAxisData: ['研发一部', '研发二部', '研发三部', '产品部', '设计部', '测试部', '工程部', '市场部', '质量部', '制造部'],
                series: [{ name: '申请数', data: [2350, 1980, 1650, 1420, 1180, 980, 860, 720, 650, 580] }],
            }),
            createComponent('ti-grant-title', 'title', '近期授权标题', 50, 545, 250, 28, 30, {
                text: '近期专利授权（近半年）', fontSize: 15, fontWeight: '600', color: '#e8eaed', textAlign: 'left',
            }),
            createComponent('ti-grant-board', 'scroll-board', '近期授权列表', 30, 580, 590, 470, 20, {
                header: ['授权日期', '专利号', '专利名称', '部门'],
                data: [
                    ['2025-01-28', 'ZL2024100012.5', '一种智能数据处理方法', '研发一部'],
                    ['2025-01-25', 'ZL2024100013.X', '分布式存储系统及装置', '研发二部'],
                    ['2025-01-22', 'ZL2024100014.4', '基于AI的图像识别系统', '研发三部'],
                    ['2025-01-18', 'ZL2024100015.9', '多模态交互界面设计', '设计部'],
                    ['2025-01-15', 'ZL2024100016.3', '高性能缓存优化方法', '研发一部'],
                    ['2024-12-28', 'ZL2024100017.8', '自动化测试框架系统', '测试部'],
                    ['2024-12-20', 'ZL2024100018.0', '数据安全加密传输协议', '研发二部'],
                    ['2024-12-15', 'ZL2024100019.5', '智能推荐算法引擎', '产品部'],
                    ['2024-11-28', 'ZL2024100020.X', '低功耗芯片散热结构', '工程部'],
                    ['2024-11-15', 'ZL2024100021.4', '新型柔性显示面板', '制造部'],
                ],
                rowNum: 8, headerBGC: '#282c35',
                oddRowBGC: 'rgba(255,255,255,0.02)', evenRowBGC: 'rgba(255,255,255,0.04)', waitTime: 3000,
            }),
            createComponent('ti-detail-title', 'title', '申请详情标题', 650, 545, 200, 28, 30, {
                text: '当年申请详情', fontSize: 15, fontWeight: '600', color: '#e8eaed', textAlign: 'left',
            }),
            createComponent('ti-detail-board', 'scroll-board', '申请详情列表', 640, 580, 640, 470, 20, {
                header: ['申请日期', '专利号', '专利名称', '类型', '状态'],
                data: [
                    ['2025-02-05', 'CN2025100001.2', '一种新型机器学习框架', '发明', '已受理'],
                    ['2025-02-03', 'CN2025100002.7', '物联网设备管理平台', '发明', '审查中'],
                    ['2025-01-28', 'CN2025100003.1', '便携式检测装置', '实用新型', '已受理'],
                    ['2025-01-25', 'CN2025100004.6', '智能温控系统', '发明', '已受理'],
                    ['2025-01-20', 'CN2025100005.0', '电子设备外壳结构', '外观设计', '已授权'],
                    ['2025-01-18', 'CN2025100006.5', '自适应负载均衡方法', '发明', '审查中'],
                    ['2025-01-15', 'CN2025100007.X', '新型散热器结构', '实用新型', '已受理'],
                    ['2025-01-10', 'CN2025100008.4', '语音交互处理方法', '发明', '已授权'],
                    ['2025-01-08', 'CN2025100009.9', '数据压缩编码方法', '发明', '审查中'],
                    ['2025-01-05', 'CN2025100010.0', '柔性电路板结构设计', '实用新型', '已受理'],
                ],
                rowNum: 8, headerBGC: '#282c35',
                oddRowBGC: 'rgba(255,255,255,0.02)', evenRowBGC: 'rgba(255,255,255,0.04)', waitTime: 3500,
            }),
            createComponent('ti-overdue-title', 'title', '超期预警标题', 1320, 545, 250, 28, 30, {
                text: '受理超期预警', fontSize: 15, fontWeight: '600', color: '#ef4444', textAlign: 'left',
            }),
            createComponent('ti-overdue-board', 'scroll-board', '超期预警列表', 1300, 580, 590, 470, 20, {
                header: ['申请日期', '专利号', '专利名称', '超期天数'],
                data: [
                    ['2023-05-10', 'CN2023100001.5', '高并发消息队列系统', '1006'],
                    ['2023-06-15', 'CN2023100002.X', '智能仓储管理方法', '970'],
                    ['2023-07-20', 'CN2023100003.4', '分布式计算调度引擎', '935'],
                    ['2023-08-08', 'CN2023100004.9', '生物特征识别装置', '916'],
                    ['2023-09-12', 'CN2023100005.3', '自动驾驶决策系统', '881'],
                    ['2023-10-05', 'CN2023100006.8', '量子加密通信协议', '858'],
                    ['2023-11-18', 'CN2023100007.2', '柔性传感器阵列', '814'],
                    ['2023-12-01', 'CN2023100008.7', '智能电网调度方法', '801'],
                    ['2024-01-10', 'CN2024100009.1', '新型催化剂制备方法', '761'],
                    ['2024-02-20', 'CN2024100010.3', '多模态融合检测方法', '720'],
                ],
                rowNum: 8, headerBGC: '#3a2020',
                oddRowBGC: 'rgba(239,68,68,0.05)', evenRowBGC: 'rgba(239,68,68,0.08)', waitTime: 3000,
            }),
        ],
    },
};

/**
 * 商务数据看板 · 白色
 * 参考 Innovation Dashboard 风格，浅灰底 + 白色面板 + ECharts 图表
 * 不使用任何 DataV 组件，纯 ECharts + 基础组件
 */
const businessLightTemplate: ScreenTemplate = {
    id: 'business-light',
    name: '商务数据看板 · 白色',
    description: '浅灰底白面板风格，2x2 图表布局，适合办公会议投屏',
    thumbnail: '📊',
    category: 'general',
    tags: ['商务', '白色', '会议', 'ROI'],
    config: {
        name: '商务数据看板',
        description: '白色商务风格数据看板',
        width: 1920,
        height: 1080,
        backgroundColor: '#f6f7f9',
        theme: 'glacier',
        components: [
            // ===== 顶部标题栏 =====
            createComponent('bl-title', 'title', '主标题', 660, 18, 600, 44, 100, {
                text: 'Innovation Dashboard', fontSize: 24, fontWeight: '650', color: '#1f2328', textAlign: 'center',
            }),
            createComponent('bl-datetime', 'datetime', '日期时间', 1660, 24, 220, 32, 98, {
                format: 'YYYY-MM-DD HH:mm:ss', fontSize: 13, color: '#6b7280',
            }),

            // ===== KPI 指标卡 =====
            createComponent('bl-kpi-1', 'number-card', '总收入', 30, 76, 290, 80, 50, {
                title: 'Projected Revenue', value: 3085, prefix: '$', suffix: 'M',
            }),
            createComponent('bl-kpi-2', 'number-card', '总成本', 340, 76, 290, 80, 50, {
                title: 'Projected Cost', value: 785, prefix: '$', suffix: 'M',
            }),
            createComponent('bl-kpi-3', 'number-card', '利润率', 650, 76, 290, 80, 50, {
                title: 'Projected Margin', value: 2300, prefix: '$', suffix: 'M',
            }),
            createComponent('bl-kpi-4', 'number-card', '项目数', 960, 76, 290, 80, 50, {
                title: 'Active Projects', value: 6, prefix: '', suffix: '',
            }),
            createComponent('bl-kpi-5', 'number-card', 'ROI', 1270, 76, 290, 80, 50, {
                title: 'Average ROI', value: 293, prefix: '', suffix: '%',
            }),
            createComponent('bl-kpi-6', 'number-card', '对齐得分', 1580, 76, 310, 80, 50, {
                title: 'Alignment Score', value: 4.2, prefix: '', suffix: '/5',
            }),

            // ===== 第一行图表: 柱状图 + 折线图 =====
            createComponent('bl-bar-title', 'title', '收入对比标题', 46, 174, 300, 24, 30, {
                text: 'Projected Return vs Cost', fontSize: 14, fontWeight: '700', color: '#1f2328', textAlign: 'left',
            }),
            createComponent('bl-bar', 'bar-chart', '收入对比', 30, 200, 920, 390, 20, {
                title: '',
                xAxisData: ['FIT3000 Treadmill', 'FIT5000 VR Trainer', 'FIT3100 Cardio', 'Eco Fitness Watch', 'Orio Wearable', 'Orio VR Device'],
                series: [
                    { name: 'Revenue', data: [900, 1250, 520, 260, 95, 60] },
                    { name: 'Cost', data: [200, 180, 280, 70, 35, 22] },
                    { name: 'Margin', data: [700, 1070, 240, 190, 60, 38] },
                ],
            }),

            createComponent('bl-line-title', 'title', '季度趋势标题', 976, 174, 300, 24, 30, {
                text: 'Projected Revenue Trend', fontSize: 14, fontWeight: '700', color: '#1f2328', textAlign: 'left',
            }),
            createComponent('bl-line', 'line-chart', '季度趋势', 960, 200, 930, 390, 20, {
                title: '',
                xAxisData: ['Q1 2024', 'Q2 2024', 'Q3 2024', 'Q4 2024', 'Q1 2025', 'Q2 2025'],
                series: [
                    { name: 'Revenue', data: [420, 580, 760, 920, 1050, 1250] },
                    { name: 'Cost', data: [180, 210, 240, 260, 280, 310] },
                ],
            }),

            // ===== 第二行图表: 饼图 + 柱状图 =====
            createComponent('bl-pie-title', 'title', '投资组合标题', 46, 608, 300, 24, 30, {
                text: 'Portfolio Distribution', fontSize: 14, fontWeight: '700', color: '#1f2328', textAlign: 'left',
            }),
            createComponent('bl-pie', 'pie-chart', '投资组合', 30, 634, 920, 420, 20, {
                title: '', data: [
                    { name: 'Fitness Equipment', value: 2670 },
                    { name: 'Wearable Devices', value: 355 },
                    { name: 'VR Products', value: 1310 },
                    { name: 'IoT Sensors', value: 450 },
                ],
            }),

            createComponent('bl-score-title', 'title', '评估得分标题', 976, 608, 300, 24, 30, {
                text: 'Strategic Alignment Scores', fontSize: 14, fontWeight: '700', color: '#1f2328', textAlign: 'left',
            }),
            createComponent('bl-score', 'bar-chart', '评估得分', 960, 634, 930, 420, 20, {
                title: '',
                xAxisData: ['FIT3000', 'FIT5000', 'FIT3100', 'Eco Watch', 'Orio', 'Orio VR'],
                series: [
                    { name: 'Impact', data: [3, 5, 4, 3, 2, 2] },
                    { name: 'Supply Fit', data: [4, 5, 4, 3, 2, 2] },
                    { name: 'Alignment', data: [4, 5, 4, 3, 2, 2] },
                ],
            }),
        ],
    },
};

// ============================================================
// 空白模板 - 不同尺寸
// ============================================================
const blank4kTemplate: ScreenTemplate = {
    id: 'blank-4k', name: '空白模板 · 4K', description: '3840×2160 超高清空白大屏', thumbnail: '🖥️',
    category: 'blank', tags: ['空白', '4K', '超高清'],
    config: { name: '未命名大屏(4K)', description: '', width: 3840, height: 2160, backgroundColor: '#0d1b2a', components: [] },
};
const blankSmallTemplate: ScreenTemplate = {
    id: 'blank-small', name: '空白模板 · 小屏', description: '1280×720 小屏幕空白模板', thumbnail: '📱',
    category: 'blank', tags: ['空白', '小屏', '720p'],
    config: { name: '未命名大屏(720p)', description: '', width: 1280, height: 720, backgroundColor: '#0d1b2a', components: [] },
};

// ============================================================
// 政务/智慧城市模板
// ============================================================
const smartCityTemplate: ScreenTemplate = {
    id: 'smart-city', name: '智慧城市总览', description: '中间地图+左右数据面板，适合城市管理/政务展示', thumbnail: '🏙️',
    category: 'government', tags: ['智慧城市', '政务', '地图', '监控'],
    config: {
        name: '智慧城市总览', description: '智慧城市数据大屏', width: 1920, height: 1080, backgroundColor: '#0a0e27',
        components: [
            createComponent('sc-title', 'title', '主标题', 660, 12, 600, 55, 100, { text: '智慧城市运营中心', fontSize: 36, fontWeight: 'bold', color: '#00d4ff', textAlign: 'center' }),
            createComponent('sc-deco-l', 'decoration', '装饰左', 200, 30, 400, 35, 99, { decorationType: 3, color: ['#00d4ff', '#0066ff'] }),
            createComponent('sc-deco-r', 'decoration', '装饰右', 1320, 30, 400, 35, 99, { decorationType: 3, color: ['#00d4ff', '#0066ff'] }),
            createComponent('sc-dt', 'datetime', '时间', 1660, 22, 220, 35, 98, { format: 'YYYY-MM-DD HH:mm:ss', fontSize: 16, color: '#66ccff' }),
            createComponent('sc-kpi-1', 'number-card', '常住人口', 40, 80, 220, 85, 50, { title: '常住人口(万)', value: 1862, prefix: '', suffix: '' }),
            createComponent('sc-kpi-2', 'number-card', '今日车流', 280, 80, 220, 85, 50, { title: '今日车流(万)', value: 342, prefix: '', suffix: '' }),
            createComponent('sc-kpi-3', 'number-card', '空气质量', 1420, 80, 220, 85, 50, { title: '空气质量(AQI)', value: 45, prefix: '', suffix: '' }),
            createComponent('sc-kpi-4', 'number-card', '城市安全指数', 1660, 80, 220, 85, 50, { title: '安全指数', value: 96.8, prefix: '', suffix: '' }),
            createComponent('sc-border-l', 'border-box', '左边框', 30, 180, 470, 440, 10, { boxType: 7, color: ['#00d4ff', '#0066ff'] }),
            createComponent('sc-bar', 'bar-chart', '各区人口', 45, 210, 440, 400, 20, { title: '各区常住人口', xAxisData: ['A区', 'B区', 'C区', 'D区', 'E区', 'F区'], series: [{ name: '人口(万)', data: [320, 285, 256, 198, 175, 168] }] }),
            createComponent('sc-map', 'map-chart', '中心地图', 520, 180, 880, 520, 20, { title: '城市区域分布', mapScope: 'china', usePresetGeoJson: true, enableRegionDrill: true, regionVariableKey: 'region', regions: [{ name: '北京市', code: '110000', value: 120 }, { name: '上海市', code: '310000', value: 180 }, { name: '广东省', code: '440000', value: 140 }, { name: '浙江省', code: '330000', value: 95 }] }),
            createComponent('sc-border-r', 'border-box', '右边框', 1420, 180, 470, 440, 10, { boxType: 7, color: ['#00d4ff', '#0066ff'] }),
            createComponent('sc-pie', 'pie-chart', '服务分布', 1435, 210, 440, 400, 20, { title: '公共服务占比', data: [{ name: '交通', value: 35 }, { name: '教育', value: 25 }, { name: '医疗', value: 20 }, { name: '环保', value: 12 }, { name: '安防', value: 8 }] }),
            createComponent('sc-border-bl', 'border-box', '左下边框', 30, 640, 620, 420, 10, { boxType: 8, color: ['#00d4ff', '#0066ff'] }),
            createComponent('sc-line', 'line-chart', '月度趋势', 45, 670, 590, 380, 20, { title: '月度事件趋势', xAxisData: ['1月', '2月', '3月', '4月', '5月', '6月'], series: [{ name: '交通事件', data: [120, 132, 101, 134, 90, 80] }, { name: '治安事件', data: [60, 72, 51, 74, 50, 40] }] }),
            createComponent('sc-border-br', 'border-box', '右下边框', 670, 640, 1220, 420, 10, { boxType: 8, color: ['#00d4ff', '#0066ff'] }),
            createComponent('sc-board', 'scroll-board', '实时事件', 685, 670, 1190, 380, 20, { header: ['时间', '区域', '事件类型', '状态'], data: [['14:32', 'A区', '交通拥堵', '处理中'], ['14:28', 'C区', '消防报警', '已处置'], ['14:25', 'B区', '公共设施报修', '已派单'], ['14:20', 'E区', '噪音投诉', '已处置'], ['14:15', 'D区', '道路积水', '处理中']], rowNum: 6, headerBGC: '#003366', oddRowBGC: 'rgba(0,100,200,0.1)', evenRowBGC: 'rgba(0,50,100,0.1)', waitTime: 2500 }),
        ],
    },
};

const govServiceTemplate: ScreenTemplate = {
    id: 'gov-service', name: '政务服务大屏', description: '政务服务数据展示，办件量/满意度/服务效率', thumbnail: '🏛️',
    category: 'government', tags: ['政务', '服务', '办件', '满意度'],
    config: {
        name: '政务服务大屏', description: '政务服务运营数据', width: 1920, height: 1080, backgroundColor: '#0a0e27',
        components: [
            createComponent('gs-title', 'title', '主标题', 560, 12, 800, 55, 100, { text: '政务服务运营大屏', fontSize: 36, fontWeight: 'bold', color: '#00d4ff', textAlign: 'center' }),
            createComponent('gs-kpi-1', 'number-card', '累计办件', 40, 85, 290, 85, 50, { title: '累计办件量', value: 1286534, prefix: '', suffix: '' }),
            createComponent('gs-kpi-2', 'number-card', '今日办件', 350, 85, 290, 85, 50, { title: '今日办件', value: 3256, prefix: '', suffix: '' }),
            createComponent('gs-kpi-3', 'number-card', '满意度', 660, 85, 290, 85, 50, { title: '群众满意度', value: 98.6, prefix: '', suffix: '%' }),
            createComponent('gs-kpi-4', 'number-card', '在线率', 970, 85, 290, 85, 50, { title: '系统在线率', value: 99.97, prefix: '', suffix: '%' }),
            createComponent('gs-kpi-5', 'number-card', '平均时长', 1280, 85, 290, 85, 50, { title: '平均办理(分)', value: 12.5, prefix: '', suffix: '' }),
            createComponent('gs-kpi-6', 'number-card', '好评率', 1590, 85, 290, 85, 50, { title: '好评率', value: 96.3, prefix: '', suffix: '%' }),
            createComponent('gs-bar', 'bar-chart', '部门办件量', 30, 190, 620, 400, 20, { title: '部门办件量TOP10', xAxisData: ['民政局', '人社局', '住建局', '公安局', '市监局', '教育局', '卫健委', '交通局'], series: [{ name: '办件量', data: [4560, 3890, 3420, 3100, 2860, 2540, 2100, 1850] }] }),
            createComponent('gs-line', 'line-chart', '月度趋势', 670, 190, 620, 400, 20, { title: '月度办件趋势', xAxisData: ['1月', '2月', '3月', '4月', '5月', '6月'], series: [{ name: '办件量', data: [42000, 38000, 45000, 48000, 52000, 56000] }, { name: '在线办件', data: [28000, 26000, 32000, 36000, 40000, 45000] }] }),
            createComponent('gs-pie', 'pie-chart', '办件类型', 1310, 190, 580, 400, 20, { title: '办件类型分布', data: [{ name: '即办件', value: 45 }, { name: '承诺件', value: 30 }, { name: '联办件', value: 15 }, { name: '上报件', value: 10 }] }),
            createComponent('gs-board', 'scroll-board', '实时办件', 30, 610, 920, 440, 20, { header: ['时间', '事项', '部门', '状态'], data: [['14:30', '营业执照变更', '市监局', '已办结'], ['14:28', '社保转移', '人社局', '办理中'], ['14:25', '不动产登记', '住建局', '已办结'], ['14:22', '户口迁移', '公安局', '办理中'], ['14:20', '婚姻登记', '民政局', '已办结'], ['14:18', '食品经营许可', '市监局', '审核中']], rowNum: 7, headerBGC: '#003366', oddRowBGC: 'rgba(0,100,200,0.1)', evenRowBGC: 'rgba(0,50,100,0.1)', waitTime: 3000 }),
            createComponent('gs-gauge', 'gauge-chart', '效率仪表', 970, 620, 300, 300, 20, { title: '办结效率', value: 95.2, min: 0, max: 100 }),
            createComponent('gs-ranking', 'scroll-ranking', '窗口排名', 1290, 620, 590, 430, 20, { data: [{ name: '1号窗口', value: 156 }, { name: '2号窗口', value: 142 }, { name: '3号窗口', value: 128 }, { name: '4号窗口', value: 115 }, { name: '5号窗口', value: 98 }], rowNum: 5, waitTime: 2000 }),
        ],
    },
};

// ============================================================
// 制造/能源模板
// ============================================================
const productionMonitorTemplate: ScreenTemplate = {
    id: 'production-monitor', name: '生产监控大屏', description: '工厂生产线实时监控，OEE/产量/告警', thumbnail: '🏭',
    category: 'manufacturing', tags: ['生产', '制造', 'OEE', '产线'],
    config: {
        name: '生产监控大屏', description: '生产线实时监控', width: 1920, height: 1080, backgroundColor: '#0a0e27',
        components: [
            createComponent('pm-title', 'title', '主标题', 560, 12, 800, 55, 100, { text: '智能制造监控中心', fontSize: 36, fontWeight: 'bold', color: '#00d4ff', textAlign: 'center' }),
            createComponent('pm-dt', 'datetime', '时间', 1660, 22, 220, 35, 98, { format: 'YYYY-MM-DD HH:mm:ss', fontSize: 16, color: '#66ccff' }),
            createComponent('pm-g1', 'gauge-chart', 'OEE', 40, 85, 220, 220, 50, { title: 'OEE', value: 87.5, min: 0, max: 100 }),
            createComponent('pm-g2', 'gauge-chart', '可用率', 280, 85, 220, 220, 50, { title: '可用率', value: 95.2, min: 0, max: 100 }),
            createComponent('pm-g3', 'gauge-chart', '性能率', 520, 85, 220, 220, 50, { title: '性能率', value: 92.1, min: 0, max: 100 }),
            createComponent('pm-g4', 'gauge-chart', '良品率', 760, 85, 220, 220, 50, { title: '良品率', value: 99.6, min: 0, max: 100 }),
            createComponent('pm-kpi-1', 'number-card', '今日产量', 1020, 85, 210, 85, 50, { title: '今日产量', value: 12560, prefix: '', suffix: '' }),
            createComponent('pm-kpi-2', 'number-card', '计划达成', 1250, 85, 210, 85, 50, { title: '计划达成率', value: 98.7, prefix: '', suffix: '%' }),
            createComponent('pm-kpi-3', 'number-card', '故障次数', 1480, 85, 200, 85, 50, { title: '今日故障', value: 3, prefix: '', suffix: '次' }),
            createComponent('pm-kpi-4', 'number-card', '能耗', 1700, 85, 180, 85, 50, { title: '累计能耗(kWh)', value: 28560, prefix: '', suffix: '' }),
            createComponent('pm-line', 'line-chart', '产量趋势', 30, 320, 620, 360, 20, { title: '24小时产量趋势', xAxisData: ['00:00', '02:00', '04:00', '06:00', '08:00', '10:00', '12:00', '14:00', '16:00', '18:00', '20:00', '22:00'], series: [{ name: '产量', data: [0, 0, 0, 120, 480, 520, 310, 540, 560, 480, 420, 280] }, { name: '计划', data: [0, 0, 0, 500, 500, 500, 300, 500, 500, 500, 500, 300] }] }),
            createComponent('pm-bar', 'bar-chart', '产线对比', 670, 320, 620, 360, 20, { title: '产线产量对比', xAxisData: ['A线', 'B线', 'C线', 'D线', 'E线'], series: [{ name: '实际', data: [2560, 2340, 2180, 1980, 1560] }, { name: '计划', data: [2500, 2500, 2300, 2000, 1800] }] }),
            createComponent('pm-pie', 'pie-chart', '停机原因', 1310, 320, 580, 360, 20, { title: '停机原因分布', data: [{ name: '设备故障', value: 35 }, { name: '物料短缺', value: 25 }, { name: '换型调试', value: 20 }, { name: '品质异常', value: 12 }, { name: '计划停机', value: 8 }] }),
            createComponent('pm-progress1', 'progress-bar', '产线A进度', 40, 700, 420, 35, 20, { value: 95, showLabel: true }),
            createComponent('pm-progress2', 'progress-bar', '产线B进度', 40, 745, 420, 35, 20, { value: 88, showLabel: true }),
            createComponent('pm-progress3', 'progress-bar', '产线C进度', 40, 790, 420, 35, 20, { value: 92, showLabel: true }),
            createComponent('pm-progress4', 'progress-bar', '产线D进度', 40, 835, 420, 35, 20, { value: 76, showLabel: true }),
            createComponent('pm-board', 'scroll-board', '告警列表', 490, 700, 900, 350, 20, { header: ['时间', '产线', '设备', '告警内容', '级别'], data: [['14:32', 'A线', '注塑机#3', '温度超限 285°C', '警告'], ['14:28', 'C线', '输送带#1', '速度异常', '提示'], ['14:15', 'B线', 'CNC#5', '刀具寿命到期', '提示'], ['13:50', 'D线', '焊接机#2', '焊接质量异常', '警告'], ['13:30', 'A线', '冲压机#1', '液压压力低', '严重']], rowNum: 6, headerBGC: '#003366', oddRowBGC: 'rgba(0,100,200,0.1)', evenRowBGC: 'rgba(0,50,100,0.1)', waitTime: 3000 }),
            createComponent('pm-ranking', 'scroll-ranking', '设备效率', 1410, 700, 480, 350, 20, { data: [{ name: 'CNC加工中心', value: 96 }, { name: '注塑机组', value: 92 }, { name: '冲压线', value: 89 }, { name: '焊接工站', value: 85 }, { name: '喷涂线', value: 82 }], rowNum: 5, waitTime: 2000 }),
        ],
    },
};

const energyManagementTemplate: ScreenTemplate = {
    id: 'energy-management', name: '能源管理大屏', description: '能耗监测/碳排放/节能分析', thumbnail: '⚡',
    category: 'manufacturing', tags: ['能源', '能耗', '碳排放', '节能'],
    config: {
        name: '能源管理大屏', description: '企业能源管理数据', width: 1920, height: 1080, backgroundColor: '#0a0e27',
        components: [
            createComponent('em-title', 'title', '主标题', 560, 12, 800, 55, 100, { text: '能源管理监控平台', fontSize: 36, fontWeight: 'bold', color: '#10b981', textAlign: 'center' }),
            createComponent('em-kpi-1', 'number-card', '总能耗', 40, 85, 290, 85, 50, { title: '今日总能耗(kWh)', value: 128560, prefix: '', suffix: '' }),
            createComponent('em-kpi-2', 'number-card', '碳排放', 350, 85, 290, 85, 50, { title: '碳排放(tCO2)', value: 86.2, prefix: '', suffix: '' }),
            createComponent('em-kpi-3', 'number-card', '节能率', 660, 85, 290, 85, 50, { title: '节能达成率', value: 92.5, prefix: '', suffix: '%' }),
            createComponent('em-kpi-4', 'number-card', '用电成本', 970, 85, 290, 85, 50, { title: '今日电费(元)', value: 89650, prefix: '¥', suffix: '' }),
            createComponent('em-line', 'line-chart', '能耗趋势', 30, 190, 920, 400, 20, { title: '24小时能耗趋势', xAxisData: ['00:00', '04:00', '08:00', '12:00', '16:00', '20:00'], series: [{ name: '电能(kWh)', data: [2800, 1500, 8200, 12500, 11800, 6200] }, { name: '天然气(m³)', data: [120, 80, 350, 480, 420, 200] }] }),
            createComponent('em-pie', 'pie-chart', '能源结构', 970, 190, 460, 400, 20, { title: '能源消费结构', data: [{ name: '电力', value: 65 }, { name: '天然气', value: 20 }, { name: '蒸汽', value: 10 }, { name: '其他', value: 5 }] }),
            createComponent('em-bar', 'bar-chart', '车间对比', 1450, 190, 440, 400, 20, { title: '车间能耗对比', xAxisData: ['一车间', '二车间', '三车间', '四车间'], series: [{ name: '能耗', data: [42000, 38000, 28000, 20000] }] }),
            createComponent('em-combo', 'combo-chart', '月度能耗', 30, 610, 920, 440, 20, { title: '月度能耗与成本', xAxisData: ['1月', '2月', '3月', '4月', '5月', '6月'], series: [{ name: '能耗(万kWh)', type: 'bar', yAxisIndex: 0, data: [380, 350, 420, 460, 440, 400] }, { name: '成本(万元)', type: 'line', yAxisIndex: 1, data: [268, 245, 296, 325, 310, 282] }], yAxis: [{ name: '能耗(万kWh)' }, { name: '成本(万元)' }] }),
            createComponent('em-ranking', 'scroll-ranking', '耗能排行', 970, 620, 450, 430, 20, { data: [{ name: '空调系统', value: 35200 }, { name: '生产设备', value: 28600 }, { name: '照明系统', value: 12400 }, { name: '压缩空气', value: 8900 }, { name: '其他', value: 5400 }], rowNum: 5, waitTime: 2000 }),
            createComponent('em-gauge', 'gauge-chart', '节能指数', 1450, 620, 430, 300, 20, { title: '节能指数', value: 92.5, min: 0, max: 100 }),
        ],
    },
};

// ============================================================
// 零售/电商模板
// ============================================================
const salesRealtimeTemplate: ScreenTemplate = {
    id: 'sales-realtime', name: '销售实时大屏', description: 'GMV/订单量/区域销售实时监控', thumbnail: '💰',
    category: 'retail', tags: ['销售', 'GMV', '电商', '实时'],
    config: {
        name: '销售实时大屏', description: '销售数据实时监控', width: 1920, height: 1080, backgroundColor: '#0a0e27',
        components: [
            createComponent('sr-title', 'title', '主标题', 560, 12, 800, 55, 100, { text: '全渠道销售实时监控', fontSize: 36, fontWeight: 'bold', color: '#f59e0b', textAlign: 'center' }),
            createComponent('sr-dt', 'datetime', '时间', 1660, 22, 220, 35, 98, { format: 'YYYY-MM-DD HH:mm:ss', fontSize: 16, color: '#f59e0b' }),
            createComponent('sr-kpi-gmv', 'number-card', 'GMV', 560, 80, 340, 100, 50, { title: '今日GMV', value: 8956234, prefix: '¥', suffix: '', valueFontSize: 42, valueColor: '#f59e0b' }),
            createComponent('sr-kpi-1', 'number-card', '订单量', 40, 80, 240, 85, 50, { title: '今日订单', value: 42568, prefix: '', suffix: '' }),
            createComponent('sr-kpi-2', 'number-card', '客单价', 300, 80, 240, 85, 50, { title: '客单价', value: 210, prefix: '¥', suffix: '' }),
            createComponent('sr-kpi-3', 'number-card', '转化率', 960, 80, 240, 85, 50, { title: '转化率', value: 3.8, prefix: '', suffix: '%' }),
            createComponent('sr-kpi-4', 'number-card', '退货率', 1220, 80, 240, 85, 50, { title: '退货率', value: 2.1, prefix: '', suffix: '%' }),
            createComponent('sr-kpi-5', 'number-card', '新客占比', 1480, 80, 200, 85, 50, { title: '新客占比', value: 28.5, prefix: '', suffix: '%' }),
            createComponent('sr-kpi-6', 'number-card', '复购率', 1700, 80, 180, 85, 50, { title: '复购率', value: 45.2, prefix: '', suffix: '%' }),
            createComponent('sr-map', 'map-chart', '区域销售', 30, 190, 620, 440, 20, { title: '区域销售分布', mapScope: 'china', mapMode: 'bubble', usePresetGeoJson: true, scatterData: [{ name: '北京', value: [116.46, 39.92, 2600] }, { name: '上海', value: [121.48, 31.22, 3200] }, { name: '广州', value: [113.23, 23.16, 1800] }, { name: '深圳', value: [114.07, 22.62, 1500] }, { name: '杭州', value: [120.19, 30.26, 1200] }], bubbleSizeRange: [10, 40] }),
            createComponent('sr-line', 'line-chart', '销售趋势', 670, 190, 620, 440, 20, { title: '24小时GMV趋势', xAxisData: ['00:00', '02:00', '04:00', '06:00', '08:00', '10:00', '12:00', '14:00', '16:00', '18:00', '20:00', '22:00'], series: [{ name: '今日', data: [120, 80, 50, 30, 180, 520, 680, 560, 450, 620, 890, 780] }, { name: '昨日', data: [100, 70, 45, 25, 160, 480, 620, 520, 410, 580, 820, 720] }] }),
            createComponent('sr-funnel', 'funnel-chart', '转化漏斗', 1310, 190, 580, 440, 20, { title: '购买转化漏斗', data: [{ name: '浏览', value: 100 }, { name: '加购', value: 42 }, { name: '下单', value: 28 }, { name: '付款', value: 22 }, { name: '签收', value: 20 }] }),
            createComponent('sr-ranking', 'scroll-ranking', '品类排行', 30, 650, 620, 400, 20, { data: [{ name: '数码电子', value: 2850000 }, { name: '服饰鞋包', value: 2120000 }, { name: '美妆护肤', value: 1560000 }, { name: '食品饮料', value: 980000 }, { name: '家居日用', value: 720000 }, { name: '母婴玩具', value: 560000 }], rowNum: 6, waitTime: 2000 }),
            createComponent('sr-board', 'scroll-board', '实时订单', 670, 650, 1220, 400, 20, { header: ['时间', '订单号', '商品', '金额', '城市'], data: [['14:32:15', 'SO20260224001', 'iPhone 16 Pro', '¥8,999', '上海'], ['14:32:12', 'SO20260224002', 'AirPods Pro', '¥1,899', '北京'], ['14:32:08', 'SO20260224003', 'Nike AJ1', '¥1,299', '广州'], ['14:31:55', 'SO20260224004', '戴森吹风机', '¥3,290', '杭州'], ['14:31:48', 'SO20260224005', 'MacBook Air', '¥9,999', '深圳']], rowNum: 7, headerBGC: '#003366', oddRowBGC: 'rgba(0,100,200,0.1)', evenRowBGC: 'rgba(0,50,100,0.1)', waitTime: 2000 }),
        ],
    },
};

const storeOperationTemplate: ScreenTemplate = {
    id: 'store-operation', name: '门店运营看板', description: '门店销售对比/日趋势/品类占比', thumbnail: '🏪',
    category: 'retail', tags: ['门店', '零售', '运营', '对比'],
    config: {
        name: '门店运营看板', description: '门店运营数据看板', width: 1920, height: 1080, backgroundColor: '#0a0e27',
        components: [
            createComponent('so-title', 'title', '主标题', 560, 12, 800, 55, 100, { text: '门店运营分析看板', fontSize: 36, fontWeight: 'bold', color: '#00d4ff', textAlign: 'center' }),
            createComponent('so-kpi-1', 'number-card', '总营收', 40, 85, 290, 85, 50, { title: '今日总营收', value: 856200, prefix: '¥', suffix: '' }),
            createComponent('so-kpi-2', 'number-card', '客流量', 350, 85, 290, 85, 50, { title: '今日客流', value: 12860, prefix: '', suffix: '' }),
            createComponent('so-kpi-3', 'number-card', '坪效', 660, 85, 290, 85, 50, { title: '坪效(元/㎡)', value: 285, prefix: '', suffix: '' }),
            createComponent('so-kpi-4', 'number-card', '人效', 970, 85, 290, 85, 50, { title: '人效(元/人)', value: 4280, prefix: '', suffix: '' }),
            createComponent('so-bar', 'bar-chart', '门店对比', 30, 190, 620, 400, 20, { title: '门店销售额对比', xAxisData: ['旗舰店', 'A商场店', 'B广场店', 'C购物中心', 'D社区店', 'E机场店'], series: [{ name: '销售额', data: [186000, 152000, 128000, 96000, 72000, 56000] }] }),
            createComponent('so-line', 'line-chart', '日趋势', 670, 190, 620, 400, 20, { title: '近7日销售趋势', xAxisData: ['周一', '周二', '周三', '周四', '周五', '周六', '周日'], series: [{ name: '销售额', data: [120000, 115000, 125000, 118000, 135000, 186000, 168000] }, { name: '客流', data: [1800, 1720, 1850, 1760, 2100, 2860, 2540] }] }),
            createComponent('so-pie', 'pie-chart', '品类占比', 1310, 190, 580, 400, 20, { title: '品类销售占比', data: [{ name: '服饰', value: 35 }, { name: '鞋品', value: 25 }, { name: '配饰', value: 20 }, { name: '箱包', value: 12 }, { name: '其他', value: 8 }] }),
            createComponent('so-combo', 'combo-chart', '客单价趋势', 30, 610, 920, 440, 20, { title: '销售额与客单价', xAxisData: ['1月', '2月', '3月', '4月', '5月', '6月'], series: [{ name: '销售额(万)', type: 'bar', yAxisIndex: 0, data: [580, 520, 620, 680, 650, 720] }, { name: '客单价', type: 'line', yAxisIndex: 1, data: [210, 195, 225, 238, 230, 252] }], yAxis: [{ name: '销售额(万)' }, { name: '客单价(元)' }] }),
            createComponent('so-ranking', 'scroll-ranking', 'TOP商品', 970, 620, 920, 430, 20, { data: [{ name: '经典款外套', value: 56800 }, { name: '限定版运动鞋', value: 48200 }, { name: '真丝连衣裙', value: 42500 }, { name: '设计师手袋', value: 38900 }, { name: '羊绒围巾', value: 32600 }], rowNum: 5, waitTime: 2000 }),
        ],
    },
};

// ============================================================
// 金融/风控模板
// ============================================================
const financeMonitorTemplate: ScreenTemplate = {
    id: 'finance-monitor', name: '资金监控大屏', description: '资金流动/结构分析/交易明细', thumbnail: '🏦',
    category: 'finance', tags: ['金融', '资金', '交易', '监控'],
    config: {
        name: '资金监控大屏', description: '资金监控数据大屏', width: 1920, height: 1080, backgroundColor: '#0a0e27',
        components: [
            createComponent('fm-title', 'title', '主标题', 560, 12, 800, 55, 100, { text: '资金监控中心', fontSize: 36, fontWeight: 'bold', color: '#f59e0b', textAlign: 'center' }),
            createComponent('fm-kpi-1', 'number-card', '总资产', 40, 85, 290, 85, 50, { title: '管理总资产(亿)', value: 1256.8, prefix: '¥', suffix: '' }),
            createComponent('fm-kpi-2', 'number-card', '今日流入', 350, 85, 290, 85, 50, { title: '今日流入(万)', value: 86520, prefix: '¥', suffix: '' }),
            createComponent('fm-kpi-3', 'number-card', '今日流出', 660, 85, 290, 85, 50, { title: '今日流出(万)', value: 72350, prefix: '¥', suffix: '' }),
            createComponent('fm-kpi-4', 'number-card', '净流入', 970, 85, 290, 85, 50, { title: '净流入(万)', value: 14170, prefix: '¥', suffix: '' }),
            createComponent('fm-line', 'line-chart', '资金流趋势', 30, 190, 920, 400, 20, { title: '近30日资金流趋势', xAxisData: ['1日', '5日', '10日', '15日', '20日', '25日', '30日'], series: [{ name: '流入', data: [8200, 9100, 7800, 8600, 9500, 10200, 8650] }, { name: '流出', data: [7100, 8200, 6900, 7800, 8600, 9100, 7235] }] }),
            createComponent('fm-pie', 'pie-chart', '资产结构', 970, 190, 460, 400, 20, { title: '资产结构分布', data: [{ name: '固定收益', value: 45 }, { name: '权益投资', value: 25 }, { name: '货币基金', value: 15 }, { name: '另类投资', value: 10 }, { name: '现金', value: 5 }] }),
            createComponent('fm-waterfall', 'waterfall-chart', '资金变动', 1450, 190, 440, 400, 20, { title: '月度资金变动', data: [{ name: '期初', value: 10000, isTotal: true }, { name: '利息收入', value: 800 }, { name: '投资收益', value: 1200 }, { name: '运营支出', value: -600 }, { name: '税费', value: -200 }, { name: '期末', value: 11200, isTotal: true }] }),
            createComponent('fm-board', 'scroll-board', '交易明细', 30, 610, 1200, 440, 20, { header: ['时间', '交易类型', '对手方', '金额(万)', '状态'], data: [['14:32', '融资', '工商银行', '+5,000', '已完成'], ['14:28', '投资', '国债', '-3,200', '已完成'], ['14:22', '回款', '项目A', '+1,800', '已完成'], ['14:18', '付款', '供应商B', '-960', '处理中'], ['14:12', '收款', '客户C', '+2,400', '已完成']], rowNum: 7, headerBGC: '#003366', oddRowBGC: 'rgba(0,100,200,0.1)', evenRowBGC: 'rgba(0,50,100,0.1)', waitTime: 3000 }),
            createComponent('fm-gauge', 'gauge-chart', '流动性', 1250, 620, 300, 300, 20, { title: '流动性指标', value: 85, min: 0, max: 100 }),
            createComponent('fm-radar', 'radar-chart', '风险评估', 1570, 620, 320, 300, 20, { title: '风险评估', indicator: [{ name: '信用风险', max: 100 }, { name: '市场风险', max: 100 }, { name: '流动性', max: 100 }, { name: '操作风险', max: 100 }, { name: '合规风险', max: 100 }], data: [85, 72, 88, 92, 95] }),
        ],
    },
};

const riskAlertTemplate: ScreenTemplate = {
    id: 'risk-alert', name: '风控预警大屏', description: '风险等级/告警滚动/风险分布', thumbnail: '🛡️',
    category: 'finance', tags: ['风控', '预警', '风险', '合规'],
    config: {
        name: '风控预警大屏', description: '风控预警数据大屏', width: 1920, height: 1080, backgroundColor: '#0a0e27',
        components: [
            createComponent('ra-title', 'title', '主标题', 560, 12, 800, 55, 100, { text: '风控预警监控中心', fontSize: 36, fontWeight: 'bold', color: '#ef4444', textAlign: 'center' }),
            createComponent('ra-kpi-1', 'number-card', '待处理告警', 40, 85, 290, 85, 50, { title: '待处理告警', value: 23, prefix: '', suffix: '条', valueColor: '#ef4444' }),
            createComponent('ra-kpi-2', 'number-card', '今日新增', 350, 85, 290, 85, 50, { title: '今日新增', value: 8, prefix: '', suffix: '条' }),
            createComponent('ra-kpi-3', 'number-card', '处置率', 660, 85, 290, 85, 50, { title: '处置率', value: 94.5, prefix: '', suffix: '%' }),
            createComponent('ra-kpi-4', 'number-card', '误报率', 970, 85, 290, 85, 50, { title: '误报率', value: 3.2, prefix: '', suffix: '%' }),
            createComponent('ra-g1', 'gauge-chart', '风险等级', 40, 190, 280, 280, 50, { title: '综合风险', value: 32, min: 0, max: 100 }),
            createComponent('ra-g2', 'gauge-chart', '信用风险', 340, 190, 280, 280, 50, { title: '信用风险', value: 28, min: 0, max: 100 }),
            createComponent('ra-g3', 'gauge-chart', '市场风险', 640, 190, 280, 280, 50, { title: '市场风险', value: 42, min: 0, max: 100 }),
            createComponent('ra-g4', 'gauge-chart', '操作风险', 940, 190, 280, 280, 50, { title: '操作风险', value: 18, min: 0, max: 100 }),
            createComponent('ra-map', 'map-chart', '风险分布', 1240, 190, 650, 380, 20, { title: '区域风险分布', mapScope: 'china', usePresetGeoJson: true, enableRegionDrill: false, regionVariableKey: 'region', regions: [{ name: '北京市', code: '110000', value: 45 }, { name: '上海市', code: '310000', value: 38 }, { name: '广东省', code: '440000', value: 52 }, { name: '浙江省', code: '330000', value: 28 }, { name: '江苏省', code: '320000', value: 32 }] }),
            createComponent('ra-board', 'scroll-board', '告警列表', 30, 490, 1190, 560, 20, { header: ['时间', '告警ID', '风险类型', '客户/对象', '风险等级', '状态'], data: [['14:32', 'RA-20260224-001', '异常交易', '客户A', '高', '待处理'], ['14:28', 'RA-20260224-002', '信用违约', '企业B', '中', '处理中'], ['14:22', 'RA-20260224-003', '合规风险', '项目C', '低', '已处置'], ['14:18', 'RA-20260224-004', '欺诈嫌疑', '账户D', '高', '待处理'], ['14:12', 'RA-20260224-005', '大额转账', '客户E', '中', '已处置']], rowNum: 8, headerBGC: '#4a1a1a', oddRowBGC: 'rgba(200,50,50,0.1)', evenRowBGC: 'rgba(150,30,30,0.1)', waitTime: 3000 }),
            createComponent('ra-line', 'line-chart', '告警趋势', 1240, 590, 650, 460, 20, { title: '近7日告警趋势', xAxisData: ['周一', '周二', '周三', '周四', '周五', '周六', '周日'], series: [{ name: '高风险', data: [3, 5, 2, 4, 6, 1, 2] }, { name: '中风险', data: [8, 12, 6, 10, 14, 4, 6] }, { name: '低风险', data: [15, 18, 12, 16, 20, 8, 10] }] }),
        ],
    },
};

// ============================================================
// 教育/医疗模板
// ============================================================
const campusDataTemplate: ScreenTemplate = {
    id: 'campus-data', name: '校园数据大屏', description: '在校生/教师/班级/成绩数据', thumbnail: '🎓',
    category: 'education', tags: ['教育', '校园', '学生', '教学'],
    config: {
        name: '校园数据大屏', description: '校园数据可视化大屏', width: 1920, height: 1080, backgroundColor: '#0a0e27',
        components: [
            createComponent('cd-title', 'title', '主标题', 560, 12, 800, 55, 100, { text: '智慧校园数据中心', fontSize: 36, fontWeight: 'bold', color: '#8b5cf6', textAlign: 'center' }),
            createComponent('cd-kpi-1', 'number-card', '在校生', 40, 85, 290, 85, 50, { title: '在校学生', value: 12860, prefix: '', suffix: '人' }),
            createComponent('cd-kpi-2', 'number-card', '教师', 350, 85, 290, 85, 50, { title: '教职工', value: 856, prefix: '', suffix: '人' }),
            createComponent('cd-kpi-3', 'number-card', '班级', 660, 85, 290, 85, 50, { title: '教学班级', value: 420, prefix: '', suffix: '个' }),
            createComponent('cd-kpi-4', 'number-card', '就业率', 970, 85, 290, 85, 50, { title: '就业率', value: 96.8, prefix: '', suffix: '%' }),
            createComponent('cd-bar', 'bar-chart', '学院人数', 30, 190, 620, 400, 20, { title: '学院在校生人数', xAxisData: ['计算机', '经管', '外语', '机械', '艺术', '医学', '法学', '理学'], series: [{ name: '在校生', data: [2860, 2120, 1650, 1420, 1080, 960, 850, 720] }] }),
            createComponent('cd-pie', 'pie-chart', '学历分布', 670, 190, 580, 400, 20, { title: '学历层次分布', data: [{ name: '本科', value: 65 }, { name: '硕士', value: 25 }, { name: '博士', value: 8 }, { name: '其他', value: 2 }] }),
            createComponent('cd-radar', 'radar-chart', '教学评估', 1270, 190, 620, 400, 20, { title: '教学质量评估', indicator: [{ name: '教学水平', max: 100 }, { name: '科研能力', max: 100 }, { name: '就业质量', max: 100 }, { name: '国际化', max: 100 }, { name: '基础设施', max: 100 }], data: [92, 85, 88, 72, 90] }),
            createComponent('cd-line', 'line-chart', '招生趋势', 30, 610, 920, 440, 20, { title: '近5年招生趋势', xAxisData: ['2022', '2023', '2024', '2025', '2026'], series: [{ name: '招生人数', data: [3200, 3400, 3600, 3800, 4000] }, { name: '毕业人数', data: [2800, 3000, 3200, 3400, 3500] }] }),
            createComponent('cd-ranking', 'scroll-ranking', '成绩排名', 970, 620, 920, 430, 20, { data: [{ name: '计算机学院', value: 88 }, { name: '经管学院', value: 85 }, { name: '理学院', value: 84 }, { name: '外语学院', value: 82 }, { name: '医学院', value: 81 }], rowNum: 5, waitTime: 2000 }),
        ],
    },
};

const hospitalOperationTemplate: ScreenTemplate = {
    id: 'hospital-operation', name: '医院运营大屏', description: '门诊/住院/手术/科室数据', thumbnail: '🏥',
    category: 'education', tags: ['医疗', '医院', '门诊', '运营'],
    config: {
        name: '医院运营大屏', description: '医院运营数据大屏', width: 1920, height: 1080, backgroundColor: '#0a0e27',
        components: [
            createComponent('ho-title', 'title', '主标题', 560, 12, 800, 55, 100, { text: '智慧医院运营大屏', fontSize: 36, fontWeight: 'bold', color: '#10b981', textAlign: 'center' }),
            createComponent('ho-kpi-1', 'number-card', '门诊量', 40, 85, 290, 85, 50, { title: '今日门诊量', value: 3256, prefix: '', suffix: '人次' }),
            createComponent('ho-kpi-2', 'number-card', '住院', 350, 85, 290, 85, 50, { title: '在院患者', value: 1286, prefix: '', suffix: '人' }),
            createComponent('ho-kpi-3', 'number-card', '手术', 660, 85, 290, 85, 50, { title: '今日手术', value: 86, prefix: '', suffix: '台' }),
            createComponent('ho-kpi-4', 'number-card', '床位率', 970, 85, 290, 85, 50, { title: '床位使用率', value: 92.5, prefix: '', suffix: '%' }),
            createComponent('ho-kpi-5', 'number-card', '满意度', 1280, 85, 290, 85, 50, { title: '患者满意度', value: 97.2, prefix: '', suffix: '%' }),
            createComponent('ho-bar', 'bar-chart', '科室门诊', 30, 190, 620, 400, 20, { title: '科室门诊量', xAxisData: ['内科', '外科', '儿科', '妇产', '骨科', '眼科', '口腔', '中医'], series: [{ name: '门诊量', data: [620, 480, 350, 320, 280, 240, 210, 180] }] }),
            createComponent('ho-line', 'line-chart', '月度趋势', 670, 190, 620, 400, 20, { title: '月度门诊量趋势', xAxisData: ['1月', '2月', '3月', '4月', '5月', '6月'], series: [{ name: '门诊', data: [82000, 76000, 88000, 92000, 95000, 98000] }, { name: '住院', data: [1200, 1150, 1280, 1300, 1320, 1350] }] }),
            createComponent('ho-gauge', 'gauge-chart', '急诊等待', 1310, 190, 280, 280, 20, { title: '急诊平均等待(分)', value: 28, min: 0, max: 60 }),
            createComponent('ho-pie', 'pie-chart', '收入结构', 1610, 190, 280, 280, 20, { title: '收入结构', data: [{ name: '药品', value: 35 }, { name: '医疗服务', value: 40 }, { name: '检查检验', value: 18 }, { name: '其他', value: 7 }] }),
            createComponent('ho-board', 'scroll-board', '手术排程', 30, 610, 920, 440, 20, { header: ['时间', '手术室', '科室', '手术类型', '状态'], data: [['14:30', '手术室1', '外科', '腹腔镜手术', '进行中'], ['14:00', '手术室2', '骨科', '关节置换', '进行中'], ['15:00', '手术室3', '妇产', '剖宫产', '等待中'], ['15:30', '手术室1', '外科', '甲状腺手术', '等待中'], ['16:00', '手术室4', '眼科', '白内障手术', '等待中']], rowNum: 7, headerBGC: '#003366', oddRowBGC: 'rgba(0,100,200,0.1)', evenRowBGC: 'rgba(0,50,100,0.1)', waitTime: 3000 }),
            createComponent('ho-ranking', 'scroll-ranking', '医生排名', 970, 620, 920, 430, 20, { data: [{ name: '张主任(外科)', value: 156 }, { name: '李教授(内科)', value: 142 }, { name: '王主任(儿科)', value: 128 }, { name: '赵教授(妇产)', value: 115 }, { name: '刘主任(骨科)', value: 98 }], rowNum: 5, waitTime: 2000 }),
        ],
    },
};

// ============================================================
// 通用模板补充
// ============================================================
const opsKpiTemplate: ScreenTemplate = {
    id: 'ops-kpi-center', name: '运营指标中心', description: '九宫格KPI+趋势+进度条', thumbnail: '📋',
    category: 'general', tags: ['运营', 'KPI', '指标', '九宫格'],
    config: {
        name: '运营指标中心', description: '核心运营指标看板', width: 1920, height: 1080, backgroundColor: '#0a0e27',
        components: [
            createComponent('ok-title', 'title', '主标题', 560, 12, 800, 55, 100, { text: '运营指标中心', fontSize: 36, fontWeight: 'bold', color: '#00d4ff', textAlign: 'center' }),
            createComponent('ok-kpi-1', 'number-card', 'DAU', 40, 85, 280, 90, 50, { title: '日活用户(DAU)', value: 128560, prefix: '', suffix: '' }),
            createComponent('ok-kpi-2', 'number-card', 'MAU', 340, 85, 280, 90, 50, { title: '月活用户(MAU)', value: 2856000, prefix: '', suffix: '' }),
            createComponent('ok-kpi-3', 'number-card', '留存率', 640, 85, 280, 90, 50, { title: '次日留存率', value: 42.5, prefix: '', suffix: '%' }),
            createComponent('ok-kpi-4', 'number-card', 'ARPU', 940, 85, 280, 90, 50, { title: 'ARPU(元)', value: 28.6, prefix: '¥', suffix: '' }),
            createComponent('ok-kpi-5', 'number-card', '付费率', 1240, 85, 280, 90, 50, { title: '付费转化率', value: 5.2, prefix: '', suffix: '%' }),
            createComponent('ok-kpi-6', 'number-card', 'NPS', 1540, 85, 340, 90, 50, { title: 'NPS得分', value: 72, prefix: '', suffix: '' }),
            createComponent('ok-line1', 'line-chart', 'DAU趋势', 30, 195, 620, 380, 20, { title: 'DAU趋势(近30日)', xAxisData: ['1日', '5日', '10日', '15日', '20日', '25日', '30日'], series: [{ name: 'DAU', data: [118000, 125000, 128000, 132000, 126000, 130000, 128560] }] }),
            createComponent('ok-line2', 'line-chart', '留存曲线', 670, 195, 620, 380, 20, { title: '留存率曲线', xAxisData: ['次日', '3日', '7日', '14日', '30日'], series: [{ name: '留存率', data: [42.5, 28.2, 18.6, 12.4, 8.2] }] }),
            createComponent('ok-bar', 'bar-chart', '渠道分布', 1310, 195, 580, 380, 20, { title: '用户来源渠道', xAxisData: ['自然流量', 'SEM', '社交媒体', '应用商店', '推荐', '其他'], series: [{ name: '用户数', data: [45000, 28000, 22000, 18000, 12000, 3560] }] }),
            createComponent('ok-p1', 'progress-bar', 'DAU目标', 40, 600, 420, 35, 20, { value: 86, showLabel: true }),
            createComponent('ok-p2', 'progress-bar', '收入目标', 40, 645, 420, 35, 20, { value: 92, showLabel: true }),
            createComponent('ok-p3', 'progress-bar', '留存目标', 40, 690, 420, 35, 20, { value: 78, showLabel: true }),
            createComponent('ok-funnel', 'funnel-chart', '转化漏斗', 490, 600, 580, 450, 20, { title: '用户转化漏斗', data: [{ name: '访问', value: 100 }, { name: '注册', value: 45 }, { name: '激活', value: 32 }, { name: '付费', value: 8 }, { name: '续费', value: 5 }] }),
            createComponent('ok-pie', 'pie-chart', '收入结构', 1090, 600, 400, 450, 20, { title: '收入结构', data: [{ name: '会员订阅', value: 45 }, { name: '广告收入', value: 30 }, { name: '增值服务', value: 18 }, { name: '其他', value: 7 }] }),
            createComponent('ok-scatter', 'scatter-chart', '用户分布', 1510, 600, 380, 450, 20, { title: '活跃度-消费分布', data: [[10, 8], [8, 7], [13, 8], [9, 9], [11, 8], [14, 10], [6, 7], [4, 4], [12, 11], [7, 5]] }),
        ],
    },
};

/**
 * 所有可用模板
 */
export const screenTemplates: ScreenTemplate[] = [
    blankTemplate,
    blank4kTemplate,
    blankSmallTemplate,
    techDataCenterTemplate,
    patentDataCenterTemplate,
    patentTitaniumTemplate,
    businessLightTemplate,
    opsKpiTemplate,
    smartCityTemplate,
    govServiceTemplate,
    productionMonitorTemplate,
    energyManagementTemplate,
    salesRealtimeTemplate,
    storeOperationTemplate,
    financeMonitorTemplate,
    riskAlertTemplate,
    campusDataTemplate,
    hospitalOperationTemplate,
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
        schemaVersion: SCREEN_SCHEMA_VERSION,
        ...template.config,
        components: template.config.components.map((comp, idx) => ({
            ...comp,
            id: `comp_${timestamp}_${idx}_${Math.random().toString(36).substr(2, 9)}`,
        })),
    };
}
