# 用户服务

用户和自选股的数据归属。当前只有进程健康和工程状态端点，业务实现按后续 SDD 增量添加。独立启动：在 `backend/` 执行 `mvn -pl stock-user-service -am spring-boot:run`；默认端口 8081，可用环境变量覆盖。此端点不得标注为行情或预测完成。
