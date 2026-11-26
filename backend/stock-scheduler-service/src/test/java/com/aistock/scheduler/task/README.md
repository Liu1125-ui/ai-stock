# 调度测试
作用：验证任务幂等、并发领取、租约恢复和状态转换。方法：独立stock_scheduler_test MySQL，真实事务与可控时间，不访问行情业务库。

ComparisonTaskMysqlTest在13316隔离库验证日去重、并发创建/领取、固定快照、累计预算、跨日过期、类型筛选及父子事务回滚；未知股票是否ACTIVE由市场服务核对，不跨库读取。

ComparisonUpdatePlanTest/ComparisonTaskClientTest/ComparisonTaskWorkerTest覆盖收盘边界、跨午夜、真实本地HTTP协议及超时、四种开关组合、原操作响应丢失、未知状态和404到期恢复。

Task5D1：真实HTTP验证明确拒绝与未知分流、原RAW/attempt；MySQL验证比较/验证拒绝不自动重试。
