# SDD-004 行情持续更新设计
日期：2026-10-02。依据需求 V1.9 MS-02/MS-04、FR-11/13及 pending-items TODO-02。
用户授权：用户要求想好后开始、先计划再检查最后实施，并授权常规细节自主处理；本次按该顺序执行，不逐项重复确认。

## 目的与范围
使已发布真实日线的股票能够持续更新；失败保留旧版本，任务可追踪、可重试、重启可恢复。先完成内部运行闭环；管理员任务页面、完整股票基础资料、预测/预警后续任务仍属后续阶段，13项业务和五服务不变。
选择：MS-04数据库任务队列 + MS-02同步执行接口 + Python有界采集；不在Python建立独立调度计划。相比直接定时调用，持久队列可恢复；相比增加MQ，减少部署依赖。

## 数据与采集
当前支持Baostock RAW日线，仅更新已有真实行情头的股票。完整快照从原版本coverage_start采集至目标日，保留指标历史输入。Python同会话查询query_trade_dates；返回自然日连续日历（含休市），不得把日历获取失败当空数据成功。支持范围1500自然日以内，超范围明确失败，不静默裁切。
刷新必须覆盖原起点，日历覆盖全部请求日期，开放交易日应有行情行；暂停交易行允许tradeStatus=0。已有历史的完整刷新全空返回为MARKET_DATA_INCOMPLETE，拒绝发布，不能伪装合法空成功。这比历史导入更严格，历史导入仍可标记CALENDAR_UNKNOWN。
行情来源非自动混用，不购买权限，不写Tushare Token。

## 服务接口
MS-02 POST /internal/v1/market/refresh，服务认证在JSON解析前；字段operationId（尝试ID）、instrumentId、targetDate、leaseUntil。返回统一响应，data为operationId/state/versionId/errorCode；state AVAILABLE/LEGITIMATE_EMPTY/FAILED/RUNNING。GET /internal/v1/market/refresh/{operationId}用于查询和核对回执，同样在解析前校验服务身份。
执行回执由MS-02保存，重复相同operationId请求复用已结束结果，内容冲突409，正在运行409。回执是执行去重记录，不是另一套排队计划。
采集子进程最多25秒，Java算法请求配置连接2秒/请求30秒，调度Feign配置连接2秒/读取45秒；不把这些配置写成所有传输因素的严格总体墙钟时限证明。发布沿用内容哈希与expectedRevision，先锁刷新回执，将可能阻塞的写入放在最终期限检查前，再检查leaseUntil；行情头、导入结果和AVAILABLE回执同事务提交；过期尝试不能发布，失败不改变旧版本行情。leaseUntil不可续期或同ID变更，最长120秒。
MS-04内部接口 /internal/v1/tasks：POST创建{instrumentId,targetDate,key}；GET列表/GET{id}详情；POST{id}/retry仅失败任务；POST{id}/cancel仅QUEUED/RETRY_WAIT。所有接口服务认证，网关不公开，脚本读取忽略的本地服务凭证。

## 任务与计划
MS-04独立库记录计划、任务、尝试。计划以股票ID为键，每日北京时间20点后创建股票/目标日唯一任务；20点前最新到期日为昨日，20点后为当日；重启仅补最新到期一天，重复触发不创建新任务。休市日也允许日历核验，不能假装有新价格。
状态QUEUED/RUNNING/RECONCILING/RETRY_WAIT/SUCCEEDED/EMPTY/FAILED/CANCELLED；数据库行锁领取，最大3次，租约90秒，序列worker。HTTP在事务外，结果不确定或租约过期时先核对MS-02回执，任务RECONCILING、尝试UNKNOWN；确证失败后才按条件重试，确证成功可恢复原任务结果，旧令牌不能覆盖更新的尝试。
SOURCE_TIMEOUT/SOURCE_BUSY/SOURCE_UNAVAILABLE及版本冲突可重试；数据非法、参数非法、无真实版本不可自动重试。退避60秒、120秒；用户手动重试清零次数并新建尝试，原尝试保留。
执行期间不强制取消；终态重试须显式触发。默认工作器与每日计划开关关闭；部署验证后显式启用。计划列表配置不限制以后增加股票。

## 安全与验收
不跨库读写；调度只保存versionId引用。服务Token不出现在日志或响应，内部路由经网关404。候选响应限制2MB，拒绝未知字段和非法日期、身份、来源、范围。
验收：真实更新与日历、重复任务/版本、日历缺失/闭市行情拒绝、超时失败旧头保留、过期发布阻断、并发领取唯一、重启租约恢复、重试/取消条件、内部认证及网关隔离。测试使用独立MySQL测试库及伪造SDK边界，不把测试数据发布到业务库。

## 自查结论
职责/数据库边界与需求一致；版本不可变和截止期限双检查已列入；来源错误不伪装空数据；任务状态与回执用途区分；本阶段不承诺真实预测或扩大业务。实现后真实外部源验收已完成，三股日线/完整日历/FRESH查询、同内容去重、每日计划及重启有证据；首次源故障及保留旧头也已记录。详见verification-sdd004.md。
