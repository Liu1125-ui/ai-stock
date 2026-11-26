# SDD006预警设计自审

2026-10-10，审查范围为设计草案与现有实现契合度，不执行功能开发、数据库迁移或外发邮件。结论：原草案存在会影响正确性的遗漏，已修订为V1.1，仍待用户审核。

## 已修订问题

1. 需求确认清单第7节采用“超过”，草案涨跌幅却用>=/<=，相等边界不一致。已统一四种比较为严格高于/低于，UI明确比较符。
2. 仅用当前RAW日期作为规则起效边界，滞后数据和积压事件可能给新规则补发创建前提醒。已加入应到期交易日、周末/完整日历、生效边界、过期事件跳过和最新日期对账；修改与重新启用也有明确边界。停用不依赖行情接口。
3. 后复权计算曾用“优先”但未说明替代行为，可能退回RAW并误报除权跌幅。已固定可比后复权口径、明确公式与前一交易日、不先舍入比较、标识与普通昨收参考价的区别；只有价格规则不依赖复权采集。
4. 已有TaskWorker.runOnce/recover把非PREDICTION_VERIFY任务当MARKET_REFRESH，直接添加预警类型会错路由。方案要求明确类型分派、同步表约束、独立游标及旧类型回归；既有预测验证使用的事件游标不得被预警消费推进。
5. 非触发和缺证据原因缺少持久载体，服务超时也缺恢复回执。已补alert_evaluation_operation、分页/游标原子提交、规则修订校验及有界补查。
6. 邮件渠道切换和邮箱变化语义不全，旧邮件可能被重新开启或发送到新地址。已增加渠道修订、收件人身份绑定、旧待发取消、规则停用/修改取消、关闭再开启不复活，以及触发时未选择邮件不补发。
7. SMTP发送中进程崩溃可能使重试重复发送；跨服务核验也不能确保零竞态。已明确过期SENDING转UNKNOWN、不盲目重发、已进入SMTP不可保证撤回，以及依赖失败/投递有效期处理。不把SMTP接受等同收件成功。
8. 补规则触发记录入口、批量已读所有权原子校验、未读数和首页摘要、移出自选不自动停规则；不增加新业务类别。

## 核对依据

- 需求与设计/股票分析系统_需求分析说明书_V1.2.md：FR-07、AC-07。
- 需求与设计/股票分析系统_需求确认清单_V1.1.md：第7节及通知设置。
- backend/stock-scheduler-service/src/main/java/com/aistock/scheduler/task/TaskWorker.java：现有两类任务分派。
- backend/stock-scheduler-service/src/main/java/com/aistock/scheduler/task/RevisionScanRepository.java：独立游标与事务分页模式。
- backend/stock-notification-service/src/main/java/com/aistock/notification/email/EmailCommand.java、SmtpMailTransport.java及V1__email_delivery.sql：仅注册验证码模板/类型，不可直接作为预警邮件投递。
- backend/stock-market-service/src/main/java/com/aistock/market/realization/RealizationValidator.java及backend/algorithm/app/forecast/provider.py：已有配对RAW与后复权证据，但真实验证合同限定五个预测目标，预警需新增专用合同而非伪造预测。

## 尚未声称完成的事项

尚未实现预警业务、接口、数据表或邮件模板。字段级合同、错误码、租约和可配置上限将在设计确认后的实施计划中明确；跨服务账号/邮箱变更不能用本地行锁解决。尚未开展涨跌幅预警证据专项采集核验，实施前先以实际来源确认该专用证据链，再做页面和调度联调。本轮只运行文档差异检查，不把既有其他业务测试冒充本轮预警测试。没有独立审查代理参与。
