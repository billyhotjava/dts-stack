import type { ScreenConfig, ScreenComponent } from './types';
import { SCREEN_SCHEMA_VERSION } from './specV2';

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
 * 内置模板：专利数据中心
 * 深蓝背景，展示专利申请/受理/授权核心指标、类型分布、月度趋势、部门排行和最新授权
 */
const patentDataCenterTemplate: ScreenTemplate = {
    id: 'patent-data-center',
    name: '专利数据中心',
    description: '专利数据可视化大屏：KPI指标、类型占比、月度趋势、部门排行、近期授权、申请详情、超期预警',
    thumbnail: '📋',
    category: 'business',
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
    category: 'business',
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
    category: 'business',
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

/**
 * 所有可用模板
 */
export const screenTemplates: ScreenTemplate[] = [
    blankTemplate,
    techDataCenterTemplate,
    patentDataCenterTemplate,
    patentTitaniumTemplate,
    businessLightTemplate,
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
