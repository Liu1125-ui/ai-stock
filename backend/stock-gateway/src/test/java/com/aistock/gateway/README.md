# 工程入口 测试

验证工程状态返回结构与组件标识，不代替业务测试。在 `backend/` 运行 `mvn -pl stock-gateway -am test`。

NotificationProxyTest：预警路由精确白名单，内部端点不可转发、身份头/查询过滤、请求体上限。
