"""
从 screen-instances-layout-only/ 读取布局 JSON，
从 screen-queries/ 读取 SQL，
注入 sqlConfig 后写入 screen-instances-with-sql/
"""
import json, glob, os, re

SRC_DIR = "screen-instances-layout-only"
SQL_DIR = "screen-queries"
OUT_DIR = "screen-instances-with-sql"
DATABASE_ID_PLACEHOLDER = "{{DATABASE_ID}}"

# 读取所有查询 SQL（去掉注释行）
def read_sql(filename):
    path = os.path.join(SQL_DIR, filename)
    if not os.path.exists(path):
        return None
    with open(path) as f:
        return '\n'.join(l for l in f.read().split('\n') if not l.startswith('--')).strip()

# 按大屏 ID + 组件类型匹配查询卡片
SCREEN_KPI_CARD = {
    'gpmc-strategic-overview': 'card-execution-kpi-overview.sql',
    'gpmc-execution-board': 'card-execution-kpi-overview.sql',
    'gpmc-quality-board': 'card-quality-kpi.sql',
    'gpmc-tech-state-board': 'card-tech-state-kpi.sql',
    'gpmc-cost-board': 'card-cost-kpi.sql',
    'gpmc-risk-board': 'card-risk-kpi.sql',
    'gpmc-drill-execution': 'card-execution-kpi-overview.sql',
    'gpmc-drill-quality': 'card-quality-kpi.sql',
    'gpmc-drill-tech-state': 'card-tech-state-kpi.sql',
    'gpmc-drill-cost': 'card-cost-kpi.sql',
    'gpmc-drill-risk': 'card-risk-kpi.sql',
}

# 按组件名称关键词匹配 SQL 文件
KEYWORD_SQL_MAP = {
    '预算': 'card-cost-kpi.sql', '成本': 'card-cost-kpi.sql', '执行率': 'card-cost-kpi.sql',
    '偏差': 'card-cost-kpi.sql', '燃尽': 'card-cost-kpi.sql',
    '风险': 'card-risk-kpi.sql', '闭环率': 'card-risk-kpi.sql',
    '质量': 'card-quality-kpi.sql', '归零': 'card-quality-kpi.sql',
    '签署': 'card-tech-state-kpi.sql', '更改': 'card-tech-state-kpi.sql', '技术状态': 'card-tech-state-kpi.sql',
    '里程碑': 'card-milestone-kpi.sql',
    '完成率': 'card-execution-kpi-overview.sql', '达成率': 'card-execution-kpi-overview.sql',
    '延期': 'card-execution-kpi-overview.sql', '阻塞': 'card-incomplete-risk.sql',
    '健康': 'card-major-project-overview.sql', '项目总': 'card-major-project-overview.sql',
    '进行中': 'card-execution-kpi-overview.sql', '预警': 'card-risk-kpi.sql',
    '措施覆盖': 'card-tech-state-kpi.sql', '变更频率': 'card-tech-state-kpi.sql',
    '平均关闭': 'card-risk-kpi.sql', '平均闭环': 'card-risk-kpi.sql',
}

# 下钻页表格匹配
DRILL_TABLE_MAP = {
    'gpmc-drill-execution': {'detail': 'card-project-tree-snapshot.sql', 'side': 'card-weekly-subproject-summary.sql', 'support': 'card-delay-reason-trend.sql'},
    'gpmc-drill-quality': {'detail': 'card-quality-issue-list.sql', 'side': 'card-quality-kpi.sql', 'support': 'card-quality-measure-list.sql'},
    'gpmc-drill-tech-state': {'detail': 'card-tech-state-list.sql', 'side': 'card-tech-state-kpi.sql', 'support': 'card-tech-state-measure-list.sql'},
    'gpmc-drill-cost': {'detail': 'card-cost-period-summary.sql', 'side': 'card-cost-kpi.sql', 'support': 'card-cost-kpi.sql'},
    'gpmc-drill-risk': {'detail': 'card-risk-info-list.sql', 'side': 'card-risk-period-summary.sql', 'support': 'card-risk-measure-list.sql'},
}

DATA_TYPES = {'number-card', 'line-chart', 'bar-chart', 'pie-chart', 'gauge-chart', 'table', 'gantt-chart'}

def find_sql_for_component(comp, screen_id):
    name = comp.get('name', '')
    comp_id = comp.get('id', '')
    comp_type = comp.get('type', '')
    
    # 下钻页表格
    if screen_id.startswith('gpmc-drill-') and comp_type == 'table':
        table_map = DRILL_TABLE_MAP.get(screen_id, {})
        if 'detail' in comp_id: return table_map.get('detail')
        if 'side' in comp_id: return table_map.get('side')
        if 'support' in comp_id: return table_map.get('support')
        return table_map.get('detail')
    
    # 甘特图
    if comp_type == 'gantt-chart': return 'card-project-tree-snapshot.sql'
    if comp_type == 'gauge-chart': return 'card-major-project-overview.sql'
    
    # 按名称关键词匹配
    for kw, sql_file in KEYWORD_SQL_MAP.items():
        if kw in name:
            return sql_file
    
    # fallback 到大屏默认 KPI
    return SCREEN_KPI_CARD.get(screen_id)

def make_sql_config(sql_content):
    return {
        'type': 'sql',
        'sqlConfig': {
            'databaseId': DATABASE_ID_PLACEHOLDER,
            'query': sql_content,
            'queryTimeoutSeconds': 30,
            'maxRows': 2000,
        }
    }

# 处理每个 JSON
for src_path in sorted(glob.glob(os.path.join(SRC_DIR, '*.json'))):
    fname = os.path.basename(src_path)
    with open(src_path) as f:
        screen = json.load(f)
    
    screen_id = screen.get('id', '')
    injected = 0
    
    for comp in screen.get('components', []):
        if comp.get('type') not in DATA_TYPES:
            continue
        
        sql_file = find_sql_for_component(comp, screen_id)
        if not sql_file:
            continue
        
        sql_content = read_sql(sql_file)
        if not sql_content:
            continue
        
        comp['dataSource'] = make_sql_config(sql_content)
        injected += 1
    
    out_path = os.path.join(OUT_DIR, fname)
    with open(out_path, 'w') as f:
        json.dump(screen, f, ensure_ascii=False, indent=2)
    
    total_data = sum(1 for c in screen.get('components', []) if c.get('type') in DATA_TYPES)
    print(f"{fname}: {injected}/{total_data} 组件注入 sqlConfig")

