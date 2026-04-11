<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width,initial-scale=1.0">
<title>项目综合看板</title>
<script src="https://cdnjs.cloudflare.com/ajax/libs/Chart.js/4.4.1/chart.umd.js"></script>
<style>
*,*::before,*::after{box-sizing:border-box;margin:0;padding:0}
:root{
  --bg:#1356A0;
  --panel:rgba(8,42,90,0.75);
  --panel2:rgba(5,30,70,0.90);
  --border:rgba(255,255,255,0.18);
  --border2:rgba(255,255,255,0.35);
  --cyan:#7dd4ff;
  --cyan2:#b0e4ff;
  --blue:#1a6fd4;
  --purple:#a855f7;
  --gold:#ffb800;
  --green:#00e59a;
  --red:#ff4560;
  --amber:#ff9900;
  --pink:#ff3eb5;
  --text:#ffffff;
  --text2:#80b8d8;
  --text3:#90c4e8;
  /* Tech palette for charts */
  --tc1:#2ab8e8; /* cyan */
  --tc2:#065887; /* blue */
  --tc3:#a855f7; /* purple */
  --tc4:#00e59a; /* green */
  --tc5:#ff9900; /* amber */
  --tc6:#ff4560; /* red */
  --tc7:#ff3eb5; /* pink */
  --tc8:#ffee00; /* yellow */
}
html,body{width:100%;height:100%;overflow:hidden}
body{
  background:linear-gradient(160deg,#0d4a96 0%,#1356A0 30%,#1060b0 60%,#0d4a96 100%);
  font-family:'PingFang SC','Microsoft YaHei','Hiragino Sans GB',sans-serif;
  color:#ffffff;min-height:100vh;
}
body::before{
  content:'';position:fixed;inset:0;
  background-image:
    linear-gradient(rgba(255,255,255,0.06) 1px,transparent 1px),
    linear-gradient(90deg,rgba(255,255,255,0.06) 1px,transparent 1px);
  background-size:44px 44px;pointer-events:none;z-index:0;
}
.app{position:relative;z-index:1;display:flex;flex-direction:column;height:100vh}

/* ── HEADER ── */
.hdr{height:50px;display:flex;align-items:center;justify-content:space-between;
  padding:0 16px;border-bottom:1px solid rgba(255,255,255,0.25);flex-shrink:0;position:relative;background:rgba(5,35,80,0.5)}
.hdr::after{content:'';position:absolute;bottom:0;left:8%;right:8%;height:1px;
  background:linear-gradient(90deg,transparent,rgba(255,255,255,0.6),transparent);opacity:.6}
.hdr-left{display:flex;align-items:center;gap:12px}
.hdr-dots{display:flex;gap:5px}
.hdr-dots i{width:7px;height:7px;border-radius:50%;animation:blink 2s infinite}
.hdr-dots i:nth-child(1){background:#2ab8e8;box-shadow:0 0 6px #2ab8e8}
.hdr-dots i:nth-child(2){background:rgba(255,255,255,.7);animation-delay:.5s}
.hdr-dots i:nth-child(3){background:var(--green);animation-delay:1s}
@keyframes blink{0%,100%{opacity:1}50%{opacity:.3}}
.hdr-title{font-size:21px;font-weight:800;letter-spacing:4px;color:#ffffff;
  text-shadow:0 0 18px rgba(255,255,255,.5),0 0 36px rgba(125,212,255,.3)}
.hdr-title em{color:#2ab8e8;font-style:normal}
.hdr-nav{display:flex;gap:4px}
.hdr-nav button{background:rgba(6,88,135,.15);border:1px solid rgba(6,140,200,.28);
  color:#c0dce8;padding:5px 14px;border-radius:4px;font-size:12px;cursor:pointer;
  font-family:inherit;transition:all .2s;letter-spacing:.3px}
.hdr-nav button:hover,.hdr-nav button.act{background:rgba(6,140,200,.20);
  border-color:#2ab8e8;color:#2ab8e8;box-shadow:0 0 10px rgba(6,140,200,.28)}
.hdr-time{font-size:12px;color:#90b8cc;min-width:155px;text-align:right;
  font-variant-numeric:tabular-nums}

/* ── MAIN ── */
.main{flex:1;display:flex;flex-direction:column;gap:7px;padding:7px 13px 8px;min-height:0;overflow:hidden}

/* ── KPI ── */
.kpi-row{display:grid;grid-template-columns:repeat(5,1fr);gap:7px;flex-shrink:0}
.kpi{background:rgba(255,255,255,0.12);border:1px solid rgba(255,255,255,0.22);border-radius:8px;
  padding:10px 14px;text-align:center;position:relative;overflow:hidden;
  backdrop-filter:blur(8px);transition:border-color .2s,box-shadow .2s;cursor:default}
.kpi:hover{border-color:rgba(6,140,200,.45);box-shadow:0 0 18px rgba(6,88,135,.18)}
.kpi::before{content:'';position:absolute;top:0;left:15%;right:15%;height:1.5px;
  background:linear-gradient(90deg,transparent,rgba(255,255,255,.6),transparent);opacity:.5}
.kpi::after{content:'';position:absolute;top:0;left:0;width:13px;height:13px;
  border-top:2px solid rgba(255,255,255,.55);border-left:2px solid rgba(255,255,255,.55);
  border-radius:7px 0 0 0;opacity:.6}
.kpi-label{font-size:11px;color:#a8cce0;letter-spacing:.3px;margin-bottom:5px}
.kpi-val{font-size:30px;font-weight:800;line-height:1;font-variant-numeric:tabular-nums}
.kv-w{color:#ffffff} .kv-g{color:#44f0b0;text-shadow:0 0 10px rgba(0,229,154,.5)}
.kv-r{color:#ff8090;text-shadow:0 0 10px rgba(255,69,96,.5)}
.kv-a{color:#ffcc44;text-shadow:0 0 10px rgba(255,200,0,.5)}
.kv-c{color:#b0e4ff;text-shadow:0 0 10px rgba(180,230,255,.4)}

/* ── CHART GRID ── */
.chart-grid{display:grid;grid-template-columns:repeat(4,1fr);gap:7px;flex-shrink:0}
.card{background:rgba(255,255,255,0.10);border:1px solid rgba(255,255,255,0.18);border-radius:8px;
  padding:9px 11px 7px;backdrop-filter:blur(8px);position:relative;overflow:hidden}
.card::before{content:'';position:absolute;top:0;left:0;right:0;height:1px;
  background:linear-gradient(90deg,transparent,rgba(6,140,200,.45),transparent)}
.card::after{content:'';position:absolute;top:0;left:0;width:11px;height:11px;
  border-top:2px solid rgba(255,255,255,.45);border-left:2px solid rgba(255,255,255,.45);
  border-radius:5px 0 0 0;opacity:.5}

/* card header with pagination */
.card-hdr{display:flex;align-items:center;justify-content:space-between;margin-bottom:5px;padding-bottom:4px;border-bottom:1px solid rgba(6,88,135,.14)}
.card-title{font-size:12px;font-weight:700;letter-spacing:.3px;display:flex;align-items:center;gap:4px}
.ct-b{color:#b0e4ff} .ct-r{color:#ff7090} .ct-a{color:#ffbb44} .ct-g{color:#44f0b0}
.card-pg{display:flex;align-items:center;gap:5px}
.pg-btn{width:18px;height:18px;background:rgba(6,88,135,.20);border:1px solid rgba(6,140,200,.28);
  border-radius:3px;color:#c0dce8;font-size:10px;cursor:pointer;
  display:flex;align-items:center;justify-content:center;transition:all .15s;padding:0;font-family:inherit}
.pg-btn:hover:not(:disabled){background:rgba(6,140,200,.28);border-color:#2ab8e8;color:#b0e4ff}
.pg-btn:disabled{opacity:.3;cursor:default}
.pg-info{font-size:9px;color:rgba(255,255,255,.65);white-space:nowrap}

/* mini legend */
.mleg{display:flex;flex-wrap:wrap;gap:4px 8px;font-size:9px;color:rgba(255,255,255,.80);margin-bottom:4px}
.mleg span{display:flex;align-items:center;gap:2px}
.mleg .sq{width:8px;height:8px;border-radius:2px;flex-shrink:0}
.mleg .ln{width:12px;height:2px;border-radius:1px;flex-shrink:0}

/* TOP-N badge */
.topn-badge{font-size:9px;background:rgba(255,255,255,.12);border:1px solid rgba(255,255,255,.30);
  color:#d0f0ff;padding:1px 6px;border-radius:8px;white-space:nowrap}

.cw{position:relative;height:142px}

/* ── GANTT ── */
.gantt-row{display:grid;grid-template-columns:1fr 250px;gap:7px;flex:1;min-height:0}
.gantt-card{background:rgba(255,255,255,0.10);border:1px solid rgba(255,255,255,0.18);border-radius:8px;
  padding:9px 12px 7px;position:relative;overflow:hidden;display:flex;flex-direction:column}
.gantt-card::before{content:'';position:absolute;top:0;left:0;right:0;height:1px;
  background:linear-gradient(90deg,transparent,rgba(6,140,200,.45),transparent)}
.gantt-card::after{content:'';position:absolute;top:0;left:0;width:11px;height:11px;
  border-top:2px solid rgba(255,255,255,.45);border-left:2px solid rgba(255,255,255,.45);
  border-radius:5px 0 0 0;opacity:.5}
.g-hdr{display:flex;justify-content:space-between;align-items:center;margin-bottom:7px;flex-shrink:0}
.g-hdr-l{display:flex;align-items:center;gap:10px}
.g-title{font-size:12px;font-weight:700;color:#ffffff;display:flex;align-items:center;gap:5px}
.g-title::before{content:'';display:block;width:3px;height:12px;background:#2ab8e8;
  border-radius:2px;box-shadow:0 0 6px #2ab8e8}
.vtabs{display:flex;gap:3px}
.vtabs button{background:rgba(255,255,255,.08);border:1px solid rgba(255,255,255,.20);
  color:rgba(255,255,255,.65);padding:2px 9px;border-radius:3px;font-size:10px;
  cursor:pointer;font-family:inherit;transition:all .15s}
.vtabs button.act,.vtabs button:hover{background:rgba(6,140,200,.22);border-color:#2ab8e8;color:#b0e4ff}
.g-hdr-r{display:flex;align-items:center;gap:10px}
.g-leg{display:flex;gap:8px;font-size:9px;color:rgba(255,255,255,.80)}
.g-leg span{display:flex;align-items:center;gap:3px}
.g-leg .sq{width:9px;height:7px;border-radius:2px}
.g-info{font-size:9px;color:rgba(255,255,255,.60)}
.cdots{display:flex;gap:4px}
.cdot{width:5px;height:5px;border-radius:3px;background:rgba(6,140,200,.28);cursor:pointer;transition:all .3s}
.cdot.on{width:14px;background:#ffffff;box-shadow:0 0 5px rgba(255,255,255,.6)}
.pbtn{width:20px;height:20px;background:rgba(6,88,135,.18);border:1px solid rgba(6,140,200,.3);
  border-radius:3px;color:#2ab8e8;font-size:9px;cursor:pointer;
  display:flex;align-items:center;justify-content:center;font-family:inherit}
.pbtn:hover{background:rgba(6,140,200,.28)}

.gt{width:100%;border-collapse:collapse;font-size:10px}
.gt th{font-size:9px;color:rgba(255,255,255,.65);padding:3px 6px;text-align:left;
  border-bottom:1px solid rgba(6,88,135,.18);font-weight:500;white-space:nowrap}
.gt tr.gr{cursor:pointer;transition:background .12s}
.gt tr.gr:hover{background:rgba(6,88,135,.12)}
.gt td{padding:5px 6px;vertical-align:middle;color:#ffffff}
.pj-id{font-size:10px;font-weight:700;color:#b0e4ff}
.pj-sub{font-size:9px;color:rgba(255,255,255,.60);margin-top:1px}
.dept-t{font-size:9px;color:rgba(255,255,255,.80)}
.rpill{display:inline-flex;align-items:center;justify-content:center;
  padding:1px 6px;border-radius:7px;font-size:9px;font-weight:700}
.rp-ok{background:rgba(0,229,154,.1);color:#00e59a;border:1px solid rgba(0,229,154,.3)}
.rp-warn{background:rgba(255,153,0,.20);color:#ffcc44;border:1px solid rgba(255,153,0,.45)}
.rp-bad{background:rgba(255,69,96,.20);color:#ff7090;border:1px solid rgba(255,69,96,.45)}
.date-t{font-size:9px;color:rgba(255,255,255,.65);white-space:nowrap}
.btrack{position:relative;height:16px;background:rgba(6,88,135,.12);
  border-radius:4px;overflow:hidden;border:1px solid rgba(6,88,135,.18)}
.bplan{position:absolute;top:0;height:100%;background:rgba(6,88,135,.25);border-radius:3px}
.bfill{position:absolute;top:0;height:100%;border-radius:3px;
  display:flex;align-items:center;padding-left:5px}
.bfill span{font-size:8px;color:#fff;font-weight:700;white-space:nowrap}
.b-ok{background:linear-gradient(90deg,rgba(0,229,154,.6),rgba(0,229,154,.85))}
.b-delay{background:linear-gradient(90deg,rgba(255,69,96,.6),rgba(255,80,60,.85))}
.today-ln{position:absolute;top:0;bottom:0;width:1.5px;background:rgba(255,220,0,.9);z-index:2}

/* side table */
.side-card{background:rgba(255,255,255,0.10);border:1px solid rgba(255,255,255,0.18);border-radius:8px;
  padding:9px 11px 7px;position:relative;overflow:hidden;display:flex;flex-direction:column}
.side-card::before{content:'';position:absolute;top:0;left:0;right:0;height:1px;
  background:linear-gradient(90deg,transparent,rgba(6,140,200,.45),transparent)}
.side-card::after{content:'';position:absolute;top:0;left:0;width:11px;height:11px;
  border-top:2px solid rgba(255,255,255,.45);border-left:2px solid rgba(255,255,255,.45);
  border-radius:5px 0 0 0;opacity:.5}
.sc-title{font-size:11px;font-weight:700;color:#e8f4ff;margin-bottom:7px;
  display:flex;align-items:center;gap:5px}
.sc-title::before{content:'';display:block;width:3px;height:12px;background:#2ab8e8;
  border-radius:2px;box-shadow:0 0 6px #2ab8e8}
.st{width:100%;border-collapse:collapse;font-size:10px}
.st th{padding:4px 7px;text-align:left;background:rgba(255,255,255,.15);
  color:#ffffff;font-size:9px;font-weight:600;border-bottom:1px solid rgba(6,88,135,.22)}
.st td{padding:4px 7px;color:#d0e8f4;border-bottom:1px solid rgba(6,88,135,.12)}
.st tr:last-child td{border-bottom:none}
.st tr:hover td{background:rgba(6,88,135,.12);color:#ffffff}

/* MODAL */
.mmask{display:none;position:fixed;inset:0;background:rgba(0,4,18,.75);z-index:9000;
  align-items:center;justify-content:center;backdrop-filter:blur(4px)}
.mmask.open{display:flex}
.mbox{background:linear-gradient(145deg,#0e4590,#082070);
  border:1px solid rgba(255,255,255,.30);border-radius:12px;width:490px;max-width:94vw;
  max-height:86vh;overflow-y:auto;
  box-shadow:0 0 40px rgba(6,140,200,.28),0 0 80px rgba(0,100,255,.08);
  padding:20px;position:relative;animation:mIn .2s ease}
.mbox::before{content:'';position:absolute;top:0;left:15%;right:15%;height:1px;
  background:linear-gradient(90deg,transparent,rgba(255,255,255,.5),transparent)}
@keyframes mIn{from{opacity:0;transform:translateY(14px) scale(.97)}to{opacity:1;transform:none}}
.mcls{position:absolute;top:11px;right:11px;background:rgba(6,88,135,.18);
  border:1px solid rgba(6,140,200,.28);width:24px;height:24px;border-radius:5px;
  font-size:13px;color:#c0dce8;cursor:pointer;
  display:flex;align-items:center;justify-content:center;transition:all .15s}
.mcls:hover{background:rgba(6,140,200,.25);color:#b0e4ff}
.m-id{font-size:17px;font-weight:800;color:#b0e4ff;text-shadow:0 0 10px rgba(180,220,255,.4)}
.m-sub{font-size:11px;color:rgba(255,255,255,.65);margin-top:2px}
.m-div{border:none;border-top:1px solid rgba(6,88,135,.18);margin:10px 0}
.m-meta{display:grid;grid-template-columns:1fr 1fr;gap:9px 14px}
.mf label{font-size:9px;color:rgba(255,255,255,.65);display:block;margin-bottom:2px}
.mf p{font-size:12px;font-weight:600;color:#ffffff}
.m-rbar{margin:10px 0 0;height:6px;background:rgba(6,88,135,.18);border-radius:4px;
  overflow:hidden;border:1px solid rgba(6,88,135,.18)}
.m-rfill{height:100%;border-radius:4px;background:linear-gradient(90deg,#4090e0,#80d0ff)}
.m-ttitle{font-size:10px;color:rgba(255,255,255,.65);margin:12px 0 5px}
.trow{display:flex;align-items:center;justify-content:space-between;padding:4px 0;
  border-bottom:1px solid rgba(6,88,135,.12);font-size:10px}
.trow:last-child{border-bottom:none}
.tname{color:#ffffff}
.tt{padding:2px 7px;border-radius:5px;font-size:9px;font-weight:700}
.td{background:rgba(0,229,154,.1);color:var(--green);border:1px solid rgba(0,229,154,.2)}
.tw{background:rgba(80,160,255,.18);color:#b0d8ff;border:1px solid rgba(80,160,255,.35)}
.tl{background:rgba(255,69,96,.18);color:#ff7090;border:1px solid rgba(255,69,96,.35)}
.tn{background:rgba(255,255,255,.10);color:rgba(255,255,255,.55);border:1px solid rgba(255,255,255,.08)}
.stag{display:inline-block;padding:2px 8px;border-radius:7px;font-size:10px;font-weight:700}
.sok{background:rgba(0,229,154,.1);color:#00e59a;border:1px solid rgba(0,229,154,.3)}
.sdl{background:rgba(255,69,96,.20);color:#ff7090;border:1px solid rgba(255,69,96,.45)}

::-webkit-scrollbar{width:4px;height:4px}
::-webkit-scrollbar-track{background:transparent}
::-webkit-scrollbar-thumb{background:rgba(6,140,200,.32);border-radius:2px}
</style>
</head>
<body>
<div class="app">

<header class="hdr">
  <div class="hdr-left">
    <div class="hdr-dots"><i></i><i></i><i></i></div>
    <div class="hdr-title">项目<em>综合</em>看板</div>
  </div>
  <nav class="hdr-nav">
    <button class="act" onclick="navBtn(this)">综合看板</button>
    <button onclick="navBtn(this)">进度看板</button>
    <button onclick="navBtn(this)">质量看板</button>
    <button onclick="navBtn(this)">技术状态</button>
    <button onclick="navBtn(this)">风险看板</button>
  </nav>
  <div class="hdr-time" id="clk"></div>
</header>

<div class="main">

  <!-- KPI -->
  <div class="kpi-row">
    <div class="kpi"><div class="kpi-label">项目总数</div><div class="kpi-val kv-w" id="k0">0</div></div>
    <div class="kpi"><div class="kpi-label">项目完成率</div><div class="kpi-val kv-g" id="k1">0%</div></div>
    <div class="kpi"><div class="kpi-label">未闭环质量问题</div><div class="kpi-val kv-r" id="k2">0</div></div>
    <div class="kpi"><div class="kpi-label">未整改技术更改单</div><div class="kpi-val kv-a" id="k3">0</div></div>
    <div class="kpi"><div class="kpi-label">高风险数量</div><div class="kpi-val kv-r" id="k4">0</div></div>
  </div>

  <!-- CHART GRID -->
  <div class="chart-grid">

    <!-- C1: 进度计划-项目 -->
    <div class="card">
      <div class="card-hdr">
        <div class="card-title ct-b">进度计划 <span style="font-size:9px;color:rgba(255,255,255,.60);font-weight:400">项目维度</span></div>
        <div class="card-pg">
          <span class="pg-info" id="pg1info"></span>
          <button class="pg-btn" id="pg1prev" onclick="pgPrev(1)">◀</button>
          <button class="pg-btn" id="pg1next" onclick="pgNext(1)">▶</button>
        </div>
      </div>
      <div class="mleg">
        <span><span class="sq" style="background:#2ab8e8"></span>已完成</span>
        <span><span class="sq" style="background:#065887"></span>未完成</span>
        <span><span class="sq" style="background:#a855f7"></span>待完成</span>
        <span><span class="ln" style="background:#ffb800"></span>完成率</span>
      </div>
      <div class="cw"><canvas id="c1" role="img" aria-label="进度计划项目维度"></canvas></div>
    </div>

    <!-- C2: 质量问题-项目 -->
    <div class="card">
      <div class="card-hdr">
        <div class="card-title ct-r">质量问题 <span style="font-size:9px;color:rgba(255,255,255,.60);font-weight:400">项目维度</span></div>
        <div class="card-pg">
          <span class="pg-info" id="pg2info"></span>
          <button class="pg-btn" id="pg2prev" onclick="pgPrev(2)">◀</button>
          <button class="pg-btn" id="pg2next" onclick="pgNext(2)">▶</button>
        </div>
      </div>
      <div class="mleg">
        <span><span class="sq" style="background:#ff4560"></span>现存问题</span>
        <span><span class="sq" style="background:#00e59a"></span>已归零</span>
        <span><span class="sq" style="background:#ff9900"></span>未提交</span>
      </div>
      <div class="cw"><canvas id="c2" role="img" aria-label="质量问题项目维度"></canvas></div>
    </div>

    <!-- C3: 技术状态-项目 -->
    <div class="card">
      <div class="card-hdr">
        <div class="card-title ct-a">技术状态 <span style="font-size:9px;color:rgba(255,255,255,.60);font-weight:400">项目维度</span></div>
        <div class="card-pg">
          <span class="pg-info" id="pg3info"></span>
          <button class="pg-btn" id="pg3prev" onclick="pgPrev(3)">◀</button>
          <button class="pg-btn" id="pg3next" onclick="pgNext(3)">▶</button>
        </div>
      </div>
      <div class="mleg">
        <span><span class="sq" style="background:#00e59a"></span>已整改</span>
        <span><span class="sq" style="background:#ff4560"></span>未整改</span>
        <span><span class="sq" style="background:#ff9900"></span>未签署</span>
      </div>
      <div class="cw"><canvas id="c3" role="img" aria-label="技术状态项目维度"></canvas></div>
    </div>

    <!-- C4: 项目风险-项目 -->
    <div class="card">
      <div class="card-hdr">
        <div class="card-title ct-g">项目风险 <span style="font-size:9px;color:rgba(255,255,255,.60);font-weight:400">项目维度</span></div>
        <div class="card-pg">
          <span class="pg-info" id="pg4info"></span>
          <button class="pg-btn" id="pg4prev" onclick="pgPrev(4)">◀</button>
          <button class="pg-btn" id="pg4next" onclick="pgNext(4)">▶</button>
        </div>
      </div>
      <div class="mleg">
        <span><span class="sq" style="background:#ff4560"></span>高风险</span>
        <span><span class="sq" style="background:#ff9900"></span>中风险</span>
        <span><span class="sq" style="background:#ffee00"></span>低风险</span>
      </div>
      <div class="cw"><canvas id="c4" role="img" aria-label="项目风险项目维度"></canvas></div>
    </div>

    <!-- C5: 进度计划-科室 TOP5 -->
    <div class="card">
      <div class="card-hdr">
        <div class="card-title ct-b">进度计划 <span style="font-size:9px;color:rgba(255,255,255,.60);font-weight:400">科室维度</span></div>
        <span class="topn-badge">完成率 TOP5</span>
      </div>
      <div class="mleg">
        <span><span class="sq" style="background:#00e59a"></span>已完成</span>
        <span><span class="sq" style="background:#a855f7"></span>未完成</span>
        <span><span class="ln" style="background:#b0e4ff"></span>完成率%</span>
      </div>
      <div class="cw"><canvas id="c5" role="img" aria-label="进度计划科室TOP5"></canvas></div>
    </div>

    <!-- C6: 质量问题-科室 TOP5 -->
    <div class="card">
      <div class="card-hdr">
        <div class="card-title ct-r">质量问题 <span style="font-size:9px;color:rgba(255,255,255,.60);font-weight:400">科室维度</span></div>
        <span class="topn-badge">问题数 TOP5</span>
      </div>
      <div class="mleg">
        <span><span class="sq" style="background:#ff4560"></span>现存问题</span>
        <span><span class="sq" style="background:#00e59a"></span>已归零</span>
      </div>
      <div class="cw"><canvas id="c6" role="img" aria-label="质量问题科室TOP5"></canvas></div>
    </div>

    <!-- C7: 技术状态-科室 TOP5 -->
    <div class="card">
      <div class="card-hdr">
        <div class="card-title ct-a">技术状态 <span style="font-size:9px;color:rgba(255,255,255,.60);font-weight:400">科室维度</span></div>
        <span class="topn-badge">待整改 TOP5</span>
      </div>
      <div class="mleg">
        <span><span class="sq" style="background:#ff4560"></span>未整改</span>
        <span><span class="sq" style="background:#ff9900"></span>未签署</span>
      </div>
      <div class="cw"><canvas id="c7" role="img" aria-label="技术状态科室TOP5"></canvas></div>
    </div>

    <!-- C8: 项目风险-科室 TOP5 -->
    <div class="card">
      <div class="card-hdr">
        <div class="card-title ct-g">项目风险 <span style="font-size:9px;color:rgba(255,255,255,.60);font-weight:400">科室维度</span></div>
        <span class="topn-badge">风险数 TOP5</span>
      </div>
      <div class="mleg">
        <span><span class="sq" style="background:#ff4560"></span>高</span>
        <span><span class="sq" style="background:#ff9900"></span>中</span>
        <span><span class="sq" style="background:#ffee00"></span>低</span>
      </div>
      <div class="cw"><canvas id="c8" role="img" aria-label="项目风险科室TOP5"></canvas></div>
    </div>

  </div><!-- /chart-grid -->

  <!-- GANTT ROW -->
  <div class="gantt-row">
    <div class="gantt-card" id="gcrd">
      <div class="g-hdr">
        <div class="g-hdr-l">
          <div class="g-title">项目进度甘特图</div>
          <div class="vtabs" id="vtabs">
            <button class="act" onclick="vt(this)">日</button>
            <button onclick="vt(this)">周</button>
            <button onclick="vt(this)">月</button>
            <button onclick="vt(this)">季度</button>
          </div>
          <span style="font-size:9px;color:#90b8cc">时间粒度</span>
        </div>
        <div class="g-hdr-r">
          <div class="g-leg">
            <span><span class="sq" style="background:rgba(6,140,200,.38)"></span>计划</span>
            <span><span class="sq" style="background:rgba(0,229,154,.7)"></span>正常</span>
            <span><span class="sq" style="background:rgba(255,69,96,.7)"></span>延期</span>
          </div>
          <div class="g-info">2个重大项目 · 2025-01-14 ～ 2026-04-11</div>
          <div class="cdots" id="cdots"></div>
          <button class="pbtn" id="pbtn" onclick="tpause()">⏸</button>
        </div>
      </div>
      <div id="gbody" style="flex:1;overflow:hidden"></div>
    </div>

    <div class="side-card">
      <div class="sc-title">项目汇总信息</div>
      <table class="st">
        <thead><tr><th>列1</th><th>列2</th><th>列3</th></tr></thead>
        <tbody>
          <tr><td>行1-1</td><td>行1-2</td><td>行1-3</td></tr>
          <tr><td>行2-1</td><td>行2-2</td><td>行2-3</td></tr>
          <tr><td>行3-1</td><td>行3-2</td><td>行3-3</td></tr>
          <tr><td style="color:#90b8cc">—</td><td style="color:#90b8cc">—</td><td style="color:#90b8cc">—</td></tr>
          <tr><td style="color:#90b8cc">—</td><td style="color:#90b8cc">—</td><td style="color:#90b8cc">—</td></tr>
        </tbody>
      </table>
    </div>
  </div>

</div><!-- /main -->
</div><!-- /app -->

<!-- MODAL -->
<div class="mmask" id="mmask" onclick="mout(event)">
  <div class="mbox">
    <button class="mcls" onclick="mc()">✕</button>
    <div class="m-id" id="mid"></div>
    <div class="m-sub" id="msub"></div>
    <hr class="m-div">
    <div class="m-meta">
      <div class="mf"><label>责任科室/经理</label><p id="mdept"></p></div>
      <div class="mf"><label>交付日期</label><p id="mdate"></p></div>
      <div class="mf"><label>完成率</label><p id="mrate" style="font-size:19px;color:#2ab8e8;text-shadow:0 0 8px rgba(42,184,232,.6)"></p></div>
      <div class="mf"><label>当前状态</label><p id="mstat"></p></div>
    </div>
    <div class="m-rbar"><div class="m-rfill" id="mrfill" style="width:0"></div></div>
    <div class="m-ttitle">任务清单</div>
    <div id="mtasks"></div>
  </div>
</div>

<script>
/* ═══════════════════════════════
   DATA  — all from screenshot
═══════════════════════════════ */

/* Full project list (simulate 10 projects for pagination demo) */
const ALL_PROJECTS = [
  {id:'PJ-2025-001',done:4,undone:4,normal:0,rate:50,qExist:0,qZero:0,qNo:0,techFixed:0,techNot:3,techUnsign:2,riskH:2,riskM:2,riskL:2},
  {id:'PJ-2025-002',done:4,undone:3,normal:0,rate:57,qExist:0,qZero:0,qNo:0,techFixed:0,techNot:2,techUnsign:1,riskH:1,riskM:1,riskL:1},
  /* Simulated additional projects to show pagination */
  {id:'PJ-2025-003',done:6,undone:4,normal:1,rate:65,qExist:1,qZero:0,qNo:1,techFixed:1,techNot:2,techUnsign:1,riskH:1,riskM:2,riskL:1},
  {id:'PJ-2025-004',done:2,undone:8,normal:0,rate:25,qExist:2,qZero:1,qNo:0,techFixed:0,techNot:4,techUnsign:2,riskH:3,riskM:1,riskL:0},
  {id:'PJ-2025-005',done:7,undone:3,normal:0,rate:70,qExist:0,qZero:2,qNo:0,techFixed:2,techNot:1,techUnsign:0,riskH:0,riskM:1,riskL:3},
  {id:'PJ-2025-006',done:3,undone:7,normal:2,rate:30,qExist:3,qZero:0,qNo:2,techFixed:0,techNot:3,techUnsign:3,riskH:2,riskM:3,riskL:1},
  {id:'PJ-2025-007',done:5,undone:5,normal:1,rate:55,qExist:1,qZero:1,qNo:1,techFixed:1,techNot:1,techUnsign:2,riskH:1,riskM:2,riskL:2},
];

/* Full department list (simulate 10 depts) */
const ALL_DEPTS = [
  {name:'软件室', done:0,undone:1,rate:0,  qExist:0,qZero:0, techNot:0,techUnsign:0, riskH:0,riskM:0,riskL:3},
  {name:'结构室', done:3,undone:3,rate:50, qExist:0,qZero:0, techNot:1,techUnsign:1, riskH:0,riskM:1,riskL:1},
  {name:'电子室', done:1,undone:3,rate:25, qExist:1,qZero:0, techNot:2,techUnsign:0, riskH:1,riskM:1,riskL:1},
  {name:'热控室', done:1,undone:2,rate:33, qExist:0,qZero:0, techNot:0,techUnsign:0, riskH:0,riskM:2,riskL:0},
  {name:'推进室', done:1,undone:2,rate:33, qExist:0,qZero:0, techNot:1,techUnsign:0, riskH:1,riskM:0,riskL:0},
  {name:'动力室', done:2,undone:4,rate:33, qExist:2,qZero:1, techNot:1,techUnsign:2, riskH:2,riskM:1,riskL:1},
  {name:'测控室', done:4,undone:2,rate:67, qExist:1,qZero:2, techNot:0,techUnsign:1, riskH:0,riskM:1,riskL:2},
  {name:'总体室', done:5,undone:1,rate:83, qExist:0,qZero:3, techNot:0,techUnsign:0, riskH:0,riskM:0,riskL:1},
  {name:'载荷室', done:1,undone:5,rate:17, qExist:3,qZero:0, techNot:2,techUnsign:1, riskH:3,riskM:2,riskL:0},
  {name:'通信室', done:2,undone:3,rate:40, qExist:1,qZero:1, techNot:1,techUnsign:1, riskH:1,riskM:1,riskL:1},
];

const GANTT_PROJECTS = [
  {id:'PJ-2025-001',sub:'4子项目 · 8任务',dept:'热控室 · 赵志强',date:'2025-06-30',rate:50,delay:true,
    planL:16,planW:210,barL:16,barW:220,todayOff:90,
    tasks:[{n:'需求分析',s:'d'},{n:'方案设计',s:'d'},{n:'原型开发',s:'w'},{n:'集成测试',s:'l'},{n:'系统验证',s:'n'},{n:'文档编制',s:'n'},{n:'评审评定',s:'n'},{n:'交付移交',s:'n'}]},
  {id:'PJ-2025-002',sub:'5子项目 · 7任务',dept:'推进室 · 钱芩文',date:'2025-09-15',rate:57,delay:false,
    planL:36,planW:235,barL:36,barW:240,todayOff:100,
    tasks:[{n:'立项评审',s:'d'},{n:'接口定义',s:'d'},{n:'硬件设计',s:'d'},{n:'软件开发',s:'w'},{n:'联调测试',s:'w'},{n:'鉴定试验',s:'n'},{n:'交付归档',s:'n'}]},
];

const PAGE_SIZE = 5; // Max items per chart page
let pgState = {1:0, 2:0, 3:0, 4:0}; // current page per chart
let chartInst = {}; // chart instances

/* ═══════════════════════════════
   CLOCK & NAV
═══════════════════════════════ */
function tick(){
  const d=new Date(),p=n=>String(n).padStart(2,'0');
  document.getElementById('clk').textContent=
    `${d.getFullYear()}-${p(d.getMonth()+1)}-${p(d.getDate())}  ${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`;
}
setInterval(tick,1000); tick();
function navBtn(b){document.querySelectorAll('.hdr-nav button').forEach(x=>x.classList.remove('act'));b.classList.add('act')}

/* ═══════════════════════════════
   KPI
═══════════════════════════════ */
function cup(id,tgt,sfx,dur){
  const el=document.getElementById(id); let s=null;
  const go=ts=>{if(!s)s=ts;const p=Math.min((ts-s)/dur,1),e=1-Math.pow(1-p,3);
    el.textContent=Math.round(tgt*e)+(sfx||'');if(p<1)requestAnimationFrame(go)};
  requestAnimationFrame(go);
}
setTimeout(()=>{
  cup('k0',2,'',900); cup('k1',2,'%',1100);
  cup('k2',2,'',900); cup('k3',2,'',900); cup('k4',2,'',900);
},200);

/* ═══════════════════════════════
   CHART DEFAULTS
═══════════════════════════════ */
Chart.defaults.font.family="'PingFang SC','Microsoft YaHei',sans-serif";
Chart.defaults.font.size=10;
Chart.defaults.color='#80b8d8';

const TT={backgroundColor:'rgba(5,35,80,.95)',titleColor:'#b0e0ff',bodyColor:'#80b8e0',
  padding:8,cornerRadius:5,borderColor:'rgba(255,255,255,.25)',borderWidth:1};
const GX={grid:{color:'rgba(255,255,255,0.08)'},ticks:{color:'rgba(255,255,255,0.55)',maxRotation:0}};
const GY={grid:{color:'rgba(255,255,255,0.08)'},ticks:{color:'rgba(255,255,255,0.55)'}};

/* Tech color palettes */
const TC_DONE    = 'rgba(42,184,232,0.80)';      // cyan
const TC_UNDONE  = 'rgba(6,88,135,0.88)';     // blue
const TC_NORMAL  = 'rgba(168,85,247,0.70)';     // purple
const TC_RATE    = '#ffb800';                    // gold line
const TC_EXIST   = 'rgba(255,69,96,0.80)';      // red
const TC_ZERO    = 'rgba(0,229,154,0.75)';      // green
const TC_NOSUB   = 'rgba(255,153,0,0.75)';      // amber
const TC_FIXED   = 'rgba(0,229,154,0.75)';      // green
const TC_NOTFIX  = 'rgba(255,69,96,0.80)';      // red
const TC_UNSIGN  = 'rgba(255,153,0,0.75)';      // amber
const TC_RH      = 'rgba(255,69,96,0.82)';      // red
const TC_RM      = 'rgba(255,153,0,0.78)';      // amber
const TC_RL      = 'rgba(255,238,0,0.72)';      // yellow

/* ═══════════════════════════════
   PAGINATION HELPERS
═══════════════════════════════ */
function getPageSlice(arr, page) {
  const start = page * PAGE_SIZE;
  return arr.slice(start, start + PAGE_SIZE);
}
function totalPages(arr) { return Math.ceil(arr.length / PAGE_SIZE); }

function updatePgButtons(n, page, arr) {
  const tp = totalPages(arr);
  document.getElementById(`pg${n}prev`).disabled = page <= 0;
  document.getElementById(`pg${n}next`).disabled = page >= tp - 1;
  document.getElementById(`pg${n}info`).textContent =
    arr.length > PAGE_SIZE ? `${page+1}/${tp}页` : '';
}

function pgPrev(n) { if(pgState[n]>0){pgState[n]--;refreshChart(n);} }
function pgNext(n) {
  if(pgState[n] < totalPages(ALL_PROJECTS)-1){pgState[n]++;refreshChart(n);}
}

/* ═══════════════════════════════
   TOP-N SELECTOR (for dept charts)
   Sort by total descending, take top 5
═══════════════════════════════ */
function top5Depts_progress() {
  return [...ALL_DEPTS].sort((a,b)=>(b.done+b.undone)-(a.done+a.undone)).slice(0,5);
}
function top5Depts_quality() {
  return [...ALL_DEPTS].sort((a,b)=>(b.qExist+b.qZero)-(a.qExist+a.qZero)).slice(0,5);
}
function top5Depts_tech() {
  return [...ALL_DEPTS].sort((a,b)=>(b.techNot+b.techUnsign)-(a.techNot+a.techUnsign)).slice(0,5);
}
function top5Depts_risk() {
  return [...ALL_DEPTS].sort((a,b)=>(b.riskH+b.riskM+b.riskL)-(a.riskH+a.riskM+a.riskL)).slice(0,5);
}

/* ═══════════════════════════════
   BUILD / REFRESH CHARTS
═══════════════════════════════ */
function buildChart(id, config) {
  if(chartInst[id]) chartInst[id].destroy();
  chartInst[id] = new Chart(document.getElementById(id), config);
}

function refreshChart(n) {
  const page = pgState[n];
  const slice = getPageSlice(ALL_PROJECTS, page);
  const labels = slice.map(p=>p.id.replace('PJ-2025-','PJ-'));
  updatePgButtons(n, page, ALL_PROJECTS);

  if(n===1) {
    buildChart('c1', {
      data:{
        labels,
        datasets:[
          {type:'bar',label:'已完成', data:slice.map(p=>p.done),   backgroundColor:TC_DONE,   borderRadius:3,order:2},
          {type:'bar',label:'未完成', data:slice.map(p=>p.undone), backgroundColor:TC_UNDONE, borderRadius:3,order:2},
          {type:'bar',label:'待完成', data:slice.map(p=>p.normal), backgroundColor:TC_NORMAL, borderRadius:3,order:2},
          {type:'line',label:'完成率',data:slice.map(p=>p.rate),yAxisID:'y2',
            borderColor:TC_RATE,backgroundColor:'rgba(255,184,0,.06)',
            pointBackgroundColor:TC_RATE,pointRadius:3.5,tension:.35,
            borderWidth:2,fill:false,order:1,
            pointStyle:'circle',pointHoverRadius:5}
        ]
      },
      options:{responsive:true,maintainAspectRatio:false,
        plugins:{legend:{display:false},tooltip:TT},
        scales:{
          x:{...GX,grid:{display:false}},
          y:{...GY,min:0,max:Math.max(...slice.map(p=>p.done+p.undone+p.normal))+2},
          y2:{position:'right',grid:{display:false},min:0,max:100,
            ticks:{color:TC_RATE,callback:v=>v+'%',font:{size:9}}}
        }}
    });
  }
  if(n===2) {
    const maxQ = Math.max(...slice.map(p=>p.qExist+p.qZero+p.qNo), 1)+0.5;
    buildChart('c2', {
      type:'bar',
      data:{labels,datasets:[
        {label:'现存问题',data:slice.map(p=>p.qExist),backgroundColor:TC_EXIST,borderRadius:3},
        {label:'已归零',  data:slice.map(p=>p.qZero), backgroundColor:TC_ZERO, borderRadius:3},
        {label:'未提交',  data:slice.map(p=>p.qNo),   backgroundColor:TC_NOSUB,borderRadius:3}
      ]},
      options:{responsive:true,maintainAspectRatio:false,
        plugins:{legend:{display:false},tooltip:TT},
        scales:{x:{...GX,grid:{display:false}},y:{...GY,min:0,max:maxQ,ticks:{stepSize:.5}}}}
    });
  }
  if(n===3) {
    const maxT = Math.max(...slice.map(p=>p.techFixed+p.techNot+p.techUnsign), 3)+1;
    buildChart('c3', {
      type:'bar',
      data:{labels,datasets:[
        {label:'已整改',data:slice.map(p=>p.techFixed), backgroundColor:TC_FIXED, borderRadius:3},
        {label:'未整改',data:slice.map(p=>p.techNot),   backgroundColor:TC_NOTFIX,borderRadius:3},
        {label:'未签署',data:slice.map(p=>p.techUnsign),backgroundColor:TC_UNSIGN,borderRadius:3}
      ]},
      options:{responsive:true,maintainAspectRatio:false,
        plugins:{legend:{display:false},tooltip:TT},
        scales:{x:{...GX,grid:{display:false}},y:{...GY,min:0,max:maxT}}}
    });
  }
  if(n===4) {
    const maxR = Math.max(...slice.map(p=>p.riskH+p.riskM+p.riskL), 6)+1;
    buildChart('c4', {
      type:'bar',
      data:{labels,datasets:[
        {label:'高风险',data:slice.map(p=>p.riskH),backgroundColor:TC_RH,borderRadius:3,stack:'s'},
        {label:'中风险',data:slice.map(p=>p.riskM),backgroundColor:TC_RM,stack:'s'},
        {label:'低风险',data:slice.map(p=>p.riskL),backgroundColor:TC_RL,borderRadius:3,stack:'s'}
      ]},
      options:{responsive:true,maintainAspectRatio:false,
        plugins:{legend:{display:false},tooltip:TT},
        scales:{x:{...GX,grid:{display:false},stacked:true},
          y:{...GY,stacked:true,min:0,max:maxR}}}
    });
  }
}

function buildDeptCharts() {
  /* C5: progress dept TOP5 by total tasks (highest task count) */
  const d5 = top5Depts_progress();
  buildChart('c5', {
    data:{
      labels: d5.map(d=>d.name),
      datasets:[
        {type:'bar',label:'已完成',data:d5.map(d=>d.done), backgroundColor:TC_ZERO,   borderRadius:3,order:2},
        {type:'bar',label:'未完成',data:d5.map(d=>d.undone),backgroundColor:TC_NORMAL, borderRadius:3,order:2},
        {type:'line',label:'完成率',data:d5.map(d=>d.rate),yAxisID:'y2',
          borderColor:'#2ab8e8',backgroundColor:'rgba(42,184,232,.06)',
          pointBackgroundColor:'#2ab8e8',pointRadius:3.5,tension:.35,
          borderWidth:1.8,fill:false,order:1}
      ]
    },
    options:{responsive:true,maintainAspectRatio:false,
      plugins:{legend:{display:false},tooltip:TT},
      scales:{
        x:{...GX,grid:{display:false}},
        y:{...GY,min:0,max:Math.max(...d5.map(d=>d.done+d.undone))+2},
        y2:{position:'right',grid:{display:false},min:0,max:100,
          ticks:{color:'#5dd0f0',callback:v=>v+'%',font:{size:9}}}
      }}
  });

  /* C6: quality dept TOP5 (horizontal, sorted by qExist desc) */
  const d6 = top5Depts_quality();
  buildChart('c6', {
    type:'bar',
    data:{
      labels:d6.map(d=>d.name),
      datasets:[
        {label:'现存问题',data:d6.map(d=>d.qExist),backgroundColor:TC_EXIST,borderRadius:3},
        {label:'已归零',  data:d6.map(d=>d.qZero), backgroundColor:TC_ZERO, borderRadius:3}
      ]
    },
    options:{responsive:true,maintainAspectRatio:false,indexAxis:'y',
      plugins:{legend:{display:false},tooltip:TT},
      scales:{x:{...GX,min:0},y:{...GY,grid:{display:false}}}}
  });

  /* C7: tech dept TOP5 (horizontal, sorted by techNot+techUnsign desc) */
  const d7 = top5Depts_tech();
  buildChart('c7', {
    type:'bar',
    data:{
      labels:d7.map(d=>d.name),
      datasets:[
        {label:'未整改',data:d7.map(d=>d.techNot),   backgroundColor:TC_NOTFIX,borderRadius:3},
        {label:'未签署',data:d7.map(d=>d.techUnsign),backgroundColor:TC_UNSIGN,borderRadius:3}
      ]
    },
    options:{responsive:true,maintainAspectRatio:false,indexAxis:'y',
      plugins:{legend:{display:false},tooltip:TT},
      scales:{x:{...GX,min:0},y:{...GY,grid:{display:false}}}}
  });

  /* C8: risk dept TOP5 (horizontal stacked, sorted by total risk desc) */
  const d8 = top5Depts_risk();
  buildChart('c8', {
    type:'bar',
    data:{
      labels:d8.map(d=>d.name),
      datasets:[
        {label:'高风险',data:d8.map(d=>d.riskH),backgroundColor:TC_RH,  borderRadius:2,stack:'s'},
        {label:'中风险',data:d8.map(d=>d.riskM),backgroundColor:TC_RM,  stack:'s'},
        {label:'低风险',data:d8.map(d=>d.riskL),backgroundColor:TC_RL,  borderRadius:2,stack:'s'}
      ]
    },
    options:{responsive:true,maintainAspectRatio:false,indexAxis:'y',
      plugins:{legend:{display:false},tooltip:TT},
      scales:{x:{...GX,stacked:true,min:0},y:{...GY,stacked:true,grid:{display:false}}}}
  });
}

/* Init */
[1,2,3,4].forEach(n => refreshChart(n));
buildDeptCharts();

/* ═══════════════════════════════
   GANTT
═══════════════════════════════ */
const GPAGE=2, GTOTAL=Math.ceil(GANTT_PROJECTS.length/GPAGE);
let gPage=0, gPaused=false;

function rpill(r,dl){
  if(dl) return`<span class="rpill rp-bad">${r}%</span>`;
  return r>=50?`<span class="rpill rp-ok">${r}%</span>`:`<span class="rpill rp-warn">${r}%</span>`;
}
function renderGantt(){
  const rows=GANTT_PROJECTS.slice(gPage*GPAGE,gPage*GPAGE+GPAGE);
  let html=`<table class="gt"><thead><tr>
    <th style="width:118px">重大项目</th>
    <th style="width:112px">责任科室/经理</th>
    <th style="width:58px">完成率</th>
    <th style="width:76px">交付日期</th>
    <th>进度甘特</th>
  </tr></thead><tbody>`;

  rows.forEach(p=>{
    const gi=GANTT_PROJECTS.indexOf(p);
    const bc=p.delay?'b-delay':'b-ok', bl=p.delay?'延期':'正常';
    const fw=Math.round((p.barW-4)*p.rate/100);
    html+=`<tr class="gr" onclick="om(${gi})">
      <td><div class="pj-id">${p.id}</div><div class="pj-sub">${p.sub}</div></td>
      <td><div class="dept-t">${p.dept}</div></td>
      <td>${rpill(p.rate,p.delay)}</td>
      <td><span class="date-t">${p.date}</span></td>
      <td style="padding-right:8px">
        <div class="btrack">
          <div class="bplan" style="left:${p.planL}px;width:${p.planW}px"></div>
          <div class="bfill ${bc}" style="left:${p.barL}px;width:${fw}px"><span>${bl}</span></div>
          <div class="today-ln" style="left:${p.barL+p.todayOff}px"></div>
        </div>
      </td>
    </tr>`;
  });
  html+='</tbody></table>';
  document.getElementById('gbody').innerHTML=html;

  const de=document.getElementById('cdots'); de.innerHTML='';
  for(let i=0;i<GTOTAL;i++){
    const d=document.createElement('div');
    d.className='cdot'+(i===gPage?' on':'');
    const ii=i; d.onclick=()=>{gPage=ii;renderGantt()};
    de.appendChild(d);
  }
}
let gtimer=setInterval(()=>{if(!gPaused){gPage=(gPage+1)%GTOTAL;renderGantt();}},4500);
function tpause(){gPaused=!gPaused;document.getElementById('pbtn').textContent=gPaused?'▶':'⏸'}
function vt(b){document.querySelectorAll('#vtabs button').forEach(x=>x.classList.remove('act'));b.classList.add('act')}
const gc=document.getElementById('gcrd');
gc.addEventListener('mouseenter',()=>{gPaused=true;document.getElementById('pbtn').textContent='▶'});
gc.addEventListener('mouseleave',()=>{gPaused=false;document.getElementById('pbtn').textContent='⏸'});
renderGantt();

/* ═══════════════════════════════
   MODAL
═══════════════════════════════ */
const TLC={d:'td',w:'tw',l:'tl',n:'tn'}, TLL={d:'已完成',w:'进行中',l:'延期',n:'未开始'};
function om(idx){
  const p=GANTT_PROJECTS[idx];
  document.getElementById('mid').textContent=p.id;
  document.getElementById('msub').textContent=p.sub;
  document.getElementById('mdept').textContent=p.dept;
  document.getElementById('mdate').textContent=p.date;
  document.getElementById('mrate').textContent=p.rate+'%';
  document.getElementById('mstat').innerHTML=`<span class="stag ${p.delay?'sdl':'sok'}">${p.delay?'延期':'正常'}</span>`;
  document.getElementById('mrfill').style.width=p.rate+'%';
  document.getElementById('mtasks').innerHTML=p.tasks.map(t=>
    `<div class="trow"><span class="tname">${t.n}</span><span class="tt ${TLC[t.s]}">${TLL[t.s]}</span></div>`
  ).join('');
  document.getElementById('mmask').classList.add('open');
}
function mc(){document.getElementById('mmask').classList.remove('open')}
function mout(e){if(e.target===document.getElementById('mmask'))mc()}
document.addEventListener('keydown',e=>{if(e.key==='Escape')mc()});
</script>
</body>
</html>