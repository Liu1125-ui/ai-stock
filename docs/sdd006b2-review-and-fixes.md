# SDD006B2 最终独立审查与修复

审查范围：0c6dbfa..2c8f642，Task1至Task6。独立审查者sdd006b2_final_review；只读审查源码、规格和账本，未改业务数据库，未调用外部数据源。无Critical，2项Important，2项Minor。重要问题均进入本轮一次修复流程，不再派重复审查。

## Important：旧日期回执契约
MS-02真实返回SKIPPED_STALE / ALERT_DATE_NOT_LATEST，MS-04先前只接受空errorCode，导致合法响应及后续回执反复成为UNKNOWN。HTTP客户端和本地任务核验同步接受该明确原因，保留非法状态/引用校验。actualMarketStaleReasonIsAcceptedForExecuteAndRecovery与actualStaleReceiptTerminalizesAndReplaysAcrossRestart均RED（受控异常），修复后GREEN；重启可读原回执并结束共享工作。

## Important：共享源积压阻塞其他股票
候选先LIMIT50，再跳过共享RUNNING源，可无限重复相同50个等待者。现选候选时在LIMIT之前排除共享RUNNING和未到期RETRY_WAIT源，实际加锁后仍检查最新工作状态，不绕过未决操作。blockedSourceCannotStarveAnotherStockAfterFiftyWaiters RED（其他股票claim为空），修复后GREEN；只取有界50个候选，保持未知工作身份。

定向调度42项通过，日志runtime/sdd006b2-review-red.log及runtime/sdd006b2-review-green.log。最终全套回归及部署事实另见验收记录，不能用定向通过代替上线。

## Minor：延后
- 网关ProxyHeaderPolicy只转发首个Origin/CSRF头，通知端新加的重复头检查无法检测网关丢弃的重复值。未发现浏览器可利用的跨站攻击，既有来源/会话/CSRF仍有效；后续补网关层严格重复头拒绝。
- 规则页以当前分页行推断evaluationAvailability；空页可能显示未启用，即使部署开关已开启。后续由明确服务状态契约修正空页提示，不新增伪造运行状态。

## 审查未覆盖与本轮裁定
来源行情/日历准确性：独立审查未验证，继续用当前三股已有真实来源证据核对，不将代码审查视为数据真实性证明。
部署与凭据：由本轮本机健康、内部鉴权、数据保全检查验证，不输出凭据。
长期调度吞吐：单次运行与隔离并发测试不能证明长期连续可用，继续待观察。
浏览器视觉：部署后本轮检查，窄屏能力如工具不支持须明确记录。
邮件C、模型与MaaS：本轮不修改、不发送邮件、不调用模型，保留独立待办。
真实触发：严格受有效交易日、生效边界和启停状态约束，无符合条件规则则记录待未来交易日，不造通知。

## 部署中发现的覆盖契约问题（作者诊断）
真实日历refresh已成功，但默认current保留更远future的短历史头，导致扫描CALENDAR_SNAPSHOT_UNVERIFIED。新增严格有界start/end内部查询，复用covering选择；调度请求明确367天覆盖。没有改头策略或放松验证。HTTP RED503/200与客户端RED缺查询均复现，13项定向GREEN，最终全后端487/前端102/算法76及构建通过。已部署并核对自动日历、事件/补查游标、重启和原数据保全；详见verification-sdd006b2.md。这不是独立审查结果。
