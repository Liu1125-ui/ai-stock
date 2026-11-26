# SDD010B3 Task4B分析owner使用协议核验

2026-11-08。完成STOCK_ANALYSIS单链路的市场使用登记、固定RAW转发、实际终态证明及保守恢复。Task4C预测/实际验证/Python转发和完整owner能力仍待实施；Task5—8保持待办。未部署、未启用生产门禁，FR-11不关闭。

## 实现范围与边界

V13新增本库stock_analysis_market_use。分析操作持久准入后，先保存不可变原requestId/代次/RAW，再在登记POST前提交registration_started。客户端固定市场根地址，实际核对protocolEnabled和evidenceGatesEnabled均开启；能力接口新增后者真实配置值，仍不声称全局enforcement或管理员动作就绪。

登记/查询响应须匹配原身份、requestId及完整RAW集合，核验use条目及修订；重复JSON、错误类型/集合、重定向和过大响应拒绝。完整响应64KiB与最长4秒截止，连接1秒，不自动重试写请求。登记结果未知仅GET原requestId；404仍保留未知，不新登记、换头或重算。

取得ACTIVE凭据后，市场证据客户端转发Bundle/Owner/Purpose/Operation/Generation五项头，scope结束清理；不向MaaS附加凭据。完成本地结果提交后才发完成核对通知，失败保留结果和未确认行。既有成功请求优先重放，不重新调用模型。

证明接口只接受内部服务认证的无正文GET及单一generation参数，no-store。证明来自AnalysisRepository实际同代次不可再写终态与持久版本集合，九字段白名单不含用户、问题、事实或持仓。未知/在途/错代次不提供终态证明；停止新登记后仍能查询原绑定终态。市场依赖未就绪或许可拒绝时，不读取证据、不调用模型。

默认关闭的恢复每批最多25条，按检查时间轮转。损坏/未知记录保留，不猜测释放；恢复只找原登记、核对实际终态并通知，不计算新结果。新增raw_version_id引用同步加入预测owner固定扫描白名单，第16组；候选引用行数不能代替在途许可判定。

## 测试与审查

先观察现有能力接口缺少实际门禁字段的HTTP反例失败；新增客户端/owner测试在实现缺失时编译失败。新增测试覆盖请求身份与RAW绑定、准备持久化先于网络、在途拒证明/实际失败或成功证明、通知丢失恢复、未知登记重启后按原编号恢复、404不重复POST、默认关闭零活动、完整分析链路提交顺序及已有成功重放。

HTTP专项验证认证先于解析、重复token、非GET/正文/多余和重复参数拒绝、no-store；使用本机HttpServer验证真正五项头转发/清理、重复JSON/错误集合、重定向、64KiB和响应头到达后停滞的完整截止。恢复验证每批25条及未知记录的公平轮转。

独立只读审查发现一项P2：坏receipt在解码后才记录检查时间，会让最旧25条永久饿死后续。先用25坏回执+1正常pending复现两批零次查询，再将markChecked前置，回归确认正常记录得到查询且所有行未被误确认释放。另用15→16分组反例补齐新表引用扫描。修复后专项通过，再跑完整回归；未声称独立审查重新执行测试。

完整命令：

```powershell
mvn.cmd -f backend/pom.xml '-Pauth-integration,cli-build' -pl 'stock-market-service,stock-prediction-service' -am verify
```

2026-11-08 14:43完成，退出码0、BUILD SUCCESS。按本次日志创建时间筛选新XML：公共65、行情313、预测287，共665项，失败/错误/跳过均0，两个服务打包通过。新增22项已包含于总数，不重复加总；既有迁移、分析准入、模型发布/预测/验证等相关回归包含在上述模块中。未改前端，不宣称本轮重跑前端或全系统其余服务。

Compose config --quiet、git diff --check通过。V13仅在13316隔离预测测试库；行情测试库仍V17。旧V11→V12保全测试固定target12，避免新增迁移改变旧测试范围；隔离夹具按子表→操作顺序清理。测试全部为本地HTTP/MockMvc/MySQL及伪模型，不作为真实行情或准确率证据。

## 配置和后续

PREDICTION_MARKET_USE_ANALYSIS_ENABLED与PREDICTION_MARKET_USE_RECOVERY_ENABLED模板均false；实际密钥配置未改。未部署或迁移业务库，未调用真实MaaS、数据源或SMTP；当前不应单独打开生产市场证据门禁。全局enforced仍在构建中拒绝开启，管理员动作仍未就绪。

下一步Task4C需接预测/实际验证本地代次与终态、固定选择、Java和Python的同份凭据转发，以及完整owner能力。比较、持仓和预警owner仍留Task5，不能将本片单股分析接入等同全部消费者已保护。

忽略日志：runtime/lifecycle-task4b-capability-red.log、lifecycle-task4b-owner-red.log、lifecycle-task4b-review-red.log、lifecycle-task4b-review-green.log、lifecycle-task4b-final-verify.log。
