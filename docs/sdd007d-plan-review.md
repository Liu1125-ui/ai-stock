# SDD007D 实施计划自审记录

日期：2026-10-13。用户已明确确认设计并要求编写实施计划。对象：docs/superpowers/plans/2026-10-13-sdd007d-comparison-refresh.md。本轮为作者自审，不是独立代码评审；没有修改产品、迁移库、运行新功能测试或新增供应商调用。

## 范围与交付

五任务：就绪及固定窗口；有界接收/回执/原子发布；持久任务和预算；计划/HTTP/工作器恢复；完整测试/审查/真实推进。设计原四交付中“MS04持久与执行”拆成两个独立测试任务，不新增功能。保持当前Native方式，不逐项分派或重问方法。

## 已完成的核对与修订

1. 已核实真实文件名TaskMysqlTest、TaskVerificationMysqlTest、RevisionScanMysqlTest及三处new TaskWorker调用；去掉计划初稿的条件占位测试名。
2. 原TaskRepository.create的key正则不允许冒号；新类型自行生成comp-update键，不将新键塞进旧create、也不修改旧输入契约。
3. 原pendingReceipts/claim没有类型过滤；两者及过期扫描都增加allowedTypes，不只修改tick开关。保留旧record与显式runOnce语义，生产tick按开关选择。
4. 原retry会清零attempt_count；比较类型依据总尝试行与固定attemptLimit守住预算。演练限制1不超过设计3，下一周期才采用默认3，不改旧上下文。
5. 固定RAW读取不重选head；CURRENT比较窗口投影而非RAW ID，允许停牌，日历TTL在带采集时间的Query层验证，纯target不伪装可判断TTL。
6. 明确asOf/planAsOf毫秒精度、MutableClock实现共享时刻、锁等待跨租约的实际并发步骤；成功回执与证据在同一事务，失败/GET不推进旧结果。
7. Python比较根协议为state/candidate/errorCode，不能照抄旧RAW collector的根operationId；请求恰好五字段，候选内op/身份/范围另查。Java Future覆盖body期限和2MiB上限。
8. 测试清理次序包含市场新回执先于证据、调度context先于任务；不改现用库清理路径。SQL迁移号执行前重新核验，不覆盖并行迁移。
9. 真实首验用单股配置+attemptLimit1逐股，总9次query且失败停止，不一次开三股导致失控重试；SDK login/logout及跨午夜逻辑周期计量单列。
10. check-comparison-snapshot原输出固定B目录；计划增加可选OutputDirectory，D独立输出，原默认不改。只读导出明确TaskIds来源、三股/状态/ID核对及裸data结构；Python复核两个目录参数已核实。
11. 现有Evidence没有definition字段；导出核验provider/basis/hash，不虚构字段。真实样本含合法缺失不能填数据凑36数值通过，分别记录状态和未完成的数值样本验收。
12. 隔离回退需同时编译旧Repository和旧Worker，否则新Repository会过滤新类型而掩盖旧程序unsupported；先排空/取消新类型再回旧镜像，旧Flyway完整启动未做就不能宣称已演练。
13. 浏览器真实步骤用用户正常登录，不读凭据/伪造会话。全片MaaS0/邮件0；CodeArts/MP/自然预警等历史待办保留。

## 规格覆盖

- 设计1—5目的/就绪/边界：Task1与Task4。
- 设计6固定任务/周期/预算：Task3与Task4，真实限制Task5。
- 设计7接收端/原子性/失效：Task2，调用恢复Task4。
- 设计8接口/数据库/隔离：Task1—4。
- 设计9新旧选源与历史：Task5混合滚动窗口及旧结果hash。
- 设计10部署/回退/验收：Task5，原开关与配置Task4。
- 设计11边界：五Review Focus分别加入所属任务具体RED步骤。
- 设计12顺序：五任务均独立测试与选择性提交，接口产物/消费者已显式列出。

## 接口一致性

MS02 Request八字段，Python发送五字段；MS02 Result/现用MS04 MarketRefreshClient.Result的四字段同名，versionId为比较evidenceId。Task/Claim旧签名保持；ComparisonTaskRecords独立声明，Context引用Snapshot而不跨服务import。Plan入参为三股字符串、限制1..3；Worker只通过TaskRepository本库Context取得固定参数。就绪三状态前置允许null，READY所需字段完整；当前计划没有新增模型或用户接口。

## 门禁状态

计划与设计无未决业务选择或实现占位；所需文件/签名/测试和命令已落实。计划内测试及验收命令尚未执行，不把历史606/148当本轮结果。旧.superpowers不动，无产品变更。

按writing-plans的Execution Handoff要求："wait for that review before implementation"。本计划需用户审阅后才进入executing-plans；执行方式已保留Native，不再次要求选择。


## 2026-10-14 最终文件校验

五任务、勾选步骤、代码围栏、无占位扫描、关键接口及真实测试文件路径校验通过；git diff --check通过。补充窗口数值投影与参数化类型过滤示例，核对旧类型为MARKET_REFRESH/PREDICTION_VERIFY，修正设计阶段过时状态。上述为文档校验，未执行产品测试、部署或外部调用。等待用户审阅计划。
