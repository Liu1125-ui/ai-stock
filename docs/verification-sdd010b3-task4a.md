# SDD010B3 Task4A分析准入与终态保护核验

2026-11-08。仅完成Task4A分析owner的持久准入与本地终态保护，未完成Task4整体。市场bundle登记、能力检查、完成证明/恢复、预测和实际验证链路、Java/Python凭据转发仍留Task4B/C；管理员操作与上线仍留后续任务。未部署，FR-11不关闭。

## 实现与行为

综合分析先按原owner/operationId/requestHash登记并提交，再读取可选预测引用及行情。准备阶段contextReady=false、contextHash=null，保留原predictionId以便刷新恢复；网络取证据、调用模型均不持数据库事务。并发的同编号请求读取原RUNNING回执，不重复取证据。

取证据失败持久保存FAILED，再返回原安全业务错误；相同请求重放返回原失败，不盲目再次取数。已登记的编号不能改owner或参数，修正选择须使用新编号。此前接口测试把多个错误predictionId与有效请求放在同一编号下，已改为各例独立编号；不放宽原指纹校验。

bindFacts只允许原代次RUNNING且未准备操作绑定一次；先查本人已有context缓存，再申请共享MaaS槽。缓存复用不申请槽位，不清空其他类型操作的槽位。新操作generation固定1，同编号不重启，错代次拒绝；本地失败/成功/到期状态不可逆，条件终态写入递增fencingRevision。V12增加三列，旧记录默认1/1/true；不改V6旧迁移或历史结果。

前端AnalysisAttempt允许contextHash为空，新增回执字段保持可选兼容；恢复原操作仍使用原marketVersionId与predictionId，不自行生成执行代次。

## 验证证据

先添加三个真实MySQL服务反例，观察三项断言失败：取证据时尚无持久操作、并发同编号二次取数、失败后原请求再次取数。修复后通过。另补错误代次、未准备禁止成功、上下文只绑定一次、终态不可覆盖、准备超时、缓存保留其他付费槽、真实双连接并发登记及终态竞争、原预测选择恢复等测试，共10项新分析MySQL用例。

新增迁移保全测试仅创建强校验的随机临时schema，迁V11、插入完整已完成操作/结果、升级V12，逐字段比较原记录并验证新增默认值；finally仅清理自己创建的schema。旧V10→V11迁移测试固定target11，保留原有迁移次数、版本与数据保全断言；避免新增V12改变旧测试目标。

完整命令：

```powershell
mvn.cmd -f backend/pom.xml '-Pauth-integration,cli-build' -pl stock-prediction-service -am verify
```

2026-11-08 12:45完成，退出码0、BUILD SUCCESS。按本次日志创建时间筛选新报告：公共65项、预测266项，共331项，失败/错误/跳过均0；新11项MySQL测试已包含于总数。预测服务打包成功。前端StockAiAnalysisPanel专项14项与全工程vue-tsc类型检查通过；不宣称全部前端测试或市场模块重测。

独立只读代码审查及后补迁移测试/前端类型审查无可操作的重要问题；审查人未运行测试或操作数据库。git diff --check通过。

## 运行边界

仅隔离测试库13316使用V12；本轮未启动业务服务、执行生产迁移或部署，未修改真实配置和开关。测试仅使用本机MySQL、MockMvc及伪依赖，无真实模型、Tushare/Baostock或邮件调用，不作为准确率实验数据。

本片不接触市场凭据释放，不能因本地租约到期便释放市场使用。后续需将已持久的本地身份绑定固定manifest、终态证明及跨服务请求头；这些链路完成前全局保护仍关闭。

本地忽略日志：runtime/lifecycle-task4a-red.log、runtime/lifecycle-task4a-green.log、runtime/lifecycle-task4a-final-verify.log、runtime/lifecycle-task4a-frontend.log。首次全量回归的旧迁移夹具失败见runtime/lifecycle-task4a-verify.log；不得将该失败记录当作最终通过依据。
