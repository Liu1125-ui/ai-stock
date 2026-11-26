# SDD-010B1 管理员行情重取 Implementation Plan

> 当前进度2026-10-25：Task1/2及部署审查重要问题修复已完成。用户另行要求提前部署这两步，现仅market已更新；Task3—7尚未实现，Task7不据此勾选完成。内部错误响应no-store minor与其他原待办继续保留。下方编写计划时状态为历史。

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. 沿用当前对话逐项实施方式；本文件待用户审核后执行，末尾一次独立代码审查。

**Goal:** 为管理员交付已有Baostock RAW行情的可追踪手动重取，失败保留旧数据，新建和重试共享并发保护。

**Architecture:** MS-04原子保存管理员操作、任务和冻结上下文，MS-02提供只读预检并沿现有刷新发布链执行。网关按明确路径区分行情查询和调度命令，前端只在明确操作时提交，并能用原编号恢复回执。跨服务预检不构成分布式事务，执行前及发布时再次检查头版本。

**Tech Stack:** Java17、Spring Boot3.2.5、JdbcTemplate、MySQL8、JUnit5；Vue3/TypeScript/Pinia/Vitest。沿用现有依赖，不新增服务或数据源。

**Spec:** E:/aiStock/项目代码/stock-system/docs/superpowers/specs/2026-10-25-sdd010b1-admin-market-refresh-design.md

日期2026-10-25。计划编写基线d85172f；本轮只有设计/计划文档，没有代码实现、测试成绩或部署。七步按依赖顺序进行，不改任务书日期，不把预计工作量当完成时间承诺。

## Global Constraints

- 13项业务、五业务服务、ADMIN/USER两角色不变。仅已有真实Baostock RAW头可重取；无头初始化、更早历史扩展和010B2停用依赖保护继续待办，FR-11不关闭。
- 统一{code,message,data,traceId}、no-store；权威ADMIN校验；不跨库读写/联表/外键；不输出Token、SMTP、MaaS、原始SQL/供应商正文。
- 日期按Asia/Shanghai，20时前截止昨日、之后今日；目标不得早于coverageEnd，跨度不超过1500日；人工准入冷却60秒；操作历史size默认20/max50、游标max768字符；正文max4096字节；写事务5秒。
- operationId/baseVersionId规范小写UUID；revision是0至Long.MAX_VALUE的非负十进制字符串；UTC毫秒时间。冻结上下文创建后不修改；旧快照、预测、解释和持仓分析不重写。
- 新入口默认关闭，不改变原worker/周期计划开关。开发和隔离测试不访问供应商、不调用付费AI、不发邮件。真实验收单独限定股票、日期、数据源请求及重试预算。
- 只用有专用标签的隔离MySQL与_test库，不用生产口令/数据卷跑清理。每任务先观察RED再实现GREEN，小步选择性提交；无remote不push，不捏造CodeArts来源。
- 所有任务开始前先核对git工作区；任何MySQL测试前先核对专用容器标签及实际_test连接，不能等到Task7才做环境检查。若工具沙箱不能进入E:/aiStock，使用正式审批访问，禁止退回错误工程执行。
- 当前工程E:/aiStock/项目代码/stock-system，沿既有分支；开始编码前核对工作区/隔离策略，不能自动套用E:/TianMao规则。新建源码目录附README，说明作用与实现方法。

## Review Focus

1. 升级RefreshRequest使旧toString哈希变化，导致已有回执无法恢复：Task2固定旧字节与旧receipt fixture。
2. 旧FAILED人工任务在另一任务已排队后重试绕过守卫：Task4覆盖公开/内部两条入口与并发竞争。
3. 保留前缀任务context丢失而被当普通任务采集：Task2/3证明fail-closed及供应商零调用。
4. 同编号请求跨股票/管理员竞争、事务死锁或HTTP丢响应：Task3/5验证唯一键、回滚和原编号恢复，不换编号重发。
5. 页面换账号/股票时旧响应回填或刷新造成自动POST：Task6拒绝迟到响应，隔离sessionStorage pending并保持GET只读。

## 文件结构和约定

以下缩写仅用于定位文件，都是工程下真实模块：
- U=backend/stock-common/src/main/java/com/aistock/common，UT为同模块src/test/java/com/aistock/common。
- M=backend/stock-market-service/src/main/java/com/aistock/market，MT为同模块测试根。
- S=backend/stock-scheduler-service/src/main/java/com/aistock/scheduler，ST为同模块测试根。
- G=backend/stock-gateway/src/main/java/com/aistock/gateway，GT为同模块测试根。
- F=frontend/src。新增目录为U/adminrefresh、S/adminrefresh及对应测试目录；行情内部预检沿M/refresh，网关沿G/adminmarket，页面沿F/pages/admin/data。每个新增目录及测试目录都附README。
- DTO字段以规格第5、12—13节为唯一字段清单，下列类型统一在AdminMarketRefreshRecords定义，禁止在各任务另造同义字段。接口正文解析后保留规范值，服务端原子层只接收验证后的记录。

### Task 1：契约、参数和纯策略

**Files:** Create U/adminrefresh/{AdminMarketRefreshRecords.java,AdminMarketRefreshInput.java,AdminMarketRefreshPolicy.java,README.md}；Test UT/adminrefresh/{AdminMarketRefreshInputTest.java,AdminMarketRefreshPolicyTest.java,README.md}。

**Interfaces:** Records包含Command(operationId:UUID,baseVersionId:UUID,expectedHeadRevision:String,targetDate:LocalDate,reasonCode:String)、Constraint(baseVersionId:String,headRevision:long,coverageStart:LocalDate,policyVersion:String)、Context（规格内部context全部字段）、Options（规格公开options全部字段）、Receipt（规格12.2全部持久字段）、TaskSnapshot、OperationView(queriedAt,receipt,task)、HistoryPage、HistoryQuery(cursor:String,size:int)、Decision(allowed:boolean,reasonCode:String,cooldownUntil:Instant)。Input.parse(byte[]):Command、instrument(String):String、history(MultiValueMap<String,String>):HistoryQuery、writePath(String):boolean与readPath(String):boolean做精确路径匹配。Policy.window(Context,Clock):DateWindow(min,max,defaultTarget)；Policy.admit(Context,LocalDate,Instant lastAdmittedAt,Clock):Decision。入口/worker布尔配置在服务层组合，不让纯策略访问HTTP或DB。

- [x] **Step1 RED：** 新建测试，断言4096字节合法且4097拒绝；未知/重复JSON键、指数修订、+1、01、超Long、非真实日期及非规范UUID拒绝。固定上海2026-10-25 19:59截止24日、20:00截止25日；start.plusDays(1500)合法、1501拒绝；coverageEnd超过上界禁止；最近准入59秒禁止、60秒允许；重复/未知查询参数和size51拒绝。
- [x] **Step2 运行：** backend执行 `mvn -B -Pcli-build -pl stock-common -am test '-Dtest=AdminMarketRefreshInputTest,AdminMarketRefreshPolicyTest' '-Dsurefire.failIfNoSpecifiedTests=false'`，观察缺类型或断言失败。PowerShell含逗号参数整体引号。
- [x] **Step3 GREEN：** 实现上述类型/纯校验，policyVersion=admin-market-refresh-v1、固定原因RECHECK_DATA/RECOVER_UPDATE及集中配置默认值；record仅表达事实，不把QUEUED回执解释成任务执行完成。内部Constraint字段完整才能有效。
- [x] **Step4 验证：** 同命令PASS，附查询路径反例：任意后缀不能命中新写代理，readPath与writePath互不错误吞路由。
- [x] **Step5 提交：** 仅本任务文件，`feat: 定义管理员行情重取契约与策略`。

### Task 2：行情预检、冻结约束和旧回执兼容

**Files:** Create M/refresh/{AdminRefreshContextRepository.java,AdminRefreshContextController.java,RefreshRequestHasher.java}；Modify M/refresh/{RefreshRequest.java,RefreshService.java,RefreshRepository.java,RefreshController.java,README.md}及M/ingestion/CandidateRequestFilter.java（现有内部服务凭证保护，不放宽/public或用户路径）；Test MT/refresh/{AdminRefreshContextMysqlTest.java,AdminRefreshConstraintTest.java,RefreshRequestCompatibilityTest.java}，现有Refresh测试按受影响范围修改。

**Interfaces:** AdminRefreshContextRepository.find(String instrumentId,Instant queriedAt):Context，本库只读；内部GET /internal/v1/market/admin-refresh-context/{instrumentId}返回ApiResponse<Context>。RefreshRequest增加Constraint adminConstraint并保留四参构造器兼容调用；RefreshRequestHasher.hash(RefreshRequest):String。RefreshService.refresh签名保持，adminConstraint不为空时检查原头版本/修订/起点，失败通过原receipt可查询。

- [x] **Step1 RED：** 预检fixture覆盖demo、无头、非RAW、错归属、损坏日期、非AVAILABLE；读前后业务表与checked_at相同，collector零调用。冻结版本/修订变化不采集、有FAILED回执；格式不完整Constraint在反序列化阶段拒绝且不创建回执，调度派发前先验证context；无约束自动刷新旧行为保持。
- [x] **Step2 RED兼容：** 固定原四字段RefreshRequest，测试原toString字节与SHA256值一致；先插入按旧哈希创建的RUNNING/终态receipt，再用升级后无约束请求恢复应不冲突。带约束请求改变任一约束、target或lease则同编号冲突。旧请求需写死fixture值，不能调用新Hasher自己生成期望值。
- [x] **Step3 运行：** 经隔离DB核对后backend执行 `mvn -B -Pauth-integration,cli-build -pl stock-market-service -am test '-Dtest=AdminRefreshContextMysqlTest,AdminRefreshConstraintTest,RefreshRequestCompatibilityTest' '-Dsurefire.failIfNoSpecifiedTests=false'`，确认失败原因是目标行为未实现。
- [x] **Step4 GREEN：** 先begin原刷新receipt再检查约束；无约束保持旧四字段哈希格式，带约束用规范v2哈希。采集前检查头，发布仍用原expectedRevision；不改MarketVersionRepository的内容去重和修订事件事务。新错误ADMIN_REFRESH_CONTEXT_CHANGED/INVALID不可加入可恢复白名单；安全错误文案不含原始供应商响应。
- [x] **Step5 验证并提交：** 同专项及完整market回归PASS，旧接入/自动刷新/预测输入测试不回归；提交`feat: 为行情重取增加预检与冻结约束`。本步没有公开管理员写入口。

### Task 3：调度存储、原子登记和只读历史

**Files:** Create backend/stock-scheduler-service/src/main/resources/db/migration/V10__admin_market_refresh.sql（实施前复查序号）；Create S/adminrefresh/{AdminMarketRefreshRepository.java,AdminMarketRefreshService.java,AdminMarketRefreshCursor.java,MarketRefreshContextClient.java,README.md}；Modify S/task/{TaskRepository.java,MarketRefreshClient.java,TaskWorker.java,README.md}；Test ST/adminrefresh/{AdminMarketRefreshMysqlTest.java,AdminMarketRefreshCursorTest.java,AdminMarketRefreshServiceTest.java,README.md}及ST/task/TaskWorker相关现有测试。

**Interfaces:** MarketRefreshContextClient.context(String instrumentId,String serviceToken):ApiResponse<Context>使用现有Feign服务发现、禁自动重试。Service.options(String,Instant):Options；submit(UUID actor,String instrument,Command,Instant):Submission；receipt(UUID operation,Instant):OperationView；history(String instrument,HistoryQuery,Instant):HistoryPage。Submission(status:int,code:String,receipt:Receipt)追加到Task1 Records。Repository.submit(UUID,String,Command,Context,Instant):Submission（不做HTTP），receipt/history只读。TaskRepository.createAdminRefresh(...)在调用方同一调度库事务下写task+context；实现可委托同DataSource事务模板，禁止REQUIRES_NEW及分离提交。TaskWorker识别保留前缀并取context，向Task2 Request带Constraint；旧任务无约束。

- [x] **Step1 RED：** MySQL fixture证明一次提交写一operation/task/context/guard；任一步故障四者全部回滚；同编号同正文重放不新增；异actor/异股票/异正文409。双线程同股票同约束最多一新建并一ATTACHED，不同约束拒绝；cooldown关闭入口/worker关闭保存正确拒绝，成功旧回执仍能重放。
- [x] **Step2 RED边界：** 通用TaskRepository.create禁止使用admin-market-refresh-前缀；worker看到该前缀但缺context或task/context不符，记录ADMIN_REFRESH_CONTEXT_INVALID且不调用market。未知状态或残留UNKNOWN尝试不能释放guard。游标同毫秒多ID、size绑定、错误股票、768以上/重复字段拒绝；第一页upper排除新增历史，旧下页不重复。
- [x] **Step3 运行：** backend执行 `mvn -B -Pauth-integration,cli-build -pl stock-scheduler-service -am test '-Dtest=AdminMarketRefreshMysqlTest,AdminMarketRefreshCursorTest,AdminMarketRefreshServiceTest' '-Dsurefire.failIfNoSpecifiedTests=false'`，记录失败断言；新增worker反例用实际测试类名运行。
- [x] **Step4 GREEN：** 建三表及索引，事务guard→operation→task，锁内重查终态与冷却；预检在事务外，SQL内不做HTTP。service对同编号先查已提交匹配回执再预检，事务内仍重新确认。实现冻结上下文、回执与当前task分离、拒绝持久化规则及安全503恢复；历史只读RR快照，不触发receipt.lookup或worker。
- [x] **Step5 验证并提交：** 专项和现有调度回归PASS；原自动任务键/每日计划、比较预算、RECONCILING不改。记录迁移全新库与V9→V10升级结果；提交`feat: 原子登记管理员行情重取任务`。

### Task 4：统一人工重试守卫，防止旧任务绕过

**Files:** Create S/adminrefresh/AdminMarketRefreshAdmission.java；Modify S/admin/{AdminTaskActionRepository.java,AdminTaskActionPolicy.java}、S/task/TaskRepository.java、S/adminrefresh/AdminMarketRefreshRepository.java；Test ST/adminrefresh/AdminMarketRefreshRetryMysqlTest.java，原ST/admin任务操作测试按需要追加。

**Interfaces:** Admission.lock(String instrumentId):GuardSnapshot；retryWithinTransaction(String taskId,Instant now):Decision；onRetryApplied(String taskId,Instant now):void；这些方法只允许在与AdminTaskActionRepository/TaskRepository相同DataSource的现存事务中调用。GuardSnapshot(activeTaskId,lastAdmittedAt)仅内部类型。管理员来源由保留前缀+不可变context确定；公共与内部retry均经过同一Admission。options返回相同守卫条件提示，不替代提交复核。

- [x] **Step1 RED：** A失败→B排队→经公共RETRY和内部retry(A)均拒绝，guard继续B；B终结且无在途证据、冷却满足后允许A；重试和新建双线程仅一准入；成功重试同事务更新时间/guard/task和原任务操作回执，写回执失败时全部回滚。
- [x] **Step2 运行：** backend执行 `mvn -B -Pauth-integration,cli-build -pl stock-scheduler-service -am test '-Dtest=AdminMarketRefreshRetryMysqlTest' '-Dsurefire.failIfNoSpecifiedTests=false'`，观察旧路径绕过守卫导致失败。
- [x] **Step3 GREEN：** AdminTaskActionRepository在创建操作记录之前，为管理员来源RETRY先锁guard，再按稳定taskId顺序锁需核对task；不得先锁task再guard。原任务revision/白名单/冷却和股票60秒冷却同时生效；RECONCILING/未知/残留租约拒绝，冻结内容不改。普通任务无context保留旧逻辑；cancel无需释放guard或反向加guard锁。
- [x] **Step4 验证：** 上述测试和SDD009B完整测试PASS；验证重放/ATTACHED/拒绝不延长冷却，自动退避保持占用，context变化错误不可重试。options与POST条件一致但仍以POST锁内复查为准。
- [x] **Step5 提交：** `fix: 防止历史行情任务重试绕过并发保护`。

### Task 5：安全HTTP接口和网关明确分流

**Files:** Create S/adminrefresh/{AdminMarketRefreshController.java,AdminMarketRefreshAccessFilter.java,AdminMarketRefreshSecurityConfiguration.java}；Modify S/security/README.md及内部Feign配置（仅必要Bean）；Create G/adminmarket/{AdminMarketRefreshProxyController.java,AdminMarketRefreshCorsConfiguration.java}；Modify G/adminmarket/{AdminMarketDataProxyController.java,README.md}、G/UserApiProxyController.java及G/statistics/HttpStatisticsFilter.java；Test ST/adminrefresh/AdminMarketRefreshHttpTest.java、GT/adminmarket/{AdminMarketRefreshProxyTest.java,AdminMarketRefreshDiscoveryTest.java}。

**Interfaces:** 四个公开资源按规格：GET股票refresh-options、POST股票refresh-operations、GET股票refresh-operations历史、GET refresh-operations/{operationId}。Controller调用Task3 Service，actor仅由权威会话Filter放入请求属性；网关用现有AdminReadAccess/Origin与CSRF规则，转发目标仅stock-scheduler-service。原三个行情只读资源仍转stock-market-service；调用路径禁止catch-all互吞。

- [x] **Step1 RED：** 匿名401、USER403（直连和网关）、ADMIN读写按开关；伪造X-Role无效；用户服务503不放行；未知路径/方法/重复参数/正文GET拒绝；409已保存拒绝data必须透传，503不伪造回执。OPTIONS no-store且不解析身份/仓库。
- [x] **Step2 RED路由：** 真Feign上下文验证内部context只去market，新四资源只去scheduler；网络错误写POST恰好一次，禁止HTTP/Feign自动重试；原market GET、用户代理和HTTP ADMIN分组不回归。关闭新入口仍可GET原操作，不调供应商。
- [x] **Step3 运行：** backend执行 `mvn -B -Pcli-build -pl stock-scheduler-service,stock-gateway -am test '-Dtest=AdminMarketRefreshHttpTest,AdminMarketRefreshProxyTest,AdminMarketRefreshDiscoveryTest' '-Dsurefire.failIfNoSpecifiedTests=false'`，确定失败后实现；MySQL HTTP fixture需要时加auth-integration配置并核对隔离库。
- [x] **Step4 GREEN：** 严格4096字节解析与Task1统一校验；精确GET/POST/OPTIONS路由及CORS顺序；统一安全错误白名单、no-store、只转256KiB以内响应、不传播内部地址/原文；共享管理员校验不重复解析身份。不公开/internal接口或数据库。
- [x] **Step5 验证并提交：** 三专项、现有gateway/scheduler回归PASS；提交`feat: 提供受保护的管理员行情重取接口`。

### Task 6：管理员页面、操作历史和原请求恢复

**Files:** Create F/api/{adminMarketRefresh.ts,adminMarketRefresh.test.ts}；Create F/pages/admin/data/{MarketRefreshPanel.vue,MarketRefreshHistory.vue,marketRefreshPending.ts,MarketRefreshPanel.test.ts,MarketRefreshHistory.test.ts,marketRefreshPending.test.ts}；Modify F/pages/admin/data/{DataPage.vue,MarketHeadList.vue,MarketVersionPanel.vue,MarketVersionDetail.vue,README.md,DataPage.test.ts}。若原组件只需事件透传，不把整个命令逻辑复制到每个组件。

**Interfaces:** API exports getRefreshOptions(instrumentId):Promise<Options>、submitRefresh(instrumentId,Command):Promise<Receipt>、getRefreshOperation(operationId):Promise<OperationView>、getRefreshHistory(instrumentId,cursor?,size?):Promise<HistoryPage>。409持久拒绝通过有类型的RefreshCommandError.receipt恢复，不丢data。Panel props instrumentId、actorUserId；emits submitted(Receipt)。History props instrumentId、refreshEpoch:number；只在展开或刷新时GET。pending模块savePending(actorId,PendingCommand)、loadPending(actorId)、clearPending(actorId)，只用sessionStorage；PendingCommand含股票、operationId和规范正文，不含会话。

- [x] **Step1 RED：** Vitest断言初始化不POST、不轮询；只有合法可用Baostock RAW显示可提交。重复点击仅一次POST；HTTP丢响应保留原编号，刷新先GET，同编号重发须明确点击；换账号/退出清pending，旧Promise不回填新股票；409显示安全拒绝并保存回执。
- [x] **Step2 RED历史：** 展开才查第一页、更多携带cursor、换股票清空、同条件503保留旧值和查询时间；QUEUED不显示更新成功，ATTACHED链接原任务；成功版本未变显示复用而非虚构新版本；窄屏表格横滚、按钮accessible name及Tab/Enter可用。
- [x] **Step3 运行：** frontend执行 `npm test -- src/api/adminMarketRefresh.test.ts src/pages/admin/data/MarketRefreshPanel.test.ts src/pages/admin/data/MarketRefreshHistory.test.ts src/pages/admin/data/marketRefreshPending.test.ts src/pages/admin/data/DataPage.test.ts`，观察缺组件或行为断言失败。
- [x] **Step4 GREEN：** 完成统一Panel/History，继承现有蓝白样式；历史详情入口明确操作当前头，20时/范围以options为准；请求代数+账号绑定、失败留原数据标签。前端仅提示，不放宽服务端权限和校验。
- [x] **Step5 验证：** 同测试PASS；`npm run typecheck`、`npm run build`通过；完整前端测试检查旧/admin/data、/admin/tasks、身份恢复均不回归。
- [x] **Step6 提交：** `feat: 增加管理员行情重取与操作记录页面`。

### Task 7：完整回归、独立审查、部署和验收记录

**Files:** Create docs/{verification-sdd010b1.md,sdd010b1-review-and-fixes.md}、docs/evidence/sdd010b1-verification.json；Modify docs/{implementation-status.md,pending-items.md}及实际部署模板.example/README（仅新开关及必要参数，绝不提交.env）；日志进入忽略的runtime/sdd010b1。

**Interfaces:** 交付证据分别记录设计/代码/隔离测试/独立审查/部署/真实取数状态，保留FR-11部分完成；JSON不含密钥和用户会话。部署前后校验头/版本/任务/操作引用及旧快照，不改变MaaS或邮件开关。

- [x] **Step1 核对环境：** 执行前检查git状态、实际分支、Docker容器/卷标签及_test库连接；读脚本scripts/start-auth-test-db.ps1并沿已验证方法配置测试环境，凭据只留本地、不输出日志。发现其他工作不覆盖。
- [x] **Step2 完整验证：** backend执行 `mvn -B -Pauth-integration,cli-build clean verify`；frontend依次执行 `npm test`、`npm run typecheck`、`npm run build`。Expected退出0、无失败/错误，记录当前实际测试数，不把旧1012/295当本次成绩。未执行命令的检查标未执行。
- [x] **Step3 独立审查：** 一次fresh reviewer核对规格和实际差异；重要问题先复现RED再修复GREEN，按受影响范围回归，必要时再全量，不反复做无变化全套。minor记录代价及后续处理，不把独立审查未返回当通过。
- [x] **Step4 受控部署：** 先备份scheduler/market与受影响镜像，核对原卷和配置；入口保持false，迁移调度库，再market→scheduler→gateway/frontend。读健康和新/旧契约，不让旧worker处理新上下文，不无故重建其他服务。
- [x] **Step5 只读验收：** admin页面预检/历史/错误与关闭提示、窄屏和键盘；普通角色直连/网关拒绝；读取前后业务表与collector计数不变。供应商0、MaaS0、邮件0；原worker自然活动单独标记。
- [x] **Step6 真实写验收门槛：** 先准备具体股票、目标日、最大供应商请求/重试预算和待执行任务，再取得对应授权。未授权不启用入口/提交任务；没有自然失败不造生产失败。失败不自动追加外部预算，真实验收未做就明确保留待办。
- [x] **Step7 收尾提交：** 更新证据、限制、回退条件，记录实际提交哈希和部署镜像；`test: 记录管理员行情重取核验结果`。仅文档/代码指定文件小步提交，不git add -A，不默认推送。

## 计划自审（2026-10-25）

- 规格1—4目标/边界→Task2/3/6；5—6契约/原子流程→Task1/3/5；7执行与恢复→Task2/3；8/13数据安全→Task3/5；9/15验收→Task7；11重试互锁→Task4；12完整接口/历史/恢复→Task1/3/5/6；14哈希兼容及部署→Task2/7；16后续边界保留。
- 五项Review Focus均有明确所属反例测试；新增Submission类型由Task3补入Task1同一Records文件，后续调用不自造第二套响应；Task2 Constraint和Task3 Feign Request统一引用Task1类型。
- 所有七任务有RED/GREEN、可执行验证与选择性提交；部署/审查本身不伪造RED单测，审查发现行为缺陷才新增回归。
- Task3与Task4共享股票准入规则：Task3首次创建/ATTACHED与Task4重试调用同一Admission；Task4落地时将Task3局部锁逻辑提取，不保留两个实现。并发测试覆盖迁移前后逻辑一致。
- 全部参数默认值来自规格；后续调整集中参数必须同步前后端及测试。没有代码、数据库、worker开关或供应商调用在本次编写计划中发生。

## 执行交接

本计划待用户审核。确认后沿当前对话按七步实施，先完成Task1，不提前部署或消耗供应商/AI/邮件额度。若实际代码与规格出现重大冲突，先更新设计再继续；普通文件命名/局部实现细节由执行者处理并记录，不逐项打断用户。
2026-10-28验收裁决：USER生产人工登录不重复，以隔离角色测试+产物匹配+生产管理员/匿名证据组成验证，明确保留限制；用户明确授权的一项真实任务已成功，原入口受控关闭。下一引用切片单独实施，不将FR-11全部关闭。
