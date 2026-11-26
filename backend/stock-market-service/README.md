# 行情服务

证券资料及行情数据归属。当前实现了带服务身份校验的联调证券读取接口 `GET /internal/v1/instruments/{id}`，使用 Flyway 管理本服务的 `instrument` 表。`DEMO-001` 为虚构联调样本，`demoOnly=true`，不可当真实行情。未设置 `MARKET_DB_URL` 时使用内存 H2，仅供本机联调；MySQL 8 与独立账号尚待验证。启动前设置 `SERVICE_TOKEN`，在 `backend/` 执行 `mvn -pl stock-market-service -am spring-boot:run`。默认端口 8082。Actuator `UP` 不表示行情数据业务已完成。
