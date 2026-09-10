const {chromium}=require('/opt/prod/s10/deploy/source/dts-platform-webapp/node_modules/@playwright/test');
const fs=require('node:fs');
(async()=>{
 const browser=await chromium.launch({executablePath:'/opt/google/chrome/chrome',headless:true,args:['--no-sandbox']});
 const page=await browser.newPage({viewport:{width:1366,height:768}});
 const errors=[],calls=[]; page.on('pageerror',e=>errors.push(e.message));
 await page.addInitScript(()=>{
 localStorage.setItem('dts.platform.userStore',JSON.stringify({state:{userInfo:{username:'f8-fixture',fullName:'质量验收',roles:['ROLE_OP_ADMIN'],permissions:['read','write'],enabled:true},userToken:{authenticated:true,accessToken:'fixture'}},version:0}));
 localStorage.setItem('dts.platform.session.loginTs',String(Date.now()));localStorage.setItem('dts.platform.session.lastActivity',String(Date.now()));
 });
 await page.route('**/api/**',async route=>{
 const p=new URL(route.request().url()).pathname;let data=[];
 if(p==='/api/session/status') data={authenticated:true,remainingSeconds:3600};
 if(p==='/api/ingestion/default-destination') data={available:true,dataSourceId:'source',destinationName:'验收数据湖'};
 if(p==='/api/governance/quality/datasets') data=[{id:'11111111-1111-1111-1111-111111111111',name:'项目检查',schemaName:'public',tableName:'projects',sourceId:'source'}];
 if(p.endsWith('/validate-sql')||p.endsWith('/dry-run')) {
 const body=route.request().postDataJSON();calls.push({path:p,body});const bad=body.definition.sql.includes('pg_sleep');
 data=p.endsWith('/validate-sql')?{valid:!bad,checksum:'current',diagnostics:bad?[{statementKey:'sql',reasonCode:'UNSUPPORTED_FUNCTION',detail:'pg_sleep'}]:[],allowedFunctions:'count, coalesce'}:{checksum:'current',outcome:{schemaVersion:1,qualityOutcome:'VIOLATION',executionOutcome:'OK',statisticsStatus:'EXACT',violationOccurrences:2,diagnostics:[]},rowsTotal:10,failingRowCount:2};
 }
 await route.fulfill({status:200,contentType:'application/json',body:JSON.stringify({status:200,data,message:'OK'})});
 });
 await page.goto('http://127.0.0.1:4188/#/governance/rules/catalog/new?datasetId=11111111-1111-1111-1111-111111111111');
 await page.getByRole('button',{name:'校验 SQL',exact:true}).waitFor({timeout:25000});
 const sql=page.locator('#sql');
 await sql.fill('SELECT pg_sleep(1) FROM public.projects');
 await page.getByRole('button',{name:'校验 SQL',exact:true}).click();
 await page.getByText('函数不受支持，请使用允许的函数（pg_sleep）').waitFor();
 await sql.fill('SELECT project_code FROM public.projects WHERE project_code IS NULL');
 await page.getByRole('button',{name:'试跑当前 SQL',exact:true}).click();
 await page.getByText('试跑：发现违规；执行完成').waitFor();
 await page.getByRole('button',{name:'试跑当前 SQL',exact:true}).scrollIntoViewIfNeeded();
 await page.screenshot({path:'/tmp/f8-desktop.png'});
 await page.setViewportSize({width:390,height:844});
 await page.getByRole('button',{name:'试跑当前 SQL',exact:true}).scrollIntoViewIfNeeded();
 await page.screenshot({path:'/tmp/f8-narrow.png'});
 const bounds=await page.getByRole('button',{name:'试跑当前 SQL',exact:true}).boundingBox();
 const result={browser:browser.version(),calls,errors,narrowButton:bounds};
 fs.writeFileSync('/tmp/f8-browser-result.json',JSON.stringify(result,null,2));
 if(errors.length||!bounds||bounds.x<0||bounds.x+bounds.width>390)throw Error(JSON.stringify(result));
 console.log(JSON.stringify(result));await browser.close();
})().catch(e=>{console.error(e);process.exit(1)});
