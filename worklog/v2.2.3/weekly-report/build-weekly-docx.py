# -*- coding: utf-8 -*-
"""以 weekly-2026-08-14.docx 为模板，生成同版式的本周周报 docx。"""
import re, shutil, zipfile, os
from xml.sax.saxutils import escape

REF = "/opt/prod/s10/v2.2.3/worklog/v2.2.3/weekly-report/weekly-2026-08-14.docx"
OUT = "/opt/prod/s10/v2.2.3/worklog/v2.2.3/weekly-report/weekly-2026-08-21.docx"
TITLE = "DTS v2.2.3 周报（2026年8月17日—8月21日）"

def t(s):
    return f'<w:r><w:rPr /><w:t xml:space="preserve">{escape(s)}</w:t></w:r>'

def h1(s):
    return ('<w:p><w:pPr><w:pStyle w:val="Heading1" /><w:bidi w:val="0" />'
            '<w:spacing w:before="360" w:after="160" /><w:rPr /></w:pPr>' + t(s) + '</w:p>')

def subtitle(s):
    return ('<w:p><w:pPr><w:pStyle w:val="BodyTextsubtitle" /><w:bidi w:val="0" />'
            '<w:rPr /></w:pPr>' + t(s) + '</w:p>')

def h2(s):
    return ('<w:p><w:pPr><w:pStyle w:val="Heading2" /><w:keepLines w:val="false" />'
            '<w:bidi w:val="0" /><w:ind w:hanging="0" w:start="0" w:end="0" />'
            '<w:jc w:val="start" /><w:rPr /></w:pPr>' + t(s) + '</w:p>')

def h3(s):
    return ('<w:p><w:pPr><w:pStyle w:val="Heading3" /><w:keepLines w:val="false" />'
            '<w:bidi w:val="0" /><w:jc w:val="start" /><w:rPr /></w:pPr>' + t(s) + '</w:p>')

def bullet(s, last=False):
    sp = '' if last else '<w:spacing w:before="100" w:after="0" />'
    return ('<w:p><w:pPr><w:pStyle w:val="BodyText" /><w:keepLines w:val="false" />'
            '<w:numPr><w:ilvl w:val="0" /><w:numId w:val="1" /></w:numPr>'
            '<w:tabs><w:tab w:val="clear" w:pos="1134" />'
            '<w:tab w:val="left" w:pos="809" w:leader="none" /></w:tabs>'
            '<w:bidi w:val="0" />' + sp +
            '<w:ind w:hanging="283" w:start="809" /><w:jc w:val="start" /><w:rPr /></w:pPr>'
            + t(s + ' ') + '</w:p>')

def lead(s):
    """“数据资产方面：”这类引导句，无项目符号。"""
    return ('<w:p><w:pPr><w:pStyle w:val="BodyText" /><w:keepLines w:val="false" />'
            '<w:bidi w:val="0" /><w:spacing w:before="160" w:after="0" />'
            '<w:jc w:val="start" /><w:rPr /></w:pPr>' + t(s) + '</w:p>')

def bullets(items):
    return ''.join(bullet(x, last=(i == len(items) - 1)) for i, x in enumerate(items))

def footer(s):
    return ('<w:p><w:pPr><w:pStyle w:val="BodyTextfooter-note" /><w:bidi w:val="0" />'
            '<w:spacing w:before="320" w:after="100" /><w:jc w:val="start" /><w:rPr /></w:pPr>'
            + t(s) + '</w:p>')

def cell(w, txt, style="TableContents", jc="start", shd=True):
    shd_xml = '<w:shd w:fill="F4F7FB" w:val="clear" />' if shd else ''
    return (f'<w:tc><w:tcPr><w:tcW w:w="{w}" w:type="dxa" /><w:tcBorders />{shd_xml}'
            f'<w:vAlign w:val="center" /></w:tcPr>'
            f'<w:p><w:pPr><w:pStyle w:val="{style}" /><w:bidi w:val="0" />'
            f'<w:spacing w:before="100" w:after="100" /><w:jc w:val="{jc}" /><w:rPr /></w:pPr>'
            + t(txt) + '</w:p></w:tc>')

def info_table(rows):
    grid = '<w:tblGrid><w:gridCol w:w="2751" /><w:gridCol w:w="8587" /></w:tblGrid>'
    body = ''
    for k, v in rows:
        body += ('<w:tr><w:trPr />' + cell(2751, k) + cell(8587, v) + '</w:tr>')
    return ('<w:tbl><w:tblPr><w:tblW w:w="5000" w:type="pct" /><w:jc w:val="start" />'
            '<w:tblInd w:w="0" w:type="dxa" /><w:shd w:fill="F4F7FB" w:val="clear" />'
            '<w:tblLayout w:type="fixed" /><w:tblCellMar>'
            '<w:top w:w="28" w:type="dxa" /><w:start w:w="28" w:type="dxa" />'
            '<w:bottom w:w="28" w:type="dxa" /><w:end w:w="28" w:type="dxa" />'
            '</w:tblCellMar></w:tblPr>' + grid + body + '</w:tbl>')

ISSUE_W = [640, 836, 1034, 4601, 2867]

def issue_table(header, rows):
    grid = '<w:tblGrid>' + ''.join(f'<w:gridCol w:w="{w}" />' for w in ISSUE_W) + '</w:tblGrid>'
    hd = '<w:tr><w:trPr><w:tblHeader w:val="true" /><w:cantSplit w:val="true" /></w:trPr>'
    for w, txt in zip(ISSUE_W, header):
        hd += cell(w, txt, style="TableHeading", jc="center", shd=False)
    hd += '</w:tr>'
    body = ''
    for r in rows:
        body += '<w:tr><w:trPr><w:cantSplit w:val="true" /></w:trPr>'
        for i, (w, txt) in enumerate(zip(ISSUE_W, r)):
            body += cell(w, txt, jc="center" if i == 0 else "start", shd=False)
        body += '</w:tr>'
    return ('<w:tbl><w:tblPr><w:tblW w:w="4400" w:type="pct" /><w:jc w:val="center" />'
            '<w:tblInd w:w="0" w:type="dxa" /><w:tblLayout w:type="fixed" /><w:tblCellMar>'
            '<w:top w:w="28" w:type="dxa" /><w:start w:w="28" w:type="dxa" />'
            '<w:bottom w:w="28" w:type="dxa" /><w:end w:w="28" w:type="dxa" />'
            '</w:tblCellMar></w:tblPr>' + grid + hd + body + '</w:tbl>')

# ---------------- 内容 ----------------
P = []
P.append(h1("DTS v2.2.3 周报"))
P.append(subtitle("商业智能、数据门户、数据资产、数据建模及安全合规工作汇总"))
P.append(info_table([
    ("报告周期", "2026年8月17日—2026年8月21日"),
    ("下周周期", "2026年8月24日—2026年8月28日"),
    ("产品版本", "v2.2.3"),
]))

P.append(h2("一、本周工作内容"))

P.append(h3("1. 商业智能与数据分析"))
P.append(bullets([
    "完成受治理的分析创作闭环，业务人员可在既有分析入口完成拖拽出图、样式调整、计算字段、联动配置、发布和导出，全过程继续受数据集契约、权限和查询预算约束。",
    "在分析编辑器中暴露图表类型选择器，并统一分析数据集详情的术语表述，减少同一概念在不同页面的叫法差异。",
    "完成看板编排与发布闭环，作者可在同一编辑器内选择已发布分析、拖放编排、编辑组件、保存草稿并选择发布范围。",
    "修复未命名草稿导致看板无法保存的阻断问题，新建草稿的命名改为可直接编辑。",
    "修复治理型商业智能的升级连续性、分析发布服务启动异常，以及平台与分析服务之间的联动断链。",
]))

P.append(h3("2. 数据门户与数据消费"))
P.append(bullets([
    "新增客户数据门户，登录用户从统一入口按主题域浏览自己有权访问的已发布大屏，点击后在右侧加载当前发布版本。",
    "门户支持稳定链接直达、刷新保持和跳转大屏管理，未发布内容不进入门户目录。",
    "门户目录复用大屏既有的发布状态与权限体系，不新建第二套权限或发布控制面。",
]))

P.append(h3("3. 数据资产与标签"))
P.append(bullets([
    "完成企业数据资产目录闭环，数据表、数据模型、指标、分析数据集、看板、数据产品和数据服务进入统一资产目录。",
    "恢复可发现的数据标签入口，贯通按标签筛选、资产关联、关系查看和质量事实展示。",
    "本项在不复制业务归属数据、不新建第二套资产身份的前提下完成，已按计划收口。",
]))

P.append(h3("4. 数据建模"))
P.append(bullets([
    "完成依赖感知的模型物化流程，模型按依赖顺序构建。",
    "修复当前版本维度无法创建新修订版本的编辑锁问题。",
    "完成建模治理证据闭环的增量回归，模型构建、物化、质量检查到发布后的资产身份保持一致。",
]))

P.append(h3("5. 安全与合规"))
P.append(bullets([
    "完成技术协议逐条对账复核，以源码实测方式核验协议十个功能模块及非功能要求，形成缺口复核报告。",
    "复核结论：协议原列的十三项一级缺口目前已闭合两项（会话安全、操作权限矩阵），仍有十一项未闭合；二级缺口剩余二十五项。",
    "完成密级控制现状确认：统一密级目录、入湖密级封存、准入校验、沿血缘只升不降传播、消费侧密级拦截均已具备，可作为后续功能的依赖。",
    "完成下一阶段合规专项立项，覆盖口令策略与登录失败锁定、保密符合性映射与测评整改台账、敏感数据自动识别、系统监控告警四个方向。",
    "受客户审批模型尚未确定影响，数据生命周期审批、文件维护审批、元数据审核发布三项暂缓，已单独登记并保留统一接入位置，待审批模型确定后再实施。",
]))

P.append(h2("二、文档更新内容"))
P.append(bullets([
    "新增技术协议缺口复核报告，逐项记录每条缺口的当前状态与源码证据位置。",
    "新增合规专项实施方案，含五个工作项、十五项任务、端到端契约链、现状勘察账本与验收证据清单。",
    "新增密级控制现状确认说明，记录已具备能力与仍存在的缺口。",
    "新增协议缺口登记册，将未纳入本期的六项一级缺口与二十五项二级缺口按工作项与任务粒度登记并划分波次。",
    "更新商业智能看板发布、数据门户、企业资产目录、建模治理证据等方案与验收文档。",
]))

P.append(h2("三、下周工作计划"))

P.append(h3("1. 安全与合规"))
P.append(lead("口令与登录安全方面："))
P.append(bullets([
    "配置统一认证的口令复杂度、有效期、历史口令限制及登录失败锁定策略，并提供变更回滚预案。",
    "新增口令策略查询接口与前端策略提示，用户在修改口令时可实时看到未满足的具体要求。",
    "登录失败锁定事件纳入审计，登录失败提示统一措辞，避免暴露账号是否存在。",
]))
P.append(lead("保密合规方面："))
P.append(bullets([
    "扩展现有安全基线能力，按保密标准条款组织符合性检查，并支持按测评轮次管理。",
    "实现自动校验能力，口令策略、失败锁定、审计开启、备份可恢复等项由系统自动判定并产出机器证据。",
    "支持不符合项的整改登记、责任人指派与证据补充，并可按轮次导出完整证据包。",
]))
P.append(lead("数据安全方面："))
P.append(bullets([
    "建设敏感数据识别规则库并预置常见个人信息规则，支持按范围发起扫描。",
    "扫描结果确认后自动生成脱敏规则并在查询期生效；密级建议提交既有密级控制面处理，不绕过只升不降约束。",
    "扫描过程中的样本值全链路脱敏，不落前端、不落日志。",
]))
P.append(lead("监控告警方面："))
P.append(bullets([
    "部署监控与告警组件，接入四个后台服务的指标采集。",
    "定义服务不可用、入湖任务连续失败、审计写入中断、磁盘水位等告警规则，并配置抑制策略避免告警风暴。",
    "补充平台业务指标埋点，提供只读监控看板，平台内不重复建设监控页面。",
]))

P.append(h3("2. 交付验收与收尾"))
P.append(bullets([
    "执行交付基线核验，确认运行实例、登录方式、关键页面可达性及浏览器兼容环境，作为后续界面验收的前置条件。",
    "完成看板发布闭环在容器环境下的真实发布验收。",
    "评估治理型商业智能升级项的阻塞事项，确定本轮解决或延后。",
    "跟进客户侧待确认事项：保密标准条款目录、口令策略具体数值、测评证据包格式要求。",
]))

P.append(h2("四、测试问题清单"))
P.append(issue_table(
    ["编号", "优先级", "模块", "问题或验收缺口", "处理计划"],
    [
        ["T-01", "P0", "安全合规", "统一认证尚未配置口令复杂度策略，登录失败锁定未启用，机密级测评基础控制项不可证明。", "下周完成配置并补充实测证据。"],
        ["T-02", "P0", "安全合规", "缺少保密标准条款符合性映射与测评整改台账，测评现场无法逐条举证。", "扩展现有安全基线能力，条款目录待客户提供。"],
        ["T-03", "P0", "数据安全", "无敏感数据自动识别能力，脱敏规则依赖人工逐个标注。", "建设识别规则库与扫描能力，结果确认后生成脱敏规则。"],
        ["T-04", "P0", "监控告警", "无自定义告警规则与告警组件，系统异常依赖人工发现。", "部署监控告警组件并定义规则集。"],
        ["T-05", "P0", "浏览器兼容", "多个页面仅完成构建与静态检查，缺少登录态下的真实浏览器截图与控制台证据。", "先完成交付基线核验，再集中执行一次真实浏览器回归。"],
        ["T-06", "P1", "商业智能", "看板发布闭环的容器环境真实发布验收尚未执行。", "下周在目标环境完成发布与回滚验证。"],
        ["T-07", "P1", "数据管理", "数据生命周期仍只有归档、处置、延长保留三类，缺少临时销毁与永久销毁双态及回收站还原。", "非审批部分排入下一期，审批部分待客户审批模型确定。"],
        ["T-08", "P1", "非功能", "全部服务为单节点部署，且缺少覆盖协议性能指标的统一压测报告。", "需环境资源支持，单独排期。"],
    ]))

P.append(footer("说明：以上工作内容依据本周代码提交、方案文档与复核记录整理。缺口结论均以源码实测为准，未采用文档声明状态；标注为待验收的项目需在正式验收前于真实环境再次确认。"))

BODY = ''.join(P)

# ---------------- 模板手术 ----------------
os.makedirs(os.path.dirname(OUT), exist_ok=True)
shutil.copy(REF, OUT + ".tmp")
src = zipfile.ZipFile(REF, 'r')
doc = src.read('word/document.xml').decode('utf-8')
head = doc[:doc.find('<w:body>') + len('<w:body>')]
tail = doc[doc.find('<w:sectPr>'):]
new_doc = head + BODY + tail

core = src.read('docProps/core.xml').decode('utf-8')
core = re.sub(r'<dc:title>.*?</dc:title>', f'<dc:title>{escape(TITLE)}</dc:title>', core, flags=re.S)

with zipfile.ZipFile(OUT, 'w', zipfile.ZIP_DEFLATED) as z:
    for item in src.infolist():
        if item.filename == 'word/document.xml':
            z.writestr(item, new_doc.encode('utf-8'))
        elif item.filename == 'docProps/core.xml':
            z.writestr(item, core.encode('utf-8'))
        else:
            z.writestr(item, src.read(item.filename))
src.close()
os.remove(OUT + ".tmp")
print("生成:", OUT, os.path.getsize(OUT), "bytes")
