# SDD007D 比较证据持续更新验证记录

记录更新：2026-10-17。2026-10-16已完成部署、三股真实首验、独立指标复核、正常登录页面验收及正常周期启用。长期连续运行仍需观察，不能据一次成功保证供应商持续可用。

## 功能与边界

1. MS02只读就绪检查依赖有效独立交易日历、上海20点截止及完整一年RAW窗口。按三个月、半年、一年实际选源核对量价、状态与日历，不因RAW编号变化重复采集，不从任意历史匹配项推导CURRENT。
2. MS02固定RAW上下文、五字段采集请求、30秒正文期限、2MiB限制、严格JSON及持久操作回执。成功证据与回执原子提交；纯读回执通过锁定当前读等待在途提交，数据库等待超时保持未知，不伪造终态。
3. MS04持久COMPARISON_UPDATE任务、日周期去重、累计尝试预算、900/1800秒退避及原操作恢复。未知POST先RECONCILING，不盲目重采；实际领取过滤上海当日周期，不依赖100条过期清理上限。
4. 发布操作序号表达成功激活顺序，新操作可重新激活复用的内容；旧证据JSON、created_at和用户固定结果不改写。三股滚动推进仍保留共同范围历史覆盖回退。
5. 正常周期已配置三股、20点后检查、每周期最多3次尝试。2026-10-16首验每股上限1且实际各1次；启用正常配置没有改写旧周期预算。系统与Docker需运行，关机期间不能执行任务。

## 审查与自动化验证

原独立审查最初因额度失败，2026-10-16恢复同一审查，发现3项Important，0项Critical/Minor。分别补真实断言RED并完成一次修复轮次；没有第二次独立复审。审查与修复详情见[sdd007d-final-review.md](sdd007d-final-review.md)。

修复提交1781cc8（提交前回执竞争）、4f4679e（第101条过期任务领取）、c410d18（RAW回切、复用与实际选源一致）。原任务1—4提交da022d3、2351941、13ad0f4、449c9f3，混合窗口与审计工具提交ea00fac。

完整后端clean verify：660项、180套件，失败0、错误0、跳过0，BUILD SUCCESS。前端148项及严格构建通过，原有大包体警告保留。Python比较18项通过，保留anyio弃用和容器缓存目录权限两条既有警告。前端及Python未改代码，不把替身测试当真实供应商验收。

日志位于.superpowers/sdd/2026-10-13-sdd007d-comparison-refresh/final-review-backend-full.log、final-review-frontend.log、final-review-frontend-build.log、final-review-python.log；XML汇总runtime/sdd007d-review-backend-counts.json。

## 部署与数据保全

先MS02、健康后MS04，升级时两新开关显式false；生产市场迁移13、调度7成功。V12已存在不改写，新增V13；六Java服务UP，算法运行；内部匿名401、网关内部路由404。旧镜像与摘要仍保存在runtime/sdd007d-rollback-images.json和回退override，未删除旧数据。

原比较01a31765-75cf-4bf5-95be-df7037e57b81与AI解释a4b1997e-8aeb-4ac4-a74c-92b33a599144的内容哈希在部署前后保持一致；原预测1、模型3、AI结果1、AI操作2。页面验收通过本人正常登录新增一份三股对比8807800a-c643-4205-81ca-02e8f4b58d6b，因此比较总量预期由1变为2，不视为旧结果变更。

基线runtime/sdd007d-live-before.txt、runtime/sdd007d-live-resume-before.txt；部署保全runtime/sdd007d-postdeploy-preservation.txt。首次补充只读基线误用字符串MAX(version)得到9，已按installed_rank核实并修正为11；未修改数据库。

## 三股真实首验

中信证券SH600030：任务d1a2b354-e846-443a-8a4f-38d89ef05b4c，证据a655d270-78a3-4787-9727-c4eb5560fea3。

恒瑞医药SH600276：任务950c868d-4506-4793-a477-cd61e1867596，证据cd037540-d563-4a02-9827-f916b28f1cf8。

万科A SZ000002：任务95b50a1f-2564-4a56-a2a9-00ca7a2098f2，证据35c7f01b-bd67-4bd9-ae49-7af78b757b79。

三股均SUCCEEDED、每股1次尝试、attemptLimit仍1，证据为Baostock后复权，范围2025-10-16至2026-10-16。真实源方法查询按成功采集代码路径计9次（每股RAW、后复权、日历各1次），无自动重试；不是底层网络包或供应商账单计数。MaaS0、邮件0。既有RAW管道自然更新不计入本片新增查询。

只读导出runtime/sdd007d/live/acceptance-20261016；实际ComparisonCalculator与独立Decimal复核runtime/sdd007d/audit-20261016，三股×三周期×四指标共36项通过。原有旧样本36项离线审计单独保留，不与新更新样本混淆。

## 页面与启用

使用已有正常浏览器会话，在股票对比页选择三股并生成结果，截止日期2026-10-16，共同区间2026-07-16至2026-10-16，曲线和四项指标显示。刷新后相同记录、保存时间和指标保持；旧记录仍可见。没有点击新对比的生成AI解释。

全部通过后本机.env启用COMPARISON_UPDATE_PLAN_ENABLED、COMPARISON_UPDATE_WORKER_ENABLED，INSTRUMENTS现三股，MAX_ATTEMPTS=3。真实首验上下文仍1，不放宽原周期。最终三股只读就绪均CURRENT，证据ID与成功任务一致。

## 恢复与仍待办

2026-10-16 Docker被残留socket运行目录阻断，只改名保留运行目录，没有删除VHD、数据库、镜像或设置。依赖未就绪时旧业务服务注册失败，待数据库与Nacos健康后启动恢复。2026-10-17发现容器同时Exited255，按依赖顺序重新启动；不能据此断言是产品代码导致。

实际旧Repository、Worker、匹配PredictionTaskClient隔离探针通过并回滚。完整旧镜像/Flyway启动仍未演练，不能把隔离探针当完整回退成功。长期运行、完整窄屏/键盘视觉与性能、自然预警完整邮件链路、CodeArts真实证据、MyBatis-Plus继续待办。本片完成不代表全系统或论文完成。

2026-10-17恢复复核：数据库/Nacos健康后启动业务，六Java UP、算法/health UP、首页HTTP200；原比较/AI哈希、预测1/模型3/AI结果1/AI操作2不变，比较总量2符合新增验收记录预期。正常更新开关及三股/新周期上限3保留。证据runtime/sdd007d-resume-20261017-preservation.txt。
