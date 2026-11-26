# 工程状态契约

`GET /api/v1/system/status` 返回 `{code:"OK",message:"成功",data:{component,phase:"ENGINEERING_SCAFFOLD"},traceId}`。它只说明进程接口可达。算法 `POST /internal/v1/infer` 在未配置已发布模型时返回 HTTP 503，不返回假预测。

`GET /internal/v1/instruments/{instrumentId}` 属于行情服务；必须带有效 `X-Service-Token`，返回 `instrumentId/code/name/demoOnly/sourceService`。`GET /internal/v1/diagnostics/instruments/{instrumentId}` 属于预测服务联调接口，同样要求服务凭据，经 Feign 获取行情结果。两个接口都不经网关公开。行情停机时预测服务返回 HTTP 503、`code=DEPENDENCY_UNAVAILABLE`、`data=null` 和 `traceId`。当前本机联调使用 `DEMO-001` 虚构样本，不是公开股票查询接口。
