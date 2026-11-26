# 生命周期数据库回归

作用：验证V14升级到V15及全量新库迁移、不可变旧数据和凭据约束。实现方法：仅使用127.0.0.1:13316隔离MySQL中的固定专用schema stock_market_lifecycle_migration_test；Flyway clean仅用于这个测试schema，禁止读取生产连接。Task2另含MarketLifecycleTransactionsMysqlTest：在stock_market_test通过真实连接和屏障验证登记/停用、重放/释放及恢复/新周期竞争，故障trigger仅在隔离库使用并finally清理。

Task3A另有HTTP认证/严格正文、固定owner网络限体/超时/重定向、默认配置、持久回执与异常轮转测试。网络证明只使用本机临时HttpServer或mock，不调用真实MaaS、邮件或数据源。

Task3B有真实HTTP认证/用途/路由与scope清理测试、固定RAW跨预测/分析/比较/估值/验证/预警测试，包含同RAW但未选evidence拒绝、迟到采集不发布、RAW发布与停用双向并发、释放先于预警prepare时不读RAW/建回执。测试数据为显式夹具，不作为真实行情或模型结果。

Task4B能力测试分别检查证据门禁false/true，均不得声称全局enforcement或管理员动作已启用。

Task5D2本地比较proof仍内部鉴权/严格bodyless GET；新增配置组合测试，不满足protocol/evidence gates启动失败。
MarketLifecycleAdminReadHttpTest：通过真实访问过滤器核对匿名、普通用户、管理员、禁止写入、no-store 及分页边界，不连接业务数据库。
