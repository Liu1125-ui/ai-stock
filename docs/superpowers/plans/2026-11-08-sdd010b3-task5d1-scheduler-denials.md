# SDD010B3 Task5D1 调度许可拒绝处理计划

连续实施，无例行确认。父Task5后台任务的一部分，尚不代表本地生产者接入完成。

1. [x] HTTP反例先测：比较更新、预测验证、预警准备/评价收到确定性许可拒绝时，以原attemptId失败；未知回执/超时继续原编号核对，不盲目POST。
2. [x] 共享小型错误分类：仅409的MARKET_VERSION_USE_BLOCKED/MARKET_VERSION_UNAVAILABLE/MARKET_USE_ALREADY_RELEASED/MARKET_USE_BINDING_MISMATCH/MARKET_USE_NOT_READY，以及503的MARKET_USE_NOT_READY可作为明确拒绝；其他响应保持未知。
3. [x] 三个执行适配器映射，receipt只读不转换成伪回执。记录安全中文原因，保持原冻结RAW/原attemptId，不换头。现有重试白名单不扩大；真实库证明明确拒绝不会自动重试，既有在途取消保护不变。
4. [x] 专项、调度全量verify/打包、独立审查，单功能提交。继续Task5D2市场本地派生登记及完整入口审计；本阶段不部署。

结果：独立审查Important已以重复/尾随JSON反例RED→GREEN修复，349项verify及打包通过；未部署，继续D2。
