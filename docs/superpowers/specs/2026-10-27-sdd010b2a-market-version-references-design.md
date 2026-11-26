# SDD010B2A 行情版本引用检查详细设计

2026-10-27。用户授权本部分完成后自主规划、检查、编码、测试、部署，不再逐项询问可行性。本切片是执行者选择的SDD010B2前置只读能力，不增加第14项业务；仍为五业务服务、两角色，沿用Java/Spring Boot、Vue3/TypeScript、独立MySQL和现有身份体系。

## 目的与边界

管理员选择固定行情版本后，主动查询它在预测分析、任务调度和通知业务中的候选引用记录。为后续版本可用性管理提供可核对依据；不修改任何版本状态、当前头、已存预测、持仓、对比、问答、任务或通知。此次不实现“停用版本”，也不输出canDisable之类许可字段。

引用概览是各服务分别查询时的快照，非跨库原子快照。结果为包含该UUID标识的记录行数：同一业务可能有结果、操作回执、问答等多行，不能相加当独立用户数/业务数。JSON只做完整标识精确匹配，不以子串匹配；业务语境相同标识可能产生保守候选计数，不能据零引用证明没有在途引用、可以停用或可以删除。

## 数据归属与范围

行情服务先在本库核对股票、版本归属、非演示股票，并读取状态、来源、价格口径和是否查询时当前头。不存在、演示或错归属统一404，不能向其他服务发送查询。

其他服务只查自己的库，通过内部GET返回聚合数量与原因码，绝不返回ownerId、问题正文、持仓金额、邮箱、记录ID或JSON内容；不跨库联表/FK。账户服务持仓只保存用户输入与交易数量/成本，未保存行情版本引用，本片不新增其接口。

- 预测服务：forecast_request.snapshot_json；forecast_verification_version.evidence_json；prediction_explanation和prediction_explanation_operation.context_json；stock_analysis_result/operation的market_version_id与context_json；stock_question_result/operation.context_json；stock_comparison_result的source_snapshots_json/result_json/summary_json；stock_comparison_operation.input_json；comparison_ai_result/operation.context_json；portfolio_analysis_result的snapshot_json/quotes_json/result_json；portfolio_question_result/operation.context_json。同一表多个列用OR，只计该行一次。模型制品、纯参数操作和仅含派生验证ID的字段不计为直接RAW引用。
- 调度服务：scheduled_task.version_id、task_attempt.version_id（候选产物标识，不混称唯一行情使用次数）；verification_revision_scan.raw_version_id、alert_evidence_work.raw_version_id、alert_job.raw_version_id、alert_scan_cursor.event_raw_version、comparison_update_context.raw_version_id、admin_market_refresh_context.base_version_id、admin_market_refresh_operation.base_version_id。
- 通知服务：alert_rule的boundary_raw_version/boundary_json；alert_rule_request.response_json；alert_trigger.snapshot_json。同表OR计数，已停用规则和历史回执也保留计数。

## 契约

公开唯一新资源：GET /api/v1/admin/market-data/{instrumentId}/versions/{versionId}/references，零查询参数、零正文。网关仅转行情服务，沿用权威管理员身份校验；USER403、匿名401、身份服务失效503。精确OPTIONS不调用身份/数据库。公开/internal路径继续404，所有响应no-store。

内部资源：GET /internal/v1/market-version-references/{versionId}。每个拥有引用的服务注册同一条内部路径，本库静态白名单定义不同。需单个有效X-Service-Token，方法仅GET、零参数/正文、规范小写UUID；无授权401，方法/路径不符404，格式400。内部认证先于查询，常量时间比较，不允许请求指定表或字段。

统一包络{code,message,data,traceId}。公开Report含instrumentId、versionId、provider、priceBasis、versionState、currentHead、startedAt、finishedAt、state(COMPLETE/PARTIAL)、owners。每个OwnerResult含owner(prediction/scheduler/notification)、versionId、queriedAt、state(COMPLETE/PARTIAL/UNKNOWN)、reasonCode、groups。每个Group含category、label、state(COMPLETE/UNKNOWN)、rows(规范非负十进制字符串或null)、reasonCode。COMPLETE组仅rows非空，UNKNOWN不能伪造0；owner整体与组一致。网络错误、超时、响应越界/错owner/错版本/格式错误映射该owner UNKNOWN，仍保留其他owner结果，不把原错误正文传出。

## 有界查询

公共技术模块只提供记录、严格校验、只读扫描引擎和内部Token过滤器；业务表定义留在所属服务。扫描引擎使用JDK DataSource/Connection，不向common引入Spring JDBC starter或跨库权限。标识符只能来自代码中经正则校验的静态白名单，版本UUID始终预编译参数。

每个服务同一时刻最多1个引用扫描，忙时返回UNKNOWN/REFERENCE_QUERY_BUSY，不排队。一个独立只读RR事务查询本服务；连接由既有池管理，SQL查询每条1秒超时、扫描预算3秒（在每条SQL前检查，超时余下组UNKNOWN），无HTTP在事务内。连接等待仍受既有连接池上限约束，不声称网络超时可以取消所有服务端工作。

每表先对LIMIT1001子查询统计行数、JSON字节与JSON合法性；超过1000行、JSON合计超过8MiB或有损坏JSON，该组UNKNOWN，不提供截断数量。只有完整覆盖且有效才做精确UUID列/JSON_SEARCH计数（同表OR）；不从数据库读出正文。SQL错误统一REFERENCE_QUERY_UNAVAILABLE，确保回滚/关闭连接/释放扫描许可。

行情服务每个owner最多一次逻辑GET，无业务重试、不转发用户Cookie；HttpClient连接超时1秒、单请求超时4秒、正文64KiB上限，禁止重定向，三个owner顺序访问，页面说明在查询中；最坏约12秒加本库查询，不使用无限等待。内部URL由受控配置给出，不使用用户输入作主机地址。默认本地18083/18084/18085，compose用服务DNS8080。所有owner失败仍返回200/PARTIAL；本库版本核对失败503，不能用远端成功掩盖错归属。

## 页面与故障语义

固定版本详情中显示“检查此版本引用”按钮，默认不请求、不轮询、不取行情、不调用AI、不发送邮件。点击后显示版本状态、查询时是否当前头、起止时间及三个业务区域；每组展示记录行数或明确未知原因。COMPLETE也醒目说明“不能据此直接停用/删除”。不设停用按钮。

503在同版本下保留旧结果并标记未刷新和原时间；换股票/版本/账号清空，旧Promise不回填。401/ACCESS_DENIED403通知父页统一清空私人状态并刷新身份，与已修复重取面板一致。窄屏表格仅区内横滚，键盘按钮可达、有等待和禁用反馈。

## 验证与上线

测试先RED后GREEN。覆盖精确JSON UUID与子串、同表OR去重、无引用0与未知区分、损坏JSON/行数/字节/忙/SQL失败、身份拒绝、错归属不访问远端、错owner/版本/格式/超时/越界、部分成功、重复参数/GET正文、迟到响应与权限撤销。使用独立_test库，不并行清理同一测试库。

一次fresh最终代码审查，重要问题一次修复和回归；部署前完整相关后端/前端测试、类型检查和打包。无数据库迁移。备份本轮受影响五后端(行情/预测/调度/通知/网关)与前端旧镜像和五业务库的必要基线；只重建实际变化服务，账户、算法和原卷保留。部署内部查询owner后，再行情聚合、网关和前端。已有后台计划只在必要只读基线期间短暂停顿并恢复；新人工重取写入口状态不因本片改变，MaaS/邮件开关不变。只读生产核验+管理员页面验收，查询不登记业务操作；不把当前无在途引用当未来停用互锁已实现。

## 自审与后续

自审已识别并明确：全局快照竞态、JSON行重复、隐藏/历史记录引用、服务故障不能计零、大表扫描、身份撤销、旧回执兼容、无迁移回退。SDD010B2后续需另行设计版本状态变更、在途准入互锁和消费者可用性检查，引用概览仅前置证据；FR-11不因此标完成。
