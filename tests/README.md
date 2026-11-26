# 跨服务测试

`check-cross-service.ps1` 是本机联调脚本：先在 `backend/` 执行 `mvn package`，然后在 PowerShell 运行它。脚本启动行情与预测服务，检查数据库联调样本、Feign 返回值、缺少内部服务凭据时的 401、行情停机时的受控 503 和重启恢复，最后停止由它启动的进程。当前使用本机固定地址和 H2；Nacos 与 MySQL 8 的正式验收另做。

check-compose.ps1 使用 PowerShell 7 验证实际容器。默认短暂停止行情验证故障并恢复，-SkipFault仅检查连接。算法容器可执行 docker compose --env-file .env -f deploy/compose.yaml exec -T algorithm python -m pytest -q -p no:cacheprovider。

check-market-business.ps1：核验行情登录边界、三股版本行数；可选-Faults逐一停启算法、行情、用户服务，并在finally恢复。

check-start-system.ps1：无Docker、无付费调用的启动编排隔离测试，验证依赖顺序、失败阻断和原容器身份；运行 pwsh -File tests/check-start-system.ps1。真实启动验收单独运行 scripts/start-system.ps1 -NoBrowser，记录见 docs/startup-reliability-20261104.md。

check-stock-user-workflow.cjs 使用本机 Edge 的独立会话验收股票搜索、分析、预警预填、预测准备与返回搜索。先启动本机系统，再执行 node tests/check-stock-user-workflow.cjs。浏览器中的业务请求由模拟数据响应。
