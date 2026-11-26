# SDD006B0 交易日历准备 Implementation Plan

> REQUIRED SUB-SKILL: superpowers:executing-plans。按已批准SDD006设计及用户“下一步”以内联方式执行；先测试、再实现、最后核验。本次只完成B的日历前置，不能宣称自动评价或站内信已完成。

Goal: 预警边界不再因RAW快照日历止于最后行情日而在休市日失效；使用来源明确、完整、持久化的独立日历，禁止猜测。
Architecture: algorithm单独查询Baostock日历，沿用共享单采集槽、子进程超时和内部令牌；MS-02验证完整连续日期与哈希后持久化不可变版本和幂等回执。边界优先使用覆盖有效且未过期的独立日历，未覆盖时仅可使用完整RAW日历；历史RAW和预测版本不修改。MS-04自动刷新/扫描在后续B切片接入，本片先提供受保护刷新接口。
Spec: docs/superpowers/specs/2026-10-10-sdd006-alert-notification-design.md V1.1；A验收及当前日历覆盖限制见docs/verification-sdd006a.md。

## Global Constraints
保持13业务、五服务、两角色，不跨库。日历只有真来源采集允许发布，不因周一至周五猜测开市；未知、缺日、重复、非法标志、源失败保留旧版。内部接口不经网关公开。无邮件、MaaS、行情刷新或新预测；只发布独立日历缓存。
当前策略：最多400天连续区间、结束日不超过北京时间今日90天、开始不早于今日366天；新采集时间在5分钟内、允许时钟前漂5秒。独立日历使用TTL默认7天可配置；请求operationId幂等，同ID不同范围409。请求体4096字节、响应256KiB、采集子进程25秒，Java连接2/读取30秒。

## Review Focus
- 长假/周末20点后日期覆盖、未来截止时点测试；不能以采集当天成功替代未来钟点模拟。
- 来源截断/重复/失败、非法布尔、哈希不一致，不得覆盖好版本。
- 同操作重试不再采集；并发提交及旧响应不倒退当前头；查询读不可变版本。
- 内部接口匿名/错误令牌拒绝，网关404，真实新旧预测和邮件计数不变。
- 日历过期或不包含RAW最后日期时不采用；RAW自身完整时可以保留现有行为，否则受控拒绝。

## Task 1 — 独立来源采集
Files: algorithm/app/calendar/{provider.py,worker.py,README.md}、app/main.py、tests/test_calendar_source.py、tests/README.md。
- [x] RED: 独立查询无股票价格调用，连续含休市日，缺失/重复/非法标志/源错误；HTTP身份/请求界限/繁忙/超时。
- [x] 实现严格请求及单SDK会话，完整日历与规范哈希；子进程沿用共享槽，客户端断开也由工作线程持有到退出。
- [x] pytest通过，README说明来源及用途，本地提交。
Produces: operationId/state/candidate，candidate为provider、start、end、fetchedAt、sourcePayloadHash、days(date/open)；成功不得缺candidate，失败不得带candidate。

## Task 2 — MS-02日历持久化及边界消费
Files: calendar/{CalendarRecords.java,CalendarCollector.java,CalendarService.java,CalendarController.java,README.md}；refresh/CalendarCollectorClient.java；V9__independent_calendar.sql；alert/AlertBoundaryService.java；配置；tests/calendar及support/MarketDatabaseFixture.java。
- [x] RED: MySQL幂等/冲突、完整验证/失败保留、旧响应不覆盖、TTL与覆盖；真实服务代理读回。边界周末/交易日20点前后、缺日拒绝；长假评价场景留后续B。
- [x] 独立版本/头/操作回执同库短事务；HTTP在事务外；并发最终检查回执。只有已验证日历进入当前头；旧版本保留。用构造器注入，不访问代理字段。
- [x] POST /internal/v1/market/calendar/refresh 输入operationId/start/end；GET /internal/v1/market/calendar 返回当前版本及范围/采集时间元数据；GET不承诺覆盖，实际边界必须调用covering核验TTL和所需日期。Controller严格JSON/现有内部令牌保护；失败统一BusinessException。
- [x] 预警边界消费新日历，保持既有返回合同；行情RAW日历完整时可回退原逻辑，缺失仍拒绝。不修改已存规则边界或预测输入。
- [x] 定向和隔离MySQL受影响回归通过；本地提交。
Consumes: Task1严格candidate；Produces: 固定snapshot与覆盖查询，供后续MS-04调用。

## Task 3 — 审查、本机验证与文档
- [x] 完整后端回归及algorithm测试；一次全切片独立审查，Important用RED/GREEN修复，Minor记入待办。
- [x] 只更新algorithm/market；真实只读采集2026-07-01至2026-11-09日历后发布独立版本（不发布行情），核验真实休市标记与固定未来时点边界；有界失败如实记录。
- [x] 六服务健康、内部401/网关404、三股边界、新日历持久化；原行情头/预测模型/邮件数量保持不变。
- [x] 更新状态、待办、验收及执行记录；只本地提交。不开启自动扫描/站内通知/预警邮件，本片不声称B全完成。

## Preflight
Task1candidate严格字段与Task2HTTP解码一致；canonical哈希使用每行date+冒号+(1/0)+换行的UTF-8 SHA256，排序且不得漏自然日。Task2 snapshot与AlertBoundary已有definition不变，calendarHash仍由实际所用日历计算。沿用当前feature checkout和本机固定部署路径；不是main，不合并不push。Windows原生ledger代替POSIX辅助脚本，记录全部取舍。

## Final rulings
最终审查要求防止未来子区间丢失当日覆盖：covering查询所有未过期且满足所需区间的不可变版本，而非只查询头。GET状态字段Minor延后，当前明确为审计快照；后续调度必须另行判定覆盖和TTL。真实验证及边界模拟见verification-sdd006b0.md。
