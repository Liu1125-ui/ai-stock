# 行情刷新

作用：执行 MS-04 的行情更新尝试，完整 RAW 日线和连续自然日日历通过校验后发布；本模块不建立独立队列。

实现方法：解析前服务认证、操作标识去重、完整历史起点校验、源错误分类、受限 HTTP 候选读取、内容哈希与版本 CAS。已有历史空返回拒绝发布。先锁执行回执，再在写入结束前核验不可续期的截止时间，行情头和 AVAILABLE 回执同事务提交；失败不能覆盖已成功回执。

接口：POST /internal/v1/market/refresh 执行；GET /internal/v1/market/refresh/{operationId} 查询与核对已登记结果。结果 AVAILABLE / LEGITIMATE_EMPTY / FAILED / RUNNING；本阶段已有历史完整刷新不会将空结果作为合法成功。失败保留旧行情，响应丢失后可通过回执恢复，不伪造更新成功。

管理员重取预检：GET /internal/v1/market/admin-refresh-context/{instrumentId}仅查非demo股票的Baostock RAW可用头，no-store，不核对/写回执；沿原内部服务凭证Filter认证。RefreshRequest可带完整冻结约束，执行前检查原版本/修订/起点，发布时继续CAS；旧四参数调用与旧哈希保持兼容。结构错误在反序列化阶段拒绝；结构合法但与当前头不符保存FAILED回执。
