# 预测输入快照测试：仅在13316隔离MySQL执行，覆盖冻结、错配、日历、并发、身份与过期。

ForecastInputMysqlTest：真实MySQL验证冻结/重放/并发/状态/精度，必须用隔离库13316。ForecastInputValidatorTest：无数据库验证极端指数在哈希展开前拒绝。ForecastInputHttpTest：MockMvc走Controller映射与过滤器，验证分号路径鉴权、体积限制和已认证读取；不连接生产数据。
