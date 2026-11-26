# SDD010B3 Task5D2 市场比较派生执行保护计划

父Task5本地派生分项，连续执行；当前功能末单次独立审查，不增加例行确认。

背景：比较更新的fixedWindow目前先于持久begin，HTTP外层门禁要求凭据又先于真实市场操作身份。调度器不应冒充市场owner。改由内部认证后的比较服务自身先准入、登记原RAW，再读取窗口及外部采集，原操作结果保存后才证明终态。

1. [x] 先写反例：固定RAW被停止则无fixedWindow/collector；先准入后读取；相同请求重放无登记/取数；采集在途停用处于DRAINING；终态证明过期先物理失败，旧执行时钟回拨不能保存；丢失登记回执只查原编号。
2. [x] V18新增比较操作fencing及market_local_comparison_use原编号/RAW/回执；真实comparison_refresh_receipt为owner来源，原operation UUID/generation=1。prepare锁父操作确认原指纹；网络/采集不持锁，grant登记独立短事务，挂接回执只锁使用行，避免parent→use与use→parent反锁。
3. [x] 新MarketComparisonUseOwner使用同服务MarketLifecycleService原requestId登记/查询，Scope固定原RAW。恢复每批25条只核对/证明；不能为丢失状态重POST或重新采集。proof锁实际比较操作，锁后读取Clock，先物理过期再证明；终态fencing与真正发布/失败同事务。
4. [x] 比较refresh服务先begin，再acquire，再fixedWindow/collector/complete。完成/失败后关闭scope再通知；默认false不改变旧配置。市场本地生产者开关与protocol/evidence gates绑定，配置不一致启动失败。仅开启该owner时，HTTP comparison-refresh POST从外层凭据改由服务准入承担，内部token始终先验证；其他证据路径保持原门禁。
5. [x] 内部market proof控制器/认证增加实际支持COMPARISON_EVIDENCE_PUBLISH；市场本地原comparison_refresh_receipt.raw_version_id外键继续保留引用，使用记录跟随该父操作并由已有生命周期use表核对在途；跨服务固定扫描不扩owner，新开关false。未覆盖alert/realization/forecast前保持PARTIAL，禁止全局动作。
6. [x] 真实双连接在途/终态竞争、HTTP认证/门禁与本机采集边界测试，完整行情verify、独立审查、单功能提交。接着Task5D3预警本地派生及其余入口审计，再Task6—8。

工程边界：无真实数据源/AI/邮件；无生产迁移/部署。保留原一年窗口、原operation指纹、原预算/期限，不切换新头，不重写历史证据。

结果：393项完整verify/打包通过，独立审查无重要问题；V18仅隔离库，未部署。继续D3预警本地派生。
