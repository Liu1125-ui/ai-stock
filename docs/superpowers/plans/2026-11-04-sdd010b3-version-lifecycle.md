# SDD010B3 行情版本生命周期实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement task-by-task in this chat. 用户已授权自主推进常规实现，不再重复询问形式确认；不得因此跳过测试、独立审查和受控部署。

**Goal:** 为历史RAW版本实现可审计的停用/恢复，并用持久使用凭据保护所有新计算和在途结果。

**Architecture:** 行情服务拥有版本许可、聚合使用manifest和动作记录，owner服务拥有执行代次及不可再提交的终态。先拒绝新使用，等待真实在途结束，再完成停用；跨库无事务假设，未知状态保守等待。

**Tech Stack:** 现有Java17、Spring Boot、JdbcTemplate/MySQL8、Flyway、JUnit5、Vue3/TypeScript/Vitest，无新增分布式事务框架。

**Spec:** E:/aiStock/项目代码/stock-system/docs/superpowers/specs/2026-11-04-sdd010b3-version-lifecycle-design.md

状态：2026-11-04 Task1/2契约、迁移和事务基础已实现并通过隔离测试，未部署；Task3A协议底座已完成，Task3B证据/发布门禁已完成，公共65/行情312共377项verify通过；Task4—8仍待实施，未部署。本计划按B3-1、B3-2、B3-3交付，不把第一阶段基础代码宣称为功能已上线。

## 全局约束

- 只管理已发布baostock/RAW历史版本；当前头不得停用，恢复不切头。内容发布状态与useState分开。
- ENABLED/DRAINING/DISABLED；revision/generation均以十进制字符串传输，使用UUID原编号恢复。
- 所有新版本引用在返回证据前原子登记全部RAW；比较按固定evidenceIds解析，持仓才解析当前头。旧快照读取不新建使用。
- owner先建持久操作身份，最终成功/失败事务后才能释放；超时、租约到期、找不到和依赖故障均不释放。
- 新动作入口和强制门禁初始false；未接齐全部owner时不开放。保留原后台/AI/邮件配置。
- 每个新自建目录有README；不得跨库写入，不提交密钥，不改开题报告和任务书。

## 审查重点

- 集合登记响应丢失后当前头已变：必须返回原manifest，不能重选或混用版本；Task2/4/5加断言。
- 已停用RAW已有AVAILABLE预测输入：不得通过旧输入绕过；Task3/4加真实入口反例。
- owner提交成功但完成通知丢失，以及迟到失败/成功响应：凭据保留至同代次终态证明，CAS禁止二次写；Task4/5。
- 恢复和旧停用完成/二次停用竞争：cycleId与行锁保证新状态不被旧回执覆盖；Task1/2/6。
- 门禁不齐或回滚旧worker：禁止开启动作，拒绝不能保证完整保护的部署；Task3/7/8。

## Task1（B3-1）：契约、状态策略及迁移

**Create:** backend/stock-common/src/main/java/com/aistock/common/marketlifecycle/{README.md,MarketLifecycleContract.java,MarketUseRecords.java,MarketLifecyclePolicy.java}；对应测试目录与README。
**Create:** backend/stock-market-service/src/main/resources/db/migration/V15__market_version_lifecycle.sql。
**Modify:** 对应migration/README.md和test/support/MarketDatabaseFixture.java（核对fixture采用Flyway还是显式schema后同步，不改生产库造测试）。

- [x] 先写纯策略测试：ENABLED接受新使用，DRAINING/DISABLED拒绝；当前头拒绝停用；恢复不改头；最后凭据完成与cycle不符；revision溢出；空/重复/超限版本与未知用途、正代次及安全请求hash。
- [x] 执行专项确认失败，再实现最小记录/校验/纯状态决策。
- [x] 迁移建立lifecycle/use_bundle/use/action/action_event，建立外键仅限同库及聚合唯一键/索引，回填已有RAW为ENABLED。读取/释放按owner+代次，不按未经认证的useId授权。
- [x] 独立MySQL测试验证回填幂等、唯一约束、非RAW不误入及旧行情头/日线/预测输入行不变；新库全迁移和既有V14升级均验证。
- [x] 运行公共模块及迁移测试，提交单一基础功能；此时无公开写入口，不部署到生产。

## Task2（B3-1）：本库原子登记和生命周期动作存储

**Create:** backend/stock-market-service/src/main/java/com/aistock/market/marketlifecycle/{README.md,MarketUseRepository.java,MarketLifecycleRepository.java,MarketLifecycleService.java}；相应测试与README。

- [x] 先写双连接屏障测试：acquire先提交→disable等待；disable先提交→acquire拒绝；多版本一个被禁则全部回滚；同代次不同manifest冲突；同bundle登记重放与整组释放竞争，所有更新bundle的路径先锁bundle，不得version/use后再锁bundle。
- [x] 先写恢复/最后释放/再停用、动作重放与异actor冲突、预检后头改变、依赖未齐、SQL中途异常回滚测试，确认失败。
- [x] 实现聚合键先锁、重放先返回绑定，再按稳定顺序锁头/RAW/许可；基于固定evidenceIds解析比较证据，不查询当前头替换证据。CURRENT_HEADS仅用于明确允许的用途。
- [x] 实现动作原子写、事件追加、原编号查询及有界历史分页；DRAINING持续显示未完成，最后release只有在当前cycle仍有效时完成动作；恢复取代等待动作。
- [x] 实现终态核对后的整bundle释放，所有版本同事务；未知状态保持ACTIVE。历史引用数量不参与许可决策。
- [x] 独立MySQL运行并保存并发证据、全部专项回归后提交。尚不开启生产动作。

## Task3（B3-2）：内部入口、证据读取门禁与发布互锁

**Create:** market/marketlifecycle/{MarketUseController.java,MarketUseAccessFilter.java,MarketUseConfiguration.java,MarketUseProofClient.java,MarketUseReconciler.java}。
**Modify:** market/version/MarketVersionRepository.java；market/forecast/{ForecastInputRepository.java,ForecastInputService.java,ForecastInputController.java}；market/quotation/AnalysisEvidenceController.java；market/comparison/{ComparisonEvidenceController.java,ComparisonEvidenceService.java,ComparisonEvidenceRepository.java,ComparisonRefreshService.java}；market/valuation/{ValuationQuoteController.java,ValuationQuoteRepository.java}；market/alert/{AlertEvidenceController.java,AlertEvidenceService.java}；application.yml。
执行前核对真实文件名，计划中未确认存在的方法不得凭空调用；改动只落在真实证据产生或读取边界。

- [x] 先写真实HTTP反例：门禁开启无使用身份拒绝；错owner/用途/代次/RAW拒绝；RELEASED拒绝；原ACTIVE在DRAINING可读；内部认证错误及公共代理路由不可达。
- [x] 先写发布与停用竞争、复用停用旧版本拒绝、停用RAW旧输入准备/读取拒绝，以及同库输入发布持锁测试。
- [x] 按规格§11实现64KiB请求上限、100版本上限、短HTTP超时与固定owner地址、no-store、白名单错误；实际证据必须匹配完整manifest，不能仅校验token格式。
- [x] 新RAW发布同事务建ENABLED许可；内容去重只复用ENABLED版本；维护与Task2一致的锁顺序。外部采集不得长持行锁。
- [x] reconciler以lastCheckedAt+useId稳定游标分批，避免前50条UNKNOWN饿死后续；owner证明不存在/不可达不释放。非生产隔离环境断网/超时验证。
- [x] 配置默认不强制且动作始终关闭；报告协议能力，但未完成owner接入不能以此认为可开放。专项与市场模块回归，提交。

### Task3A执行状态（2026-11-04）

内部认证协议、requestId持久回执、selector_json原选择、固定owner证明client、有界保守轮转及默认关闭配置已完成；新迁移V17只在隔离测试库。以上为Task3的前置部分，原Task3清单中的证据读取/发布互锁尚未完成，不能将Task3整体勾选。

Task3B已实施：保护旧证据入口，依据selector将预测/估值/比较/验证解析固定到bundle的RAW，禁止随新头变化重选；取数返回与同库派生发布使用短事务校验，外部采集不能持DB锁。新RAW同事务创建许可、旧停用版本不得去重复用。回执/只读binding不能代替原子读取准入。完成后再进入Task4 owner终态CAS及真实消费接入，仍不启用生产动作。

2026-11-07 Task3B完整verify通过（公共65/行情312共377项，无失败/错误/跳过），独立审查重要项已复现修复。以上勾选仅表示市场侧实现及隔离验证完成；公共网关无内部证据转发路由作静态核对，生产现场验收仍留Task8。详见docs/verification-sdd010b3-task3b.md。

## Task4（B3-2）：预测、分析与实际验证接入

**Create:** backend/stock-prediction-service/src/main/java/com/aistock/prediction/marketuse/{README.md,MarketUseClient.java,MarketUseOwnerService.java,MarketUseProofController.java,MarketUseRecovery.java}；相应测试目录。
**Modify:** backend/algorithm/app/inference/publication.py及相关算法服务路由/测试（在线行情复核必须转发同一使用凭据；离线候选不冒充在线使用）；forecast/{ForecastService.java,ForecastRepository.java,HttpForecastDependencies.java,ForecastDependencies.java}；analysis/{AnalysisService.java,AnalysisRepository.java,AnalysisMarketClient.java}；verification/{VerificationService.java,VerificationRepository.java,HttpVerificationMarketClient.java}；安全配置和必要新Flyway迁移（实施前核对当前最大序号，不覆盖旧迁移）。

- [x] 先写测试复现AnalysisService先取证据后claim的缺口，再把持久操作身份前置，保持既有请求指纹和已保存结果重放。
- [x] owner客户端在接线时明确核对市场侧协议及证据门禁实际开启状态，不能把协议可用当作门禁已开启。给缺少执行代次/终态CAS的仓储补本地fencing；记录使用bundle和完整版本集合，新代次不能复用旧凭据。
- [x] 三条链路均实现claim→绑定使用→取证据/计算→终态事务→通知核对；许可拒绝不调用模型/外部源。验证仅为新实际证据登记，旧预测快照保持只读。
- [x] 证明接口只返回代次/终态/版本集合/结果身份，不泄露原问题或用户身份。未知bundle响应通过原业务键读取恢复，不能重新选头。
- [x] 用伪HTTP依赖/独立MySQL测试取证据后停用、推理晚到、结果落库后通知丢失、owner重启、未知提交、重复请求和代次错误；确认旧快照hash不变且不产生额外AI调用。
- [x] 跑公共/预测/市场受影响回归，提交。不开放生产动作。

### Task4分步记录（2026-11-08）

Task4A先修复分析owner取证据前无持久身份的问题，增加V12代次/终态fencing和准备状态，保持原指纹、缓存及共享MaaS槽。本片已完成，公共65/预测266共331项verify、前端14项/类型检查通过，独立审查无重要问题；详见verification-sdd010b3-task4a.md。Task4第一项准入顺序可由本片证明；其余bundle登记/能力检查/终态证明/恢复以及预测、验证与Python转发仍为Task4B/C，不能勾选整个Task4或部署开启门禁。

Task4B已实现分析owner原请求持久登记、RAW凭据转发、同代次实际终态证明与保守恢复；V13仅隔离测试，新开关默认false。独立审查指出坏回执可能阻塞轮转，已先复现再修复；固定引用扫描补第16组。公共65/行情313/预测287共665项完整verify已通过，详见verification-sdd010b3-task4b.md。原Task4余下清单仍不能整体勾选：Task4C继续预测/验证/Python、完整owner能力和恢复，不将分析单链路等同所有消费者已就绪。

Task4C已完成三条链路隔离接入，公共65/行情313/预测298共676项verify、Python全套102项通过，独立审查无Important/Critical，详见verification-sdd010b3-task4c.md；以上Task4勾选不是生产上线，全局消费者就绪仍须Task5—8。用户已授权连续实施，不再逐项询问常规细节。

## Task5（B3-2）：比较、持仓、预警和后台任务接入

**Modify:** prediction/comparison/{ComparisonService.java,ComparisonRepository.java,HttpComparisonMarketClient.java}；prediction/portfolioanalysis/{PortfolioService.java,PortfolioRepository.java,PortfolioSources.java}；notification/alert/{AlertEvaluationService.java,AlertEvaluationStore.java,AlertEvidenceClient.java}。
**Create:** notification/marketuse/{README.md,MarketUseClient.java,MarketUseProofController.java,MarketUseRecovery.java}和测试；按实际schema补必要owner迁移。
**Modify:** scheduler/task/{TaskWorker.java,HttpPredictionTaskClient.java}及比较/预警现有适配器中涉及证据使用身份的部分；市场本地comparison/alert采集仓储补终态身份。

- [ ] 先写比较旧evidenceIds与当前头不同的反例，要求登记旧证据的真实RAW全集，禁止当前头凭据配旧证据。重放不改比较输入fingerprint。
- [ ] 测试持仓多版本原子绑定、空仓不造use、部分不许可全拒绝，结果保存后读取原快照不补登记。
- [ ] 测试预警同库触发/消息/outbox终态后才释放；只读历史与实际邮件发送不重新读取RAW、不补发历史邮件。
- [ ] 队列冻结版本执行时被禁明确失败；不自动换数据、不无限重试，安全错误白名单同步；RUNNING/RECONCILING取消保护不削弱。
- [ ] 为市场本地派生计算补覆盖取证据至发布的使用身份或同事务许可锁，逐入口清单核对无漏口。任何未接入入口使门禁上线检查不通过。
- [ ] 各owner证据恢复与终态CAS测试、相关前端旧结果显示测试、后端受影响模块集成通过后提交。

## Task6（B3-3）：管理员安全API与网关

**Create:** market/marketlifecycle/MarketLifecycleController.java；gateway/adminmarket/{MarketLifecycleProxyController.java,MarketLifecycleCorsConfiguration.java}。
**Modify:** market/adminquery/AdminMarketDataAccessFilter.java；gateway现有管理员权限代理及精确路由；公共契约/安全错误映射；compose.yaml和.env.example新增默认false配置，不改含密钥.env。

- [ ] HTTP先测ADMIN/USER/匿名、直连/网关、Origin与自定义操作头、错误method/额外字段/正文上限、no-store；依赖不齐时即使动作开关true也拒绝。
- [ ] 实现lifecycle、actions、原operation和历史查询，返回许可状态/原发布状态分列，当前头原因、在途数量、版本revision及能力准备状态。
- [ ] 超时/断线不产生新operation，恢复只能原actor读取操作正文；历史列表只含管理必要信息。
- [ ] 协议能力探测不采用任意客户端URL；未知旧owner版本不通过写入就绪检查。完成HTTP回归并提交。

## Task7（B3-3）：管理员页面与历史可追溯提示

**Create:** frontend/src/api/{marketVersionLifecycle.ts,marketVersionLifecycle.test.ts}；frontend/src/pages/admin/data/{MarketLifecyclePanel.vue,MarketLifecyclePanel.test.ts,marketLifecyclePending.ts,marketLifecyclePending.test.ts}。
**Modify:** frontend/src/pages/admin/data/MarketVersionDetail.vue、marketDataPresentation.ts和相关README；必要DTO为许可与发布状态分字段，不让旧AVAILABLE误显示“允许新使用”。

- [ ] 测试固定版本/原因/确认、未齐备入口禁用、DRAINING等待与刷新、恢复取代旧请求、原编号超时恢复及跨账号/版本迟到响应。
- [ ] 实现默认只读状态，按钮明确“停止新使用并等待在途结束”；服务返回DISABLED才显示已停用，数量为零也不自行判断成功。
- [ ] 保留审核/引用/使用状态分区；历史预测/估值快照不修改，若追加当前状态说明，失败显示未知而不是改原快照结论。
- [ ] 窄屏横向滚动、Tab/Enter、加载及安全错误提示测试通过，前端全量测试/typecheck/build，提交。

## Task8：完整审查、隔离演练、受控上线

**Create:** docs/verification-sdd010b3.md、tests/check-market-lifecycle.ps1及tests/README说明；结构化证据只含非敏感状态/hash。

- [ ] 对照所有新引用创建入口逐项勾稽，完整受影响后端MySQL集成/非DB测试、前端全量和构建；不以历史XML数量当本轮成绩。
- [ ] 按requesting-code-review做一次独立终审，修复重要问题再回归。
- [ ] 隔离部署全链路：任务在途时停用进入DRAINING→新使用拒绝→老任务成功落库→完成停用→旧结果仍可读→恢复不切头；再演练服务崩溃/迟到响应，任何未知保持等待。
- [ ] 原镜像/数据库/配置有备份后，在动作关闭下部署兼容代码。短维护窗口暂停新接入和相关worker，核对旧在途终态；未核对清楚则留入口关闭，不伪造终态。
- [ ] 全owner协议齐备、强制门禁启用与匿名/USER/ADMIN边界通过后，按原值恢复后台开关；固定版本生产写验收需限定对象和预算，不能默认停用用户数据。
- [ ] 检查运行产物、历史快照/账户/头指纹，记录自动化与人工/真实生产验收的区别；未完成门槛保留待办。
- [ ] 按细粒度提交，仅在用户另行要求时推送/远程发布。启动脚本继续按就绪启动，不改变日常操作方式。

本计划的三阶段不是三个新增业务，也不是已完成功能。管理员写入口只有Task8全部条件满足才开放；不为展示功能牺牲在途安全。

Task5进度：Task5A cbb54fa（比较683项）、Task5B 328baa8（持仓690项）、Task5C（预警515项）均完成隔离实现/独立审查，见各核验记录；市场本地派生与scheduler及Task6—8仍开放，不勾选整个Task5。
