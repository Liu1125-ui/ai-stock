# 真实行情验证证据

作用：独立保存预测起点和已到目标日的RAW/后复权配对证据，绑定不可变RAW行情版本；不是新的60日模型输入。

实现：内部请求认证由CandidateRequestFilter在解析前校验；4KiB请求、128KiB完整来源响应、35秒截止且禁止重定向。短事务保存操作回执，网络在事务外；原RAW逐行匹配、完整日历、因子正值、股票/日期/版本验证通过才追加证据。价格以字符串保存与输出，LONGTEXT+JSON_VALID保留数值口径，避免数据库JSON浮点往返改变原数据。内容相同复用版本，来源故障/过期保留已有行情与证据。未到期跳过不采集，停牌/缺失/日历改变单独状态，不算预测正确。

验证：RealizationMysqlTest、RealizationHttpTest、RealizationCollectorTest；测试库必须stock_market_test。外部源与正式部署另记录证据。用户验证结果存储及统一调度仍由MS-03/MS-04后续接通，本模块不提供用户直接上传真实价格入口。

证据保留完整当次calendar及其hash，后续日历修订不覆盖固定版本。不同operation并发内容去重后以FOR UPDATE当前读获取胜出版本，避免重复读快照误报缺行。

SDD010B3 Task3B：计算证据读取/派生发布接入MarketEvidenceGate.local短事务校验，按业务凭据固定RAW；外部采集在事务外，发布前再次校验。门禁默认关闭，实际owner转发和完整上线仍在后续阶段，历史快照与只读元数据保留。
