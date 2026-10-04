"""
生成客户技术协议 Gap 分析与落地计划 Excel（v2.0 基于代码粗 review）。
输出：worklog/v2.2.3/protocol-gap-plan.xlsx
"""
from openpyxl import Workbook
from openpyxl.styles import Font, PatternFill, Alignment, Border, Side
from openpyxl.utils import get_column_letter
from pathlib import Path

# -----------------------------
# 样式
# -----------------------------
TITLE_FONT = Font(name="微软雅黑", size=14, bold=True, color="FFFFFF")
HEADER_FONT = Font(name="微软雅黑", size=11, bold=True, color="FFFFFF")
CELL_FONT = Font(name="微软雅黑", size=10)
WRAP = Alignment(wrap_text=True, vertical="top", horizontal="left")
CENTER = Alignment(wrap_text=True, vertical="center", horizontal="center")
TITLE_FILL = PatternFill("solid", fgColor="1F4E79")
HEADER_FILL = PatternFill("solid", fgColor="2F75B5")
GROUP_FILL = PatternFill("solid", fgColor="D9E1F2")
COVER_FILL_FULL = PatternFill("solid", fgColor="C6EFCE")
COVER_FILL_PART = PatternFill("solid", fgColor="FFEB9C")
COVER_FILL_GAP = PatternFill("solid", fgColor="FFC7CE")
PRIORITY_P0 = PatternFill("solid", fgColor="FFC7CE")
PRIORITY_P1 = PatternFill("solid", fgColor="FFEB9C")
PRIORITY_P2 = PatternFill("solid", fgColor="C6EFCE")
DELTA_UP = PatternFill("solid", fgColor="C6EFCE")      # 升级（更好）
DELTA_DOWN = PatternFill("solid", fgColor="FFC7CE")    # 下调
THIN = Side(border_style="thin", color="BFBFBF")
BORDER = Border(left=THIN, right=THIN, top=THIN, bottom=THIN)


def style_header(ws, row, cols):
    for c in range(1, cols + 1):
        cell = ws.cell(row=row, column=c)
        cell.font = HEADER_FONT
        cell.fill = HEADER_FILL
        cell.alignment = CENTER
        cell.border = BORDER


def apply_cells(ws, start_row, end_row, cols):
    for r in range(start_row, end_row + 1):
        for c in range(1, cols + 1):
            cell = ws.cell(row=r, column=c)
            cell.font = CELL_FONT
            cell.alignment = WRAP
            cell.border = BORDER


def set_col_widths(ws, widths):
    for idx, w in enumerate(widths, start=1):
        ws.column_dimensions[get_column_letter(idx)].width = w


def write_title(ws, text, cols):
    ws.merge_cells(start_row=1, start_column=1, end_row=1, end_column=cols)
    cell = ws.cell(row=1, column=1, value=text)
    cell.font = TITLE_FONT
    cell.fill = TITLE_FILL
    cell.alignment = CENTER
    ws.row_dimensions[1].height = 28


# =========================================================
# 数据（v2.0 基于代码粗 review 修订）
# =========================================================

# 1) 总览
overview_rows = [
    # 模块, 协议子项数, 已覆盖, 部分, 缺口, 评级, 总体评估
    ("1 *数据规划", 6, 2, 4, 0, "✅", "CatalogDomain + DataStandard + 附件 + WorkflowCenter 都在位；仅流程模板待预置"),
    ("2 *数据采集与清洗转换加工", 8, 7, 1, 0, "✅", "Ingestion + dbt + TransformCreatePage(71KB) + GovRule/DataCleansing 全齐，仅填报设计器需补全"),
    ("3 *数据存储", 3, 3, 0, 0, "✅", "数仓分层 / 模型 / Schema 全覆盖"),
    ("4 数据管理", 11, 7, 3, 1, "🟡", "OM 血缘 + 资产 + 标准均在；标签、全生命周期审批、销毁还原需补"),
    ("5 *数据安全", 7, 5, 2, 0, "✅", "RLS + 列掩码 + 分级 + 脱敏 + 备份均在；操作权限矩阵、自动敏感识别待补"),
    ("6 *数据质量", 5, 5, 0, 0, "✅", "QualityRule + QualityTask + IssueTicket + 报告导出 端到端闭环"),
    ("7 *数据服务管理", 4, 4, 0, 0, "✅", "SvcApi + ApiCatalog + BasicApi + 多格式交换都有"),
    ("8 *数据分析与可视化", 8, 6, 2, 0, "✅", "指标中心 + 35+ 图表 + 模板 / 节点编程补三件套 + 多终端 + Word 导出"),
    ("9 *监控和告警", 2, 1, 1, 0, "🟡", "AuditEntry 审计中心齐；Prometheus metrics 在，缺 Grafana/Alertmanager"),
    ("10 *安全保密功能", 2, 1, 1, 0, "🟡", "PKI + PKCS#7 + X509 已落地；JM 合规文档 / 整改 待启动"),
]

# 2) 协议对照（49 条）—— v2.0 真实代码证据
# 列：模块 / 子项 / 子功能 / 功能描述 / 旧版评级 / 新版评级 / 代码证据 / 对应 Feature
protocol_rows = [
    ("*数据规划", 1, "数据主题规划与管理", "支持根据业务域或职能、业务对象、分析主题进行数据主题规划与管理，支待多层级数据主题维护。",
     "🟡 部分", "✅ 已覆盖",
     "catalog/CatalogDomain.java + CatalogDomainResource + useCatalogDomainOptions.ts", "—"),
    ("*数据规划", 2, "数据全生命周期管理", "支持数据管理制度、标准、规范等数据的全生命周期管理。",
     "🟡 部分", "🟡 部分",
     "modeling/DataStandard + DataStandardVersion + MetadataStandard", "F-DP-02"),
    ("*数据规划", 3, "数据文件管理", "支持文件的分组、上传和下载，文件维护操作应具备审批流程。",
     "🔴 缺口", "🟡 部分",
     "infra/InfraExternalExchangeFile + DataStandardAttachmentService", "F-DP-03"),
    ("*数据规划", 4, "数据文件管理（格式）", "支待对多种格式(DOC、DOCX、PDF等）的制度标准等文件进行管理。",
     "🟡 部分", "🟡 部分",
     "modeling/AttachmentSignatureValidator", "F-DP-03"),
    ("*数据规划", 5, "数据管理流程", "支待数据管理流程在数据管理平台落地。",
     "🟡 部分", "🟡 部分",
     "workflow/AdminWorkflowConfigClient + WorkflowCenterPage.tsx + admin 工作流引擎", "F-DP-04"),
    ("*数据规划", 6, "数据标准", "支待数据标准定义和维护，支待以填写、模板导入方式建立数据标准。",
     "✅ 已覆盖", "✅ 已覆盖",
     "modeling/DataStandardImportService + MetadataStandardImportService + infra/ExcelImportService", "—"),

    ("*数据采集与清洗转换加工", 1, "数据源", "支持关系型数据库、API接口(Restful、消息队列）、离线文件（日志文件、CSV、XLS、XLSX、JSON)等多种数据源的迁移、集成与整合。",
     "🟡 部分", "🟡 部分",
     "ingestion/IngestionTask + infra/InfraDataSource + InfraManagementService", "F-ETL-01"),
    ("*数据采集与清洗转换加工", 2, "数据采集", "支待实时、定时采集及批量数据采集。",
     "✅ 已覆盖", "✅ 已覆盖",
     "ingestion/IncrementalSyncService + infra/InfraTaskSchedule + TaskSchedulingPage.tsx", "—"),
    ("*数据采集与清洗转换加工", 3, "数据同步", "支持数据全量、 增量更新、增量追加等多种数据同步策略。",
     "✅ 已覆盖", "✅ 已覆盖",
     "ingestion/IngestionIncrementalStateDTO + IngestionSourceResolver", "—"),
    ("*数据采集与清洗转换加工", 4, "数据填报", "具备数据填报功能，支持填报表单自定义创建和维护、数据填报任务下发和汇总、表单数据的统计查询及导入导出。",
     "🟡 部分", "🟡 部分",
     "infra/ExcelImportService + governance/OdsDataEditorService + modeling/BatchImportModal.tsx", "F-ETL-02"),
    ("*数据采集与清洗转换加工", 5, "ETL工具", "提供ETL（提取、 转换、 加载）工具或服务。",
     "✅ 已覆盖", "✅ 已覆盖",
     "platform/service/etl/（21 服务类）+ DbtConfig/DbtGit/DbtDagService", "—"),
    ("*数据采集与清洗转换加工", 6, "数据加工", "支持以图形化方式进行数据加工处理，支持数据加工任务定义与执行，提供输入、输出、查询、 转换、 去重、 合并、 拆分、过滤、连接、 统计、 排序、批量加载、脚本操作等数据处理组件。",
     "🔴 缺口", "✅ 已覆盖",
     "etl/DbtDagService + explore/etl/TransformCreatePage.tsx(71KB) + ModelGenerationService", "—"),
    ("*数据采集与清洗转换加工", 7, "跨库数据融合", "支持多数据库之间跨库数据融合加工。",
     "🟡 部分", "✅ 已覆盖",
     "sql/SqlCatalogService + SqlExecutionService + SqlConnection", "—"),
    ("*数据采集与清洗转换加工", 8, "数据清洗规则", "支持根据数据规则匹配对应的模型进行数据清洗。",
     "🟡 部分", "✅ 已覆盖",
     "governance/DataCleansingService + GovCleansingFunction + GovRule/GovRuleVersion/GovRuleBinding", "—"),

    ("*数据存储", 1, "数据仓库", "支持数仓分层规划， 数仓可划分为ODS贴源层、DWD明细层、DWS汇总层、ADS应用层或其他自定义层级。 数仓各层可按数据主题规划。",
     "✅ 已覆盖", "✅ 已覆盖",
     "catalog/CatalogDataset + etl/OdsTableMappingSyncService + MetadataPage.tsx", "—"),
    ("*数据存储", 2, "数据模型", "提供灵活的数据模型定义和管理功能，支待模型导入及模型关联关系维护。",
     "✅ 已覆盖", "✅ 已覆盖",
     "modeling/ModelingSqlModel + ModelingSqlModelService(54KB) + ModelingAssetReferenceService", "F-DS-01"),
    ("*数据存储", 3, "数据存储", "支持结构化、 半结构化和非结构化数据的存储。",
     "🟡 部分", "✅ 已覆盖",
     "catalog/CatalogTableSchema + CatalogColumnSchema + CatalogColumnSyncService", "—"),

    ("数据管理", 1, "数据标准管理（生命周期）", "支持数据标准的全生命周期管理。",
     "🟡 部分", "✅ 已覆盖",
     "modeling/DataStandardService + DataStandardVersion + governance/DimensionService", "—"),
    ("数据管理", 2, "数据标准管理（多维属性）", "支持数据标准多维度的属性定义。",
     "✅ 已覆盖", "✅ 已覆盖",
     "governance/DimensionService + ReferenceCodeService", "—"),
    ("数据管理", 3, "数据标准管理（导入）", "支持手动、EXCEL导入等多种方式在系统中建立数据标准。",
     "🟡 部分", "✅ 已覆盖",
     "modeling/DataStandardImportService + infra/ExcelImportService", "—"),
    ("数据管理", 4, "数据资产目录", "支持数据资产目录清单自动生成，支持数据资产管理维护和查询查看。",
     "✅ 已覆盖", "✅ 已覆盖",
     "infra/CatalogAutoSyncJob + JdbcCatalogSyncService + PostgresCatalogSyncService", "—"),
    ("数据管理", 5, "数据资产维护", "支持数据资产管理维护和查询查看。",
     "🟡 部分", "✅ 已覆盖",
     "catalog/CatalogDataset + AssetResource + catalog/AssetDetailPage.tsx", "—"),
    ("数据管理", 6, "数据标签管理", "支待数据标签及标签目录管理，预置常用的数据标签，并可根据业务需要自定义数据标签，支持对数据资源打标签。",
     "🔴 缺口", "🔴 缺口",
     "未找到 DataTag/DataLabel domain", "F-DM-02"),
    ("数据管理", 7, "元数据管理", "支持元模型定义和管理，支待元数据采集、审批、发布、维护和查询。",
     "🟡 部分", "🟡 部分",
     "catalog/CatalogMetadataService + openmetadata/OpenMetadataService + MetadataPage.tsx（审批缺）", "F-DM-03"),
    ("数据管理", 8, "元数据管理（血缘）", "支持数据血缘分析、影响分析，可实现血缘关系图形化展示。",
     "✅ 已覆盖", "✅ 已覆盖",
     "catalog/CatalogAutoLineageService + CatalogDbtLineageService + LineagePage.tsx", "—"),
    ("数据管理", 9, "数据全生命周期管理", "支持数据在创建、存储、使用、共享、归档、销毁等各阶段的审批管理流程经审批通过后方可执行相应操作。",
     "🔴 缺口", "🟡 部分",
     "catalog/CatalogLifecycleRequest + CatalogLifecycleRequestService + CatalogDatasetAccessApprovalResource", "F-DM-04"),
    ("数据管理", 10, "数据监控", "提供监控界面查看、统计在用、共享、销毁、归档的数据量。",
     "🔴 缺口", "🟡 部分",
     "governance/GovernanceOpsMetricsService + QualityDashboard（缺资产生命周期专项）", "F-DM-05"),
    ("数据管理", 11, "数据销毁", "包括临时销毁和永久销毁，临时销毁的数据可一键还原。",
     "🔴 缺口", "🟡 部分",
     "catalog/CatalogLifecycleRequest.requestType（销毁/还原逻辑待补）", "F-DM-06"),

    ("*数据安全", 1, "数据访问权限管理（角色）", "支持根据角色设置数据访问和操作权限。",
     "✅ 已覆盖", "✅ 已覆盖",
     "admin/AdminCustomRole + AdminRoleAssignment + platform/AuthoritiesConstants", "—"),
    ("*数据安全", 2, "数据访问权限管理（行列）", "支持基于数据资产目录、数据表、数据行和列设置数据访问权限。",
     "🔴 缺口", "✅ 已覆盖",
     "iam/IamDatasetPolicy.rowExpression + catalog/CatalogMaskingRule + ExploreResource.isRowLevelAllowed", "—"),
    ("*数据安全", 3, "数据访问权限管理（操作）", "操作权限包括新增、删除、修改、复制、导入导出、归档、销毁等。",
     "🟡 部分", "🟡 部分",
     "CatalogLifecycleRequestResource + CatalogDatasetAccessApprovalResource（缺粒度矩阵）", "F-SEC-OP"),
    ("*数据安全", 4, "数据脱敏管理（分级）", "支持对敏感数据进行分类分级管理。",
     "🔴 缺口", "✅ 已覆盖",
     "security/policy/DataLevel + iam/IamClassification + IamUserClassification", "—"),
    ("*数据安全", 5, "数据脱敏管理（识别）", "支待敏感数据识别规则定义并能自动识别敏感数据，支持敏感数据监控查询。",
     "🔴 缺口", "🟡 部分",
     "CatalogMaskingRuleRepository（手工标注，无自动 scan 引擎）", "F-SEC-04"),
    ("*数据安全", 6, "数据脱敏管理（规则任务）", "支持脱敏规则自定义和数据脱敏任务设置，并能根据脱敏规则或数据脱敏任务执行数据脱敏操作和查看执行结果。",
     "🔴 缺口", "✅ 已覆盖",
     "catalog/CatalogMaskingRule.function+args + CatalogMaskingResource + data-security.tsx", "—"),
    ("*数据安全", 7, "数据备份", "支持数据备份恢复等功能，能对应用系统本身及数据库进行备份与恢复，防止数据泄露和丢失。 可手动备份和定 时备份， 支待定期自动全量备份和增量备份。",
     "🔴 缺口", "✅ 已覆盖",
     "security/SecurityBackupPlan + SecurityBackupRun + SecurityBackupRecoveryResource", "—"),

    ("*数据质量", 1, "数据质量规则（定义）", "支待数据质量规则定义、发布和维护， 内置常用质量规则， 如非空、 唯一、 正则、 阙值等。",
     "🔴 缺口", "✅ 已覆盖",
     "governance/QualityRuleService + GovRule + GovRuleVersion + QualityRulesPage.tsx", "—"),
    ("*数据质量", 2, "数据质量规则（联动标准）", "支待根据数据标准快速建立数据质量规则， 数据标准变更时， 数据质量规则自动同步。",
     "🔴 缺口", "✅ 已覆盖",
     "modeling/DataStandardService + DataStandardVersion + QualityRuleService", "—"),
    ("*数据质量", 3, "数据质量检查、监控和预警", "支持以可视化方式进行数据质量检查任务定义和执行，支待数据质量检查任务监控和预警。",
     "🔴 缺口", "✅ 已覆盖",
     "governance/QualityTaskService + GovQualityTask + GovQualityRun + QualityRulesPage.tsx(27KB)", "—"),
    ("*数据质量", 4, "数据质量检查分析报告", "支持数据质量检查与分析、检查分析结果查询和检查分析报告生成。",
     "🔴 缺口", "✅ 已覆盖",
     "governance/QualityReportExportService + QualityDashboardService + QualityReportPage.tsx", "—"),
    ("*数据质量", 5, "数据质量问题处理", "支待向数据责任人下发数据质量问题处理任务，并记录任务执行结果。",
     "🔴 缺口", "✅ 已覆盖",
     "governance/IssueTicketService + GovIssueTicket + GovIssueAction + GovernanceCenterPage.tsx", "—"),

    ("*数据服务管理", 1, "数据查询", "支持面向不同场景需求的数据查询服务，可对数据资产进行统一查询查看， 支持数据及时查询。",
     "🟡 部分", "✅ 已覆盖",
     "services/SvcApiQueryService + SvcApi + ApiServicesPage.tsx", "—"),
    ("*数据服务管理", 2, "数据交换（API）", "可通过界面基于数据和数据资产目录进行数据API接口自定义， 便于集成、应用开发和供外部应用调用。",
     "🔴 缺口", "✅ 已覆盖",
     "ApiServicesResource + services/ApiCatalogService + SvcDataProduct", "—"),
    ("*数据服务管理", 3, "数据查询（内置接口）", "内置接口支持元数据查询、 数据查询、数据更新、 数据字典查询。",
     "✅ 已覆盖", "✅ 已覆盖",
     "BasicApiResource + governance/ReferenceCodeService + catalog/CatalogMetadataService", "—"),
    ("*数据服务管理", 4, "数据交换（格式）", "支持如JSON、XML、CSV等通用标准数据交换格式， 确保数据交换的通用性等。",
     "🟡 部分", "✅ 已覆盖",
     "infra/ExcelImportService + sql/SqlResultStreamServiceImpl + ResultSetResource", "—"),

    ("*数据分析与可视化", 1, "指标管理与维护", "支持指标管理与维护， 可自定义各类指标和指标属性。",
     "🟡 部分", "✅ 已覆盖",
     "governance/GovIndicatorDefinition + IndicatorService + IndicatorCenterPage.tsx", "—"),
    ("*数据分析与可视化", 2, "可视化组件", "提供丰富的可视化组件， 包括但不限于饼图、柱图、折线图、地图等常规可视化组件，内置多种主题配色方案， 帮助用户简单、快速、生动的实现数据可视化展示，满足精细化业务需求。",
     "✅ 已覆盖", "✅ 已覆盖",
     "analytics/screens/renderers/echarts/（35+ 图表）+ 多主题系统", "—"),
    ("*数据分析与可视化", 3, "数据可视化（拖拽/编辑）", "支待复杂的数据分析场景和直观的数据展示，支持可视化的模板设置，提供可视化编辑面板，支持拖拽布局、交互式数据可视化。",
     "✅ 已覆盖", "✅ 已覆盖",
     "screens/ScreenDesignerPage + DesignerCanvas + PropertyPanel", "—"),
    ("*数据分析与可视化", 4, "数据可视化（风格）", "通过简单的拖拽方式实现看板的布局，支持对看板显示风格和样式进行自定义设置。",
     "✅ 已覆盖", "✅ 已覆盖",
     "screens/themes + screenCssVariables + configSchema", "—"),
    ("*数据分析与可视化", 5, "节点编程", "支持事件包括高亮事件、选中事件等、支待的动作包括钻取、联动和跳转等，支待的逻辑包括 触发器、转换器、定时器等。 可通过配置实现页面弹层、组件 显示／隐藏、页面切换等交互内容。",
     "🟡 部分", "🟡 部分",
     "screens/interaction（钻取/联动/跳转）；触发器/转换器/定时器 待补", "F-BI-02"),
    ("*数据分析与可视化", 6, "可视化素材与模板管理", "供数据可视化素材与模板资源管理，数据可视化设计者可直接选择资源库中的模板， 一键复制， 再结合实际业务需要对其 进行修改、调整、配置， 形成个性化看板。",
     "✅ 已覆盖", "✅ 已覆盖",
     "screens/screenTemplates.ts + componentLibrary.ts + 23 个行业模板", "—"),
    ("*数据分析与可视化", 7, "终端适配", "数据可视化支待对多终端可视化展示，支持多种屏幕自适应方式， 包括大屏、PC、电视等展示设备。",
     "🟡 部分", "🟡 部分",
     "sprint-12-202604 BI 大屏响应式改造（C 方案）规划中", "F-BI-ADAPT"),
    ("*数据分析与可视化", 8, "数据报表", "支持多种报表格式， 包括EXCEL、PDF、 WORD等。 允许用户自行创建各类报表、仪表盘、驾驶舱，满足用户的不同需求。",
     "🔴 缺口", "🟡 部分",
     "governance/QualityReportExportService（Excel/PDF 已有；Word 待补）", "F-BI-03"),

    ("*监控和告警", 1, "系统监控和告警", "提供全面的系统监控和告警功能， 包括性能监控、错误日志、资源使用情况、数据采集任务执行情况等，支持自定义告警规则， 确保系统的稳定运行。",
     "🟡 部分", "🟡 部分",
     "micrometer-registry-prometheus + application.yml actuator；缺 Grafana dashboard + Alertmanager rules", "F-OPS-01"),
    ("*监控和告警", 2, "功能及接口日志", "对功能运行及接口调用等情况进行日志记录， 日志要素齐全且可读性强， 包括如时间、 源 IP、目标 IP、操作、 异常原因、 受影响的功能等。",
     "🟡 部分", "✅ 已覆盖",
     "admin/audit/AuditEntry + AdminAuditService + AuditLogResource + PermissionAuditPage.tsx", "—"),

    ("*安全保密功能", 1, "安全保密", "参照JM级应用系统的安全保密要求进行安全保密功能设计及开发。且乙方所提供的应用系统能通过甲方指定机构开展的离线在线测评及安全检测，对于指定机构提出的整改项乙方须进行整改直到通过测评和检测。",
     "🔴 缺口", "🟡 部分",
     "platform/security/SecurityUtils + DataLevelSqlHelper + TLS 配置 + sprint-9 Cookie 安全；合规文档/强密码策略待完善", "F-SEC-10"),
    ("*安全保密功能", 2, "USBKey认证", "支持与甲方PKI/CA系统兼容，集成基于USBKey的用户认证方式进行登录管理。",
     "🔴 缺口", "✅ 已覆盖",
     "admin/pki/PkiVerificationService(PKCS#7+X509) + PkiChallengeService + SecurityPkiResource + SecurityPkiBinding", "—"),
]

# 3) 详细任务（v2.0 —— 缩减至真正缺口，估算下调）
feature_rows = [
    # ID, 模块, 子项, 功能名, 现状, 落地包, 估算(人·日), Sprint, 优先级, 状态
    # ---- 数据规划 ----
    ("F-DP-02", "数据规划", "1-2/1-3/1-4", "数据文件全生命周期 + 多格式",
     "InfraExternalExchangeFile / DataStandardAttachment 已有；DOC/DOCX/PDF 预览 & 版本管理薄",
     "在现有附件体系上补：文件分组、版本、DOC/DOCX/PDF 在线预览、附件审批接入",
     5, "C", "P2", "待启动"),
    ("F-DP-03", "数据规划", "1-3", "文件审批流模板",
     "AdminWorkflowConfigClient 可用，无专门文件流模板",
     "预置 3 套文件流转模板（标准附件 / 共享文件 / 销毁申请），对接审批中心",
     2, "F", "P2", "待启动"),
    ("F-DP-04", "数据规划", "1-5", "数据管理流程模板预置",
     "WorkflowCenterPage 在位，流程模板缺预置",
     "预置 5 套流程：标准发布 / 资产上下架 / 销毁申请 / 权限申请 / 数据共享",
     3, "B", "P1", "待启动"),

    # ---- 数据采集 ----
    ("F-ETL-01", "采集清洗", "2-1", "多源采集补 MQ / Webhook",
     "RDB / 文件 / Restful 已覆盖，缺消息队列",
     "Kafka / RocketMQ 连接器 + Restful Webhook 入口到 IngestionTask",
     5, "D", "P1", "待启动"),
    ("F-ETL-02", "采集清洗", "2-4", "数据填报表单设计器",
     "ExcelImport + OdsDataEditor 偏编辑，缺表单设计 / 任务下发 / 汇总查询",
     "表单控件库（文本/数字/日期/下拉/文件） + 表单版本 + 下发 + 汇总统计 + 导入导出",
     8, "C", "P1", "待启动"),

    # ---- 数据存储 ----
    ("F-DS-01", "数据存储", "3-2", "ER 关系图可视化",
     "ModelingSqlModel 关系已建，缺前端图形展示",
     "基于现有 asset reference，加 ER 图组件（参考 dagre / antv）",
     3, "F", "P2", "待启动"),

    # ---- 数据管理 ----
    ("F-DM-02", "数据管理", "4-6", "数据标签管理（真缺口）",
     "无 DataTag / DataLabel domain",
     "标签目录 + 预置标签 + 自定义标签 + 资源批量打标 + 按标签筛选资产",
     5, "B", "P1", "待启动"),
    ("F-DM-03", "数据管理", "4-7", "元数据变更审批",
     "采集 / 展示 / 血缘已有，缺审批链路",
     "元模型 / 字段变更路由到审批中心，通过后再生效",
     4, "B", "P2", "待启动"),
    ("F-DM-04", "数据管理", "4-9", "全生命周期 6 阶段审批完整闭环",
     "CatalogLifecycleRequest + AccessApproval 雏形在",
     "补齐 创建/存储/使用/共享/归档/销毁 6 阶段的状态流、审批人路由、操作执行钩子",
     6, "B", "P1", "待启动"),
    ("F-DM-05", "数据管理", "4-10", "数据资产专项监控仪表盘",
     "GovernanceOpsMetrics 覆盖质量 / 指标，缺资产生命周期视角",
     "仪表盘：在用/共享/归档/销毁数量 + 趋势 + Top 责任人 + 操作热度",
     3, "F", "P2", "待启动"),
    ("F-DM-06", "数据管理", "4-11", "销毁沙箱（临时/永久 + 还原）",
     "LifecycleRequest.requestType 支持，缺实际 trash 与还原 / 硬删",
     "临时销毁 → trash 30 天保留；永久销毁审批 → 硬删 + 审计；一键还原 API",
     5, "C", "P1", "待启动"),

    # ---- 数据安全 ----
    ("F-SEC-OP", "数据安全", "5-3", "操作权限矩阵",
     "角色 / 行列 / 分级 已有，细粒度操作（复制 / 导入导出）矩阵未集中配置",
     "操作权限矩阵（资产×角色×动作） + 前端编辑器 + 拦截点接入",
     5, "B", "P1", "待启动"),
    ("F-SEC-04", "数据安全", "5-5", "敏感数据自动识别引擎",
     "手工标注 masking 支持，缺自动 scan",
     "规则引擎（正则 + 字典 + AI 推断） → 扫描资产列 → 推荐分级 → 人工确认",
     6, "C", "P1", "待启动"),
    ("F-SEC-10", "安全保密", "10-1", "JM 合规整改 + 测评对接",
     "有基础 TLS / 密级 / Cookie / SQL 防护",
     "合规文档 + 强密码策略 + 错误屏蔽 + 密钥管理 + 会话控制整改；对接甲方测评机构两轮迭代",
     20, "A", "P0", "待启动"),

    # ---- 数据质量 —— 零新增，已全覆盖 ----

    # ---- 数据服务 —— 零新增 ----

    # ---- 可视化 ----
    ("F-BI-02", "可视化", "8-5", "节点编程：触发器/转换器/定时器",
     "钻取 / 联动 / 跳转已有",
     "新增 触发器（定时 / 变量变更）、转换器（map/filter/reduce）、定时器；页面弹层 / 显隐 / 切换标准化",
     6, "F", "P2", "待启动"),
    ("F-BI-ADAPT", "可视化", "8-7", "多终端适配 C 方案落地",
     "Sprint-12 C 方案规划已在",
     "执行 Sprint-12 规划：断点 / 缩放 / 优雅降级",
     4, "F", "P2", "待启动"),
    ("F-BI-03", "可视化", "8-8", "报表 Word 导出补齐",
     "Excel / PDF 已有（QualityReportExportService）",
     "补 Apache POI-Word（docx4j 或 poi-ooxml-word） 导出；报表设计器快捷模板",
     5, "E", "P1", "待启动"),

    # ---- 监控 ----
    ("F-OPS-01", "监控", "9-1", "Grafana 仪表盘 + Alertmanager 规则",
     "Prometheus metrics 已暴露，缺可视化 + 告警规则",
     "标配 JVM/PG/Airflow/采集任务仪表盘 + 告警规则；自定义规则管理入口",
     5, "E", "P1", "待启动"),
]

# 4) Sprint 计划（v2.0 缩减到 7 周 + 1 周复审）
sprint_rows = [
    ("Sprint-A", "2 周", "JM 合规 & 测评准备",
     "F-SEC-10",
     "合规文档集 + 强密码 / 错误屏蔽 / 密钥管理整改；准备离线在线测评材料"),
    ("Sprint-B", "2 周", "数据管理补齐 + 操作权限",
     "F-DP-04, F-DM-02, F-DM-03, F-DM-04, F-SEC-OP",
     "流程模板；标签体系；元数据审批；生命周期 6 阶段闭环；操作矩阵"),
    ("Sprint-C", "2 周", "敏感识别 + 销毁沙箱 + 文件管理",
     "F-SEC-04, F-DM-06, F-ETL-02, F-DP-02",
     "敏感自动识别；trash / 还原；填报表单设计器；附件版本预览"),
    ("Sprint-D", "1 周", "采集补齐",
     "F-ETL-01",
     "Kafka / Webhook 采集源"),
    ("Sprint-E", "1 周", "报表 + 监控聚合",
     "F-BI-03, F-OPS-01",
     "Word 导出；Grafana + Alertmanager"),
    ("Sprint-F", "1 周", "可视化 + 存储扫尾",
     "F-BI-02, F-BI-ADAPT, F-DS-01, F-DM-05, F-DP-03",
     "节点编程三件套；多终端；ER 图；资产仪表盘；文件流模板"),
    ("复审窗口", "1 周", "测评整改第二轮 + 回归",
     "—",
     "通过甲方指定机构测评；性能 / 渗透回归"),
]

# 5) 风险
risk_rows = [
    ("JM 测评多轮整改", "🔴 高", "交付时间受测评机构节奏牵引",
     "Sprint-A 启动 + 留 1 周复审 buffer；提前拿机构 checklist",
     "安全 / PM"),
    ("PKI/USBKey 跨浏览器兼容", "🟡 中", "部分终端登录异常",
     "已有 PkiVerificationService 底座，主要是驱动/插件白名单",
     "安全 / 前端"),
    ("填报设计器工作量超预期", "🟡 中", "Sprint-C 溢出",
     "控件库先 MVP 6 个，表单结构沿用 JSON Schema 减少后端改动",
     "前端 / 后端"),
    ("敏感识别准确率", "🟡 中", "误标 / 漏标",
     "预置金融/政务/科研 3 套分级 + 保留人工确认环节",
     "业务 / 数据"),
    ("审批流与现有 AdminWorkflow 解耦程度", "🟡 中", "多处依赖改动易踩坑",
     "Sprint-B 先做接口契约评审，再铺代码",
     "后端"),
]

# 6) v1 → v2 修订说明（新增 sheet）
delta_rows = [
    # 协议子项, v1 评级, v2 评级, 变化, 原因
    ("1-1 数据主题多层级", "🟡", "✅", "↑", "CatalogDomain + CatalogDomainResource 已在位"),
    ("1-3 数据文件管理", "🔴", "🟡", "↑", "InfraExternalExchangeFile + DataStandardAttachment 已有"),
    ("2-1 多数据源", "🟡", "🟡", "—", "RDB/API/文件 ✅；MQ 仍缺（F-ETL-01）"),
    ("2-6 图形化数据加工", "🔴", "✅", "↑↑", "TransformCreatePage.tsx(71KB) + DbtDagService 画布 ETL 已落地"),
    ("2-7 跨库数据融合", "🟡", "✅", "↑", "SqlCatalog / SqlExecution / SqlConnection 已完成联邦"),
    ("2-8 数据清洗规则", "🟡", "✅", "↑", "DataCleansingService + GovCleansingFunction + GovRule 三位一体"),
    ("3-3 结构化/半/非结构化", "🟡", "✅", "↑", "TableSchema / ColumnSchema 支持到 JSON 字段"),
    ("4-1/2/3 数据标准", "🟡", "✅", "↑", "DataStandardService + Version + ImportService 齐"),
    ("4-5 数据资产维护", "🟡", "✅", "↑", "AssetResource + AssetDetailPage 已在位"),
    ("4-9 全生命周期审批", "🔴", "🟡", "↑", "CatalogLifecycleRequest 雏形在，待补 6 阶段闭环"),
    ("4-10 数据监控", "🔴", "🟡", "↑", "GovernanceOpsMetrics 覆盖质量指标，仅缺资产视角"),
    ("4-11 数据销毁", "🔴", "🟡", "↑", "LifecycleRequest.requestType 支持，待补 trash/还原"),
    ("5-2 行列权限", "🔴", "✅", "↑↑↑", "IamDatasetPolicy.rowExpression + CatalogMaskingRule 均已上线"),
    ("5-4 敏感分类分级", "🔴", "✅", "↑↑↑", "security/policy/DataLevel 四级 + IamClassification 完整"),
    ("5-5 敏感识别", "🔴", "🟡", "↑", "手工标注已有；自动扫描规则引擎待补（F-SEC-04）"),
    ("5-6 脱敏规则任务", "🔴", "✅", "↑↑↑", "CatalogMaskingRule + CatalogMaskingResource + masked 视图"),
    ("5-7 数据备份", "🔴", "✅", "↑↑↑", "SecurityBackupPlan + SecurityBackupRun + Recovery 已落地"),
    ("6-1 DQ 规则", "🔴", "✅", "↑↑↑", "QualityRuleService + GovRule/Version 完整"),
    ("6-2 标准联动", "🔴", "✅", "↑↑↑", "DataStandardVersion + QualityRuleService 事件联动"),
    ("6-3 DQ 任务", "🔴", "✅", "↑↑↑", "QualityTaskService + GovQualityTask/Run"),
    ("6-4 DQ 报告", "🔴", "✅", "↑↑↑", "QualityReportExportService + QualityDashboardService"),
    ("6-5 DQ 问题处理", "🔴", "✅", "↑↑↑", "IssueTicketService + GovIssueTicket/Action 闭环"),
    ("7-1 统一查询", "🟡", "✅", "↑", "SvcApiQueryService + ApiServicesPage"),
    ("7-2 API 自定义", "🔴", "✅", "↑↑↑", "ApiServicesResource + ApiCatalogService + SvcDataProduct"),
    ("7-4 交换格式", "🟡", "✅", "↑", "ExcelImport + SqlResultStream + ResultSet 已覆盖"),
    ("8-1 指标管理", "🟡", "✅", "↑", "GovIndicatorDefinition + IndicatorService + IndicatorCenterPage"),
    ("8-8 报表多格式", "🔴", "🟡", "↑", "Excel/PDF 已有；仅 Word 待补"),
    ("9-2 接口日志", "🟡", "✅", "↑", "AuditEntry + AdminAuditService + AuditLogResource 完整"),
    ("10-2 USBKey/PKI", "🔴", "✅", "↑↑↑", "PkiVerificationService(PKCS#7+X509) + PkiChallenge + SecurityPkiBinding"),
]


# =========================================================
# 写入
# =========================================================
wb = Workbook()

# ---------- Sheet 0: 说明 ----------
ws0 = wb.active
ws0.title = "说明"
write_title(ws0, "DTS 平台 · 客户技术协议 Gap 分析与落地计划 · v2.0", 2)
notes = [
    ("版本", "v2.0（2026-04-21）· 基于三路并行 agent 粗颗粒度 review 代码后修订"),
    ("文档目的", "对照客户技术协议 10 大模块 × 49 条子项，输出可落地的完善计划，含 Sprint 安排、风险与依赖。"),
    ("v1 → v2 核心变化", "实际覆盖度 >> 不看代码的推断。数据质量 / 数据服务 / 行列权限 / 脱敏 / 备份 / PKI-USBKey 均已落地；真正缺口集中在 数据标签 / 自动敏感识别 / 填报设计器 / JM 合规文档 / 监控可视化 / Word 导出 等。"),
    ("工作表", "说明 / 总览 / 协议对照 / 详细任务 / Sprint计划 / 风险与依赖 / v1-v2 修订"),
    ("覆盖度图例", "✅ 已覆盖 · 🟡 部分覆盖 · 🔴 缺口"),
    ("优先级图例", "P0 合规硬约束；P1 业务价值高且落地明确；P2 可延后"),
    ("估算单位", "人·日（以 1 人 8 小时计）"),
    ("总工期", "v1 约 17 周 → v2 约 9 周（含 1 周复审），因为大量已完成功能被剔除出工作清单"),
    ("编制推荐", "3 后端 + 2 前端 + 1 安全/DevOps"),
    ("更新方式", "修改 tools/gen_gap_plan_xlsx.py 数据列表重跑即可"),
]
ws0.append([])
ws0.append([])
for k, v in notes:
    ws0.append([k, v])
for r in range(3, 3 + len(notes)):
    ws0.cell(row=r, column=1).font = Font(name="微软雅黑", size=10, bold=True)
    ws0.cell(row=r, column=1).alignment = WRAP
    ws0.cell(row=r, column=1).border = BORDER
    ws0.cell(row=r, column=2).font = CELL_FONT
    ws0.cell(row=r, column=2).alignment = WRAP
    ws0.cell(row=r, column=2).border = BORDER
set_col_widths(ws0, [18, 100])

# ---------- Sheet 1: 总览 ----------
ws1 = wb.create_sheet("总览")
cols = 7
write_title(ws1, "模块覆盖度总览（v2.0 基于代码）", cols)
headers = ["模块", "协议子项数", "已覆盖", "部分", "缺口", "评级", "总体评估"]
for i, h in enumerate(headers, 1):
    ws1.cell(row=2, column=i, value=h)
style_header(ws1, 2, cols)
for i, row in enumerate(overview_rows, start=3):
    for j, v in enumerate(row, start=1):
        ws1.cell(row=i, column=j, value=v)
    rating = row[5]
    fill = COVER_FILL_FULL if rating == "✅" else (COVER_FILL_PART if rating == "🟡" else COVER_FILL_GAP)
    ws1.cell(row=i, column=6).fill = fill
apply_cells(ws1, 3, 2 + len(overview_rows), cols)
set_col_widths(ws1, [24, 10, 10, 10, 10, 8, 80])

# ---------- Sheet 2: 协议对照 ----------
ws2 = wb.create_sheet("协议对照")
cols = 8
write_title(ws2, "客户技术协议 × 平台覆盖度对照（v2.0 真实代码证据）", cols)
headers = ["模块", "子项", "子功能", "功能描述", "v1 评级", "v2 评级", "代码证据（路径/类名）", "Feature"]
for i, h in enumerate(headers, 1):
    ws2.cell(row=2, column=i, value=h)
style_header(ws2, 2, cols)
for i, row in enumerate(protocol_rows, start=3):
    for j, v in enumerate(row, start=1):
        ws2.cell(row=i, column=j, value=v)
    # v1 colour
    v1 = row[4]
    ws2.cell(row=i, column=5).fill = COVER_FILL_FULL if v1.startswith("✅") else (COVER_FILL_PART if v1.startswith("🟡") else COVER_FILL_GAP)
    # v2 colour
    v2 = row[5]
    ws2.cell(row=i, column=6).fill = COVER_FILL_FULL if v2.startswith("✅") else (COVER_FILL_PART if v2.startswith("🟡") else COVER_FILL_GAP)
apply_cells(ws2, 3, 2 + len(protocol_rows), cols)
ws2.freeze_panes = "A3"
ws2.auto_filter.ref = f"A2:{get_column_letter(cols)}{2 + len(protocol_rows)}"
set_col_widths(ws2, [22, 8, 26, 58, 10, 10, 64, 12])

# ---------- Sheet 3: 详细任务 ----------
ws3 = wb.create_sheet("详细任务")
cols = 10
write_title(ws3, "落地任务清单 F-xx（v2.0 缩减至真实缺口）", cols)
headers = ["Feature ID", "模块", "协议子项", "功能名", "现状（代码侧）", "落地包", "估算(人·日)", "Sprint", "优先级", "状态"]
for i, h in enumerate(headers, 1):
    ws3.cell(row=2, column=i, value=h)
style_header(ws3, 2, cols)
total_days = 0
for i, row in enumerate(feature_rows, start=3):
    for j, v in enumerate(row, start=1):
        ws3.cell(row=i, column=j, value=v)
    pri = row[8]
    if pri == "P0":
        ws3.cell(row=i, column=9).fill = PRIORITY_P0
    elif pri == "P1":
        ws3.cell(row=i, column=9).fill = PRIORITY_P1
    else:
        ws3.cell(row=i, column=9).fill = PRIORITY_P2
    total_days += row[6]
total_row = 2 + len(feature_rows) + 1
ws3.cell(row=total_row, column=1, value="合计")
ws3.cell(row=total_row, column=1).font = Font(name="微软雅黑", size=10, bold=True)
ws3.cell(row=total_row, column=7, value=total_days)
ws3.cell(row=total_row, column=7).font = Font(name="微软雅黑", size=10, bold=True)
ws3.cell(row=total_row, column=7).fill = GROUP_FILL
apply_cells(ws3, 3, total_row, cols)
ws3.freeze_panes = "A3"
ws3.auto_filter.ref = f"A2:{get_column_letter(cols)}{total_row - 1}"
set_col_widths(ws3, [12, 14, 12, 28, 50, 58, 12, 10, 10, 12])

# ---------- Sheet 4: Sprint 计划 ----------
ws4 = wb.create_sheet("Sprint计划")
cols = 5
write_title(ws4, "Sprint 落地路径（v2.0 · 9 周）", cols)
headers = ["Sprint", "时长", "目标", "纳入特性", "交付物"]
for i, h in enumerate(headers, 1):
    ws4.cell(row=2, column=i, value=h)
style_header(ws4, 2, cols)
for i, row in enumerate(sprint_rows, start=3):
    for j, v in enumerate(row, start=1):
        ws4.cell(row=i, column=j, value=v)
apply_cells(ws4, 3, 2 + len(sprint_rows), cols)
set_col_widths(ws4, [12, 10, 24, 48, 70])

# ---------- Sheet 5: 风险 ----------
ws5 = wb.create_sheet("风险与依赖")
cols = 5
write_title(ws5, "风险与依赖", cols)
headers = ["风险", "等级", "影响", "缓解", "负责方"]
for i, h in enumerate(headers, 1):
    ws5.cell(row=2, column=i, value=h)
style_header(ws5, 2, cols)
for i, row in enumerate(risk_rows, start=3):
    for j, v in enumerate(row, start=1):
        ws5.cell(row=i, column=j, value=v)
    lvl = row[1]
    ws5.cell(row=i, column=2).fill = PRIORITY_P0 if lvl.startswith("🔴") else PRIORITY_P1
apply_cells(ws5, 3, 2 + len(risk_rows), cols)
set_col_widths(ws5, [32, 10, 40, 56, 16])

# ---------- Sheet 6: v1 → v2 修订说明 ----------
ws6 = wb.create_sheet("v1-v2修订")
cols = 5
write_title(ws6, "v1（推断）→ v2（代码 review）评级修订", cols)
headers = ["协议子项", "v1 评级", "v2 评级", "变化", "原因"]
for i, h in enumerate(headers, 1):
    ws6.cell(row=2, column=i, value=h)
style_header(ws6, 2, cols)
for i, row in enumerate(delta_rows, start=3):
    for j, v in enumerate(row, start=1):
        ws6.cell(row=i, column=j, value=v)
    # 颜色
    v1 = row[1]
    v2 = row[2]
    ws6.cell(row=i, column=2).fill = COVER_FILL_FULL if v1.startswith("✅") else (COVER_FILL_PART if v1.startswith("🟡") else COVER_FILL_GAP)
    ws6.cell(row=i, column=3).fill = COVER_FILL_FULL if v2.startswith("✅") else (COVER_FILL_PART if v2.startswith("🟡") else COVER_FILL_GAP)
    change = row[3]
    if "↑" in change:
        ws6.cell(row=i, column=4).fill = DELTA_UP
    elif "↓" in change:
        ws6.cell(row=i, column=4).fill = DELTA_DOWN
apply_cells(ws6, 3, 2 + len(delta_rows), cols)
set_col_widths(ws6, [32, 10, 10, 8, 70])

# 保存
out_path = Path("worklog/v2.2.3/protocol-gap-plan.xlsx")
out_path.parent.mkdir(parents=True, exist_ok=True)
wb.save(out_path)
print(f"Saved: {out_path.resolve()}  ({out_path.stat().st_size} bytes)")
print(f"Features count: {len(feature_rows)}  Total days: {total_days}")
print(f"Sprints: {len(sprint_rows)}")
print(f"Delta entries: {len(delta_rows)}")
