# 版本化预测输入

作用：把已发布RAW版本、同源复权转换和未来交易日历冻结为不可变输入快照。
实现：Validator校验60行配对/单位/来源/日历；Repository以本库事务和唯一键保存快照及操作回执；Service计算稳定哈希、读取固定输入和判断新鲜度；Controller只开放带前置内部认证的发布/查询路由。不会改原始head，不生成模型结果。显式旧inputVersionId可供重试读取，但返回真实新鲜度；新预测必须另外检查时点、模型兼容和权限。
`GET /internal/v1/market/forecast-raw-window?instrumentId=`：准备阶段的内部读取，只返回当前RAW head固定版本的末60行与内容哈希，受CandidateRequestFilter认证；不使用用户Cookie接口、不扩大公共访问范围。

准备窗口新增 `freshness`：FRESH/UNKNOWN。若当前RAW日历明确表明截至更新时点缺少应有交易日，直接返回 `FORECAST_INPUT_STALE`，不再为已过期原始窗口采集复权输入。日历覆盖不足返回UNKNOWN，仍允许原有同源采集核验；新预测最终必须通过FRESH检查。节假日和每日更新时点按上海时区计算。显式历史输入及实际验证流程不受此准备入口限制。

SDD010B3 Task3B：计算证据读取/派生发布接入MarketEvidenceGate.local短事务校验，按业务凭据固定RAW；外部采集在事务外，发布前再次校验。门禁默认关闭，实际owner转发和完整上线仍在后续阶段，历史快照与只读元数据保留。
