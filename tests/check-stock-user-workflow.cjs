const {spawn}=require('node:child_process')
const {mkdtemp,readFile}=require('node:fs/promises')
const {tmpdir}=require('node:os')
const {join}=require('node:path')
const assert=require('node:assert/strict')
async function until(fn, timeout=15000){
 const limit=Date.now()+timeout
 while(Date.now()<limit){
  const result=await Promise.resolve().then(fn).catch(()=>null)
  if(result)return result
  await new Promise(r=>setTimeout(r,150))
 }
 throw Error('Timed out waiting for browser check')
}
async function main(){
 const profile=await mkdtemp(join(tmpdir(),'aistock-home-edge-'))
 const edge=spawn('C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',['--headless=new','--no-first-run','--no-default-browser-check','--remote-debugging-port=0','--user-data-dir='+profile,'about:blank'],{windowsHide:true,stdio:'ignore'})
 let socket
 try{
  const port=await until(async()=>(await readFile(join(profile,'DevToolsActivePort'),'utf8')).split(/\r?\n/)[0])
  const targets=await (await fetch('http://127.0.0.1:'+port+'/json/list')).json()
  socket=new WebSocket(targets.find(target=>target.type==='page').webSocketDebuggerUrl)
  await new Promise((resolve,reject)=>{socket.onopen=resolve;socket.onerror=reject})
  let serial=0
  const pending=new Map()
  socket.onmessage=event=>{
   const data=JSON.parse(event.data)
   if(!pending.has(data.id))return
   const {resolve,reject}=pending.get(data.id)
   pending.delete(data.id)
   if(data.error)reject(Error(data.error.message))
   else resolve(data.result)
  }
  function call(method,params={}){
   return new Promise((resolve,reject)=>{
    const id=++serial
    pending.set(id,{resolve,reject})
    socket.send(JSON.stringify({id,method,params}))
   })
  }
  async function run(expression){
   const response=await call('Runtime.evaluate',{expression,returnByValue:true,awaitPromise:true})
   if(response.exceptionDetails)throw Error(response.exceptionDetails.text)
   return response.result.value
  }
  await call('Page.enable')
  await call('Page.addScriptToEvaluateOnNewDocument',{source:'('+function(){
   const request=window.fetch.bind(window)
   window.checkMutations=0
   window.analysisReads=0
   window.fetch=(url,options)=>{
    const path=new URL(url,location.href).pathname
    if(!path.startsWith('/api/v1/'))return request(url,options)
    if(options?.method!=='GET')window.checkMutations++
    const instrumentId='SH600030',versionId='version-current',date='2026-10-23'
    const range={from:'2026-08-01',to:date}
    const common={instrumentId,versionId,provider:'Baostock',lastTradeDate:date,priceBasis:'RAW',state:'AVAILABLE',freshness:'STALE',lastUpdateError:null}
    const empty={configured:true,latestAttempt:null,items:[],page:1,size:20,total:0,unreadCount:0}
    let data=path.endsWith('/instruments')?{...empty,items:[{instrumentId,ticker:instrumentId,displayName:'中信证券',displayNameVerified:true,listingStatus:'ACTIVE',summary:common}]}:empty
    if(path.endsWith('/models'))data={items:[{modelId:'fixture-model',artifactId:'Transformer 示例',window:60,horizon:5,instruments:[instrumentId],recommended:true}]}
    if(path.endsWith('/users/me'))data={userId:'browser-fixture',nickname:'测试用户',role:'USER'}
    if(path.endsWith('/instruments/'+instrumentId))data={instrumentId,displayName:'中信证券',displayNameVerified:true,listingStatus:'ACTIVE',summary:common}
    if(path.endsWith('/daily'))data={...common,formulaVersion:'tech-v1',requestedRange:range,actualRange:range,checkedThrough:date,missingTradingDates:['2026-10-22'],explanation:'样本行情',bars:[{date,open:'20',high:'21',low:'19',close:'20',tradeStatus:1,volumeShares:'1000',amountYuan:null,ma5:'20',ma10:null,ma20:null,dif:null,dea:null,macd:null,rsi14:null}]}
    if(path.endsWith('/stock-analyses')){
     window.analysisReads++
     data={...empty,total:1,items:[{analysisId:'fixture-analysis',createdAt:'2026-10-23T06:00:00Z',context:{...range,instrumentId,marketVersionId:'version-original',provider:'Baostock',priceBasis:'RAW',formulaVersion:'tech-v1',promptVersion:'test',providerModel:'test',facts:{AS_OF:'2026-10-20'}},paragraphs:[{kind:'overview',text:'已保存的分析段落',refs:['AS_OF']}]}]}
    }
    return Promise.resolve(new Response(JSON.stringify({code:'OK',data}),{headers:{'Content-Type':'application/json'}}))
   }
  }+')()'})
  await call('Emulation.setDeviceMetricsOverride',{width:375,height:900,deviceScaleFactor:1,mobile:false})
  await call('Page.navigate',{url:'http://127.0.0.1:15173/?q='+encodeURIComponent('中信')})
  await until(()=>run('[...document.querySelectorAll("a")].some(a=>a.textContent.trim()==="查看分析")'))
  const click=label=>run('[...document.querySelectorAll("a")].find(a=>a.textContent.trim()==='+JSON.stringify(label)+').click()')
  async function keyPress(key, windowsVirtualKeyCode){
   await call('Input.dispatchKeyEvent',{type:'rawKeyDown',key,code:key,windowsVirtualKeyCode})
   await call('Input.dispatchKeyEvent',{type:'keyUp',key,code:key,windowsVirtualKeyCode})
  }
  async function checkNarrowPage(){
   assert.equal(await run('innerWidth'),375)
   assert(await run('document.documentElement.scrollWidth<=innerWidth+1'),'Page overflows the narrow viewport')
  }
  await checkNarrowPage()
  await run('document.getElementById("stock-query").focus()')
  await keyPress('ArrowDown',40)
  await until(()=>run('(()=>{const selected=document.querySelector("#stock-query")?.getAttribute("aria-activedescendant"); return Boolean(selected&&document.getElementById(selected)?.textContent.includes("中信证券"))})()'))
  await keyPress('Enter',13)
  await until(()=>run('location.pathname==="/market/SH600030"&&Boolean(document.querySelector(".saved-result"))'))
  await click('设置预警')
  await until(()=>run('location.pathname==="/alerts"&&document.querySelector("input[aria-label=股票代码]")?.value==="SH600030"'))
  await click('← 返回行情分析')
  await until(()=>run('location.pathname==="/market/SH600030"&&Boolean(document.querySelector(".market-chart canvas"))'))
  await click('发起预测')
  await until(()=>run('location.pathname.endsWith("/predict")&&Boolean(document.querySelector(".market-check"))'))
  assert.match(await run('document.querySelector("main h1").textContent'),/SH600030/)
  await checkNarrowPage()
  assert.equal(await run('window.checkMutations'),0)
  await click('← 返回行情分析')
  await until(()=>run('location.pathname==="/market/SH600030"&&Boolean(document.querySelector(".market-chart canvas"))'))
  await click('← 返回行情')
  await until(()=>run('location.pathname==="/"&&document.querySelector("#stock-query")?.value==="中信"'))
  assert.equal(await run('window.checkMutations'),0)
  console.log('Edge fixture: 375px layout, keyboard search, prefilled alerts, prediction preparation, and search restoration passed without submitting a request.')
 } finally {
  socket?.close()
  edge.kill()
 }
}
main().catch(error=>{console.error(error);process.exitCode=1})
