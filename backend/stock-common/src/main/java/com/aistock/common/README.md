# 公共 Java 包

`ApiResponse` 固定公开响应结构；`TraceIds` 对外来关联 ID 做有限格式校验，避免把任意长用户字符串带入日志和响应。验证：运行 common 模块测试。
