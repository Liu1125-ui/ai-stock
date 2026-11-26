# SDD-005D3A 事后验证内部流程核验

日期：2026-10-05。范围：MS-02真实配对证据、MS-03固定快照评价和追加验证版本。原预测/模型/输入不覆盖；D3B自动调度和本人页面另实施。

## 实现

1. MS-02内部prepare、固定版本及操作回执；新V7迁移，只写行情库。校验证券、版本、每行RAW、完整日历与正复权值。一次配对中的起点/目标因子换算到原预测股本口径。
2. MS-03内部verify、操作回执及当前结果读取；新V4迁移，只写预测库。读取SUCCEEDED不可变快照，不重新调用Transformer；隐藏记录及后来下线模型仍可验证。
3. 追加证据与验证版本、相同内容去重；来源故障、超时、过期保留已有结果。并发同操作仅执行一次，较早截止日不能回退当前指针。
4. 内部身份在JSON解析前检查；4KiB请求、128KiB依赖正文、禁止重定向、完整正文截止；网络不在数据库事务内。
5. 十进制字符串输出带符号/绝对误差、原点归一化MAE/RMSE、方向命中及独立朴素基线。停牌/缺失/休市修订/未到期不纳入有效分母；零有效点指标null。

## 测试

- 最终scripts/check-auth-task1.ps1 clean verify：236项通过，无失败、错误、跳过；完整日志runtime/sdd005d3-final-verified.log。包括审查与并发修复后的全部测试，不以早先230项代替。
- 真实MS-02证据交给MS-03 Java读取器：RealSourceContractProbe额外1项通过，日志runtime/sdd005d3a-real-contract.log；该探针只读实际保存样本，不造用户预测或写数据库。
- Python既有配对采集test_forecast_source.py：8项通过。容器pytest缓存无写权限提示不影响测试；未修改Python源码。
- RED→GREEN包括缺失实现接口、错误输出字段、废弃任务过期，以及完整回归暴露的JDBC时间类型差异。最终使用SQL截止比较。
- fixtures仅写stock_market_test/stock_prediction_test等隔离库；不是用户实际预测评价或真实来源成功证明。

## 独立审查与实际部署

唯一只读审查Critical0、Important2、Minor0；日历本体及总预算经RED→GREEN修复，附加并发测试暴露的当前读问题也在同一修复轮完成。见[sdd005d3a-review-and-fixes](sdd005d3a-review-and-fixes.md)。

已用验证JAR重建行情和预测容器，行情18082、预测18083、网关18080健康UP；正式库迁移分别V7/V4，完整日历已落库。新验证内部接口无身份401、未知操作404、网关访问内部404。

实际用户预测19ddcb7c-3fba-4df3-ba4f-f8eebfeb3f14仍SUCCEEDED。尚未到首个目标日，verify返回SKIPPED_NOT_DUE、null版本；重放及查询回执一致，评价版本0。snapshot SHA-256前后相同；RAW头、seed43发布状态/revision/模型哈希仍一致。

真实历史证据仅用于采集/契约核验：SH600030起点9月21日，目标9月22、23、24、28、29日，五点VERIFIED，原始与后复权价格配对可用；证据6bc2a016-8594-466d-ba8a-c193d2fcc61c，重放复用。保存日历与contentHash重算一致，MS-03读取成功。首轮探针误含9月25日休市，系统正确CALENDAR_CHANGED、不补价格；之后按真实日历选开市日。没有对该历史探针伪造成功用户预测或报告Transformer准确度。

完整证据见[evidence](evidence/sdd005d3a-20261005.json)。D3A内部范围验收通过，FR-06整体仍未完成。

## 后续验收

D3B接MS-04持久任务/每日计划、本人版本读取与历史详情页面。真实用户预测起点2026-09-30，五个目标2026-10-08、09、12、13、14；截至本日没有已到目标日，不能提前创建实际价格或未来误差。真实历史来源测试仅证明证据采集能力，不等同未来盲测。

代码由Codex实现，CodeArts暂无可核实源码差异；无Git remote，不宣称已push或远程CI。
